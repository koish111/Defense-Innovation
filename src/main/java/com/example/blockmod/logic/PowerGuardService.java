package com.example.blockmod.logic;

import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.network.SyncThrottler;
import com.example.blockmod.registry.ModAttachments;
import com.example.blockmod.registry.ModSounds;
import com.example.blockmod.state.GuardStateData;
import com.example.blockmod.state.StaminaData;

import net.minecraft.server.level.ServerPlayer;

import org.jetbrains.annotations.Nullable;

/**
 * FR-16 / T-37: power guard (great shields). Activation arms
 * {@code GuardStateData#powerGuarding}; the per-tick effects (drain, regen
 * suspension, jump clamp, gb bonus) live in the tick pipeline (§5.3.1 step 1 /
 * §5.7). Deactivation: key release, guard exit (right-click), item switch, or
 * stamina reaching zero (v2.0: auto-close, no hardstun).
 */
public final class PowerGuardService {
    private PowerGuardService() {}

    /** C2S intent path. {@code active=true} arms the state after validation. */
    public static void handleActivation(ServerPlayer player, boolean active, long now) {
        GuardStateData guardState = player.getData(ModAttachments.GUARD_STATE.get());
        if (!active) {
            if (guardState.isPowerGuarding()) {
                deactivate(player, guardState, "key released", now);
            }
            return;
        }
        if (guardState.isPowerGuarding()) {
            return;
        }
        if (now < guardState.powerGuardReadyTick()) {
            BlockModLogger.warn("POWER_GUARD", "action", "rejected", "player", player.getGameProfile().getName(),
                    "reason", "cooldown");
            return; // designer ruling 2026-09-07: 3s lockout after PG ends
        }
        if (!guardState.isGuarding()) {
            return; // designer ruling 2026-09-07: PG triggers only while the guard holds
                    // (right-click first, then the PG key). Expected input pattern, stay silent.
        }
        GuardEquipmentResolver.GuardEquipment equipment = GuardEquipmentResolver.resolve(player);
        String reject = validate(player, equipment, guardState);
        if (reject != null) {
            BlockModLogger.warn("POWER_GUARD", "action", "rejected", "player", player.getGameProfile().getName(),
                    "reason", reject);
            return; // E-11/E-19
        }
        guardState.setPowerGuarding(true);
        ModSounds.play(player, ModSounds.FORTIFIED_GUARD, 0.9f, 0.6f);
        SyncThrottler.forceSync(player);
        BlockModLogger.info("POWER_GUARD", "action", "on", "player", player.getGameProfile().getName());
    }

    /**
     * Disarms the state (guard exit, item switch, depletion paths call this). Anchors the
     * re-activation cooldown and shows it as the vanilla item-cooldown sweep on the shield
     * (designer ruling 2026-09-07).
     */
    public static void disarm(ServerPlayer player, GuardStateData guardState, long now) {
        if (guardState.isPowerGuarding()) {
            deactivate(player, guardState, "guard exit", now);
        }
    }

    /** Single funnel for every PG exit: disarm state, anchor cooldown, visualise, sync, log. */
    private static void deactivate(ServerPlayer player, GuardStateData guardState, String reason, long now) {
        guardState.setPowerGuarding(false);
        int cooldown = Config.powerGuardCooldownTicks();
        if (cooldown > 0) {
            guardState.setPowerGuardReadyTick(now + cooldown);
            GuardEquipmentResolver.GuardEquipment equipment = GuardEquipmentResolver.resolve(player);
            if (equipment != null && equipment.profile().type() == ShieldType.GREAT) {
                player.getCooldowns().addCooldown(equipment.stack().getItem(), cooldown);
            }
        }
        SyncThrottler.forceSync(player);
        BlockModLogger.info("POWER_GUARD", "action", "off", "player", player.getGameProfile().getName(),
                "reason", reason, "cooldownTicks", cooldown);
    }

    @Nullable
    private static String validate(ServerPlayer player, @Nullable GuardEquipmentResolver.GuardEquipment equipment,
            GuardStateData guardState) {
        if (equipment == null || equipment.profile().type() != ShieldType.GREAT) {
            return "not a great shield"; // E-11
        }
        StaminaData stamina = player.getData(ModAttachments.STAMINA.get());
        if (stamina.isDepleted()) {
            return "depleted (needs stamina > 0)"; // E-19 / FR-16
        }
        return null;
    }

    /** Disarms the state (guard exit, item switch paths call this). */
    public static void disarm(ServerPlayer player, GuardStateData guardState) {
        if (guardState.isPowerGuarding()) {
            guardState.setPowerGuarding(false);
            SyncThrottler.forceSync(player);
            BlockModLogger.info("POWER_GUARD", "action", "off", "player", player.getGameProfile().getName(),
                    "reason", "guard exit");
        }
    }
}
