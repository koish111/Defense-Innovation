package com.example.blockmod.probe;

import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.entity.projectile.Arrow;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.common.util.FakePlayer;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.LivingShieldBlockEvent;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * TEMPORARY M0 probe (T-03) — remove at M1.
 *
 * Runs on a dedicated server with no players. Arms itself on {@link ServerStartedEvent}
 * and walks through 13 scheduled phases on the server thread, using a {@link FakePlayer}
 * and an off-world {@link Zombie} to trigger the damage/use-item paths.
 *
 * Covers: API-01/02 (damage/shield event order + cancel + setBlocked),
 * API-03 (priority ordering), API-05 (attachment get/set), API-10 (transient modifiers),
 * API-11/12 (damage source tags + source position), API-13 (use item events / food timing),
 * API-14 (hurtAndBreak), API-16 (LivingJumpEvent cancelability).
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class ServerApiProbe {
    private static final int PHASE_FIRST = 1;
    private static final int PHASE_LAST = 13;
    private static final int PHASE_SPACING_TICKS = 25;

    private static int phase = 0; // 0 = disarmed, -1 = done
    private static int countdown = 0;
    private static int serverTick = 0;

    private static ServerLevel level;
    private static ServerPlayer dummy;
    private static Zombie zombie;

    // per-phase behaviour flags read by the event handlers
    private static boolean modifyInHigh;
    private static boolean modifyInLowest;
    private static boolean cancelIncoming;
    private static boolean disableVanillaBlock;

    private static int playerTicksLogged;
    private static int foodAtStart = -1;

    // ------------------------------------------------------------------
    // arming / scheduling

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        dummy = new FakePlayer(level, new GameProfile(UUID.fromString("b10c8b10-c8b1-0c8b-10c8-b10c8b10c8b1"), "BlockProbe"));
        zombie = EntityType.ZOMBIE.create(level);
        if (zombie == null) {
            BlockModLogger.error("PROBE", "ev", "ARM", "error", "zombie create failed");
            phase = -1;
            return;
        }
        placeActors();
        BlockModLogger.info("PROBE", "ev", "ARM", "note", "probe armed, phases " + PHASE_FIRST + ".." + PHASE_LAST);
        phase = PHASE_FIRST;
        countdown = 60; // let the world settle
    }

    @SubscribeEvent
    static void onServerTick(ServerTickEvent.Post event) {
        serverTick = event.getServer().getTickCount();
        if (phase <= 0) {
            return;
        }
        if (--countdown > 0) {
            return;
        }
        try {
            runPhase(phase);
        } catch (Throwable t) {
            BlockModLogger.error("PROBE", "ev", "PHASE_THROW", "phase", phase, "threw", t.toString());
        }
        phase++;
        countdown = PHASE_SPACING_TICKS;
        if (phase > PHASE_LAST) {
            phase = -1;
            BlockModLogger.info("PROBE", "ev", "COMPLETE", "note", "all phases executed");
        }
    }

    private static void runPhase(int p) {
        switch (p) {
            case 1 -> phaseAttachment();
            case 2 -> phaseAttributes();
            case 3 -> phasePriority();
            case 4 -> phaseRaiseShield();
            case 5 -> phaseVanillaBlock();
            case 6 -> phaseCancelIncoming();
            case 7 -> phaseDisableVanillaBlock();
            case 8 -> phaseProjectile();
            case 9 -> phaseExplosion();
            case 10 -> phaseFall();
            case 11 -> phaseEat();
            case 12 -> phaseJumpEvent();
            case 13 -> phaseHurtAndBreak();
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // phases

    private static void phaseAttachment() {
        dummy.setData(ProbeAttachments.PROBE_VALUE.get(), 42);
        int read = dummy.getData(ProbeAttachments.PROBE_VALUE.get());
        BlockModLogger.info("PROBE", "ev", "AttachmentGetSet", "written", 42, "read", read,
                "match", read == 42);
    }

    private static void phaseAttributes() {
        AttributeInstance speed = dummy.getAttribute(Attributes.MOVEMENT_SPEED);
        if (speed == null) {
            BlockModLogger.error("PROBE", "ev", "AttributeModifier", "error", "no MOVEMENT_SPEED attribute");
            return;
        }
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "probe_malus");
        speed.addTransientModifier(new AttributeModifier(id, -0.40, AttributeModifier.Operation.ADD_MULTIPLIED_TOTAL));
        double withModifier = speed.getValue();
        boolean removed = speed.removeModifier(id);
        double afterRemoval = speed.getValue();
        BlockModLogger.info("PROBE", "ev", "AttributeModifier", "base", speed.getBaseValue(),
                "withAddMultipliedTotal", withModifier, "removeModifier", removed, "afterRemoval", afterRemoval);
    }

    private static void phasePriority() {
        dummy.getFoodData().setFoodLevel(6); // below regen threshold so health deltas stay clean
        modifyInHigh = true;
        modifyInLowest = true;
        hurtAndReport("priorityTest", level.damageSources().mobAttack(zombie), 6.0f);
        modifyInHigh = false;
        modifyInLowest = false;
    }

    private static void phaseRaiseShield() {
        dummy.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.SHIELD));
        dummy.startUsingItem(InteractionHand.MAIN_HAND);
        BlockModLogger.info("PROBE", "ev", "RaiseShield", "using", dummy.isUsingItem(),
                "blocking", dummy.isBlocking(), "note", "blocking turns true after 5 use ticks");
    }

    private static void phaseVanillaBlock() {
        int shieldDmgBefore = dummy.getMainHandItem().getDamageValue();
        BlockModLogger.info("PROBE", "ev", "VanillaBlock", "shieldDmgBefore", shieldDmgBefore);
        hurtAndReport("vanillaBlock", level.damageSources().mobAttack(zombie), 6.0f);
        BlockModLogger.info("PROBE", "ev", "VanillaBlock", "shieldDmgAfter", dummy.getMainHandItem().getDamageValue());
    }

    private static void phaseCancelIncoming() {
        cancelIncoming = true;
        int shieldDmgBefore = dummy.getMainHandItem().getDamageValue();
        hurtAndReport("cancelIncoming", level.damageSources().mobAttack(zombie), 6.0f);
        cancelIncoming = false;
        BlockModLogger.info("PROBE", "ev", "CancelIncoming", "shieldDmgAfter", dummy.getMainHandItem().getDamageValue(),
                "shieldDmgBefore", shieldDmgBefore);
    }

    private static void phaseDisableVanillaBlock() {
        disableVanillaBlock = true;
        int shieldDmgBefore = dummy.getMainHandItem().getDamageValue();
        hurtAndReport("disableVanillaBlock", level.damageSources().mobAttack(zombie), 6.0f);
        disableVanillaBlock = false;
        BlockModLogger.info("PROBE", "ev", "DisableVanillaBlock", "shieldDmgBefore", shieldDmgBefore,
                "shieldDmgAfter", dummy.getMainHandItem().getDamageValue());
        dummy.stopUsingItem();
        dummy.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void phaseProjectile() {
        Arrow arrow = EntityType.ARROW.create(level);
        if (arrow == null) {
            BlockModLogger.error("PROBE", "ev", "Projectile", "error", "arrow create failed");
            return;
        }
        arrow.setPos(dummy.getX() + 2.0, dummy.getY() + 1.0, dummy.getZ());
        arrow.setOwner(zombie);
        DamageSource source = level.damageSources().arrow(arrow, zombie);
        logDamageSource("arrow", source);
        hurtAndReport("arrow", source, 4.0f);
    }

    private static void phaseExplosion() {
        DamageSource source = level.damageSources().explosion(zombie, zombie);
        logDamageSource("explosion", source);
        hurtAndReport("explosion", source, 3.0f);
    }

    private static void phaseFall() {
        DamageSource source = level.damageSources().fall();
        logDamageSource("fall", source);
        hurtAndReport("fall", source, 2.0f);
    }

    private static void phaseEat() {
        // Start/Stop fire synchronously without ticking
        dummy.getFoodData().setFoodLevel(17);
        ItemStack apple = new ItemStack(Items.APPLE);
        dummy.setItemInHand(InteractionHand.MAIN_HAND, apple);
        dummy.startUsingItem(InteractionHand.MAIN_HAND);
        dummy.stopUsingItem();

        // full eat attempt: countdown happens inside LivingEntity#tick
        dummy.getFoodData().setFoodLevel(17);
        dummy.startUsingItem(InteractionHand.MAIN_HAND);
        boolean tickOk = true;
        for (int i = 0; i < 40 && dummy.isUsingItem(); i++) {
            try {
                dummy.tick();
            } catch (Throwable t) {
                BlockModLogger.error("PROBE", "ev", "FakePlayerTick", "threw", t.toString());
                tickOk = false;
                break;
            }
        }
        BlockModLogger.info("PROBE", "ev", "EatAttempt", "tickOk", tickOk,
                "stillUsing", dummy.isUsingItem(), "foodAfter", dummy.getFoodData().getFoodLevel());
        dummy.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void phaseJumpEvent() {
        LivingEvent.LivingJumpEvent jumpEvent = new LivingEvent.LivingJumpEvent(dummy);
        BlockModLogger.info("PROBE", "ev", "LivingJumpEvent", "cancelable", jumpEvent.isCancelable(),
                "class", jumpEvent.getClass().getName());
    }

    private static void phaseHurtAndBreak() {
        ItemStack shieldCopy = new ItemStack(Items.SHIELD);
        Consumer<net.minecraft.world.item.Item> onBreak =
                item -> BlockModLogger.info("PROBE", "ev", "HurtAndBreak", "breakCallback", "fired");
        BlockModLogger.info("PROBE", "ev", "HurtAndBreak", "before", shieldCopy.getDamageValue());
        shieldCopy.hurtAndBreak(5, level, dummy, onBreak);
        BlockModLogger.info("PROBE", "ev", "HurtAndBreak", "after", shieldCopy.getDamageValue());

        zombie.hurt(level.damageSources().playerAttack(dummy), 5.0f);
        BlockModLogger.info("PROBE", "ev", "MobHurt", "note", "hurt a zombie with playerAttack to show incoming fires for mobs too");
    }

    // ------------------------------------------------------------------
    // event listeners (always on, flags gate behaviour)

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onIncomingHigh(LivingIncomingDamageEvent event) {
        BlockModLogger.info("PROBE", "ev", "IncomingDamage[HIGH]", "entity", nameOf(event.getEntity()),
                "seen", event.getAmount(), "original", event.getOriginalAmount(),
                "containerNewDamage", event.getContainer().getNewDamage());
        if (cancelIncoming) {
            event.setCanceled(true);
            BlockModLogger.info("PROBE", "ev", "IncomingDamage[HIGH]", "action", "setCanceled(true)");
            return;
        }
        if (modifyInHigh) {
            event.setAmount(event.getAmount() - 2.0f);
            BlockModLogger.info("PROBE", "ev", "IncomingDamage[HIGH]", "action", "setAmount(-2)");
        }
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onIncomingLowest(LivingIncomingDamageEvent event) {
        BlockModLogger.info("PROBE", "ev", "IncomingDamage[LOWEST]", "entity", nameOf(event.getEntity()),
                "seen", event.getAmount(), "original", event.getOriginalAmount());
        if (modifyInLowest) {
            event.setAmount(event.getAmount() - 3.0f);
            BlockModLogger.info("PROBE", "ev", "IncomingDamage[LOWEST]", "action", "setAmount(-3)");
        }
    }

    @SubscribeEvent
    static void onShieldBlock(LivingShieldBlockEvent event) {
        BlockModLogger.info("PROBE", "ev", "ShieldBlock", "entity", nameOf(event.getEntity()),
                "originalBlock", event.getOriginalBlock(), "blockedDamage", event.getBlockedDamage(),
                "shieldDamage", event.shieldDamage());
        if (disableVanillaBlock) {
            event.setBlocked(false);
            event.setShieldDamage(0.0f);
            BlockModLogger.info("PROBE", "ev", "ShieldBlock", "action", "setBlocked(false)+setShieldDamage(0)");
        }
    }

    @SubscribeEvent
    static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        boolean isApple = event.getItem().is(Items.APPLE);
        if (isApple && event.getEntity() == dummy) {
            foodAtStart = dummy.getFoodData().getFoodLevel();
        }
        BlockModLogger.info("PROBE", "ev", "UseItem.Start", "entity", nameOf(event.getEntity()),
                "item", event.getItem().getItem().toString(), "hand", event.getHand(),
                "duration", event.getDuration(), "foodLevel", isApple ? foodAtStart : "-");
    }

    @SubscribeEvent
    static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        boolean isApple = event.getItem().is(Items.APPLE);
        BlockModLogger.info("PROBE", "ev", "UseItem.Finish", "entity", nameOf(event.getEntity()),
                "item", event.getItem().getItem().toString(), "duration", event.getDuration(),
                "foodLevelAtFinish", isApple ? dummy.getFoodData().getFoodLevel() : "-");
    }

    @SubscribeEvent
    static void onUseItemStop(LivingEntityUseItemEvent.Stop event) {
        BlockModLogger.info("PROBE", "ev", "UseItem.Stop", "entity", nameOf(event.getEntity()),
                "item", event.getItem().getItem().toString());
    }

    @SubscribeEvent
    static void onPlayerTick(PlayerTickEvent.Post event) {
        if (playerTicksLogged++ < 3) {
            BlockModLogger.info("PROBE", "ev", "PlayerTickEvent.Post", "count", playerTicksLogged,
                    "player", nameOf(event.getEntity()));
        }
    }

    // ------------------------------------------------------------------
    // helpers

    private static void placeActors() {
        Vec3 spawn = Vec3.atCenterOf(level.getSharedSpawnPos());
        dummy.setPos(spawn.x, spawn.y, spawn.z);
        zombie.setPos(spawn.x + 2.0, spawn.y, spawn.z);
        face(dummy, spawn.x + 2.0, spawn.z);
    }

    private static void face(ServerPlayer player, double x, double z) {
        double dx = x - player.getX();
        double dz = z - player.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, -dz));
        player.setYRot(yaw);
        player.setYHeadRot(yaw);
        player.setXRot(0.0f);
    }

    private static void hurtAndReport(String label, DamageSource source, float amount) {
        float before = dummy.getHealth();
        dummy.hurt(source, amount);
        float after = dummy.getHealth();
        BlockModLogger.info("PROBE", "ev", "HurtResult", "label", label,
                "before", before, "after", after, "delta", before - after);
    }

    private static void logDamageSource(String label, DamageSource source) {
        Vec3 pos = source.getSourcePosition();
        BlockModLogger.info("PROBE", "ev", "DamageSourceInfo", "label", label,
                "isProjectile", source.is(DamageTypeTags.IS_PROJECTILE),
                "isExplosion", source.is(DamageTypeTags.IS_EXPLOSION),
                "bypassesShield", source.is(DamageTypeTags.BYPASSES_SHIELD),
                "isFall", source.is(DamageTypeTags.IS_FALL),
                "srcPos", pos == null ? "null" : pos.x + "," + pos.y + "," + pos.z);
    }

    private static String nameOf(net.minecraft.world.entity.Entity entity) {
        return entity.getType().toString();
    }

    private ServerApiProbe() {}
}
