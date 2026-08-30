package com.example.blockmod.input;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.logic.GuardEquipmentResolver;
import com.example.blockmod.logic.MovementService;
import com.example.blockmod.network.GuardInputPayload;
import com.example.blockmod.network.SyncThrottler;
import com.example.blockmod.registry.ModAttachments;
import com.example.blockmod.state.GuardStateData;

import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * T-27 server side: turns validated client guard intent into the authoritative
 * {@code GuardStateData#guarding} state.
 *
 * <p>Validation (E-11/E-12): a token-bucket rate limit of
 * {@code c2s_rate_limit_per_second} packets per player, and the request is
 * rejected with a WARN when the player holds no guardable equipment. While a
 * guard is held, the client sends heartbeats every {@code state_heartbeat_ticks};
 * the server drops the guard state after {@code guard_timeout_ticks} without one
 * (R-07), removing the move malus with it.
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class ServerGuardInputHandler {
    private record RateWindow(long secondStart, int count) {}

    private static final Map<UUID, RateWindow> RATES = new HashMap<>();
    private static final Map<UUID, Long> LAST_INPUT_TICK = new HashMap<>();

    public static void handle(ServerPlayer player, GuardInputPayload payload) {
        long now = player.level().getGameTime();
        if (!consumeRateToken(player, now)) {
            return; // E-12: over the limit — drop silently (WARN throttled by the token bucket)
        }
        if (payload.guarding() && GuardEquipmentResolver.resolve(player) == null) {
            BlockModLogger.warn("GUARD_INPUT", "action", "rejected", "player", player.getGameProfile().getName(),
                    "reason", "no guardable equipment");
            return; // E-11: no shield/sword — ignore the request
        }

        GuardStateData guardState = player.getData(ModAttachments.GUARD_STATE.get());
        if (guardState.isGuarding() != payload.guarding()) {
            guardState.setGuarding(payload.guarding());
            guardState.setGuardHand(payload.guarding() ? guardHand(player) : guardState.guardHand());
            if (payload.guarding()) {
                guardState.setParryUsed(false);
                // M4 (T-31/T-34) opens the parry window here; M3 keeps the state only.
                BlockModLogger.info("GUARD_INPUT", "action", "enter", "player", player.getGameProfile().getName());
            } else {
                MovementService.remove(player, guardState);
                BlockModLogger.info("GUARD_INPUT", "action", "exit", "player", player.getGameProfile().getName());
            }
        }
        LAST_INPUT_TICK.put(player.getUUID(), now);
        SyncThrottler.forceSync(player); // guard enter/exit always syncs immediately (FR-23)
    }

    private static net.minecraft.world.InteractionHand guardHand(ServerPlayer player) {
        // FR-11: the offhand wins whenever it can guard; the resolver already ran above.
        return player.getOffhandItem().isEmpty()
                || com.example.blockmod.logic.GuardEquipmentResolver.resolve(player).slot()
                        == net.minecraft.world.entity.EquipmentSlot.MAINHAND
                ? net.minecraft.world.InteractionHand.MAIN_HAND
                : net.minecraft.world.InteractionHand.OFF_HAND;
    }

    /** E-12: token bucket — one second windows, {@code c2s_rate_limit_per_second} tokens each. */
    private static boolean consumeRateToken(ServerPlayer player, long now) {
        long second = now / 20L;
        RateWindow window = RATES.get(player.getUUID());
        if (window == null || window.secondStart() != second) {
            RATES.put(player.getUUID(), new RateWindow(second, 1));
            return true;
        }
        if (window.count() >= Config.c2sRateLimitPerSecond()) {
            return false;
        }
        RATES.put(player.getUUID(), new RateWindow(second, window.count() + 1));
        return true;
    }

    /** R-07: drop guards whose client stopped sending heartbeats. */
    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        MinecraftServerHolder.server().getPlayerList().getPlayers().forEach(player -> {
            GuardStateData guardState = player.getData(ModAttachments.GUARD_STATE.get());
            if (!guardState.isGuarding()) {
                return;
            }
            Long last = LAST_INPUT_TICK.get(player.getUUID());
            long now = player.level().getGameTime();
            if (last == null || now - last > Config.guardTimeoutTicks()) {
                guardState.setGuarding(false);
                MovementService.remove(player, guardState);
                SyncThrottler.forceSync(player);
                BlockModLogger.warn("GUARD_INPUT", "action", "timeout_drop", "player",
                        player.getGameProfile().getName());
            }
        });
    }

    @SubscribeEvent
    static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RATES.remove(event.getEntity().getUUID());
        LAST_INPUT_TICK.remove(event.getEntity().getUUID());
    }

    private ServerGuardInputHandler() {}

    /** Minimal indirection so the tick handler does not capture the server instance. */
    private static final class MinecraftServerHolder {
        static net.minecraft.server.MinecraftServer server() {
            return net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
        }
    }

    private static void unused(PacketDistributor packetDistributor) {
    }
}
