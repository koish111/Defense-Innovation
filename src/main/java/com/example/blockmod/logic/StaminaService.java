package com.example.blockmod.logic;

import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.network.SyncThrottler;
import com.example.blockmod.state.GuardStateData;
import com.example.blockmod.state.StaminaData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.attachment.AttachmentType;

/**
 * Stamina regeneration and the v2.0 depletion transition (Spec §5.3, FR-02, FR-04).
 *
 * <p>Branch selection (FR-02, evaluated top-down, first match wins):
 * 1. depleted (stamina &lt;= 0) → depleted rate, immune to delay/guard/PG;
 * 2. power guard → 0;
 * 3. inside regen delay → 0;
 * 4. guarding → regen × guard multiplier;
 * 5. otherwise → regen.
 *
 * <p>The {@code depletionEdge} helper is the ONLY place {@link GuardStateData#wasDepleted}
 * is written, so side effects run exactly once per crossing of zero (E-28: no thrash).
 */
public final class StaminaService {
    private StaminaService() {}

    // ------------------------------------------------------------------
    // pure branch selection (unit-tested, no game types)

    /** FR-02 branch order in points per second. {@code ticksSinceLastEvent} may be negative before the first event. */
    public static float regenRatePerSecond(float stamina, boolean guarding, boolean powerGuarding,
            long ticksSinceLastEvent, long regenDelayTicks, float regenRate, float guardMultiplier, float depletedRate) {
        if (stamina <= 0f) {
            return depletedRate; // ADR-18: 0 counts as depleted
        }
        if (powerGuarding) {
            return 0f; // ADR-08
        }
        if (ticksSinceLastEvent < regenDelayTicks) {
            return 0f;
        }
        if (guarding) {
            return regenRate * guardMultiplier;
        }
        return regenRate;
    }

    // ------------------------------------------------------------------
    // runtime helpers

    /** {@return true} if this tick crossed the zero line (edge), updating {@code wasDepleted}. */
    public static boolean depletionEdgeFlipped(GuardStateData guardState, float stamina) {
        boolean depletedNow = stamina <= 0f;
        if (depletedNow == guardState.wasDepleted()) {
            return false;
        }
        guardState.setWasDepleted(depletedNow);
        return true;
    }

    /** Applies one tick of regeneration; the top clamp never truncates the zero crossing (FR-02 rule). */
    public static void applyRegenTick(ServerPlayer player, StaminaData stamina, GuardStateData guardState, long now) {
        float rate = regenRatePerSecond(stamina.stamina(), guardState.isGuarding(), guardState.isPowerGuarding(),
                now - stamina.lastEventTick(),
                (long) (Config.regenDelaySeconds() * 20.0f),
                Config.regenRate(), Config.guardRegenMultiplier(), Config.depletedRegenRate());
        if (rate == 0f) {
            return;
        }
        float after = Math.min(stamina.stamina() + rate / 20.0f, Config.maxStamina());
        stamina.setStamina(after);
    }

    /** Directly sets stamina (debug command path): performs the depletion edge check and syncs immediately. */
    public static void setStamina(ServerPlayer player, float value) {
        StaminaData stamina = stamina(player);
        GuardStateData guardState = guardState(player);
        stamina.setStamina(value);
        afterStaminaChanged(player, stamina, guardState);
        BlockModLogger.info("STAMINA", "action", "set", "player", player.getGameProfile().getName(), "value", value);
    }

    /** Adds stamina clamped to the maximum (FR-03 food restore path); fires the depletion edge check. */
    public static void addStamina(ServerPlayer player, float amount) {
        StaminaData stamina = stamina(player);
        GuardStateData guardState = guardState(player);
        stamina.setStamina(Math.min(stamina.stamina() + amount, Config.maxStamina()));
        afterStaminaChanged(player, stamina, guardState);
    }

    /** Shared tail of every mutation: depletion edge → side effects → immediate sync. */
    public static void afterStaminaChanged(ServerPlayer player, StaminaData stamina, GuardStateData guardState) {
        if (depletionEdgeFlipped(guardState, stamina.stamina())) {
            refreshDepletedState(player, guardState, guardState.wasDepleted());
            SyncThrottler.forceSync(player);
        } else {
            SyncThrottler.forceSync(player);
        }
    }

    /**
     * Spec §5.3.2 — the single side-effect entry point for crossing zero.
     * Entering: drop the move malus (ADR-15) so the player can retreat at full speed.
     * Leaving: remount it if the player is still holding guard (ADR-16).
     * Sound/particle feedback lands here with M6 T-40; guarding itself is NOT modified.
     */
    public static void refreshDepletedState(ServerPlayer player, GuardStateData guardState, boolean depleted) {
        if (depleted) {
            MovementService.remove(player, guardState);
            BlockModLogger.debug("DEPLETED", "phase", "enter", "player", player.getGameProfile().getName());
        } else {
            if (guardState.isGuarding()) {
                GuardProfile profile = resolveGuardProfile(player);
                if (profile != null) {
                    MovementService.apply(player, guardState, profile, true);
                }
            }
            BlockModLogger.debug("DEPLETED", "phase", "exit", "player", player.getGameProfile().getName());
        }
    }

    private static GuardProfile resolveGuardProfile(ServerPlayer player) {
        // M3's GuardEquipmentResolver replaces this placeholder; until then the
        // depletion-exit remount can only re-mount an explicit component profile.
        ItemStack offhand = player.getOffhandItem();
        GuardProfile profile = offhand.get(com.example.blockmod.registry.ModDataComponents.GUARD_PROFILE.get());
        if (profile != null) {
            return profile;
        }
        return player.getMainHandItem().get(com.example.blockmod.registry.ModDataComponents.GUARD_PROFILE.get());
    }

    private static StaminaData stamina(ServerPlayer player) {
        return player.getData(ModAttachmentsHolder.STAMINA);
    }

    private static GuardStateData guardState(ServerPlayer player) {
        return player.getData(ModAttachmentsHolder.GUARD_STATE);
    }

    /** Indirection so this class does not import the registry holder type directly in tests. */
    private static final class ModAttachmentsHolder {
        static final AttachmentType<StaminaData> STAMINA = com.example.blockmod.registry.ModAttachments.STAMINA.get();
        static final AttachmentType<GuardStateData> GUARD_STATE = com.example.blockmod.registry.ModAttachments.GUARD_STATE.get();
    }
}
