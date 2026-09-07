package com.example.blockmod.verify;

import java.util.UUID;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.handler.PlayerTickHandler;
import com.example.blockmod.registry.ModEffects;
import com.example.blockmod.state.GuardStateData;
import com.example.blockmod.state.StaminaData;

import com.mojang.authlib.GameProfile;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.animal.Pig;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.Heightmap;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.server.ServerStartedEvent;

/**
 * TEMPORARY M2 verification harness (T-15/T-16 acceptance) — remove at M3.
 *
 * <p>Drives {@link PlayerTickHandler#tick} on a {@link FakePlayer} through the FR-02
 * branch table and the depletion edge, logging one line per assertion so the
 * dedicated-server log doubles as the acceptance record:
 * <pre>
 * M2VERIFY ok=true case=... expected=... actual=...
 * </pre>
 * Real-player feel (HUD, LAN sync) stays with the manual G2 checklist. The FR-05
 * stun freeze section follows the same style with manually ticked orphan entities.
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class M2Verify {
    private record Result(String name, boolean ok, String expected, String actual) {}

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        ServerLevel level = event.getServer().overworld();
        ServerPlayer player = new FakePlayer(level, new GameProfile(UUID.fromString("b10c8b10-c8b1-0c8b-10c8-b10c8b10c8b2"), "M2Probe"));

        BlockModLogger.info("M2VERIFY", "note", "=== regen branches (FR-02) ===");
        // 正常: st=20, out of delay -> 4/s -> 20 ticks add 4.0
        Result r = drive(player, 20.0f, false, false, 0L, 20, 24.0f);
        log(r);
        // 延迟中: lastEventTick = now -> no regen
        r = driveAtDelay(player, 20.0f, 20, 20.0f);
        log(r);
        // 格挡中: 2/s -> 20 ticks add 2.0
        r = drive(player, 20.0f, true, false, 0L, 20, 22.0f);
        log(r);
        // 枯竭负值 + 格挡: 8/s beats guard halving
        r = drive(player, -10.0f, true, false, 0L, 20, -2.0f);
        log(r);
        // 枯竭零值: st=0 crosses 0 on the first tick (+0.4), then the normal branch takes
        // over for the remaining 19 ticks (+0.2 each) -> 0.4 + 3.8 = 4.2 (FR-02 rule 4).
        r = drive(player, 0.0f, false, false, 0L, 20, 4.2f);
        log(r);
        // 枯竭且PG (no guard => no drain): depleted 8/s beats the PG suppression. 5 ticks
        // keep stamina negative the whole window -> -5 + 2.0 = -3.0 (PG would hold -5).
        r = drive(player, -5.0f, false, true, 0L, 5, -3.0f);
        log(r);
        // 强力防御持续消耗: FR-16 (2026-08-30 ruling) drain = max × 1% + 1.0 = 1.4/s
        // (stamina_drain_percent=1.0, stamina_drain_flat=1.0, max=40) -> 20 ticks
        // deduct 1.4 -> 18.6 (§5.3.1 step 1); powerGuarding also zeroes the regen branch.
        r = drive(player, 20.0f, true, true, 0L, 20, 18.6f);
        log(r);
        // 上限钳制
        r = drive(player, 39.9f, false, false, 0L, 40, 40.0f);
        log(r);

        BlockModLogger.info("M2VERIFY", "note", "=== depletion edge (§5.3.2) ===");
        edgeCase(player, 0.5f, -0.2f, true);
        edgeCase(player, -0.2f, 0.5f, false);
        // no-thrash: 10 consecutive depleted ticks must not re-fire
        int fired = 0;
        player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get()).setWasDepleted(true);
        StaminaData s = player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        s.setStamina(-5.0f);
        for (int i = 0; i < 10; i++) {
            s.setStamina(-5.0f + 0.2f * (i + 1));
            if (StaminaEdgeProbe.check(player)) {
                fired++;
            }
        }
        log(new Result("edge_无跳变", fired == 0, "0 fires", fired + " fires"));
        BlockModLogger.info("M2VERIFY", "note", "=== stun freeze (FR-05) ===");
        stunFreezeCases(level);
        BlockModLogger.info("M2VERIFY", "note", "=== stun defense gate (FR-05) ===");
        stunDefenseCases(level, new GuardProbe(level,
                new GameProfile(UUID.fromString("b10c8b10-c8b1-0c8b-10c8-b10c8b10c8b3"), "M2GuardProbe")));

        BlockModLogger.info("M2VERIFY", "note", "=== complete ===");
    }

    /**
     * FR-05 stun freeze cases, driven by manual entity ticks — the same
     * deterministic style as {@link #drive} above. The entities are deliberately
     * NOT added to the world (orphan): the real tick loop cannot interfere,
     * server difficulty and daylight are irrelevant, nothing leaks into the
     * save — while every code path under test runs for real: aiStep's
     * isImmobile branch, travel() physics and the full hurt pipeline (so the
     * mixin gates and the server-authoritative event cancellations are both
     * exercised). EntityTickEvent is not part of this harness; the per-tick
     * StunHandler cleanup stays under the manual G2 checklist.
     */
    private static void stunFreezeCases(ServerLevel level) {
        BlockPos spawn = level.getSharedSpawnPos();
        double x = spawn.getX() + 2.5, z = spawn.getZ() + 2.5;
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);

        // 冻结: a stunned pig holds position and heading while gravity/travel run
        Pig pig = EntityType.PIG.create(level);
        pig.moveTo(x, y, z, 30.0f, 0.0f);
        pig.addEffect(new MobEffectInstance(ModEffects.STUN, 400, 0, false, false), null);
        for (int i = 0; i < 5; i++) pig.tick(); // settle: friction + gravity ground the body
        double fx = pig.getX(), fz = pig.getZ();
        float head = pig.yHeadRot;
        for (int i = 0; i < 20; i++) pig.tick();
        double drift = Math.hypot(pig.getX() - fx, pig.getZ() - fz);
        log(new Result("stun 冻结: 20 tick 水平零位移", drift < 0.01, "<0.01", String.format("%.4f", drift)));
        log(new Result("stun 冻结: 头部朝向不变", pig.yHeadRot == head, "Δ0",
                String.format("%.1f", Math.abs(pig.yHeadRot - head))));

        // 外力: knockback still moves the frozen body (travel runs behind the freeze)
        pig.knockback(0.5, 1.0, 0.0);
        for (int i = 0; i < 10; i++) pig.tick();
        double pushed = Math.hypot(pig.getX() - fx, pig.getZ() - fz);
        log(new Result("stun 冻结: 击退穿透外力生效", pushed > 0.05, ">0.05", String.format("%.4f", pushed)));
        pig.discard();

        // 攻击: a stunned attacker deals no damage (hurt pipeline, server-authoritative)
        Pig victim = EntityType.PIG.create(level);
        victim.moveTo(x, y, z, 0.0f, 0.0f);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(x + 1.0, y, z, 0.0f, 0.0f);
        float full = victim.getHealth();
        boolean controlLanded = zombie.doHurtTarget(victim);
        float afterControl = victim.getHealth();
        victim.invulnerableTime = 0; // same-tick re-bite: clear i-frames so only the stun differs
        zombie.addEffect(new MobEffectInstance(ModEffects.STUN, 400, 0, false, false), null);
        boolean stunnedLanded = zombie.doHurtTarget(victim);
        log(new Result("stun 攻击: 控制组未眩晕可命中", controlLanded && afterControl < full, "扣血",
                String.format("landed=%s hp=%.1f", controlLanded, afterControl)));
        log(new Result("stun 攻击: 眩晕后伤害被取消", !stunnedLanded && victim.getHealth() == afterControl, "不扣血",
                String.format("landed=%s hp=%.1f", stunnedLanded, victim.getHealth())));
        zombie.discard();
        victim.discard();
    }

    /**
     * FR-05 stun defense gate: a guarding player who gets stunned must lose BOTH
     * the block and the parry — the damage resolves normally. The same
     * zombie-bites-frontally pair as {@link #stunFreezeCases} attacks a guarding
     * {@link GuardProbe} (offhand vanilla shield, so the data-map profile
     * resolves) with only the stun effect differing between the two bites.
     */
    private static void stunDefenseCases(ServerLevel level, GuardProbe probe) {
        BlockPos spawn = level.getSharedSpawnPos();
        double x = spawn.getX() + 4.5, z = spawn.getZ() + 2.5;
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);

        // probe faces +Z (yaw 0); the zombie bites from the front → frontal check passes
        probe.moveTo(x, y, z, 0.0f, 0.0f);
        probe.setYRot(0.0f);
        probe.setYHeadRot(0.0f);
        probe.getInventory().offhand.set(0, new ItemStack(Items.SHIELD));
        StaminaData s = probe.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        GuardStateData g = probe.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());

        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(x, y, z + 1.0, 0.0f, 0.0f);

        // control: guarding + positive stamina → GUARDED (damage cancelled, cost paid)
        s.setStamina(20.0f);
        g.setGuarding(true);
        g.setPowerGuarding(false);
        g.setWasDepleted(false);
        float full = probe.getHealth();
        boolean blockedLanded = zombie.doHurtTarget(probe);
        log(new Result("stun 防御: 控制组格挡生效",
                !blockedLanded && probe.getHealth() == full && s.stamina() < 20.0f, "不扣血+扣体力",
                String.format("landed=%s hp=%.1f st=%.2f", blockedLanded, probe.getHealth(), s.stamina())));

        // stunned: identical guard, only the effect differs → gate fails, damage resolves
        s.setStamina(20.0f);
        probe.addEffect(new MobEffectInstance(ModEffects.STUN, 400, 0, false, false), null);
        probe.invulnerableTime = 0;
        boolean stunnedLanded = zombie.doHurtTarget(probe);
        log(new Result("stun 防御: 眩晕后格挡失效",
                stunnedLanded && probe.getHealth() < full, "扣血",
                String.format("landed=%s hp=%.1f", stunnedLanded, probe.getHealth())));

        probe.removeEffect(ModEffects.STUN);
        g.setGuarding(false);
        probe.getInventory().offhand.set(0, ItemStack.EMPTY);
        zombie.discard();
    }

    /** Drives N ticks with lastEventTick fixed in the past (no delay). */
    private static Result drive(ServerPlayer player, float start, boolean guarding, boolean powerGuarding,
            long lastEventTick, int ticks, float expected) {
        StaminaData s = player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        GuardStateData g = player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());
        s.setStamina(start);
        s.setLastEventTick(lastEventTick);
        g.setGuarding(guarding);
        g.setPowerGuarding(powerGuarding);
        g.setWasDepleted(start <= 0f);
        long now = player.level().getGameTime();
        for (int i = 0; i < ticks; i++) {
            now++;
            PlayerTickHandler.tick(player);
        }
        boolean ok = Math.abs(s.stamina() - expected) < 0.01f;
        return new Result(String.format("st=%.1f guard=%s pg=%s -> after %d ticks", start, guarding, powerGuarding, ticks),
                ok, String.valueOf(expected), String.format("%.3f", s.stamina()));
    }

    /** Drives N ticks with lastEventTick = now-5 (inside the 2 s delay). */
    private static Result driveAtDelay(ServerPlayer player, float start, int ticks, float expected) {
        StaminaData s = player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        GuardStateData g = player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());
        s.setStamina(start);
        s.setLastEventTick(player.level().getGameTime() - 5L);
        g.setGuarding(false);
        g.setPowerGuarding(false);
        g.setWasDepleted(false);
        for (int i = 0; i < ticks; i++) {
            PlayerTickHandler.tick(player);
        }
        boolean ok = Math.abs(s.stamina() - expected) < 0.01f;
        return new Result("delay window holds regen", ok, String.valueOf(expected), String.format("%.3f", s.stamina()));
    }

    private static void edgeCase(ServerPlayer player, float before, float after, boolean expectedDepleted) {
        StaminaData s = player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        GuardStateData g = player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());
        s.setStamina(before);
        g.setWasDepleted(before <= 0f);
        s.setStamina(after);
        boolean fired = StaminaEdgeProbe.check(player);
        log(new Result(String.format("edge %.2f -> %.2f", before, after),
                fired == (expectedDepleted != (before <= 0f)), expectedDepleted ? "enter" : "exit",
                fired ? "fired" : "not fired"));
    }

    private static void log(Result r) {
        BlockModLogger.info("M2VERIFY", "case", r.name(), "ok", r.ok(), "expected", r.expected(), "actual", r.actual());
    }

    /**
     * A FakePlayer with the blanket fake-player immunity un-faked:
     * {@link FakePlayer#isInvulnerableTo} hard-codes {@code true}, which makes
     * {@code hurt} bail BEFORE {@code LivingIncomingDamageEvent} fires — the
     * guard arbitration would never be exercised. Re-enabling the real immunity
     * chain (always-false here, beyond the scope under test) lets the full
     * hurt pipeline run so the {@code GuardResolver} verdict is observable.
     */
    private static final class GuardProbe extends FakePlayer {
        private GuardProbe(ServerLevel level, GameProfile profile) {
            super(level, profile);
            // FakePlayer.tick() is a no-op, so the ServerPlayer constructor default
            // spawnInvulnerableTime=60 NEVER decrements — ServerPlayer#hurt bails on
            // it before LivingIncomingDamageEvent can fire. Zero it once up front.
            try {
                var field = ServerPlayer.class.getDeclaredField("spawnInvulnerableTime");
                field.setAccessible(true);
                field.setInt(this, 0);
            } catch (ReflectiveOperationException fieldRenamed) {
                // a future mapping change surfaces as loudly-failing harness cases
            }
        }

        @Override
        public boolean isInvulnerableTo(net.minecraft.world.damagesource.DamageSource source) {
            return false;
        }
    }

    /** Reflection-free edge probe: re-runs the same edge check PlayerTickHandler uses. */
    private static final class StaminaEdgeProbe {
        static boolean check(ServerPlayer player) {
            return com.example.blockmod.logic.StaminaService.depletionEdgeFlipped(
                    player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get()),
                    player.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get()).stamina());
        }
    }

    private M2Verify() {}
}
