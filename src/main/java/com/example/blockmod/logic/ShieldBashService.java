package com.example.blockmod.logic;

import java.util.List;

import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.registry.ModAttachments;
import com.example.blockmod.registry.ModEffects;
import com.example.blockmod.network.SyncThrottler;
import com.example.blockmod.state.GuardStateData;
import com.example.blockmod.state.StaminaData;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import org.jetbrains.annotations.Nullable;

/**
 * FR-15 / T-36: the medium-shield bash (Spec §5.6).
 *
 * <p>Trigger → 5-tick windup → resolution: entities inside a 45° cone, 3 blocks
 * ahead take 8.0 damage and a 4-block-equivalent knockback; the cooldown counts
 * FROM RESOLUTION (ADR-11). The guard state is kept through the whole bash.
 */
public final class ShieldBashService {
    /** O-08 calibration: knockback_blocks (4.0) → velocity impulse K ≈ 0.9. */
    private static final float KNOCKBACK_CONVERSION = 0.225f;

    private ShieldBashService() {}

    /** C2S trigger path: validates state and arms the windup. */
    public static void handleTrigger(ServerPlayer player, long now) {
        GuardStateData guardState = player.getData(ModAttachments.GUARD_STATE.get());
        if (!guardState.isGuarding()) {
            return; // client gate should prevent this; stay silent on the race
        }
        if (guardState.bashReadyTick() > now || guardState.bashWindupEndTick() >= 0) {
            return; // ADR-11 cooldown or windup in progress
        }
        GuardEquipmentResolver.GuardEquipment equipment = GuardEquipmentResolver.resolve(player);
        if (equipment == null || equipment.profile().type() != ShieldType.MEDIUM) {
            BlockModLogger.warn("BASH", "action", "rejected", "player", player.getGameProfile().getName(),
                    "reason", "not a medium shield");
            return; // E-11: non-medium shields never bash
        }
        StaminaData stamina = player.getData(ModAttachments.STAMINA.get());
        if (Config.bashRequiresPositiveStamina() && stamina.isDepleted()) {
            BlockModLogger.warn("BASH", "action", "rejected", "player", player.getGameProfile().getName(),
                    "reason", "O-20 depleted");
            return;
        }
        if (player.hasEffect(ModEffects.STUN)) {
            return; // FR-05: stunned players cannot act
        }
        guardState.setBashWindupEndTick(now + Config.bashWindupTicks());
        BlockModLogger.info("BASH", "action", "windup", "player", player.getGameProfile().getName(),
                "ends", guardState.bashWindupEndTick());
    }

    /** §5.4.1 tick step 3: resolves the windup when it expires. */
    public static void tick(ServerPlayer player, GuardStateData guardState, long now) {
        if (guardState.bashWindupEndTick() < 0 || now < guardState.bashWindupEndTick()) {
            return;
        }
        guardState.setBashWindupEndTick(-1L);
        guardState.setBashReadyTick(now + Config.bashCooldownTicks()); // ADR-11: counts from resolution

        int hits = resolveHit(player);
        StaminaData stamina = player.getData(ModAttachments.STAMINA.get());
        float consume = Config.bashConsumeStamina();
        if (consume > 0f) {
            StaminaService.addStamina(player, -consume); // O-19: optional bash cost
        }
        SyncThrottler.forceSync(player);
        BlockModLogger.info("BASH", "action", "resolve", "player", player.getGameProfile().getName(),
                "hits", hits, "consume", consume);
    }

    /** Cone hit: every living entity within range and the half angle (excluding the player). */
    private static int resolveHit(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        double range = Config.bashRangeBlocks();
        double halfAngleRad = Math.toRadians(Config.bashHalfAngleDeg());
        Vec3 look = player.calculateViewVector(0.0F, player.getYHeadRot());
        double lookLen = Math.max(look.horizontalDistance(), 1.0e-4);

        List<net.minecraft.world.entity.Entity> candidates = level.getEntities(player,
                player.getBoundingBox().inflate(range),
                e -> e instanceof LivingEntity living && living.isAlive() && e != player);
        int hits = 0;
        for (net.minecraft.world.entity.Entity candidate : candidates) {
            LivingEntity target = (LivingEntity) candidate;
            double dx = target.getX() - player.getX();
            double dz = target.getZ() - player.getZ();
            double dist = Math.sqrt(dx * dx + dz * dz);
            if (dist > range) {
                continue;
            }
            if (dist > 1.0e-4) {
                double dot = (dx * look.x + dz * look.z) / Math.max(lookLen, 1.0e-4) / dist;
                if (dot < Math.cos(halfAngleRad)) {
                    continue; // outside the 45° cone
                }
            }
            damageAndKnockback(player, target, dx, dz, dist);
            hits++;
        }
        if (hits > 0) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.CRIT,
                    player.getX() + look.x * 1.5, player.getEyeY() - 0.2, player.getZ() + look.z * 1.5,
                    10, 0.3, 0.3, 0.3, 0.1);
        }
        return hits;
    }

    private static void damageAndKnockback(ServerPlayer player, LivingEntity target, double dx, double dz, double dist) {
        boolean hurt = target.hurt(player.damageSources().playerAttack(player), Config.bashDamage());
        double dirX = dist > 1.0e-4 ? dx / dist : player.getLookAngle().x;
        double dirZ = dist > 1.0e-4 ? dz / dist : player.getLookAngle().z;
        double k = Config.bashKnockbackBlocks() * KNOCKBACK_CONVERSION;
        Vec3 dm = target.getDeltaMovement();
        target.setDeltaMovement(dm.add(dirX * k, 0.4, dirZ * k)); // §5.6 step 3 (O-08)
        if (target instanceof net.minecraft.server.level.ServerPlayer targetPlayer) {
            targetPlayer.hurtMarked = true; // sync the velocity to the pushed client
        }
        BlockModLogger.info("BASH", "action", "hit", "target", target.getType().toString(),
                "damage", Config.bashDamage(), "hurt", hurt);
    }

    /** True when the player is mid-windup or on cooldown (client-side UX helper). */
    public static boolean isBusy(ServerPlayer player, long now) {
        GuardStateData guardState = player.getData(ModAttachments.GUARD_STATE.get());
        return guardState.bashWindupEndTick() >= 0 || guardState.bashReadyTick() > now;
    }
}
