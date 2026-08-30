package com.example.blockmod.logic;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** FR-16 (2026-08-30 ruling): power guard drain = max x percent + flat. */
class PowerGuardDrainTest {
    @Test
    @DisplayName("默认配置: 上限 40 × 1% + 1 = 1.4 点/秒")
    void defaultDrain() {
        assertEquals(1.4f, GuardFormulas.powerGuardDrainPerSecond(40f, 1.0f, 1.0f), 1e-6f);
    }

    @Test
    @DisplayName("动态上限: 上限 200 × 1% + 1 = 3.0 点/秒（百分比随上限自动缩放）")
    void dynamicMax() {
        assertEquals(3.0f, GuardFormulas.powerGuardDrainPerSecond(200f, 1.0f, 1.0f), 1e-6f);
    }

    @Test
    @DisplayName("每 tick 折算: 1.4/s ÷ 20 = 0.07 点/tick")
    void perTick() {
        assertEquals(0.07f, GuardFormulas.powerGuardDrainPerSecond(40f, 1.0f, 1.0f) / 20f, 1e-6f);
    }
}
