package com.example.blockmod.logic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.example.blockmod.data.DamageClass;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec §9.2 M3 rows: damage classification, equipment resolution, the ten-step
 * arbitration, the frontal check and effective strength — all through the pure
 * {@link GuardRules} core (no Minecraft types on the test runtime).
 */
class GuardRulesM3Test {
    // ------------------------------------------------------------------
    // classify_* rows (§9.2): tag inputs stand in for the runtime source adapter

    private static DamageClass classify(boolean guardIgnored, boolean bypassesShield, boolean bypassesArmor,
            boolean explosion, boolean projectile, boolean directProjectile, boolean parryable, boolean causingLiving) {
        return GuardRules.classifyDamage(guardIgnored, bypassesShield, bypassesArmor,
                explosion, projectile, directProjectile, parryable, causingLiving);
    }

    @Test
    @DisplayName("classify_近战: mob_attack 无标签命中 → MELEE（步骤 6 causing living）")
    void classifyMelee() {
        assertEquals(DamageClass.MELEE, classify(false, false, false, false, false, false, false, true));
    }

    @Test
    @DisplayName("classify_弹射物: IS_PROJECTILE → PROJECTILE")
    void classifyProjectile() {
        assertEquals(DamageClass.PROJECTILE, classify(false, false, false, false, true, false, false, false));
        assertEquals(DamageClass.PROJECTILE, classify(false, false, false, false, false, true, false, false));
    }

    @Test
    @DisplayName("classify_爆炸: IS_EXPLOSION → EXPLOSION")
    void classifyExplosion() {
        assertEquals(DamageClass.EXPLOSION, classify(false, false, false, true, false, false, false, false));
    }

    @Test
    @DisplayName("classify_摔落: BYPASSES_SHIELD → IGNORED")
    void classifyFall() {
        assertEquals(DamageClass.IGNORED, classify(false, true, false, false, false, false, false, false));
    }

    @Test
    @DisplayName("classify_标签覆盖: guard_ignored 标签优先于一切")
    void classifyTagOverride() {
        assertEquals(DamageClass.IGNORED, classify(true, false, false, true, true, true, true, true));
        assertEquals(DamageClass.IGNORED, classify(false, false, true, false, false, false, false, true));
    }

    @Test
    @DisplayName("classify_顺序: 爆炸优先于弹射物标志")
    void classifyExplosionBeatsProjectile() {
        assertEquals(DamageClass.EXPLOSION, classify(false, false, false, true, true, true, false, false));
    }

    // ------------------------------------------------------------------
    // resolveEquipment_* rows (§9.2) + FR-11 quadrants

    private static final int NONE = GuardRules.EQUIP_NONE;
    private static final int PROFILE = GuardRules.EQUIP_PROFILE;
    private static final int SWORD = GuardRules.EQUIP_SWORD;

    @Test
    @DisplayName("resolveEquipment_剑加盾: 主手剑 + 副手中盾 → 副手盾牌（FR-11）")
    void resolveSwordPlusShield() {
        assertEquals(GuardRules.SLOT_OFFHAND, GuardRules.resolveEquipmentSlot(PROFILE, SWORD));
    }

    @Test
    @DisplayName("resolveEquipment_剑加火把: 主手剑 + 副手空 → 主手剑（FR-11）")
    void resolveSwordPlusTorch() {
        assertEquals(GuardRules.SLOT_MAINHAND, GuardRules.resolveEquipmentSlot(NONE, SWORD));
    }

    @Test
    @DisplayName("resolveEquipment_镐空手: 主手镐 + 副手空 → null（FR-11）")
    void resolvePickaxeEmptyHand() {
        assertEquals(GuardRules.SLOT_NONE, GuardRules.resolveEquipmentSlot(NONE, NONE));
    }

    @Test
    @DisplayName("resolveEquipment_镐加木盾: 副手组件盾 → 副手（FR-11 验收 3）")
    void resolvePickaxePlusBuckler() {
        assertEquals(GuardRules.SLOT_OFFHAND, GuardRules.resolveEquipmentSlot(PROFILE, NONE));
    }

    @Test
    @DisplayName("resolveEquipment_双盾: 仅副手生效（ADR-10）")
    void resolveDualShields() {
        assertEquals(GuardRules.SLOT_OFFHAND, GuardRules.resolveEquipmentSlot(PROFILE, PROFILE));
    }

    @Test
    @DisplayName("resolveEquipment_副手剑不格挡")
    void resolveOffhandSwordNeverGuards() {
        assertEquals(GuardRules.SLOT_NONE, GuardRules.resolveEquipmentSlot(SWORD, NONE));
    }

    // ------------------------------------------------------------------
    // resolve_ rows (§9.2 v2.0) — the ten-step arbitration

    private static int resolve(boolean hasProfile, boolean guarding, boolean staminaPositive,
            boolean frontal, DamageClass damageClass, boolean inParryWindow) {
        return GuardRules.resolveGuard(hasProfile, guarding, staminaPositive, frontal,
                damageClass.ordinal(), inParryWindow);
    }

    @Test
    @DisplayName("resolve_枯竭格挡: guarding, st<=0, 正面 MELEE → DEPLETED_PASS")
    void resolveDepletedGuard() {
        assertEquals(GuardRules.RESULT_DEPLETED_PASS,
                resolve(true, true, false, true, DamageClass.MELEE, false));
    }

    @Test
    @DisplayName("resolve_枯竭招架: 窗口内也不招架 → DEPLETED_PASS")
    void resolveDepletedParry() {
        assertEquals(GuardRules.RESULT_DEPLETED_PASS,
                resolve(true, true, false, true, DamageClass.MELEE, true));
    }

    @Test
    @DisplayName("resolve_最后一点体力: st=3 → GUARDED（判定用扣减前值）")
    void resolveLastStaminaStillBlocks() {
        assertEquals(GuardRules.RESULT_GUARDED,
                resolve(true, true, true, true, DamageClass.MELEE, false));
    }

    @Test
    @DisplayName("resolve_未举盾 → NOT_GUARDED")
    void resolveNotGuarding() {
        assertEquals(GuardRules.RESULT_NOT_GUARDED,
                resolve(true, false, true, true, DamageClass.MELEE, false));
    }

    @Test
    @DisplayName("resolve_无装备 → NOT_GUARDED（步骤 4）")
    void resolveNoProfile() {
        assertEquals(GuardRules.RESULT_NOT_GUARDED,
                resolve(false, true, true, true, DamageClass.MELEE, false));
    }

    @Test
    @DisplayName("resolve_背后受击 → WRONG_ANGLE（步骤 7）")
    void resolveBackstab() {
        assertEquals(GuardRules.RESULT_WRONG_ANGLE,
                resolve(true, true, true, false, DamageClass.MELEE, false));
    }

    @Test
    @DisplayName("resolve_火/摔落 → IGNORED_TYPE（步骤 8）")
    void resolveIgnoredType() {
        assertEquals(GuardRules.RESULT_IGNORED_TYPE,
                resolve(true, true, true, true, DamageClass.IGNORED, false));
    }

    @Test
    @DisplayName("resolve_爆炸不可招架: 窗口内仍 → GUARDED")
    void resolveExplosionNeverParries() {
        assertEquals(GuardRules.RESULT_GUARDED,
                resolve(true, true, true, true, DamageClass.EXPLOSION, true));
    }

    @Test
    @DisplayName("resolve_窗口内近战 → PARRIED（步骤 9）")
    void resolveParryWindow() {
        assertEquals(GuardRules.RESULT_PARRIED,
                resolve(true, true, true, true, DamageClass.MELEE, true));
    }

    // ------------------------------------------------------------------
    // C4 frontal check (FR-07, E-03/E-04) — half angle 90° default

    @Test
    @DisplayName("frontal_正面: dot=-1 → 可格挡; 背后 dot=1 → 不可格挡")
    void frontalSides() {
        assertTrue(GuardRules.frontalBlocked(false, -1.0, 1.0, 90));
        assertFalse(GuardRules.frontalBlocked(false, 1.0, 1.0, 90));
    }

    @Test
    @DisplayName("frontal_侧向边界: dot=0（90° 侧向）→ 严格小于 → 不可格挡")
    void frontalPerpendicular() {
        assertFalse(GuardRules.frontalBlocked(false, 0.0, 1.0, 90));
    }

    @Test
    @DisplayName("frontal_E-03: 来源位置 null → 不可格挡")
    void frontalNullSource() {
        assertFalse(GuardRules.frontalBlocked(true, -1.0, 1.0, 90));
    }

    @Test
    @DisplayName("frontal_E-04: 正上方（水平向量退化）→ 不可格挡")
    void frontalAboveSource() {
        assertFalse(GuardRules.frontalBlocked(false, 0.0, 0.0, 90));
    }

    @Test
    @DisplayName("frontal_可配置半角: 45° 时 60° 侧向不可格挡")
    void frontalConfiguredHalfAngle() {
        // dot = -cos(60°) = -0.5; half angle 45° → threshold -cos(45°) ≈ -0.707
        assertFalse(GuardRules.frontalBlocked(false, -0.5, 1.0, 45));
        assertTrue(GuardRules.frontalBlocked(false, -0.9, 1.0, 45));
    }

    // ------------------------------------------------------------------
    // effective strength (§5.9.2 + ADR-09 clamps)

    @Test
    @DisplayName("effective_无加成: 等于基础 gb")
    void effectiveBaseOnly() {
        assertEquals(0.40f, GuardRules.effectiveStrength(0.40f, 0.0f, false, 0.01f, 0.95f), 1e-6f);
    }

    @Test
    @DisplayName("effective_强力防御: 0.50 + 0.40 → 0.70")
    void effectivePowerGuard() {
        assertEquals(0.70f, GuardRules.effectiveStrength(0.50f, 0.40f, true, 0.01f, 0.95f), 1e-6f);
    }

    @Test
    @DisplayName("effective_钳制: 结果不越 ADR-09 上下界")
    void effectiveClamped() {
        assertEquals(0.95f, GuardRules.effectiveStrength(0.94f, 0.80f, true, 0.01f, 0.95f), 1e-6f);
        assertEquals(0.01f, GuardRules.effectiveStrength(0.0f, 0.0f, false, 0.01f, 0.95f), 1e-6f);
    }
}
