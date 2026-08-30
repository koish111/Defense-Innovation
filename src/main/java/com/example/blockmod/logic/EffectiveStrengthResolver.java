package com.example.blockmod.logic;

import com.example.blockmod.config.Config;
import com.example.blockmod.data.GuardProfile;

/**
 * §5.9.2: effective guard strength = base gb composed with the power-guard bonus
 * (multiplicative diminishing), clamped to the ADR-09 bounds. The decision core
 * lives in {@link GuardRules#effectiveStrength}; this wrapper reads the config.
 */
public final class EffectiveStrengthResolver {
    private EffectiveStrengthResolver() {}

    public static float resolve(GuardProfile profile, boolean powerGuarding) {
        return GuardRules.effectiveStrength(
                profile.guardStrength(),
                profile.powerGuardBonus(),
                powerGuarding && profile.type() == com.example.blockmod.data.ShieldType.GREAT,
                Config.minGb(),
                Config.maxGb());
    }
}
