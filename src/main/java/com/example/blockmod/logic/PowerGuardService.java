package com.example.blockmod.logic;

import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.data.GuardProfile;
import com.example.blockmod.network.SyncThrottler;
import com.example.blockmod.registry.ModAttachments;
import com.example.blockmod.registry.ModSounds;
import com.example.blockmod.state.GuardStateData;
import com.example.blockmod.state.StaminaData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import org.jetbrains.annotations.Nullable;

/**
 * FR-16 / T-37: power guard with two guardable items or one great shield. Activation arms
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
            return; // The client sends guard entry before the combined-key intent.
        }
        GuardEquipmentResolver.GuardEquipment equipment = GuardEquipmentResolver.resolve(player);
        String reject = validate(player, equipment, guardState);
        if (reject != null) {
            BlockModLogger.warn("POWER_GUARD", "action", "rejected", "player", player.getGameProfile().getName(),
                    "reason", reject);
            return; // E-11/E-19
        }
        guardState.setPowerGuarding(true);
        InteractionHand secondaryHand = guardState.guardHand() == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        GuardProfile secondaryProfile = GuardEquipmentResolver.profileInHand(player, secondaryHand, Config.swordBlocking());
        guardState.setSecondaryGuardEquipment(secondaryProfile == null ? ItemStack.EMPTY : player.getItemInHand(secondaryHand),
                secondaryProfile);
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
        ItemStack secondaryStack = guardState.secondaryGuardStack();
        guardState.setPowerGuarding(false);
        int cooldown = Config.powerGuardCooldownTicks();
        if (cooldown > 0) {
            guardState.setPowerGuardReadyTick(now + cooldown);
            GuardEquipmentResolver.GuardEquipment equipment = GuardEquipmentResolver.resolve(player);
            if (equipment != null) {
                player.getCooldowns().addCooldown(equipment.stack().getItem(), cooldown);
            }
            if (!secondaryStack.isEmpty()) {
                player.getCooldowns().addCooldown(secondaryStack.getItem(), cooldown);
            }
        }
        SyncThrottler.forceSync(player);
        BlockModLogger.info("POWER_GUARD", "action", "off", "player", player.getGameProfile().getName(),
                "reason", reason, "cooldownTicks", cooldown);
    }

    @Nullable
    private static String validate(ServerPlayer player, @Nullable GuardEquipmentResolver.GuardEquipment equipment,
            GuardStateData guardState) {
        if (equipment == null || equipment.hand() != guardState.guardHand()
                || equipment.stack() != guardState.guardStack()
                || equipment.profile().type() != guardState.guardType()) {
            return "guard equipment changed";
        }
        if (!GuardEquipmentResolver.canPowerGuard(player, Config.swordBlocking())) {
            return "needs two guardable items or one great shield";
        }
        StaminaData stamina = player.getData(ModAttachments.STAMINA.get());
        if (stamina.isDepleted()) {
            return "depleted (needs stamina > 0)"; // E-19 / FR-16
        }
        if (MixinHooks.isStunned(player)) {
            return "stunned"; // FR-05: a stunned player cannot act
        }
        return null;
    }

    @Nullable
    public static GuardProfile secondaryProfile(ServerPlayer player, GuardStateData guardState) {
        GuardProfile profile = guardState.secondaryGuardProfile();
        if (!guardState.isGuarding() || !guardState.isPowerGuarding() || profile == null) return null;
        InteractionHand hand = guardState.guardHand() == InteractionHand.MAIN_HAND
                ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        ItemStack stack = guardState.secondaryGuardStack();
        return player.getItemInHand(hand) == stack && !stack.isEmpty()
                && GuardEquipmentResolver.matchesProfile(stack, profile, Config.swordBlocking()) ? profile : null;
    }

    /** Losing either captured item ends the dual hold; a valid primary may keep normal guarding. */
    public static void reconcileEquipment(ServerPlayer player, GuardStateData guardState, long now) {
        if (guardState.isPowerGuarding() && guardState.secondaryGuardProfile() != null
                && secondaryProfile(player, guardState) == null) {
            deactivate(player, guardState, "secondary equipment changed", now);
        }
    }

    /** Disarms the state (guard exit, item switch paths call this). */
    public static void disarm(ServerPlayer player, GuardStateData guardState) {
        disarm(player, guardState, player.level().getGameTime());
    }
}
