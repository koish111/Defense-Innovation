package com.example.blockmod.logic;

import java.util.List;

import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.DamageClass;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.logic.GuardEquipmentResolver.GuardEquipment;
import com.example.blockmod.state.GuardStateData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * Successful great-shield blocks shove the attacker, or the frontal area during
 * power guard. Damage scales a bounded reference distance; a small grounded lift
 * and discrete drag calibration keep the shove low and visible.
 * Projectile shooters are exempt from the single-target shove.
 */
public final class GreatshieldService {
    private GreatshieldService() {}

    /** GuardResolver settlement hook: runs after a GUARDED verdict was fully applied. */
    public static void onBlocked(ServerPlayer player, DamageSource source, GuardEquipment equipment,
            GuardStateData guardState, float damage) {
        int mode = GuardRules.greatshieldShoveMode(equipment.profile().type() == ShieldType.GREAT,
                guardState.isPowerGuarding());
        if (!Float.isFinite(damage)) {
            BlockModLogger.error("GREATSHIELD", "reason", "non-finite damage", "damage", damage);
            return;
        }
        if (mode == GuardRules.SHOVE_NONE || damage <= 0) {
            return;
        }
        if (mode == GuardRules.SHOVE_FRONTAL_AREA) {
            shoveFrontalArea(player, damage);
        } else {
            shoveAttacker(player, source, damage);
        }
    }

    /** Knocks the attacker {@code block_knockback_blocks} away from the defender. */
    private static void shoveAttacker(ServerPlayer player, DamageSource source, float damage) {
        double blocks = GreatshieldFormulas.distance(damage, Config.greatshieldBlockKnockbackBlocks(),
                Config.greatshieldDamageReference());
        if (blocks <= 0f) {
            return; // disabled by config
        }
        if (DamageClassifier.classify(source) == DamageClass.PROJECTILE) {
            return; // FR-13 precedent: ranged sources are never punished at range
        }
        if (!(source.getEntity() instanceof LivingEntity attacker) || !attacker.isAlive()) {
            return; // E-17: no living attacker to shove
        }
        double dx = attacker.getX() - player.getX();
        double dz = attacker.getZ() - player.getZ();
        shove(player, attacker, dx, dz, blocks);
    }

    /**
     * Power-guard variant: every living entity inside the frontal 180° arc and
     * {@code power_guard_knockback_range_blocks} is pushed away from the defender.
     */
    private static void shoveFrontalArea(ServerPlayer player, float damage) {
        double blocks = GreatshieldFormulas.distance(damage, Config.greatshieldPgBlockKnockbackBlocks(),
                Config.greatshieldDamageReference());
        if (blocks <= 0f || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        double range = Config.greatshieldPgKnockbackRangeBlocks();
        Vec3 look = player.calculateViewVector(0.0F, player.getYHeadRot());
        double lookLen = Math.max(look.horizontalDistance(), 1.0e-4);

        List<Entity> candidates = level.getEntities(player, player.getBoundingBox().inflate(range),
                e -> e instanceof LivingEntity living && living.isAlive());
        for (Entity candidate : candidates) {
            LivingEntity target = (LivingEntity) candidate;
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > range) {
                continue;
            }
            if (dist > 1.0e-4 && (dx * look.x + dz * look.z) / lookLen / dist <= 0.0) {
                continue; // behind the defender — not "in front"
            }
            shove(player, target, dx, dz, blocks);
        }
    }

    /** Preserve tangential movement, establish outward speed, and lift only grounded targets. */
    private static void shove(ServerPlayer player, LivingEntity target, double dx, double dz, double blocks) {
        double dist = Math.sqrt(dx * dx + dz * dz);
        double dirX;
        double dirZ;
        if (dist > 1.0e-4) {
            dirX = dx / dist;
            dirZ = dz / dist;
        } else {
            Vec3 look = player.calculateViewVector(0.0F, player.getYHeadRot());
            dirX = look.x;
            dirZ = look.z; // degenerate overlap: push along the facing
        }
        var velocity = target.getDeltaMovement();
        if (!Double.isFinite(velocity.x) || !Double.isFinite(velocity.y) || !Double.isFinite(velocity.z)
                || !Double.isFinite(dist) || !Double.isFinite(dirX) || !Double.isFinite(dirZ)) {
            BlockModLogger.error("GREATSHIELD", "reason", "non-finite target motion or position");
            return;
        }
        double gravity = target.isNoGravity() ? 0
                : target.getAttributeValue(Attributes.GRAVITY);
        if (!Double.isFinite(gravity) || gravity < 0) {
            BlockModLogger.error("GREATSHIELD", "reason", "invalid target gravity", "gravity", gravity);
            return;
        }
        boolean grounded = target.onGround();
        double lift = grounded && gravity > 0 && !target.isInWater() && !target.isInLava()
                ? Config.greatshieldShoveLift() : 0;
        double appliedY = lift > 0 ? GreatshieldFormulas.verticalSpeed(velocity.y, grounded, lift) : velocity.y;
        // Mob.setSpeed also sets forward input to speed. A pursuing vanilla mob's
        // ground acceleration is therefore speed * min(speed, 1) * input damping.
        // Compensate only active pursuers on dry ground, not passive PG bystanders.
        double groundAcceleration = 0;
        double airAcceleration = 0;
        if (grounded && !target.isInWater() && !target.isInLava()
                && target instanceof net.minecraft.world.entity.Mob mob && !mob.isNoAi()
                && mob.getTarget() == player) {
            double movementSpeed = mob.getSpeed();
            if (Double.isFinite(movementSpeed) && movementSpeed > 0) {
                double input = Math.min(1, movementSpeed) * Config.greatshieldInputDamping();
                groundAcceleration = Math.min(Config.greatshieldCounterAccelerationCap(), movementSpeed * input);
                airAcceleration = Math.min(groundAcceleration, Config.greatshieldAirInputSpeed() * input);
            }
        }
        double speed = grounded
                ? GreatshieldFormulas.opposedGroundSpeed(blocks, appliedY, gravity,
                        Config.greatshieldGroundFriction(), Config.greatshieldAirDrag(), Config.greatshieldVerticalDrag(),
                        Config.greatshieldGroundResponseTicks(), groundAcceleration, airAcceleration)
                : GreatshieldFormulas.motionSpeed(blocks, false, appliedY, gravity,
                        Config.greatshieldGroundFriction(), Config.greatshieldAirDrag(), Config.greatshieldVerticalDrag(),
                        Config.greatshieldAirResponseTicks());
        if (!Double.isFinite(speed) || speed <= 0) {
            BlockModLogger.error("GREATSHIELD", "reason", "invalid calculated shove speed", "speed", speed);
            return;
        }
        double adjustment = GreatshieldFormulas.outwardAdjustment(velocity.x * dirX + velocity.z * dirZ, speed);
        double appliedX = velocity.x + dirX * adjustment;
        double appliedZ = velocity.z + dirZ * adjustment;
        if (!Double.isFinite(appliedX) || !Double.isFinite(appliedZ)) {
            BlockModLogger.error("GREATSHIELD", "reason", "non-finite resulting motion");
            return;
        }
        target.setDeltaMovement(appliedX, appliedY, appliedZ);
        target.hasImpulse = true;
        target.hurtMarked = true;
    }
}
