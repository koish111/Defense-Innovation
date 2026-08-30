package com.example.blockmod.logic;

/**
 * Pure decision rules targeted by the Spec §9.2 unit-test table. This class must
 * stay free of Minecraft types: the test JVM links it without the game on the
 * classpath. The MC-facing services ({@link StaminaService}, {@link MovementService})
 * delegate here and own the game-type adapters.
 */
public final class GuardRules {
    private GuardRules() {}

    // ------------------------------------------------------------------
    // FR-02 regeneration branch selection (top-down, first match wins)

    /** Points per second for one tick's regen decision. {@code ticksSinceLastEvent} may be negative before the first event. */
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
    // v2.0 depletion edge detection (Spec §5.3.2, E-28)

    /** {@return true} when {@code stamina} is on the other side of zero than the stored edge state. */
    public static boolean depletionEdgeFlipped(boolean wasDepleted, float stamina) {
        return wasDepleted != (stamina <= 0f);
    }

    // ------------------------------------------------------------------
    // FR-17 move-malus decision table (v2.0)

    /** Mount only while guarding with stamina left and an actual penalty; depleted = shield lowered. */
    public static boolean shouldApplyMalus(boolean guarding, boolean staminaPositive, float moveSpeedMalus) {
        return guarding && staminaPositive && moveSpeedMalus != 0f;
    }
}
