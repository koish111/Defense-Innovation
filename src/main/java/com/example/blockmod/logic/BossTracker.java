package com.example.blockmod.logic;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * FR-14 / T-32: per-(boss × player) successful-parry counters. Not persisted —
 * a restart clears every count (Spec §4.3/FR-24).
 *
 * <p>Pure counters over UUIDs and primitives so the §9.2 rows run in the unit
 * JVM. The MC-facing boss identification lives in {@code ParryService#isBoss}.
 */
public final class BossTracker {
    /** E-18: hard cap — exceeding it wipes the table rather than leaking. */
    static final int MAX_ENTRIES = 1000;

    /** boss id → player id → long[2]{count, expireTick}. */
    private static final Map<UUID, Map<UUID, long[]>> COUNTERS = new HashMap<>();

    private BossTracker() {}

    /**
     * Records one successful parry of {@code bossId} by {@code playerId}.
     *
     * @return true when the threshold is reached (the caller stuns and resets).
     */
    public static boolean increment(UUID bossId, UUID playerId, long now, int threshold, long expireTicks) {
        if (COUNTERS.size() > MAX_ENTRIES) {
            COUNTERS.clear(); // E-18: full wipe instead of unbounded growth
        }
        purgeExpired(now);

        Map<UUID, long[]> perPlayer = COUNTERS.computeIfAbsent(bossId, id -> new HashMap<>());
        long[] entry = perPlayer.get(playerId);
        if (entry == null || now >= entry[1]) {
            entry = new long[]{0, now + expireTicks}; // expired (or first) — restart the streak
        }
        entry[0]++;
        entry[1] = now + expireTicks;
        perPlayer.put(playerId, entry);

        if (entry[0] >= threshold) {
            perPlayer.remove(playerId); // FR-14: reset immediately after the stun fires
            return true;
        }
        return false;
    }

    /** Clears a single boss×player pair (used right after a stun). */
    public static void reset(UUID bossId, UUID playerId) {
        Map<UUID, long[]> perPlayer = COUNTERS.get(bossId);
        if (perPlayer != null) {
            perPlayer.remove(playerId);
        }
    }

    /** Lazy cleanup of expired streaks; runs inside increment (no per-tick cost). */
    private static void purgeExpired(long now) {
        Iterator<Map<UUID, long[]>> bossIter = COUNTERS.values().iterator();
        while (bossIter.hasNext()) {
            Map<UUID, long[]> perPlayer = bossIter.next();
            perPlayer.values().removeIf(entry -> now >= entry[1]);
            if (perPlayer.isEmpty()) {
                bossIter.remove();
            }
        }
    }

    /** Test/inspection helper. */
    static int trackedPairs() {
        return COUNTERS.values().stream().mapToInt(Map::size).sum();
    }
}
