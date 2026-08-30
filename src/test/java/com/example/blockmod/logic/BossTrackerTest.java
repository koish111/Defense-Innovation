package com.example.blockmod.logic;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Spec §9.2 bossTracker rows + FR-14 semantics + the E-18 bound. Pure UUID/primitive
 * counters — no Minecraft types.
 */
class BossTrackerTest {
    private static final int THRESHOLD = 3;
    private static final long EXPIRE = 200L;

    @Test
    @DisplayName("bossTracker_三次: 连续 3 次 → 第 3 次返回 true 并清零")
    void thirdParryStuns() {
        UUID boss = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        assertFalse(BossTracker.increment(boss, player, 0L, THRESHOLD, EXPIRE));
        assertFalse(BossTracker.increment(boss, player, 10L, THRESHOLD, EXPIRE));
        assertTrue(BossTracker.increment(boss, player, 20L, THRESHOLD, EXPIRE)); // FR-14: counter reset on stun
        // after the reset the streak restarts
        assertFalse(BossTracker.increment(boss, player, 30L, THRESHOLD, EXPIRE));
    }

    @Test
    @DisplayName("bossTracker_过期: 2 次后 + 201 刻 + 1 次 → false（已过期清零）")
    void expiryClearsStreak() {
        UUID boss = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        BossTracker.increment(boss, player, 0L, THRESHOLD, EXPIRE);
        BossTracker.increment(boss, player, 10L, THRESHOLD, EXPIRE);
        boolean stunned = BossTracker.increment(boss, player, 10L + 201L, THRESHOLD, EXPIRE);
        assertFalse(stunned, "the streak must have expired and restarted");
    }

    @Test
    @DisplayName("FR-14: 计数按 boss×player 二元组隔离")
    void countersArePerBossAndPerPlayer() {
        UUID boss = UUID.randomUUID();
        UUID alice = UUID.randomUUID();
        UUID bob = UUID.randomUUID();
        BossTracker.increment(boss, alice, 0L, THRESHOLD, EXPIRE);
        BossTracker.increment(boss, alice, 1L, THRESHOLD, EXPIRE);
        assertFalse(BossTracker.increment(boss, bob, 2L, THRESHOLD, EXPIRE), "another player's count is independent");
        assertTrue(BossTracker.increment(boss, alice, 3L, THRESHOLD, EXPIRE));
    }

    @Test
    @DisplayName("E-18: 条目超过 1000 全量清理，不崩溃")
    void hardBoundWipesTable() {
        for (int i = 0; i < 1200; i++) {
            BossTracker.increment(UUID.randomUUID(), UUID.randomUUID(), 0L, THRESHOLD, EXPIRE);
        }
        // the table was wiped; a fresh streak starts from zero
        UUID boss = UUID.randomUUID();
        UUID player = UUID.randomUUID();
        assertFalse(BossTracker.increment(boss, player, 0L, THRESHOLD, EXPIRE));
        assertFalse(BossTracker.increment(boss, player, 1L, THRESHOLD, EXPIRE));
        assertTrue(BossTracker.increment(boss, player, 2L, THRESHOLD, EXPIRE));
    }
}
