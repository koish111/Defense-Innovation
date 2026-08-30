package com.example.blockmod.logic;

import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.network.SyncThrottler;
import com.example.blockmod.registry.ModAttachments;
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
                guardState.setPowerGuarding(false);
                SyncThrottler.forceSync(player);
                BlockModLogger.info("POWER_GUARD", "action", "off", "player", player.getGameProfile().getName(),
                        "reason", "key released");
            }
            return;
        }
        if (guardState.isPowerGuarding()) {
            return;
        }
        GuardEquipmentResolver.GuardEquipment equipment = GuardEquipmentResolver.resolve(player);
        String reject = validate(player, equipment, guardState);
        if (reject != null) {
            BlockModLogger.warn("POWER_GUARD", "action", "rejected", "player", player.getGameProfile().getName(),
                    "reason", reject);
            return; // E-11/E-19
        }
        guardState.setPowerGuarding(true);
        SyncThrottler.forceSync(player);
        BlockModLogger.info("POWER_GUARD", "action", "on", "player", player.getGameProfile().getName());
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
