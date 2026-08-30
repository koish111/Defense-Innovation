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

    private static float displayStamina = Float.NaN; // NaN = awaiting first sync (snapped on arrival)
    private static float targetStamina;
    private static float maxStamina = 40.0f; // until the first sync/config push arrives
    private static float regenRate = 4.0f;
    private static float depletedRegenRate = 8.0f;
    private static float regenDelaySeconds = 2.0f;
    private static boolean depleted;
    private static boolean guarding;
    private static int parryRemainTicks;

    public static void acceptStaminaSync(StaminaSyncPayload payload) {
        targetStamina = payload.stamina();
        maxStamina = payload.max();
        depleted = payload.depleted();
        guarding = payload.guarding();
        parryRemainTicks = payload.parryRemainTicks();
        if (Float.isNaN(displayStamina)) {
            displayStamina = targetStamina; // no interpolation on the first packet
        }
    }

    public static void acceptConfigSync(ConfigSyncPayload payload) {
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
        displayStamina += (targetStamina - displayStamina) * INTERPOLATION;
        if (Math.abs(targetStamina - displayStamina) < 0.01f) {
            displayStamina = targetStamina;
        }
    }

    public static float displayStamina() {
        return displayStamina;
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
