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
import net.minecraft.world.phys.Vec3;

/**
 * Designer ruling 2026-09-04: a successful great-shield block shoves the
 * attacker back {@code greatshield.block_knockback_blocks} (1.0); while power
 * guard holds, the shove becomes a frontal area push of
 * {@code greatshield.power_guard_block_knockback_blocks} (2.0) that replaces
 * the single-attacker knockback.
 *
 * <p>The impulse reuses the O-08 horizontal conversion shared with
 * {@link ShieldBashService} (blocks × 0.225), so a "knockback block" means the
 * same horizontal displacement as the shield bash — but without the bash's 0.4
 * lift: both shoves are purely horizontal (designer ruling 2026-09-04). Ranged
 * attackers are exempt from the single shove — FR-13 already rules that
 * projectiles never punish their shooter at range; the power-guard area push is
 * positional and therefore applies to everything in front, shooter included.
 */
public final class GreatshieldService {
    private GreatshieldService() {}

    /** GuardResolver settlement hook: runs after a GUARDED verdict was fully applied. */
    public static void onBlocked(ServerPlayer player, DamageSource source, GuardEquipment equipment,
            GuardStateData guardState) {
        int mode = GuardRules.greatshieldShoveMode(equipment.profile().type() == ShieldType.GREAT,
                guardState.isPowerGuarding());
        if (mode == GuardRules.SHOVE_NONE) {
            return;
        }
        if (mode == GuardRules.SHOVE_FRONTAL_AREA) {
            shoveFrontalArea(player);
        } else {
            shoveAttacker(player, source);
        }
    }

    /** Knocks the attacker {@code block_knockback_blocks} away from the defender. */
    private static void shoveAttacker(ServerPlayer player, DamageSource source) {
        float blocks = Config.greatshieldBlockKnockbackBlocks();
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
        BlockModLogger.info("GREATSHIELD", "action", "shove", "player", player.getGameProfile().getName(),
                "target", attacker.getType().toString(), "blocks", blocks);
    }

    /**
     * Power-guard variant: every living entity inside the frontal 180° arc and
     * {@code power_guard_knockback_range_blocks} is pushed away from the defender.
     */
    private static void shoveFrontalArea(ServerPlayer player) {
        float blocks = Config.greatshieldPgBlockKnockbackBlocks();
        if (blocks <= 0f || !(player.level() instanceof ServerLevel level)) {
            return;
        }
        double range = Config.greatshieldPgKnockbackRangeBlocks();
        Vec3 look = player.calculateViewVector(0.0F, player.getYHeadRot());
        double lookLen = Math.max(look.horizontalDistance(), 1.0e-4);

        List<Entity> candidates = level.getEntities(player, player.getBoundingBox().inflate(range),
                e -> e instanceof LivingEntity living && living.isAlive());
        int hits = 0;
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
            hits++;
        }
        BlockModLogger.info("GREATSHIELD", "action", "shove_area", "player", player.getGameProfile().getName(),
                "hits", hits, "blocks", blocks, "range", range);
    }

    /** Horizontal impulse = blocks × 0.225 (O-08 conversion), with a 0.1 vertical lift (designer ruling 2026-09-04). */
    private static void shove(ServerPlayer player, LivingEntity target, double dx, double dz, float blocks) {
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
        double k = blocks * ShieldBashService.KNOCKBACK_CONVERSION;
        target.setDeltaMovement(target.getDeltaMovement().add(dirX * k, 0.2, dirZ * k));
        if (target instanceof ServerPlayer targetPlayer) {
            targetPlayer.hurtMarked = true; // sync the velocity to the pushed client
        }
    }
}
