package com.example.blockmod.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.logic.GuardEquipmentResolver;
import com.example.blockmod.logic.GuardRules;
import com.example.blockmod.logic.MixinHooks;
import com.example.blockmod.logic.PowerGuardService;
import com.example.blockmod.state.GuardStateData;
import com.example.blockmod.state.StaminaData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.server.ServerLifecycleHooks;

/**
 * FR-23 throttle for the S2C stamina mirror: a packet goes out when the absolute
 * stamina change since the last send reaches {@code sync_threshold} OR when
 * {@code sync_interval_ticks} elapsed — whichever comes first. Depletion
 * transitions, guard enter/exit and parries bypass the throttle entirely.
 *
 * <p>Bookkeeping state is keyed by player UUID and dropped on logout, so it stays
 * bounded by the online player count (AGENTS.md §7.1 scoped-cache rule).
 */
public final class SyncThrottler {
    private record SyncState(long lastSyncTick, float lastSyncedStamina, GuardPoseSyncPayload guardPose) {}

    private static final Map<UUID, SyncState> STATES = new HashMap<>();

    /** Throttled send from the per-tick path. */
    public static void maybeSync(ServerPlayer player, long now) {
        SyncState state = STATES.get(player.getUUID());
        StaminaData stamina = player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        float lastSynced = state == null ? Float.NaN : state.lastSyncedStamina();
        boolean deltaReached = Float.isNaN(lastSynced)
                || Math.abs(stamina.stamina() - lastSynced) >= Config.syncThreshold();
        boolean intervalReached = state == null || now - state.lastSyncTick() >= Config.syncIntervalTicks();
        if (deltaReached || intervalReached) {
            send(player, now);
        }
    }

    /** Unconditional send: depletion transitions, guard enter/exit, parries, debug commands, login. */
    public static void forceSync(ServerPlayer player) {
        send(player, player.level().getGameTime());
    }

    private static float lastMaxStamina; // E-14: config-change scaling reference

    /** Pushes the active config subset to every online player (FR-20, config hot reload). */
    public static void sendConfigToAll(Iterable<ServerPlayer> players) {
        ConfigSyncPayload payload = configPayload();
        int count = 0;
        for (ServerPlayer player : players) {
            applyStaminaRescale(player); // E-14: keep stamina proportional when max_stamina changes
            PacketDistributor.sendToPlayer(player, payload);
            forceSync(player);
            count++;
        }
        BlockModLogger.info("CONFIG_SYNC", "players", count, "maxStamina", Config.maxStamina());
    }

    public static void sendConfig(ServerPlayer player) {
        PacketDistributor.sendToPlayer(player, configPayload());
    }

    private static ConfigSyncPayload configPayload() {
        return new ConfigSyncPayload(Config.maxStamina(), Config.regenRate(), Config.depletedRegenRate(),
                Config.regenDelaySeconds(), Config.swordBlocking());
    }

    /** E-14: scale the current stamina proportionally when max_stamina changed on reload. */
    private static void applyStaminaRescale(ServerPlayer player) {
        float newMax = Config.maxStamina();
        if (lastMaxStamina <= 0f || lastMaxStamina == newMax) {
            lastMaxStamina = newMax;
            return;
        }
        StaminaData stamina = player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        stamina.setStamina(stamina.stamina() * newMax / lastMaxStamina);
        lastMaxStamina = newMax;
    }

    /** FR-20: on config load/reload, push the new values if a server is running. */
    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != Config.SPEC || event instanceof ModConfigEvent.Unloading) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            server.execute(() -> sendConfigToAll(server.getPlayerList().getPlayers()));
        }
    }

    private static void send(ServerPlayer player, long now) {
        StaminaData stamina = player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        GuardStateData guardState = player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());
        int parryRemain = guardState.parryWindowEndTick() < 0
                ? 0
                : (int) Math.max(0, guardState.parryWindowEndTick() - now);
        PacketDistributor.sendToPlayer(player, new StaminaSyncPayload(
                stamina.stamina(), Config.maxStamina(), stamina.isDepleted(),
                guardState.isGuarding(), parryRemain));
        ItemStack mainStack = guardPoseStack(player, InteractionHand.MAIN_HAND);
        ItemStack offStack = guardPoseStack(player, InteractionHand.OFF_HAND);
        SyncState previous = STATES.get(player.getUUID());
        GuardPoseSyncPayload guardPose = previous == null ? null : previous.guardPose();
        if (guardPose == null || guardPose.powerGuarding() != guardState.isPowerGuarding()
                || !ItemStack.isSameItemSameComponents(guardPose.mainHand(), mainStack)
                || !ItemStack.isSameItemSameComponents(guardPose.offHand(), offStack)) {
            guardPose = new GuardPoseSyncPayload(player.getUUID(), mainStack, offStack, guardState.isPowerGuarding());
            PacketDistributor.sendToPlayersTrackingEntityAndSelf(player, guardPose);
        }
        STATES.put(player.getUUID(), new SyncState(now, stamina.stamina(), guardPose));
    }

    /** Read-only presentation snapshot; depletion never changes the underlying guard intent. */
    public static ItemStack guardPoseStack(ServerPlayer player, InteractionHand hand) {
        GuardStateData guard = player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());
        int expectedSlot = guard.guardHand() == InteractionHand.MAIN_HAND
                ? GuardRules.SLOT_MAINHAND : GuardRules.SLOT_OFFHAND;
        if (!guard.isGuarding() || guard.guardStack().isEmpty()
                || !player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get()).canDefend()
                || MixinHooks.isStunned(player)
                || player.getItemInHand(guard.guardHand()) != guard.guardStack()
                || GuardEquipmentResolver.resolveSlot(player, Config.swordBlocking()) != expectedSlot
                || GuardEquipmentResolver.typeOf(guard.guardStack(), Config.swordBlocking()) != guard.guardType()) {
            return ItemStack.EMPTY;
        }
        if (hand == guard.guardHand()) return guard.guardStack();
        return PowerGuardService.secondaryProfile(player, guard) == null ? ItemStack.EMPTY : guard.secondaryGuardStack();
    }

    public static void sendGuardPoseToTracker(ServerPlayer defender, ServerPlayer observer) {
        GuardStateData guard = defender.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());
        PacketDistributor.sendToPlayer(observer,
                new GuardPoseSyncPayload(defender.getUUID(), guardPoseStack(defender, InteractionHand.MAIN_HAND),
                        guardPoseStack(defender, InteractionHand.OFF_HAND), guard.isPowerGuarding()));
    }

    /** Drops the bookkeeping state of a disconnected player (bounded-cache hygiene). */
    public static void clear(UUID playerId) {
        STATES.remove(playerId);
    }

    private SyncThrottler() {}
}
