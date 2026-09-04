package com.example.blockmod.logic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec §9.2 v2.0 rows: regen branch selection, isDepleted bounds, depletion edge
 * detection and the move-malus decision table — all through {@link GuardRules},
 * which carries no Minecraft types so the test JVM can link it without the game.
 */
class GuardRulesTest {
    private static final float REGEN = 4.0f;
    private static final float GUARD_MULT = 0.5f;
    private static final float DEPLETED = 8.0f;
    private static final long DELAY_TICKS = 40L; // 2.0 s × 20

    private static float rate(float stamina, boolean guarding, boolean powerGuarding, long ticksSinceEvent) {
        return GuardRules.regenRatePerSecond(stamina, guarding, powerGuarding,
                ticksSinceEvent, DELAY_TICKS, REGEN, GUARD_MULT, DEPLETED);
    }

    // ------------------------------------------------------------------
    // regenRate_* rows (§9.2)

    @ParameterizedTest(name = "[{index}] st={0} guarding={1} pg={2} since={3} -> {4}")
    @CsvSource({
            "20.0,  false, false, 999, 4.0",   // regenRate_正常
            "20.0,  true,  false, 999, 2.0",   // regenRate_格挡中
            "20.0,  false, false, 39,  0.0",   // regenRate_延迟中 (still inside 40 ticks)
            "20.0,  true,  false, 40,  2.0",   // delay boundary releases guard regen
            "20.0,  true,  true,  999, 0.0",   // regenRate_强力防御 (ADR-08)
            "20.0,  false, true,  0,   0.0"    // power guard suppresses regen even in delay
    })
    @DisplayName("regenRate_分支选择")
    void regenBranches(float stamina, boolean guarding, boolean powerGuarding, long since, float expected) {
        assertEquals(expected, rate(stamina, guarding, powerGuarding, since), 1e-6f);
    }

    @ParameterizedTest(name = "[{index}] st={0} guarding={1} pg={2}")
    @CsvSource({
            "-10.0, true,  false",   // regenRate_枯竭负值: 8/s even while guarding
            "0.0,   false, false",   // regenRate_枯竭零值: ADR-18, 0 counts as depleted
            "-5.0,  true,  true"     // regenRate_枯竭且PG: depleted beats the PG suppression (no deadlock)
    })
    @DisplayName("regenRate_枯竭优先")
    void regenDepletedWins(float stamina, boolean guarding, boolean powerGuarding) {
        assertEquals(8.0f, rate(stamina, guarding, powerGuarding, 0L), 1e-6f);
    }

    @Test
    @DisplayName("regenRate_上限钳制: 39.9 + 4/s 收敛到 40.0")
    void regenClampedToMax() {
        float stamina = 39.9f;
        float perTick = REGEN / 20f;
        for (int i = 0; i < 40; i++) {
            stamina = Math.min(stamina + perTick, 40.0f);
        }
        assertEquals(40.0f, stamina, 1e-6f);
    }

    @Test
    @DisplayName("regenRate_跨越零点: -0.3 + 0.4/tick -> +0.1 不截断")
    void regenCrossesZero() {
        float stamina = -0.3f;
        stamina = Math.min(stamina + DEPLETED / 20f, 40.0f);
        assertEquals(0.1f, stamina, 1e-6f);
    }

    // ------------------------------------------------------------------
    // isDepleted_边界 (§9.2)

    @ParameterizedTest(name = "[{index}] st={0} -> depleted={1}")
    @CsvSource({
            "0.0,    true",
            "0.001,  false",
            "-0.001, true"
    })
    @DisplayName("isDepleted_边界")
    void isDepletedBounds(float stamina, boolean expected) {
        assertEquals(expected, stamina <= 0f);
    }

    // ------------------------------------------------------------------
    // depletionEdge_* rows (§9.2 v2.0) — driven through the pure boolean core

    @Test
    @DisplayName("depletionEdge_进入: 0.5 -> -0.2 触发一次, wasDepleted 置 true")
    void edgeEnter() {
        boolean wasDepleted = false;
        assertFalse(GuardRules.depletionEdgeFlipped(wasDepleted, 0.5f));
        assertTrue(GuardRules.depletionEdgeFlipped(wasDepleted, -0.2f));
        wasDepleted = true;
        assertTrue(wasDepleted);
    }

    @Test
    @DisplayName("depletionEdge_脱离: -0.2 -> 0.5 触发一次, wasDepleted 置 false")
    void edgeExit() {
        boolean wasDepleted = true;
        assertTrue(GuardRules.depletionEdgeFlipped(wasDepleted, 0.5f));
        wasDepleted = false;
        assertFalse(wasDepleted);
    }

    @Test
    @DisplayName("depletionEdge_无跳变: -5 -> -4.8 连续 10 tick 副作用 0 次")
    void edgeNoThrashWhileDepleted() {
        boolean wasDepleted = true;
        float stamina = -5.0f;
        int fired = 0;
        for (int i = 0; i < 10; i++) {
            stamina += 0.2f; // still negative
            if (GuardRules.depletionEdgeFlipped(wasDepleted, stamina)) {
                fired++;
                wasDepleted = stamina <= 0f;
            }
        }
        assertEquals(0, fired);
        assertTrue(wasDepleted);
    }

    @Test
    @DisplayName("depletionEdge_反复横跳: 次数等于实际跨越次数")
    void edgeCountsEveryCrossing() {
        boolean wasDepleted = false;
        int fired = 0;
        int crossings = 0;
        float[] sequence = {0.05f, -0.05f, 0.05f, -0.05f, 0.05f, -0.05f, 0.05f, -0.05f};
        for (float value : sequence) {
            boolean depletedNow = value <= 0f;
            if (depletedNow != wasDepleted) {
                crossings++;
            }
            if (GuardRules.depletionEdgeFlipped(wasDepleted, value)) {
                fired++;
                wasDepleted = depletedNow;
            }
        }
        assertEquals(crossings, fired);
        assertEquals(7, fired); // 8 alternating values starting un-depleted -> 7 transitions
    }

    // ------------------------------------------------------------------
    // moveMalus_* rows (§9.2 v2.0, decision part of FR-17)

    @ParameterizedTest(name = "[{index}] guarding={0} positive={1} -> mount={2}")
    @CsvSource({
            "true,  true,  true",    // moveMalus_正常格挡
            "true,  false, false",   // moveMalus_枯竭仍举盾 (ADR-15)
            "false, true,  false",   // moveMalus_未举盾
            "false, false, false"
    })
    @DisplayName("moveMalus_挂载决策")
    void moveMalusDecision(boolean guarding, boolean staminaPositive, boolean expected) {
        assertEquals(expected, GuardRules.shouldApplyMalus(guarding, staminaPositive, -0.7f));
    }

    @Test
    @DisplayName("moveMalus_零惩罚不挂载: 剑的 0% 惩罚跳过属性修改")
    void moveMalusZeroSkipsMount() {
        assertFalse(GuardRules.shouldApplyMalus(true, true, 0.0f));
    }

    // ------------------------------------------------------------------
    // greatshieldShove_* rows (designer ruling 2026-09-04)

    @ParameterizedTest(name = "[{index}] great={0} pg={1} -> mode={2}")
    @CsvSource({
            "false, false, 0",   // greatshieldShove_非大盾: 无击退
            "false, true,  0",   // greatshieldShove_非大盾且PG: 小圆/中盾/剑无 PG,更无击退
            "true,  false, 1",   // greatshieldShove_大盾格挡: 攻击者被击退 1 格
            "true,  true,  2"    // greatshieldShove_大盾PG: 改为前方群体击退,替换单体
    })
    @DisplayName("greatshieldShove_模式选择")
    void greatshieldShoveModeDecision(boolean greatShield, boolean powerGuarding, int expected) {
        assertEquals(expected, GuardRules.greatshieldShoveMode(greatShield, powerGuarding));
    }
}
