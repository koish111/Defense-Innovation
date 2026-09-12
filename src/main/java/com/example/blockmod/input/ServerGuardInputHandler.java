package com.example.blockmod.input;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.logic.GuardEquipmentResolver;
import com.example.blockmod.logic.GuardEquipmentResolver.GuardEquipment;
import com.example.blockmod.logic.GuardRules;
import com.example.blockmod.logic.MixinHooks;
import com.example.blockmod.logic.MovementService;
import com.example.blockmod.network.GuardInputPayload;
import com.example.blockmod.network.SyncThrottler;
import com.example.blockmod.registry.ModAttachments;
import com.example.blockmod.registry.ModSounds;
import com.example.blockmod.state.GuardStateData;

import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

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
            return; // E-12: over the limit — drop
        }
        GuardStateData guardState = player.getData(ModAttachments.GUARD_STATE.get());
        if (guardState.isGuarding() && !reconcileEquipment(player, guardState, now)) {
            return;
        }
        GuardEquipment equipment = payload.guarding() ? GuardEquipmentResolver.resolve(player) : null;
        if (payload.guarding() && equipment == null) {
            BlockModLogger.warn("GUARD_INPUT", "action", "rejected", "player", player.getGameProfile().getName(),
                    "reason", "no guardable equipment");
            return; // E-11: no shield/sword — ignore the request
        }
        if (payload.guarding() && MixinHooks.isStunned(player)) {
            BlockModLogger.warn("GUARD_INPUT", "action", "rejected", "player", player.getGameProfile().getName(),
                    "reason", "stunned");
            return; // FR-05: a stunned player can neither raise nor hold a guard
        }

        if (guardState.isGuarding() != payload.guarding()) {
            guardState.setGuarding(payload.guarding());
            if (payload.guarding()) {
                guardState.setGuardHand(equipment.hand());
                guardState.setGuardEquipment(equipment.stack(), equipment.profile().type());
                // FR-17 decision table: mount the malus immediately on a valid guard enter
                // (depleted entries are gated out inside apply — staminaPositive check).
                com.example.blockmod.logic.MovementService.apply(player, guardState, equipment.profile(),
                        player.getData(ModAttachments.STAMINA.get()).canDefend());
                // T-34/§5.13: the parry window opens on guard enter (gated by ADR-07 cooldown).
                com.example.blockmod.logic.ParryService.openWindow(player, guardState, equipment.profile(), now);
                // T-40: the raise cue splits by equipment class (sword vs shield).
                ModSounds.play(player, equipment.profile().type() == ShieldType.SWORD
                        ? ModSounds.SWORD_START_BLOCK : ModSounds.SHIELD_START_BLOCK, 0.8f, 1.0f);
                BlockModLogger.info("GUARD_INPUT", "action", "enter", "player", player.getGameProfile().getName(),
                        "hand", equipment.hand());
            } else {
                MovementService.remove(player, guardState);
                com.example.blockmod.logic.PowerGuardService.disarm(player, guardState, now); // §5.7: PG ends with the guard
                // T-34/ADR-07: the re-entry cooldown anchors to the release moment.
                com.example.blockmod.logic.ParryService.closeWindowOnRelease(player, guardState, now);
                BlockModLogger.info("GUARD_INPUT", "action", "exit", "player", player.getGameProfile().getName());
            }
        }
        LAST_INPUT_TICK.put(player.getUUID(), now);
        SyncThrottler.forceSync(player); // guard enter/exit always syncs immediately (FR-23)
    }

    public static void handlePowerGuard(ServerPlayer player, boolean active) {
        long now = player.level().getGameTime();
        if (!consumeRateToken(player, now)) return;
        GuardStateData guard = player.getData(ModAttachments.GUARD_STATE.get());
        if (!reconcileEquipment(player, guard, now)) return;
        com.example.blockmod.logic.PowerGuardService.handleActivation(player, active, now);
    }

    /**
     * 2026-09-11 ruling (guard interaction lockout), server-authoritative
     * backstop: a raised guard blocks EVERY vanilla interaction — attack, dig
     * (start and continue), block use, item use, entity interact. The client
     * suppresses the intent already (ClientGuardInputHandler); these
     * cancellations close hacked clients. All six events fire on the server
     * side via NeoForge patches (Player.attack, ServerPlayerGameMode,
     * ServerGamePacketListenerImpl). The guard input itself rides custom
     * payloads and raw mouse events, so nothing of this mod is affected; E-07
     * (container exit) degrades to a fallback because containers can no longer
     * be opened while guarding.
     */
    @SubscribeEvent
    static void onGuardAttackEntity(AttackEntityEvent event) {
        if (isGuarding(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onGuardLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isGuarding(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onGuardRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isGuarding(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onGuardRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isGuarding(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onGuardEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isGuarding(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onGuardEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isGuarding(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private static boolean isGuarding(net.minecraft.world.entity.player.Player player) {
        return player instanceof ServerPlayer serverPlayer
                && serverPlayer.getData(ModAttachments.GUARD_STATE.get()).isGuarding();
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
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            GuardStateData guardState = player.getData(ModAttachments.GUARD_STATE.get());
            if (!guardState.isGuarding()) {
                continue;
            }
            Long last = LAST_INPUT_TICK.get(player.getUUID());
            long now = player.level().getGameTime();
            if (!reconcileEquipment(player, guardState, now)) {
                continue;
            }
            if (last == null || now - last > Config.guardTimeoutTicks()) {
                dropGuard(player, guardState, now);
                BlockModLogger.warn("GUARD_INPUT", "action", "timeout_drop", "player",
                        player.getGameProfile().getName());
            } else if (MixinHooks.isStunned(player)) {
                // FR-05: the stun force-lowers the guard — no block, no parry window,
                // no PG. Runs every tick the stun + guard pair holds, so the guard can
                // never survive a stun regardless of what the client keeps sending.
                dropGuard(player, guardState, now);
                BlockModLogger.warn("GUARD_INPUT", "action", "stun_drop", "player",
                        player.getGameProfile().getName());
            }
        }
    }

    /** A replaced or newly forbidden item cannot retain guard, parry, PG or interaction lockout. */
    public static boolean reconcileEquipment(ServerPlayer player, GuardStateData guardState, long now) {
        if (!guardState.isGuarding()) {
            return true;
        }
        int expectedSlot = guardState.guardHand() == net.minecraft.world.InteractionHand.MAIN_HAND
                ? GuardRules.SLOT_MAINHAND : GuardRules.SLOT_OFFHAND;
        boolean sameEquipment = GuardEquipmentResolver.resolveSlot(player, Config.swordBlocking()) == expectedSlot
                && player.getItemInHand(guardState.guardHand()) == guardState.guardStack()
                && guardState.guardType() == GuardEquipmentResolver.typeOf(guardState.guardStack(), Config.swordBlocking());
        if (!sameEquipment) {
            dropGuard(player, guardState, now);
            BlockModLogger.info("GUARD_INPUT", "action", "equipment_drop", "player", player.getGameProfile().getName());
        } else {
            com.example.blockmod.logic.PowerGuardService.reconcileEquipment(player, guardState, now);
        }
        return sameEquipment;
    }

    /** Full authoritative guard exit: state + malus + PG + parry window + sync. */
    private static void dropGuard(ServerPlayer player, GuardStateData guardState, long now) {
        guardState.setGuarding(false);
        MovementService.remove(player, guardState);
        com.example.blockmod.logic.PowerGuardService.disarm(player, guardState, now); // §5.7: PG ends with the guard
        com.example.blockmod.logic.ParryService.closeWindowOnRelease(player, guardState, now); // ADR-07 cooldown anchor
        SyncThrottler.forceSync(player);
    }

    @SubscribeEvent
    static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        RATES.remove(event.getEntity().getUUID());
        LAST_INPUT_TICK.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    static void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            var state = player.getData(ModAttachments.GUARD_STATE.get());
            if (state.isGuarding()) {
                dropGuard(player, state, player.level().getGameTime());
            }
            LAST_INPUT_TICK.remove(player.getUUID());
            SyncThrottler.clear(player.getUUID());
            SyncThrottler.forceSync(player);
        }
    }

    private ServerGuardInputHandler() {}
}
