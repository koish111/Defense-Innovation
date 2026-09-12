package com.example.blockmod.verify;

import java.util.UUID;
import java.util.Set;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.GuardProfile;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.data.SwordBlockingConfig;
import com.example.blockmod.handler.PlayerTickHandler;
import com.example.blockmod.input.ServerGuardInputHandler;
import com.example.blockmod.logic.GuardEquipmentResolver;
import com.example.blockmod.logic.GuardRules;
import com.example.blockmod.network.GuardPoseSyncPayload;
import com.example.blockmod.network.SyncThrottler;
import com.example.blockmod.registry.ModAttachments;
import com.example.blockmod.registry.ModDataComponents;
import com.example.blockmod.registry.ModEffects;
import com.example.blockmod.state.GuardStateData;
import com.example.blockmod.state.StaminaData;

import com.mojang.authlib.GameProfile;
import io.netty.buffer.Unpooled;

import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
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
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
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
        BlockModLogger.info("M2VERIFY", "note", "=== creative bash resolution ===");
        creativeBashCase(level, new GuardProbe(level,
                new GameProfile(UUID.fromString("b10c8b10-c8b1-0c8b-10c8-b10c8b10c8b4"), "M2BashProbe")));
        BlockModLogger.info("M2VERIFY", "note", "=== guard interaction lockout (FR-24) ===");
        guardInteractionCases(level, new GuardProbe(level,
                new GameProfile(UUID.fromString("b10c8b10-c8b1-0c8b-10c8-b10c8b10c8b5"), "M2InteractProbe")));
        swordBlockingCases(new GuardProbe(level,
                new GameProfile(UUID.fromString("b10c8b10-c8b1-0c8b-10c8-b10c8b10c8b6"), "M2SwordProbe")));
        dualGuardCases(level, Items.IRON_SWORD, Items.DIAMOND_SWORD, "swords");
        dualGuardCases(level, Items.IRON_SWORD, Items.SHIELD, "sword_shield");
        dualGuardCases(level, Items.SHIELD, Items.IRON_SWORD, "shield_sword");
        dualGuardCases(level, Items.SHIELD, Items.SHIELD, "shields");

        BlockModLogger.info("M2VERIFY", "note", "=== complete ===");
    }

    /** Exercises real tags, profile precedence and sword presentation snapshots after registries load. */
    private static void swordBlockingCases(ServerPlayer probe) {
        for (var item : new net.minecraft.world.item.Item[] {
                Items.WOODEN_SWORD, Items.STONE_SWORD, Items.IRON_SWORD,
                Items.GOLDEN_SWORD, Items.DIAMOND_SWORD, Items.NETHERITE_SWORD }) {
            var stack = new ItemStack(item);
            probe.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, stack);
            swordCheck("tag_" + stack.getItemHolder().getKey().location(),
                    GuardEquipmentResolver.isSword(stack, SwordBlockingConfig.DEFAULT));
        }

        var sword = new ItemStack(Items.IRON_SWORD);
        var stick = new ItemStack(Items.STICK);
        var swordId = sword.getItemHolder().getKey().location();
        var stickId = stick.getItemHolder().getKey().location();
        var additions = new SwordBlockingConfig(true, Set.of(stickId), Set.of(swordId));
        var onlyListed = new SwordBlockingConfig(false, Set.of(stickId), Set.of());
        var denied = new SwordBlockingConfig(true, Set.of(stickId), Set.of(stickId));
        swordCheck("ordinary_item_denied", !GuardEquipmentResolver.isSword(stick, SwordBlockingConfig.DEFAULT));
        swordCheck("whitelist_addition", GuardEquipmentResolver.isSword(stick, additions));
        swordCheck("blacklisted_tagged_sword", !GuardEquipmentResolver.isSword(sword, additions));
        swordCheck("whitelist_only", GuardEquipmentResolver.isSword(stick, onlyListed)
                && !GuardEquipmentResolver.isSword(sword, onlyListed));
        swordCheck("blacklist_wins", !GuardEquipmentResolver.isSword(stick, denied));

        stick.set(ModDataComponents.GUARD_PROFILE.get(),
                new GuardProfile(ShieldType.SWORD, 0.2F, 5, 0.0F, 0.0F, false));
        swordCheck("sword_profile_obeys_blacklist", !GuardEquipmentResolver.isSword(stick, denied));
        probe.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        probe.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        swordCheck("offhand_shield_priority",
                GuardEquipmentResolver.resolveSlot(probe, additions) == GuardRules.SLOT_OFFHAND);
        probe.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        probe.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, sword);
        swordCheck("offhand_sword_alone_enabled",
                GuardEquipmentResolver.resolveSlot(probe, SwordBlockingConfig.DEFAULT) == GuardRules.SLOT_OFFHAND);
        probe.setItemInHand(net.minecraft.world.InteractionHand.OFF_HAND, ItemStack.EMPTY);
        swordCheck("empty_hands_denied",
                GuardEquipmentResolver.resolveSlot(probe, SwordBlockingConfig.DEFAULT) == GuardRules.SLOT_NONE);

        var buffer = Unpooled.buffer();
        try {
            SwordBlockingConfig.STREAM_CODEC.encode(buffer, additions);
            swordCheck("config_codec_roundtrip", additions.equals(SwordBlockingConfig.STREAM_CODEC.decode(buffer)));
        } finally {
            buffer.release();
        }

        var snapshot = new GuardPoseSyncPayload(probe.getUUID(), sword, ItemStack.EMPTY, false);
        sword.set(DataComponents.CUSTOM_NAME, Component.literal("Updated sword"));
        swordCheck("in_place_component_change_detected",
                !ItemStack.isSameItemSameComponents(snapshot.mainHand(), sword));

        // Lifecycle probes require an item enabled by the active server policy.
        if (!GuardEquipmentResolver.isSword(sword, Config.swordBlocking())) {
            return;
        }
        offhandSwordCases(probe, sword);
        probe.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        var guard = probe.getData(ModAttachments.GUARD_STATE.get());
        var stamina = probe.getData(ModAttachments.STAMINA.get());
        guard.setGuarding(true);
        guard.setGuardHand(net.minecraft.world.InteractionHand.MAIN_HAND);
        guard.setGuardEquipment(sword, ShieldType.SWORD);
        stamina.setStamina(Config.maxStamina());
        swordCheck("active_pose", SyncThrottler.guardPoseStack(probe, probe.getData(ModAttachments.GUARD_STATE.get()).guardHand()) == sword);
        stamina.setStamina(0.0F);
        swordCheck("depletion_hides_pose_keeps_intent",
                SyncThrottler.guardPoseStack(probe, probe.getData(ModAttachments.GUARD_STATE.get()).guardHand()).isEmpty() && guard.isGuarding());
        stamina.setStamina(Config.maxStamina());
        swordCheck("recovery_restores_pose", SyncThrottler.guardPoseStack(probe, probe.getData(ModAttachments.GUARD_STATE.get()).guardHand()) == sword);
        probe.addEffect(new MobEffectInstance(ModEffects.STUN, 20));
        swordCheck("stun_hides_pose", SyncThrottler.guardPoseStack(probe, probe.getData(ModAttachments.GUARD_STATE.get()).guardHand()).isEmpty());
        probe.removeEffect(ModEffects.STUN);
        guard.setParryWindowEndTick(probe.level().getGameTime() + Config.swordParryWindow());
        probe.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, new ItemStack(Items.DIAMOND_SWORD));
        ServerGuardInputHandler.reconcileEquipment(probe, guard, probe.level().getGameTime());
        swordCheck("replacement_drops_guard_and_parry",
                !guard.isGuarding() && guard.parryWindowEndTick() < 0 && guard.guardStack().isEmpty());
        probe.setItemInHand(net.minecraft.world.InteractionHand.MAIN_HAND, sword);
        guard.setGuarding(true);
        guard.setGuardEquipment(sword, ShieldType.SWORD);
        sword.set(ModDataComponents.GUARD_PROFILE.get(),
                new GuardProfile(ShieldType.MEDIUM, 0.4F, 0, 0.0F, 0.0F, true));
        ServerGuardInputHandler.reconcileEquipment(probe, guard, probe.level().getGameTime());
        swordCheck("profile_type_change_drops_sword_guard", !guard.isGuarding());
        SyncThrottler.clear(probe.getUUID());
    }

    private static void offhandSwordCases(ServerPlayer probe, ItemStack sword) {
        var main = net.minecraft.world.InteractionHand.MAIN_HAND;
        var off = net.minecraft.world.InteractionHand.OFF_HAND;
        probe.setItemInHand(main, ItemStack.EMPTY);
        probe.setItemInHand(off, sword);
        var guard = probe.getData(ModAttachments.GUARD_STATE.get());
        var stamina = probe.getData(ModAttachments.STAMINA.get());
        stamina.setStamina(Config.maxStamina());
        ServerGuardInputHandler.handle(probe, new com.example.blockmod.network.GuardInputPayload(true, 0));
        swordCheck("offhand_input_enters_guard", guard.isGuarding() && guard.guardHand() == off
                && guard.guardStack() == sword && SyncThrottler.guardPoseStack(probe, probe.getData(ModAttachments.GUARD_STATE.get()).guardHand()) == sword);

        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), probe.registryAccess(),
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try {
            var payload = new GuardPoseSyncPayload(probe.getUUID(), ItemStack.EMPTY, sword, false);
            GuardPoseSyncPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = GuardPoseSyncPayload.STREAM_CODEC.decode(buffer);
            swordCheck("offhand_pose_codec_roundtrip", decoded.playerId().equals(probe.getUUID())
                    && decoded.mainHand().isEmpty() && ItemStack.isSameItemSameComponents(decoded.offHand(), sword));
        } finally {
            buffer.release();
        }

        var zombie = EntityType.ZOMBIE.create(probe.level());
        probe.moveTo(probe.getX(), probe.getY(), probe.getZ(), 0.0F, 0.0F);
        zombie.moveTo(probe.getX(), probe.getY(), probe.getZ() + 1.0, 0.0F, 0.0F);
        float health = probe.getHealth();
        int durability = sword.getDamageValue();
        boolean hit = zombie.doHurtTarget(probe);
        swordCheck("offhand_parry_counters_without_cost", !hit && probe.getHealth() == health
                && stamina.stamina() == Config.maxStamina() && sword.getDamageValue() == durability
                && zombie.hasEffect(ModEffects.STUN));
        zombie.removeEffect(ModEffects.STUN);
        probe.invulnerableTime = 0;
        hit = zombie.doHurtTarget(probe);
        swordCheck("offhand_block_costs_stamina_without_durability", !hit && probe.getHealth() == health
                && stamina.stamina() < Config.maxStamina() && sword.getDamageValue() == durability);
        zombie.discard();

        stamina.setStamina(0.0F);
        swordCheck("offhand_depletion_lowers_pose", guard.isGuarding() && SyncThrottler.guardPoseStack(probe, probe.getData(ModAttachments.GUARD_STATE.get()).guardHand()).isEmpty());
        stamina.setStamina(Config.maxStamina());
        swordCheck("offhand_recovery_restores_pose", SyncThrottler.guardPoseStack(probe, probe.getData(ModAttachments.GUARD_STATE.get()).guardHand()) == sword);
        probe.setItemInHand(off, ItemStack.EMPTY);
        probe.setItemInHand(main, sword);
        ServerGuardInputHandler.reconcileEquipment(probe, guard, probe.level().getGameTime());
        swordCheck("hand_swap_drops_guard", !guard.isGuarding() && guard.parryWindowEndTick() < 0);
    }

    private static void swordCheck(String name, boolean passed) {
        log(new Result("sword_" + name, passed, "true", Boolean.toString(passed)));
    }

    /** Full server input, damage, sync and equipment lifecycle for every two-hand pairing. */
    private static void dualGuardCases(ServerLevel level, net.minecraft.world.item.Item mainItem,
            net.minecraft.world.item.Item offItem, String name) {
        var probe = new GuardProbe(level, new GameProfile(UUID.randomUUID(), "M2Dual_" + name));
        var main = net.minecraft.world.InteractionHand.MAIN_HAND;
        var off = net.minecraft.world.InteractionHand.OFF_HAND;
        var mainStack = new ItemStack(mainItem);
        var offStack = new ItemStack(offItem);
        probe.setItemInHand(main, mainStack);
        probe.setItemInHand(off, offStack);
        var guard = probe.getData(ModAttachments.GUARD_STATE.get());
        var stamina = probe.getData(ModAttachments.STAMINA.get());
        long now = level.getGameTime();
        stamina.setStamina(Config.maxStamina());
        ServerGuardInputHandler.handlePowerGuard(probe, true);
        dualCheck(name, "requires_guard", !guard.isPowerGuarding());
        ServerGuardInputHandler.handle(probe, new com.example.blockmod.network.GuardInputPayload(true, 0));
        ServerGuardInputHandler.handlePowerGuard(probe, true);
        dualCheck(name, "input_activates_both", guard.isPowerGuarding()
                && SyncThrottler.guardPoseStack(probe, main) == mainStack
                && SyncThrottler.guardPoseStack(probe, off) == offStack);
        var buffer = new net.minecraft.network.RegistryFriendlyByteBuf(Unpooled.buffer(), probe.registryAccess(),
                net.neoforged.neoforge.network.connection.ConnectionType.NEOFORGE);
        try {
            var payload = new GuardPoseSyncPayload(probe.getUUID(), mainStack, offStack, true);
            GuardPoseSyncPayload.STREAM_CODEC.encode(buffer, payload);
            var decoded = GuardPoseSyncPayload.STREAM_CODEC.decode(buffer);
            dualCheck(name, "pose_codec", decoded.powerGuarding()
                    && ItemStack.isSameItemSameComponents(decoded.mainHand(), mainStack)
                    && ItemStack.isSameItemSameComponents(decoded.offHand(), offStack));
        } finally {
            buffer.release();
        }
        for (int i = 0; i < 20; i++) PlayerTickHandler.tick(probe);
        float drain = Config.maxStamina() * Config.pgStaminaDrainPercent() / 100.0F + Config.pgStaminaDrainFlat();
        dualCheck(name, "drains_once_no_regen", Math.abs(stamina.stamina() - (Config.maxStamina() - drain)) < 0.001F);
        guard.setParryWindowEndTick(-1L);
        var zombie = EntityType.ZOMBIE.create(level);
        probe.moveTo(0.0, 100.0, 0.0, 0.0F, 0.0F);
        probe.setYHeadRot(0.0F);
        zombie.moveTo(0.0, 100.0, 1.0, 0.0F, 0.0F);
        float health = probe.getHealth();
        float before = stamina.stamina();
        float mainGb = mainItem == Items.SHIELD ? 0.4F : 0.2F;
        float offGb = offItem == Items.SHIELD ? 0.4F : 0.2F;
        float pfix = "always_pvp".equals(Config.pvpMode()) ? Config.pfixPvp() : Config.pfixPve();
        float expectedCost = com.example.blockmod.logic.GuardFormulas.staminaCost(5.0F,
                1.0F - (1.0F - mainGb) * (1.0F - offGb), pfix);
        boolean hit = probe.hurt(probe.damageSources().mobAttack(zombie), 5.0F);
        dualCheck(name, "merged_damage_cost", !hit && probe.getHealth() == health
                && Math.abs(before - stamina.stamina() - expectedCost) < 0.001F);
        dualCheck(name, "shield_only_durability", mainStack.getDamageValue() == (mainItem == Items.SHIELD ? 6 : 0)
                && offStack.getDamageValue() == (offItem == Items.SHIELD ? 6 : 0));
        zombie.discard();

        var secondaryHand = guard.guardHand() == main ? off : main;
        var secondary = probe.getItemInHand(secondaryHand);
        probe.setItemInHand(secondaryHand, secondary.copy());
        ServerGuardInputHandler.reconcileEquipment(probe, guard, now);
        dualCheck(name, "replacement_ends_pg_keeps_primary", guard.isGuarding() && !guard.isPowerGuarding()
                && guard.secondaryGuardStack().isEmpty() && guard.powerGuardReadyTick() == now + Config.powerGuardCooldownTicks());
        ServerGuardInputHandler.handlePowerGuard(probe, true);
        dualCheck(name, "cooldown_blocks_reactivation", !guard.isPowerGuarding());
        guard.setPowerGuardReadyTick(-1L);
        stamina.setStamina(0.0F);
        ServerGuardInputHandler.handlePowerGuard(probe, true);
        dualCheck(name, "depletion_blocks_reactivation", !guard.isPowerGuarding());
        stamina.setStamina(0.01F);
        ServerGuardInputHandler.handlePowerGuard(probe, true);
        PlayerTickHandler.tick(probe);
        dualCheck(name, "drain_depletion_ends_pg", guard.isGuarding() && !guard.isPowerGuarding());
        ServerGuardInputHandler.handle(probe, new com.example.blockmod.network.GuardInputPayload(false, 0));
        SyncThrottler.clear(probe.getUUID());
    }

    private static void dualCheck(String pairing, String name, boolean passed) {
        log(new Result("dual_" + pairing + "_" + name, passed, "true", Boolean.toString(passed)));
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
     * 2026-09-11: the FR-26 creative exemption used to skip the whole tick
     * pipeline, so a creative player's armed bash windup NEVER resolved (bash
     * worked in survival only). The combat state machines must run under the
     * exemption — this arms a bash on a CREATIVE probe and drives
     * {@link PlayerTickHandler#tick} through a full arm → resolve cycle.
     */
    private static void creativeBashCase(ServerLevel level, GuardProbe probe) {
        BlockPos spawn = level.getSharedSpawnPos();
        double x = spawn.getX() - 2.5, z = spawn.getZ() - 2.5;
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
        probe.moveTo(x, y, z, 0.0f, 0.0f);
        probe.setCreative(true);
        probe.getInventory().offhand.set(0, new ItemStack(Items.SHIELD)); // data map → medium profile
        GuardStateData g = probe.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());
        StaminaData s = probe.getData(com.example.blockmod.registry.ModAttachments.STAMINA.get());
        s.setStamina(Config.maxStamina());
        g.setGuarding(true);
        g.setPowerGuarding(false);

        long now = level.getGameTime();
        com.example.blockmod.logic.ShieldBashService.handleTrigger(probe, now); // arms the windup
        boolean armed = g.bashWindupEndTick() >= 0;
        g.setBashWindupEndTick(now); // simulate the windup ticks having elapsed
        PlayerTickHandler.tick(probe); // must resolve under the FR-26 exemption
        log(new Result("bash 创造: 前摇正常结算",
                armed && g.bashWindupEndTick() < 0 && g.bashReadyTick() > now,
                "armed→解除+冷却",
                String.format("armed=%s windup=%d ready=%d", armed, g.bashWindupEndTick(), g.bashReadyTick())));

        g.setGuarding(false);
        g.setBashReadyTick(-1L);
        probe.getInventory().offhand.set(0, ItemStack.EMPTY);
        probe.setCreative(false);
    }

    /**
     * 2026-09-11 ruling (guard interaction lockout): a raised guard blocks
     * every vanilla interaction — server-authoritative cancellation asserted
     * by posting the real event types the vanilla pipelines fire
     * (Player.attack via {@code CommonHooks.onPlayerAttackTarget},
     * ServerPlayerGameMode via {@code CommonHooks.onItemRightClick}). Guard
     * down → the same events pass.
     */
    private static void guardInteractionCases(ServerLevel level, GuardProbe probe) {
        BlockPos spawn = level.getSharedSpawnPos();
        double x = spawn.getX() - 2.5, z = spawn.getZ() + 0.5;
        double y = level.getHeight(Heightmap.Types.MOTION_BLOCKING_NO_LEAVES, (int) x, (int) z);
        probe.moveTo(x, y, z, 0.0f, 0.0f);
        Zombie zombie = EntityType.ZOMBIE.create(level);
        zombie.moveTo(x + 1.0, y, z, 0.0f, 0.0f);
        GuardStateData g = probe.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());
        g.setGuarding(true);
        g.setPowerGuarding(false);

        var bus = net.neoforged.neoforge.common.NeoForge.EVENT_BUS;
        boolean attackBlocked = bus.post(new AttackEntityEvent(probe, zombie)).isCanceled();
        boolean useBlocked = bus.post(new PlayerInteractEvent.RightClickItem(probe,
                net.minecraft.world.InteractionHand.MAIN_HAND)).isCanceled();
        log(new Result("防御禁交互: 举盾时攻击/使用被取消", attackBlocked && useBlocked, "均取消",
                String.format("attack=%s use=%s", attackBlocked, useBlocked)));

        g.setGuarding(false);
        boolean attackFree = !bus.post(new AttackEntityEvent(probe, zombie)).isCanceled();
        boolean useFree = !bus.post(new PlayerInteractEvent.RightClickItem(probe,
                net.minecraft.world.InteractionHand.MAIN_HAND)).isCanceled();
        log(new Result("防御禁交互: 收盾后恢复", attackFree && useFree, "均放行",
                String.format("attack=%s use=%s", attackFree, useFree)));
        zombie.discard();
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
        var equipment = GuardEquipmentResolver.resolve(probe);
        g.setGuardHand(equipment.hand());
        g.setGuardEquipment(equipment.stack(), equipment.profile().type());
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
        // A new world starts at tick zero; keep the out-of-delay fixture in its past.
        s.setLastEventTick(player.level().getGameTime()
                - Math.round(Config.regenDelaySeconds() * 20.0F) - 1L + lastEventTick);
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
        /** Forces the FR-26 creative branch in PlayerTickHandler without touching gameMode. */
        private boolean creative;

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

        void setCreative(boolean creative) {
            this.creative = creative;
        }

        @Override
        public boolean isCreative() {
            return creative || super.isCreative();
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
