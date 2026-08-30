package com.example.blockmod.network;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.state.GuardStateData;
import com.example.blockmod.state.StaminaData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.MinecraftServer;

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
    private record SyncState(long lastSyncTick, float lastSyncedStamina) {}

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

    /** Pushes the active config subset to every online player (FR-20, config hot reload). */
    public static void sendConfigToAll(Iterable<ServerPlayer> players) {
        ConfigSyncPayload payload = new ConfigSyncPayload(
                Config.maxStamina(), Config.regenRate(), Config.depletedRegenRate(), Config.regenDelaySeconds());
        int count = 0;
        for (ServerPlayer player : players) {
            PacketDistributor.sendToPlayer(player, payload);
            count++;
        }
        BlockModLogger.info("CONFIG_SYNC", "players", count);
    }

    /** FR-20: on config load/reload, push the new values if a server is running. */
    public static void onConfigLoad(ModConfigEvent event) {
        if (event instanceof ModConfigEvent.Unloading) {
            return;
        }
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server != null) {
            sendConfigToAll(server.getPlayerList().getPlayers());
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
        STATES.put(player.getUUID(), new SyncState(now, stamina.stamina()));
    }

    /** Drops the bookkeeping state of a disconnected player (bounded-cache hygiene). */
    public static void clear(UUID playerId) {
        STATES.remove(playerId);
    }

    private SyncThrottler() {}
}
