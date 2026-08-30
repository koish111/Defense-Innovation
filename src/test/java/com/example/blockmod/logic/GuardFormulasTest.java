package com.example.blockmod.logic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec §9.2 table-driven cases for {@link GuardFormulas#staminaCost} and
 * {@link GuardFormulas#combineStrength}. Pure numbers only — no config, no game.
 */
class GuardFormulasTest {
    private static final float MFIX = 9.4f;
    private static final float PFIX_PVE = 0.7f;
    private static final float MIN_COST = 0.5f;
    private static final float MAX_COST = 80.0f; // max_stamina 40 × 2.0

    @ParameterizedTest(name = "[{index}] dmg={0} gb={1} -> {2}")
    @CsvSource({
            "3,  0.35, 6.73",    // staminaCost_正常 (§9.2 row 1)
            "5,  0.40, 8.885",   // cross-check vs §13.3 PvE table (vanilla shield dmg=5 -> 8.9)
            "15, 0.50, 15.979"   // cross-check vs §13.3 PvE table (netherite buckler dmg=15 -> 16.0)
    })
    @DisplayName("staminaCost_正常")
    void staminaCostNormal(float dmg, float gb, float expected) {
        assertEquals(expected, GuardFormulas.staminaCost(dmg, gb, MFIX, PFIX_PVE, MIN_COST, MAX_COST), 0.01f);
    }

    @Test
    @DisplayName("staminaCost_gb上界: gb=0.95 仍为正 (§9.2 row 2)")
    void staminaCostGbUpperBound() {
        float cost = GuardFormulas.staminaCost(3f, 0.95f, MFIX, PFIX_PVE, MIN_COST, MAX_COST);
        assertTrue(cost > 0f && Float.isFinite(cost), "gb=0.95 must not zero out the cost, got " + cost);
    }

    @ParameterizedTest(name = "[{index}] dmg={0}")
    @CsvSource({"0", "-5"})
    @DisplayName("staminaCost_零/负伤害: 走下限 0.5 (§9.2 rows 3-4)")
    void staminaCostFloor(float dmg) {
        assertEquals(MIN_COST, GuardFormulas.staminaCost(dmg, 0.35f, MFIX, PFIX_PVE, MIN_COST, MAX_COST), 1e-6f);
    }

    @Test
    @DisplayName("staminaCost_极大伤害: 走上限 max_stamina×2 (§9.2 row 5)")
    void staminaCostCeiling() {
        assertEquals(MAX_COST, GuardFormulas.staminaCost(1e6f, 0.35f, MFIX, PFIX_PVE, MIN_COST, MAX_COST), 1e-6f);
    }

    @Test
    @DisplayName("combineStrength_单加成: base=0.5 [0.3] -> 0.65 (§9.2 row 6)")
    void combineSingle() {
        assertEquals(0.65f, GuardFormulas.combineStrength(0.5f, 0.3f), 0.0005f);
    }

    @Test
    @DisplayName("combineStrength_多加成: base=0.5 [0.3,0.1] -> 0.685 (§9.2 row 7)")
    void combineMultiple() {
        assertEquals(0.685f, GuardFormulas.combineStrength(0.5f, 0.3f, 0.1f), 0.0005f);
    }

    @Test
    @DisplayName("combineStrength_不超1: base=0.95 [0.8,0.8] -> 0.998 (§9.2 row 8)")
    void combineNeverReachesOne() {
        float result = GuardFormulas.combineStrength(0.95f, 0.8f, 0.8f);
        assertEquals(0.998f, result, 0.0005f);
        assertTrue(result < 1f, "effective strength must stay below 1");
    }
}
