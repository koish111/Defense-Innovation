package com.example.blockmod.data;

/**
 * Damage tiers for the guard system (FR-08). Tag overrides beat hard-coded checks
 * so datapacks can reclassify damage (extension point E-02).
 */
public enum DamageClass {
    MELEE(true, true),
    PROJECTILE(true, true),
    EXPLOSION(true, false),
    IGNORED(false, false);

    private final boolean guardable;
    private final boolean parryable;

    DamageClass(boolean guardable, boolean parryable) {
        this.guardable = guardable;
        this.parryable = parryable;
    }

    public boolean isGuardable() {
        return guardable;
    }

    public boolean isParryable() {
        return parryable;
    }
}
