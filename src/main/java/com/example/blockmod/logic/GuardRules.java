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

    // ------------------------------------------------------------------
    // FR-08 damage classification (tag overrides beat hard-coded checks)

    /**
     * FR-08 decision order over pre-computed tag/entity predicates:
     * 1. {@code blockmod:guard_ignored} tag; 2. BYPASSES_SHIELD / BYPASSES_ARMOR;
     * 3. IS_EXPLOSION; 4. IS_PROJECTILE or direct projectile entity;
     * 5. {@code blockmod:parryable} tag; 6. causing entity is a living entity;
     * 7. otherwise ignored. DamageClass ordinals index the result.
     */
    public static com.example.blockmod.data.DamageClass classifyDamage(
            boolean tagGuardIgnored, boolean bypassesShield, boolean bypassesArmor,
            boolean isExplosion, boolean isProjectile, boolean directProjectile,
            boolean tagParryable, boolean causingLivingEntity) {
        if (tagGuardIgnored) {
            return com.example.blockmod.data.DamageClass.IGNORED;
        }
        if (bypassesShield || bypassesArmor) {
            return com.example.blockmod.data.DamageClass.IGNORED;
        }
        if (isExplosion) {
            return com.example.blockmod.data.DamageClass.EXPLOSION;
        }
        if (isProjectile || directProjectile) {
            return com.example.blockmod.data.DamageClass.PROJECTILE;
        }
        if (tagParryable) {
            return com.example.blockmod.data.DamageClass.MELEE;
        }
        if (causingLivingEntity) {
            return com.example.blockmod.data.DamageClass.MELEE;
        }
        return com.example.blockmod.data.DamageClass.IGNORED;
    }

    // ------------------------------------------------------------------
    // FR-11 / Spec §5.12 equipment resolution (pure slot decision)

    /** Slot classes fed into {@link #resolveEquipmentSlot}. */
    public static final int EQUIP_NONE = 0;
    public static final int EQUIP_PROFILE = 1;  // guard_profile component or data map
    public static final int EQUIP_SWORD = 2;    // sword policy in either hand
    public static final int EQUIP_SHIELD = 3;   // #blockmod:guardable shield-family without a profile (Spec §5.12 step 3)

    /** Slot a resolved equipment came from. */
    public static final int SLOT_NONE = 0;
    public static final int SLOT_OFFHAND = 1;
    public static final int SLOT_MAINHAND = 2;

    /** Sword defaults and explicit additions are both subject to the blacklist. */
    public static boolean swordGuardAllowed(boolean defaultSword, boolean includeSwordsTag,
            boolean whitelisted, boolean blacklisted) {
        return !blacklisted && (whitelisted || includeSwordsTag && defaultSword);
    }

    /**
     * §5.12 / FR-11 primary-item priority; dual Power Guard adds the other hand:
     * <ol>
     *   <li>offhand profile or shield-family guardable → offhand;</li>
     *   <li>mainhand profile or shield-family guardable → mainhand;</li>
     *   <li>mainhand sword → mainhand.</li>
     *   <li>offhand sword → offhand.</li>
     * </ol>
     */
    public static int resolveEquipmentSlot(int offhandClass, int mainhandClass) {
        if (offhandClass == EQUIP_PROFILE || offhandClass == EQUIP_SHIELD) {
            return SLOT_OFFHAND;
        }
        if (mainhandClass == EQUIP_PROFILE || mainhandClass == EQUIP_SHIELD) {
            return SLOT_MAINHAND;
        }
        if (mainhandClass == EQUIP_SWORD) {
            return SLOT_MAINHAND;
        }
        if (offhandClass == EQUIP_SWORD) {
            return SLOT_OFFHAND;
        }
        return SLOT_NONE;
    }

    public static boolean powerGuardEquipmentAllowed(boolean primaryGreatShield, boolean secondaryGuardable) {
        return primaryGreatShield || secondaryGuardable;
    }

    // ------------------------------------------------------------------
    // §5.9.2 effective strength (base + power guard, clamped by ADR-09 bounds)

    public static float effectiveStrength(float baseGb, float powerGuardBonus, boolean powerGuarding,
            float minGb, float maxGb) {
        float effective = powerGuarding ? GuardFormulas.combineStrength(baseGb, powerGuardBonus) : baseGb;
        return Math.min(Math.max(effective, minGb), maxGb);
    }

    public static float combinedGuardStrength(float primary, float secondary, float minGb, float maxGb) {
        return Math.clamp(1f - (1f - primary) * (1f - secondary), minGb, maxGb);
    }

    // ------------------------------------------------------------------
    // Designer ruling 2026-09-04: great-shield shove on a successful block

    /** Shove modes returned by {@link #greatshieldShoveMode}. */
    public static final int SHOVE_NONE = 0;
    public static final int SHOVE_ATTACKER = 1;
    public static final int SHOVE_FRONTAL_AREA = 2;

    /**
     * Great-shield shove decision: a successful block knocks the attacker back;
     * while power guard holds, the shove becomes a frontal area push that
     * replaces the single-attacker knockback.
     */
    public static int greatshieldShoveMode(boolean greatShield, boolean powerGuarding) {
        if (!greatShield) {
            return SHOVE_NONE;
        }
        return powerGuarding ? SHOVE_FRONTAL_AREA : SHOVE_ATTACKER;
    }

    // ------------------------------------------------------------------
    // C4 frontal check (FR-07, E-03/E-04)

    /**
     * @param srcPosNull  true when {@code DamageSource#getSourcePosition()} is null (E-03)
     * @param dot         dot product of the normalized horizontal source→player vector
     *                    and the player's view vector; 0 means directly above/below (E-04)
     * @param halfAngleDeg configured guard half angle (90 = frontal 180 degrees)
     * @return true when the source sits inside the guard arc
     */
    public static boolean frontalBlocked(boolean srcPosNull, double dot, double horizontalLen,
            double halfAngleDeg) {
        if (srcPosNull || horizontalLen < 1.0e-4) {
            return false; // no direction information (E-03) or degenerate vector (E-04)
        }
        return dot < -Math.cos(Math.toRadians(halfAngleDeg));
    }

    // ------------------------------------------------------------------
    // §5.4.2 ten-step arbitration over primitives (pure, unit-tested)

    /** GuardResult ordinals used by {@link #resolveGuard}. */
    public static final int RESULT_NOT_GUARDED = 0;
    public static final int RESULT_DEPLETED_PASS = 1;
    public static final int RESULT_WRONG_ANGLE = 2;
    public static final int RESULT_IGNORED_TYPE = 3;
    public static final int RESULT_PARRIED = 4;
    public static final int RESULT_GUARDED = 5;

    /** §5.4.2 arbitration: conditions pre-computed by the caller, decided here.
     *  {@code canDefend} is the FR-04 defense gate — stamina positive AND not stunned
     *  (FR-05: the stun shares the depletion gate, so a stunned defender can neither
     *  block nor parry, and the damage resolves normally). */
    public static int resolveGuard(boolean hasProfile, boolean guarding, boolean canDefend,
            boolean frontal, int damageClassOrdinal, boolean inParryWindow) {
        // step 1-3 (server player / creative exemption / re-entrancy) are handled by the caller
        if (!hasProfile) {
            return RESULT_NOT_GUARDED;                       // step 4 (C6)
        }
        if (!guarding) {
            return RESULT_NOT_GUARDED;                       // step 5 (C2)
        }
        if (!canDefend) {
            return RESULT_DEPLETED_PASS;                     // step 6 (C3) — depleted or stunned
        }
        if (!frontal) {
            return RESULT_WRONG_ANGLE;                       // step 7 (C4)
        }
        com.example.blockmod.data.DamageClass damageClass = com.example.blockmod.data.DamageClass.values()[damageClassOrdinal];
        if (damageClass == com.example.blockmod.data.DamageClass.IGNORED) {
            return RESULT_IGNORED_TYPE;                      // step 8 (C5)
        }
        if (damageClass.isParryable() && inParryWindow) {
            return RESULT_PARRIED;                           // step 9
        }
        return RESULT_GUARDED;                               // step 10 (stamina re-check done by caller)
    }
}
