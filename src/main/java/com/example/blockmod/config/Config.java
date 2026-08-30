package com.example.blockmod.config;

import java.util.ArrayList;
import java.util.List;

import com.example.blockmod.BlockModLogger;

import net.neoforged.fml.ModContainer;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.ModConfigSpec;

/**
 * Server-side configuration for every gameplay number (Spec §13.2), in
 * {@code config/blockmod-server.toml}.
 *
 * Range handling: numeric keys carry their range in a validation table instead of
 * ModConfigSpec bounds, because the built-in range check clamps out-of-range values
 * to the nearest bound, while FR-20 requires an ERROR log and fallback to the default.
 * Whitelist (predicate) keys fall back to the default automatically.
 * Cross-field rules are re-checked on every load and hot reload.
 */
public final class Config {
    private static final ModConfigSpec.Builder BUILDER = new ModConfigSpec.Builder();
    private static final List<RangeRule> RANGE_RULES = new ArrayList<>();

    /** A numeric range check: violations log ERROR and fall back to {@code def}. */
    private record RangeRule(java.util.function.DoubleSupplier current, java.util.function.DoubleConsumer reset,
            double min, double max, double def, String key) {}

    /** Defines a double entry owned by {@link #RANGE_RULES}; violations fall back to {@code def}. */
    private static ModConfigSpec.ConfigValue<Double> defineDouble(String comment, String key, double def, double min, double max) {
        BUILDER.comment(comment, "Range: [" + min + ", " + max + "], default: " + def);
        ModConfigSpec.ConfigValue<Double> value = BUILDER.define(key, def);
        RANGE_RULES.add(new RangeRule(value::get, value::set, min, max, def, key));
        return value;
    }

    /** Defines an int entry owned by {@link #RANGE_RULES}; violations fall back to {@code def}. */
    private static ModConfigSpec.ConfigValue<Integer> defineInt(String comment, String key, int def, int min, int max) {
        BUILDER.comment(comment, "Range: [" + min + ", " + max + "], default: " + def);
        ModConfigSpec.ConfigValue<Integer> value = BUILDER.define(key, def);
        RANGE_RULES.add(new RangeRule(() -> value.get(), v -> value.set((int) Math.round(v)), min, max, def, key));
        return value;
    }

    /** Defines a whitelist string entry; invalid values are corrected to the default by the spec itself. */
    private static ModConfigSpec.ConfigValue<String> defineWhitelist(String comment, String key, String def, List<String> allowed) {
        BUILDER.comment(comment, "Allowed: " + allowed + ", default: " + def);
        return BUILDER.define(key, def, o -> o instanceof String s && allowed.contains(s));
    }

    // ==================================================================
    // [stamina]
    // ==================================================================
    private static final ModConfigSpec.ConfigValue<Double> MAX_STAMINA;
    private static final ModConfigSpec.ConfigValue<Double> REGEN_RATE;
    private static final ModConfigSpec.ConfigValue<Double> REGEN_DELAY;
    private static final ModConfigSpec.ConfigValue<Double> GUARD_REGEN_MULTIPLIER;
    private static final ModConfigSpec.ConfigValue<Double> DEPLETED_REGEN_RATE;
    private static final ModConfigSpec.BooleanValue DEPLETED_REMOVE_MOVE_MALUS;
    private static final ModConfigSpec.ConfigValue<Double> DEPLETED_DAMAGE_REDUCTION;
    private static final ModConfigSpec.ConfigValue<Integer> DEPLETION_HYSTERESIS_TICKS;
    private static final ModConfigSpec.ConfigValue<Double> POST_DEPLETION_BOOST_SECONDS;
    private static final ModConfigSpec.BooleanValue FOOD_RESTORE_STAMINA;
    private static final ModConfigSpec.BooleanValue RESET_STAMINA_ON_DEATH;
    private static final ModConfigSpec.BooleanValue RESET_STAMINA_ON_DIMENSION_CHANGE;
    private static final ModConfigSpec.ConfigValue<Double> MIN_COST_PER_GUARD;
    private static final ModConfigSpec.ConfigValue<Double> MAX_COST_PER_GUARD_MULTIPLIER;
    private static final ModConfigSpec.ConfigValue<Double> NEGATIVE_SYNC_CLAMP;

    // ==================================================================
    // [guard]
    // ==================================================================
    private static final ModConfigSpec.ConfigValue<Double> MFIX;
    private static final ModConfigSpec.ConfigValue<Double> PFIX_PVE;
    private static final ModConfigSpec.ConfigValue<Double> PFIX_PVP;
    private static final ModConfigSpec.ConfigValue<String> PVP_MODE;
    private static final ModConfigSpec.BooleanValue AFFECT_CREATIVE;
    private static final ModConfigSpec.ConfigValue<Integer> FRONT_HALF_ANGLE_DEG;
    private static final ModConfigSpec.ConfigValue<Double> EXPLOSION_KNOCKBACK_REDUCTION;
    private static final ModConfigSpec.BooleanValue SWORD_GUARD_REQUIRES_NO_BLOCK_TARGET;
    private static final ModConfigSpec.ConfigValue<Double> MIN_GB;
    private static final ModConfigSpec.ConfigValue<Double> MAX_GB;

    // ==================================================================
    // [parry]
    // ==================================================================
    private static final ModConfigSpec.ConfigValue<Integer> SWORD_PARRY_WINDOW;
    private static final ModConfigSpec.ConfigValue<Integer> BUCKLER_PARRY_WINDOW;
    private static final ModConfigSpec.ConfigValue<Integer> STUN_DURATION;
    private static final ModConfigSpec.ConfigValue<Integer> PARRY_COOLDOWN_TICKS;
    private static final ModConfigSpec.ConfigValue<Integer> BOSS_PARRY_THRESHOLD;
    private static final ModConfigSpec.ConfigValue<Integer> BOSS_PARRY_COUNTER_EXPIRE;
    private static final ModConfigSpec.ConfigValue<Double> DEFLECT_SPEED_MULTIPLIER;
    private static final ModConfigSpec.ConfigValue<Double> DEFLECT_YAW_JITTER_DEG;
    private static final ModConfigSpec.ConfigValue<Integer> LATENCY_COMPENSATION_MAX_TICKS;

    // ==================================================================
    // [shield_bash]
    // ==================================================================
    private static final ModConfigSpec.ConfigValue<Double> BASH_DAMAGE;
    private static final ModConfigSpec.ConfigValue<Double> BASH_KNOCKBACK_BLOCKS;
    private static final ModConfigSpec.ConfigValue<Integer> BASH_COOLDOWN_TICKS;
    private static final ModConfigSpec.ConfigValue<Integer> BASH_WINDUP_TICKS;
    private static final ModConfigSpec.ConfigValue<Double> BASH_RANGE_BLOCKS;
    private static final ModConfigSpec.ConfigValue<Integer> BASH_HALF_ANGLE_DEG;
    private static final ModConfigSpec.ConfigValue<Double> BASH_CONSUME_STAMINA;
    private static final ModConfigSpec.BooleanValue BASH_REQUIRES_POSITIVE_STAMINA;

    // ==================================================================
    // [power_guard]
    // ==================================================================
    private static final ModConfigSpec.ConfigValue<Double> PG_STAMINA_DRAIN_PERCENT;
    private static final ModConfigSpec.ConfigValue<Double> PG_STAMINA_DRAIN_FLAT;
    private static final ModConfigSpec.BooleanValue PG_DISABLE_JUMP;
    private static final ModConfigSpec.BooleanValue PG_SUSPEND_REGEN;
    private static final ModConfigSpec.ConfigValue<String> PG_KEY;

    // ==================================================================
    // [durability]
    // ==================================================================
    private static final ModConfigSpec.ConfigValue<Integer> MIN_DAMAGE_FOR_DURABILITY_LOSS;
    private static final ModConfigSpec.ConfigValue<String> DURABILITY_FORMULA;

    // ==================================================================
    // [boss_detection]
    // ==================================================================
    private static final ModConfigSpec.ConfigValue<String> BOSS_DETECTION_METHOD;
    private static final ModConfigSpec.ConfigValue<List<? extends String>> BOSS_ENTITY_LIST;

    // ==================================================================
    // [network]
    // ==================================================================
    private static final ModConfigSpec.ConfigValue<Double> SYNC_THRESHOLD;
    private static final ModConfigSpec.ConfigValue<Integer> SYNC_INTERVAL_TICKS;
    private static final ModConfigSpec.ConfigValue<Integer> C2S_RATE_LIMIT_PER_SECOND;
    private static final ModConfigSpec.ConfigValue<Integer> STATE_HEARTBEAT_TICKS;
    private static final ModConfigSpec.ConfigValue<Integer> GUARD_TIMEOUT_TICKS;

    // ==================================================================
    // [compat]
    // ==================================================================
    private static final ModConfigSpec.ConfigValue<List<? extends String>> COMPAT_DISABLED_BY_MODIDS;
    private static final ModConfigSpec.ConfigValue<String> UNKNOWN_SHIELD_DEFAULT;

    // ==================================================================
    // [debug]
    // ==================================================================
    private static final ModConfigSpec.BooleanValue VERBOSE_LOGGING;
    private static final ModConfigSpec.ConfigValue<Integer> LOG_EVENT_BUFFER_SIZE;

    static {
        BUILDER.comment("Block & Parry server configuration (Spec §13.2).").push("stamina");
        MAX_STAMINA = defineDouble("Maximum stamina. May go negative when depleted (no lower clamp).", "max_stamina", 40.0, 1.0, 1000.0);
        REGEN_RATE = defineDouble("Stamina per second while stamina > 0 and out of regen delay.", "regen_rate", 4.0, 0.0, 100.0);
        REGEN_DELAY = defineDouble("Seconds of no regen after the last blocked hit or hit taken (only when stamina > 0).", "regen_delay", 2.0, 0.0, 30.0);
        GUARD_REGEN_MULTIPLIER = defineDouble("Multiplier applied to regen_rate while guarding.", "guard_regen_multiplier", 0.5, 0.0, 2.0);
        DEPLETED_REGEN_RATE = defineDouble("Stamina per second while stamina <= 0. Highest priority: ignores delay, guard and power guard.", "depleted_regen_rate", 8.0, 0.1, 100.0);
        DEPLETED_REMOVE_MOVE_MALUS = BUILDER.comment("ADR-15: automatically remove the move-speed penalty while depleted.").define("depleted_remove_move_malus", true);
        DEPLETED_DAMAGE_REDUCTION = defineDouble("R-13 fallback: short damage reduction after depletion. 0 = disabled.", "depleted_damage_reduction", 0.0, 0.0, 0.9);
        DEPLETION_HYSTERESIS_TICKS = defineInt("R-14 fallback: hysteresis window (ticks) around depletion transitions. 0 = disabled.", "depletion_hysteresis_ticks", 0, 0, 40);
        POST_DEPLETION_BOOST_SECONDS = defineDouble("O-24 fallback: keep the depleted regen rate this long after recovery. 0 = disabled.", "post_depletion_boost_seconds", 0.0, 0.0, 30.0);
        FOOD_RESTORE_STAMINA = BUILDER.comment("FR-03: full-hunger eating restores stamina.").define("food_restore_stamina", true);
        RESET_STAMINA_ON_DEATH = BUILDER.comment("Reset stamina to max on death instead of persisting it.").define("reset_stamina_on_death", true);
        RESET_STAMINA_ON_DIMENSION_CHANGE = BUILDER.comment("Reset stamina to max on dimension change instead of persisting it.").define("reset_stamina_on_dimension_change", false);
        MIN_COST_PER_GUARD = defineDouble("ADR-09: lower clamp for a single blocked hit's stamina cost.", "min_cost_per_guard", 0.5, 0.0, 100.0);
        MAX_COST_PER_GUARD_MULTIPLIER = defineDouble("Upper clamp for a single blocked hit's cost, relative to max_stamina.", "max_cost_per_guard_multiplier", 2.0, 0.1, 10.0);
        NEGATIVE_SYNC_CLAMP = defineDouble("O-16: display clamp for the stamina value synced to clients.", "negative_sync_clamp", -40.0, -1000.0, 0.0);
        BUILDER.pop();

        BUILDER.comment("Guard formulas and combat-wide rules.").push("guard");
        MFIX = defineDouble("Stamina cost formula: cost = (max(dmg,0) * MFIX)^PFIX * (1 - effectiveGb).", "MFIX", 9.4, 0.1, 100.0);
        PFIX_PVE = defineDouble("PvE exponent of the stamina cost formula.", "PFIX_PVE", 0.7, 0.1, 3.0);
        PFIX_PVP = defineDouble("PvP exponent of the stamina cost formula (ADR-17 / O-05).", "PFIX_PVP", 0.9, 0.1, 3.0);
        PVP_MODE = defineWhitelist("PvP/PvE selection.", "pvp_mode", "auto", List.of("auto", "always_pvp", "always_pve"));
        AFFECT_CREATIVE = BUILDER.comment("ADR-13: apply stamina costs to creative players. Default off (creative exempt).").define("affect_creative", false);
        FRONT_HALF_ANGLE_DEG = defineInt("Half angle of the frontal guard arc in degrees. 90 = frontal 180 degrees.", "front_half_angle_deg", 90, 10, 180);
        EXPLOSION_KNOCKBACK_REDUCTION = defineDouble("Knockback taken off a blocked explosion. Designer ruling 2026-08-30 supersedes ADR-12: 1.0 = blocked explosions ignore knockback entirely.", "explosion_knockback_reduction", 1.0, 0.0, 1.0);
        SWORD_GUARD_REQUIRES_NO_BLOCK_TARGET = BUILDER.comment("R-04: sword guarding requires not looking at a usable block (vanilla right-click wins otherwise).").define("sword_guard_requires_no_block_target", true);
        MIN_GB = defineDouble("ADR-09: lower clamp for guard strength.", "min_gb", 0.01, 0.0, 0.5);
        MAX_GB = defineDouble("ADR-09: upper clamp for guard strength. Must stay below 1.0.", "max_gb", 0.95, 0.5, 0.99);
        BUILDER.pop();

        BUILDER.push("parry");
        SWORD_PARRY_WINDOW = defineInt("Parry window in ticks for swords.", "sword_parry_window", 5, 0, 20);
        BUCKLER_PARRY_WINDOW = defineInt("Parry window in ticks for bucklers.", "buckler_parry_window", 10, 0, 20);
        STUN_DURATION = defineInt("Stun duration in ticks applied to a parried attacker.", "stun_duration", 20, 0, 200);
        PARRY_COOLDOWN_TICKS = defineInt("ADR-07: ticks after a parry (or guard raise) before a new window may open.", "parry_cooldown_ticks", 10, 0, 100);
        BOSS_PARRY_THRESHOLD = defineInt("Successful parries required to stun a boss.", "boss_parry_threshold", 3, 1, 20);
        BOSS_PARRY_COUNTER_EXPIRE = defineInt("Ticks before accumulated boss parries expire.", "boss_parry_counter_expire", 200, 20, 1200);
        DEFLECT_SPEED_MULTIPLIER = defineDouble("Speed multiplier applied to a deflected projectile.", "deflect_speed_multiplier", 0.8, 0.1, 2.0);
        DEFLECT_YAW_JITTER_DEG = defineDouble("Random yaw jitter (degrees) applied to a deflected projectile.", "deflect_yaw_jitter_deg", 40.0, 0.0, 90.0);
        LATENCY_COMPENSATION_MAX_TICKS = defineInt("O-23 (post-MVP): parry window extension by player ping. 0 = disabled.", "latency_compensation_max_ticks", 0, 0, 6);
        BUILDER.pop();

        BUILDER.push("shield_bash");
        BASH_DAMAGE = defineDouble("FR-15: shield bash damage.", "damage", 8.0, 0.0, 100.0);
        BASH_KNOCKBACK_BLOCKS = defineDouble("FR-15: shield bash knockback in blocks.", "knockback_blocks", 4.0, 0.0, 20.0);
        BASH_COOLDOWN_TICKS = defineInt("FR-15: shield bash cooldown, counted from resolution.", "cooldown_ticks", 20, 0, 200);
        BASH_WINDUP_TICKS = defineInt("FR-15: shield bash windup in ticks.", "windup_ticks", 5, 0, 40);
        BASH_RANGE_BLOCKS = defineDouble("FR-15: shield bash reach in blocks.", "range_blocks", 3.0, 1.0, 10.0);
        BASH_HALF_ANGLE_DEG = defineInt("FR-15: half angle of the bash hit arc in degrees.", "half_angle_deg", 45, 5, 90);
        BASH_CONSUME_STAMINA = defineDouble("O-19: stamina consumed per bash. 0 = free.", "consume_stamina", 0.0, 0.0, 40.0);
        BASH_REQUIRES_POSITIVE_STAMINA = BUILDER.comment("O-20: forbid shield bash while depleted. Default allows it.").define("requires_positive_stamina", false);
        BUILDER.pop();

        BUILDER.push("power_guard");
        PG_STAMINA_DRAIN_PERCENT = defineDouble("FR-16: power guard drain per second, percentage of max_stamina (follows a dynamic max automatically).", "stamina_drain_percent", 1.0, 0.0, 100.0);
        PG_STAMINA_DRAIN_FLAT = defineDouble("FR-16: power guard drain per second, flat points on top of the percentage. Total = max_stamina x percent + flat (designer ruling 2026-08-30).", "stamina_drain_flat", 1.0, 0.0, 100.0);
        PG_DISABLE_JUMP = BUILDER.comment("FR-16: jumping is disabled during power guard.").define("disable_jump", true);
        PG_SUSPEND_REGEN = BUILDER.comment("ADR-08: stamina regen is suspended during power guard.").define("suspend_regen", true);
        PG_KEY = BUILDER.comment("FR-16: key binding name for power guard (client display only).").define("key", "key.keyboard.left.alt", o -> o instanceof String s && !s.isBlank());
        BUILDER.pop();

        BUILDER.push("durability");
        MIN_DAMAGE_FOR_DURABILITY_LOSS = defineInt("FR-10: blocked hits below this raw damage never consume shield durability.", "min_damage_for_durability_loss", 3, 0, 1000);
        DURABILITY_FORMULA = defineWhitelist("FR-10: durability formula. Only floor_damage_plus_one is implemented.", "durability_formula", "floor_damage_plus_one", List.of("floor_damage_plus_one"));
        BUILDER.pop();

        BUILDER.push("boss_detection");
        BOSS_DETECTION_METHOD = defineWhitelist("FR-14: how bosses are detected.", "method", "tag", List.of("tag", "config_list"));
        BOSS_ENTITY_LIST = BUILDER.comment("FR-14: entity IDs treated as bosses when method = config_list.").defineListAllowEmpty("entity_list", List.of(), () -> "", o -> o instanceof String s && s.contains(":"));
        BUILDER.pop();

        BUILDER.push("network");
        SYNC_THRESHOLD = defineDouble("FR-23: stamina change (points) that triggers an immediate sync.", "sync_threshold", 0.25, 0.0, 10.0);
        SYNC_INTERVAL_TICKS = defineInt("FR-23: forced stamina sync period in ticks.", "sync_interval_ticks", 20, 1, 200);
        C2S_RATE_LIMIT_PER_SECOND = defineInt("FR-23: client-to-server intent packets allowed per second per player.", "c2s_rate_limit_per_second", 20, 1, 100);
        STATE_HEARTBEAT_TICKS = defineInt("R-07: guard-state heartbeat interval in ticks.", "state_heartbeat_ticks", 40, 10, 600);
        GUARD_TIMEOUT_TICKS = defineInt("R-07: ticks without a heartbeat before the server drops the guard state.", "guard_timeout_ticks", 100, 20, 1200);
        BUILDER.pop();

        BUILDER.push("compat");
        COMPAT_DISABLED_BY_MODIDS = BUILDER.comment("R-05 (post-MVP): mod ids this mod's guard system disables itself for.").defineListAllowEmpty("disabled_by_modids", List.of(), () -> "", o -> o instanceof String s && !s.isBlank());
        UNKNOWN_SHIELD_DEFAULT = defineWhitelist("FR-27 (post-MVP): classification for third-party shields without a profile.", "unknown_shield_default", "medium", List.of("none", "buckler", "medium", "great"));
        BUILDER.pop();

        BUILDER.push("debug");
        VERBOSE_LOGGING = BUILDER.comment("Emit verbose [BP] diagnostics for guard/parry resolution.").define("verbose_logging", false);
        LOG_EVENT_BUFFER_SIZE = defineInt("Size of the in-memory recent-events ring used by /blockparry debug.", "log_event_buffer_size", 100, 10, 10000);
        BUILDER.pop();
    }

    public static final ModConfigSpec SPEC = BUILDER.build();

    // ==================================================================
    // accessors — the only way gameplay code reads numbers
    // ==================================================================

    // [stamina]
    public static float maxStamina() { return MAX_STAMINA.get().floatValue(); }
    public static float regenRate() { return REGEN_RATE.get().floatValue(); }
    public static float regenDelaySeconds() { return REGEN_DELAY.get().floatValue(); }
    public static float guardRegenMultiplier() { return GUARD_REGEN_MULTIPLIER.get().floatValue(); }
    public static float depletedRegenRate() { return DEPLETED_REGEN_RATE.get().floatValue(); }
    public static boolean depletedRemoveMoveMalus() { return DEPLETED_REMOVE_MOVE_MALUS.get(); }
    public static float depletedDamageReduction() { return DEPLETED_DAMAGE_REDUCTION.get().floatValue(); }
    public static int depletionHysteresisTicks() { return DEPLETION_HYSTERESIS_TICKS.get(); }
    public static float postDepletionBoostSeconds() { return POST_DEPLETION_BOOST_SECONDS.get().floatValue(); }
    public static boolean foodRestoreStamina() { return FOOD_RESTORE_STAMINA.get(); }
    public static boolean resetStaminaOnDeath() { return RESET_STAMINA_ON_DEATH.get(); }
    public static boolean resetStaminaOnDimensionChange() { return RESET_STAMINA_ON_DIMENSION_CHANGE.get(); }
    public static float minCostPerGuard() { return MIN_COST_PER_GUARD.get().floatValue(); }
    public static float maxCostPerGuard() { return maxStamina() * MAX_COST_PER_GUARD_MULTIPLIER.get().floatValue(); }
    public static float negativeSyncClamp() { return NEGATIVE_SYNC_CLAMP.get().floatValue(); }

    // [guard]
    public static float mfix() { return MFIX.get().floatValue(); }
    public static float pfixPve() { return PFIX_PVE.get().floatValue(); }
    public static float pfixPvp() { return PFIX_PVP.get().floatValue(); }
    public static String pvpMode() { return PVP_MODE.get(); }
    public static boolean affectCreative() { return AFFECT_CREATIVE.get(); }
    public static int frontHalfAngleDeg() { return FRONT_HALF_ANGLE_DEG.get(); }
    public static float explosionKnockbackReduction() { return EXPLOSION_KNOCKBACK_REDUCTION.get().floatValue(); }
    public static boolean swordGuardRequiresNoBlockTarget() { return SWORD_GUARD_REQUIRES_NO_BLOCK_TARGET.get(); }
    public static float minGb() {
        float min = MIN_GB.get().floatValue();
        return min >= maxGb() ? 0.01f : min;
    }
    public static float maxGb() { return MAX_GB.get().floatValue(); }

    // [parry]
    public static int swordParryWindow() { return SWORD_PARRY_WINDOW.get(); }
    public static int bucklerParryWindow() { return BUCKLER_PARRY_WINDOW.get(); }
    public static int stunDuration() { return STUN_DURATION.get(); }
    public static int parryCooldownTicks() { return PARRY_COOLDOWN_TICKS.get(); }
    public static int bossParryThreshold() { return BOSS_PARRY_THRESHOLD.get(); }
    public static int bossParryCounterExpire() { return BOSS_PARRY_COUNTER_EXPIRE.get(); }
    public static float deflectSpeedMultiplier() { return DEFLECT_SPEED_MULTIPLIER.get().floatValue(); }
    public static float deflectYawJitterDeg() { return DEFLECT_YAW_JITTER_DEG.get().floatValue(); }
    public static int latencyCompensationMaxTicks() { return LATENCY_COMPENSATION_MAX_TICKS.get(); }

    // [shield_bash]
    public static float bashDamage() { return BASH_DAMAGE.get().floatValue(); }
    public static float bashKnockbackBlocks() { return BASH_KNOCKBACK_BLOCKS.get().floatValue(); }
    public static int bashCooldownTicks() { return BASH_COOLDOWN_TICKS.get(); }
    public static int bashWindupTicks() { return BASH_WINDUP_TICKS.get(); }
    public static float bashRangeBlocks() { return BASH_RANGE_BLOCKS.get().floatValue(); }
    public static int bashHalfAngleDeg() { return BASH_HALF_ANGLE_DEG.get(); }
    public static float bashConsumeStamina() { return BASH_CONSUME_STAMINA.get().floatValue(); }
    public static boolean bashRequiresPositiveStamina() { return BASH_REQUIRES_POSITIVE_STAMINA.get(); }

    // [power_guard]
    public static float pgStaminaDrainPercent() { return PG_STAMINA_DRAIN_PERCENT.get().floatValue(); }
    public static float pgStaminaDrainFlat() { return PG_STAMINA_DRAIN_FLAT.get().floatValue(); }
    public static boolean pgDisableJump() { return PG_DISABLE_JUMP.get(); }
    public static boolean pgSuspendRegen() { return PG_SUSPEND_REGEN.get(); }
    public static String pgKey() { return PG_KEY.get(); }

    // [durability]
    public static int minDamageForDurabilityLoss() { return MIN_DAMAGE_FOR_DURABILITY_LOSS.get(); }
    public static String durabilityFormula() { return DURABILITY_FORMULA.get(); }

    // [boss_detection]
    public static String bossDetectionMethod() { return BOSS_DETECTION_METHOD.get(); }
    public static List<? extends String> bossEntityList() { return BOSS_ENTITY_LIST.get(); }

    // [network]
    public static float syncThreshold() { return SYNC_THRESHOLD.get().floatValue(); }
    public static int syncIntervalTicks() { return SYNC_INTERVAL_TICKS.get(); }
    public static int c2sRateLimitPerSecond() { return C2S_RATE_LIMIT_PER_SECOND.get(); }
    public static int stateHeartbeatTicks() { return STATE_HEARTBEAT_TICKS.get(); }
    public static int guardTimeoutTicks() { return GUARD_TIMEOUT_TICKS.get(); }

    // [compat]
    public static List<? extends String> compatDisabledByModids() { return COMPAT_DISABLED_BY_MODIDS.get(); }
    public static String unknownShieldDefault() { return UNKNOWN_SHIELD_DEFAULT.get(); }

    // [debug]
    public static boolean verboseLogging() { return VERBOSE_LOGGING.get(); }
    public static int logEventBufferSize() { return LOG_EVENT_BUFFER_SIZE.get(); }

    // ==================================================================
    // load / reload
    // ==================================================================

    public static void register(ModContainer container) {
        container.registerConfig(ModConfig.Type.SERVER, SPEC);
    }

    /** Checks every range rule and cross-field rule; violations are logged and reset to their default. */
    private static void validate() {
        boolean changed = false;
        for (RangeRule rule : RANGE_RULES) {
            double current = rule.current().getAsDouble();
            if (current < rule.min() || current > rule.max()) {
                BlockModLogger.error("CONFIG", "rule", "range", "key", rule.key(), "value", current,
                        "range", rule.min() + ".." + rule.max(), "action", "fallback to default " + rule.def());
                rule.reset().accept(rule.def());
                changed = true;
            }
        }
        if (MIN_GB.get() >= MAX_GB.get()) {
            BlockModLogger.error("CONFIG", "rule", "min_gb < max_gb", "min_gb", MIN_GB.get(), "max_gb", MAX_GB.get(),
                    "action", "min_gb falls back to 0.01");
            MIN_GB.set(0.01);
            changed = true;
        }
        if (MIN_COST_PER_GUARD.get() > MAX_STAMINA.get() * MAX_COST_PER_GUARD_MULTIPLIER.get()) {
            BlockModLogger.error("CONFIG", "rule", "min_cost_per_guard <= max_stamina * max_cost_per_guard_multiplier",
                    "min_cost_per_guard", MIN_COST_PER_GUARD.get(), "action", "fallback to default 0.5");
            MIN_COST_PER_GUARD.set(0.5);
            changed = true;
        }
        if (changed) {
            SPEC.save();
        }
    }

    public static void onConfigLoad(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC || event instanceof ModConfigEvent.Unloading) {
            return;
        }
        validate();
        BlockModLogger.info("CONFIG",
                "phase", event instanceof ModConfigEvent.Reloading ? "reloading" : "loading",
                "maxStamina", MAX_STAMINA.get(),
                "regenRate", REGEN_RATE.get(),
                "depletedRegenRate", DEPLETED_REGEN_RATE.get(),
                "MFIX", MFIX.get(), "pfixPve", PFIX_PVE.get(), "pfixPvp", PFIX_PVP.get());
        // FR-20: a config_sync push to all online players lands here with M2 T-18 (networking).
    }

    private Config() {}
}
