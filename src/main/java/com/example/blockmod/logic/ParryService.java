package com.example.blockmod.logic;

import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.DamageClass;
import com.example.blockmod.data.GuardProfile;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.registry.ModAttachments;
import com.example.blockmod.registry.ModEffects;
import com.example.blockmod.registry.ModTags;
import com.example.blockmod.state.GuardStateData;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.AbstractArrow;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * T-31/T-33/T-34: the parry pipeline (Spec §5.5, FR-12/13/14).
 *
 * <p>Window lifecycle: a window opens for {@code windowTicks} when the guard
 * state ENTERS (checked against {@code parryReadyTick}, ADR-07), closes on the
 * first successful parry or on expiry, and the re-entry cooldown anchors to the
 * moment the guard is RELEASED — FR-12 acceptance 3 (release + 5 ticks → no
 * window, release + 10 ticks → window).
 */
public final class ParryService {
    private static void spawnCrit(net.minecraft.world.level.Level level, double x, double y, double z) {
        if (level instanceof net.minecraft.server.level.ServerLevel serverLevel) {
            serverLevel.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT, x, y, z, 12, 0.3, 0.3, 0.3, 0.15);
        }
    }

    private ParryService() {}

    // ------------------------------------------------------------------
    // window lifecycle (T-34)

    /** FR-12 table: the window is a per-TYPE rule; medium/great shields never parry. */
    public static int windowTicks(GuardProfile profile) {
        return switch (profile.type()) {
            case SWORD -> Config.swordParryWindow();
            case BUCKLER -> Config.bucklerParryWindow();
            default -> 0;
        };
    }

    /** Called from the guard-enter path; opens the window unless type/cooldown forbid it. */
    public static void openWindow(ServerPlayer player, GuardStateData guardState, GuardProfile profile, long now) {
        int window = windowTicks(profile);
        if (window > 0 && now >= guardState.parryReadyTick()) {
            guardState.setParryWindowEndTick(now + window);
            guardState.setParryUsed(false);
        } else {
            guardState.setParryWindowEndTick(-1L);
        }
    }

    /** Called from the guard-exit path; anchors the ADR-07 re-entry cooldown to the release. */
    public static void closeWindowOnRelease(ServerPlayer player, GuardStateData guardState, long now) {
        long cooldownEnd = now + Config.parryCooldownTicks();
        if (guardState.parryReadyTick() < cooldownEnd) {
            guardState.setParryReadyTick(cooldownEnd);
        }
    }

    // ------------------------------------------------------------------
    // parry settlement (T-31 + T-33)

    /**
     * Counter-effects of a successful parry. The incoming damage was already
     * cancelled by {@code GuardResolver}; the defender pays no stamina, no
     * durability, and this call immediately closes the window (one parry per
     * raise, ADR-07) and anchors the cooldown.
     */
    public static void onParried(ServerPlayer player, DamageSource source, DamageClass damageClass,
            GuardProfile profile, long now) {
        GuardStateData guardState = player.getData(ModAttachments.GUARD_STATE.get());
        guardState.setParryWindowEndTick(-1L);  // one parry per raise
        guardState.setParryUsed(true);
        guardState.setParryReadyTick(now + Config.parryCooldownTicks());

        LivingEntity attacker = source.getEntity() instanceof LivingEntity le ? le : null;
        if (damageClass == DamageClass.MELEE) {
            counterStun(player, attacker, now); // E-17: null/dead attacker skips the counter
        } else if (damageClass == DamageClass.PROJECTILE) {
            if (source.getDirectEntity() instanceof AbstractArrow arrow) {
                deflect(arrow, player);
            }
            // non-arrow projectiles are parried without a special handler (FR-13)
        }
        com.example.blockmod.network.SyncThrottler.forceSync(player);
        // FR-22 MVP feedback: CRIT burst + the shield break sound marks the parry
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                net.minecraft.sounds.SoundEvents.SHIELD_BREAK, player.getSoundSource(), 0.9f, 1.2f);
        spawnCrit(player.level(), player.getX(), player.getY() + 1.0, player.getZ());
        if (attacker != null) {
            spawnCrit(player.level(), attacker.getX(), attacker.getY() + 1.0, attacker.getZ());
        }
        BlockModLogger.info("PARRY", "player", player.getGameProfile().getName(),
                "class", damageClass, "attacker", attacker == null ? "none" : attacker.getType().toString());
    }

    /** FR-13: MELEE parries stun the attacker; bosses need a threshold of parries first (FR-14). */
    private static void counterStun(ServerPlayer player, @Nullable LivingEntity attacker, long now) {
        if (attacker == null || !attacker.isAlive()) {
            return; // E-17
        }
        if (isBoss(attacker)) {
            boolean reached = BossTracker.increment(attacker.getUUID(), player.getUUID(), now,
                    Config.bossParryThreshold(), Config.bossParryCounterExpire());
            if (!reached) {
                return; // threshold not met — no stun yet
            }
            BossTracker.reset(attacker.getUUID(), player.getUUID());
        }
        // E-20: addEffect refreshes the duration instead of stacking; visible=true so the
        // vanilla swirl reads as the stun (custom stun_star is post-MVP, FR-22)
        attacker.addEffect(new MobEffectInstance(ModEffects.STUN,
                Config.stunDuration(), 0, false, true, true));
        BlockModLogger.info("PARRY", "action", "stun", "target", attacker.getType().toString(),
                "duration", Config.stunDuration());
    }

    /** FR-14 boss detection honouring {@code boss_detection.method}. */
    public static boolean isBoss(LivingEntity entity) {
        if ("config_list".equals(Config.bossDetectionMethod())) {
            String id = net.minecraft.core.registries.BuiltInRegistries.ENTITY_TYPE
                    .getKey(entity.getType()).toString();
            return Config.bossEntityList().contains(id);
        }
        return entity.getType().is(ModTags.BOSSES); // default: tag
    }

    /** T-33: deflect an arrow — 80% speed (floor 0.6), ±20° jitter, mild lift, owner cleared. */
    private static void deflect(AbstractArrow arrow, ServerPlayer player) {
        Vec3 look = player.getViewVector(1.0F);
        Vec3 v = arrow.getDeltaMovement();
        double speed = Math.max(v.length() * Config.deflectSpeedMultiplier(), 0.6);
        float halfSpan = Config.deflectYawJitterDeg();
        float yawJitter = (player.getRandom().nextFloat() - 0.5f) * 2.0f * halfSpan;
        Vec3 dir = look.yRot((float) Math.toRadians(yawJitter)).normalize()
                .add(new Vec3(0, 0.15, 0)).normalize();
        arrow.setDeltaMovement(dir.scale(speed));
        arrow.setOwner(null); // FR-13: never bounces back at the shooter
        arrow.hurtMarked = true;
        arrow.hasImpulse = true;
    }

}
