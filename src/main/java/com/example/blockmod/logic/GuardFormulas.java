package com.example.blockmod.logic;

import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;

/**
 * Pure combat formulas (Spec §5.9). All core methods take explicit parameters so
 * they stay unit-testable without the game or the config loaded; the thin overloads
 * at the bottom read the server config and are the runtime entry points.
 */
public final class GuardFormulas {
    private GuardFormulas() {}

    /**
     * Stamina cost of one blocked hit: {@code (max(dmg,0) * mfix)^pfix * (1 - gb)},
     * clamped to {@code [minCost, maxCost]} (Spec §5.9.1).
     *
     * @return 0 on a non-finite result (logged); a mod bug must never yield NaN stamina.
     */
    public static float staminaCost(float dmg, float gb, float mfix, float pfix, float minCost, float maxCost) {
        float v = (float) Math.pow(Math.max(dmg, 0f) * mfix, pfix) * (1f - gb);
        if (!Float.isFinite(v)) {
            BlockModLogger.error("FORMULA", "name", "staminaCost", "dmg", dmg, "gb", gb,
                    "mfix", mfix, "pfix", pfix, "result", v, "action", "return 0");
            return 0f;
        }
        return Math.clamp(v, minCost, maxCost);
    }

    /**
     * Power guard drain per second (designer ruling 2026-08-30):
     * {@code maxStamina x percent + flat} points. Reads the max at call time so a
     * future per-player dynamic maximum is honoured automatically.
     */
    public static float powerGuardDrainPerSecond(float maxStamina, float percent, float flat) {
        return maxStamina * (percent / 100f) + flat;
    }

    /** Runtime overload: PvE vs PvP exponent per {@link Config#pvpMode()} is resolved by the caller. */
    public static float staminaCost(float dmg, float gb, float pfix) {
        return staminaCost(dmg, gb, Config.mfix(), pfix, Config.minCostPerGuard(), Config.maxCostPerGuard());
    }

    /**
     * Effective guard strength from a base value and stacked bonuses (Spec §5.9.2):
     * multiplicative diminishing, always below 1.
     *
     * @param base      equipment base gb
     * @param modifiers stacked bonuses (power guard, effects, enchants...)
     */
    public static float combineStrength(float base, float... modifiers) {
        float effective = 1f;
        for (float r : modifiers) {
            effective *= (1f - r);
        }
        return 1f - effective * (1f - base);
    }
}
