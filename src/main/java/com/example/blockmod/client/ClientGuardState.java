package com.example.blockmod.client;

import com.example.blockmod.network.ConfigSyncPayload;
import com.example.blockmod.network.StaminaSyncPayload;

import net.neoforged.neoforge.client.event.ClientTickEvent;

/**
 * Read-only client mirror of the server-side stamina state (AGENTS.md §5.1: the
 * client never predicts or writes stamina). Written only by payload handlers,
 * read by the HUD. {@code displayStamina} interpolates toward the last synced
 * value at the ADR-04 factor so high latency cannot make the bar jump backwards.
 */
public final class ClientGuardState {
    private static final float INTERPOLATION = 0.25f; // ADR-04 hud_interpolation default
    private static final float SNAP_EPSILON = 0.01f;
    private static final int FULL_AUTO_HIDE_TICKS = 60;

    private static float displayStamina = Float.NaN; // NaN = awaiting first sync (snapped on arrival)
    private static float previousDisplayStamina = Float.NaN;
    private static float targetStamina;
    private static int unchangedFullTicks;
    private static float maxStamina = 40.0f; // until the first sync/config push arrives
    private static float regenRate = 4.0f;
    private static float depletedRegenRate = 8.0f;
    private static float regenDelaySeconds = 2.0f;
    private static boolean depleted;
    private static boolean guarding;
    private static int parryRemainTicks;

    public static void acceptStaminaSync(StaminaSyncPayload payload) {
        BucklerParryAnimation.acceptStaminaSync(payload);
        ShieldGuardPose.acceptStaminaSync(payload);
        if (Float.compare(targetStamina, payload.stamina()) != 0
                || Float.compare(maxStamina, payload.max()) != 0) {
            unchangedFullTicks = 0;
        }
        targetStamina = payload.stamina();
        maxStamina = payload.max();
        depleted = payload.depleted();
        guarding = payload.guarding();
        parryRemainTicks = payload.parryRemainTicks();
        if (Float.isNaN(displayStamina)) {
            displayStamina = targetStamina; // no interpolation on the first packet
            previousDisplayStamina = targetStamina;
        }
    }

    public static void acceptConfigSync(ConfigSyncPayload payload) {
        if (Float.compare(maxStamina, payload.maxStamina()) != 0) {
            unchangedFullTicks = 0;
        }
        maxStamina = payload.maxStamina();
        regenRate = payload.regenRate();
        depletedRegenRate = payload.depletedRegenRate();
        regenDelaySeconds = payload.regenDelaySeconds();
    }

    /** Advances the ADR-04 interpolation once per client tick. */
    public static void onClientTick(ClientTickEvent.Post event) {
        if (Float.isNaN(displayStamina)) {
            return;
        }
        previousDisplayStamina = displayStamina;
        displayStamina += (targetStamina - displayStamina) * INTERPOLATION;
        if (Math.abs(targetStamina - displayStamina) < SNAP_EPSILON) {
            displayStamina = targetStamina;
        }
        if (isVisuallyFull()) {
            if (unchangedFullTicks < FULL_AUTO_HIDE_TICKS) {
                unchangedFullTicks++;
            }
        } else {
            unchangedFullTicks = 0;
        }
    }

    public static float displayStamina() {
        return displayStamina;
    }

    public static float frameStamina(float partialTick) {
        if (Float.isNaN(displayStamina) || Float.isNaN(previousDisplayStamina)) {
            return Float.NaN;
        }
        float clampedPartialTick = Math.clamp(partialTick, 0.0f, 1.0f);
        return previousDisplayStamina
                + (displayStamina - previousDisplayStamina) * clampedPartialTick;
    }

    public static boolean isReducing(float frameStamina) {
        return !depleted && targetStamina < frameStamina - SNAP_EPSILON;
    }

    public static boolean shouldRenderHud() {
        return unchangedFullTicks < FULL_AUTO_HIDE_TICKS;
    }

    private static boolean isVisuallyFull() {
        return !depleted
                && targetStamina >= maxStamina - SNAP_EPSILON
                && displayStamina >= maxStamina - SNAP_EPSILON;
    }

    public static float targetStamina() {
        return targetStamina;
    }

    public static float maxStamina() {
        return maxStamina;
    }

    public static float regenRate() {
        return regenRate;
    }

    public static float depletedRegenRate() {
        return depletedRegenRate;
    }

    public static float regenDelaySeconds() {
        return regenDelaySeconds;
    }

    public static boolean isDepleted() {
        return depleted;
    }

    public static boolean isGuarding() {
        return guarding;
    }

    public static int parryRemainTicks() {
        return parryRemainTicks;
    }

    private ClientGuardState() {}
}
