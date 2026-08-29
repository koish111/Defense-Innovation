package com.example.blockmod.probe;

import java.util.UUID;
import java.util.function.Consumer;

import com.mojang.authlib.GameProfile;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;

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
import net.neoforged.bus.api.ICancellableEvent;
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
 * Runs on a dedicated server with no real players. Arms itself on {@link ServerStartedEvent}
 * and walks through 12 scheduled phases on the server thread.
 *
 * Subject under test is a NoAI zombie added to the world (it can be hurt, raise a shield
 * and tick naturally). NeoForge's {@link FakePlayer} is used only as the attacker and for
 * attachment/attribute tests — it is deliberately invulnerable and does not tick.
 *
 * Covers: API-01/02 (damage/shield event order + cancel + setBlocked), API-03 (priority
 * ordering), API-05 (attachment get/set), API-10 (transient modifiers), API-11/12 (damage
 * source tags + source position), API-13 (use item events / food timing), API-14
 * (hurtAndBreak), API-16 (LivingJumpEvent cancelability).
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class ServerApiProbe {
    private static final int PHASE_FIRST = 1;
    private static final int PHASE_LAST = 12;
    private static final int PHASE_SPACING_TICKS = 25;

    private static int phase = 0; // 0 = disarmed, -1 = done
    private static int countdown = 0;
    private static int serverTick = 0;

    private static ServerLevel level;
    private static ServerPlayer attacker; // FakePlayer: never hurt, never ticks, used as damage source
    private static Zombie subject;        // NoAI zombie: hurt-able, ticks, can raise a shield

    // per-phase behaviour flags read by the event handlers
    private static boolean modifyInHigh;
    private static boolean modifyInLowest;
    private static boolean cancelIncoming;
    private static boolean disableVanillaBlock;

    private static int playerTicksLogged;

    // ------------------------------------------------------------------
    // arming / scheduling

    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        level = event.getServer().overworld();
        attacker = new FakePlayer(level, new GameProfile(UUID.fromString("b10c8b10-c8b1-0c8b-10c8-b10c8b10c8b1"), "BlockProbe"));
        subject = EntityType.ZOMBIE.create(level);
        if (subject == null) {
            BlockModLogger.error("PROBE", "ev", "ARM", "error", "zombie create failed");
            phase = -1;
            return;
        }
        subject.setNoAi(true); // probe only: keep it stationary so phases stay deterministic
        subject.setPersistenceRequired(); // probe only: rule out any despawn path mid-run
        placeActors();
        level.addFreshEntity(subject);
        level.setDayTime(18000); // night: stop sunlight burning from polluting the phases
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
            case 10 -> phaseFallAndEat();
            case 11 -> phaseJumpEvent();
            case 12 -> phaseHurtAndBreak();
            default -> { }
        }
    }

    // ------------------------------------------------------------------
    // phases

    private static void phaseAttachment() {
        attacker.setData(ProbeAttachments.PROBE_VALUE.get(), 42);
        int read = attacker.getData(ProbeAttachments.PROBE_VALUE.get());
        BlockModLogger.info("PROBE", "ev", "AttachmentGetSet", "written", 42, "read", read,
                "match", read == 42);
    }

    private static void phaseAttributes() {
        AttributeInstance speed = attacker.getAttribute(Attributes.MOVEMENT_SPEED);
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
        modifyInHigh = true;
        modifyInLowest = true;
        float before = subject.getHealth();
        subject.hurt(level.damageSources().playerAttack(attacker), 6.0f);
        modifyInHigh = false;
        modifyInLowest = false;
        BlockModLogger.info("PROBE", "ev", "HurtResult", "label", "priorityTest",
                "before", before, "after", subject.getHealth(), "delta", before - subject.getHealth());
    }

    private static void phaseRaiseShield() {
        subject.setItemInHand(InteractionHand.OFF_HAND, new ItemStack(Items.SHIELD));
        subject.startUsingItem(InteractionHand.OFF_HAND);
        BlockModLogger.info("PROBE", "ev", "RaiseShield", "using", subject.isUsingItem(),
                "blocking", subject.isBlocking(), "note", "blocking turns true after 5 use ticks");
    }

    private static void phaseVanillaBlock() {
        subject.setHealth(20.0f);
        int shieldDmgBefore = subject.getOffhandItem().getDamageValue();
        float before = subject.getHealth();
        subject.hurt(level.damageSources().playerAttack(attacker), 6.0f);
        BlockModLogger.info("PROBE", "ev", "HurtResult", "label", "vanillaBlock",
                "before", before, "after", subject.getHealth(), "delta", before - subject.getHealth(),
                "shieldDmgBefore", shieldDmgBefore, "shieldDmgAfter", subject.getOffhandItem().getDamageValue());
    }

    private static void phaseCancelIncoming() {
        subject.setHealth(20.0f);
        cancelIncoming = true;
        float before = subject.getHealth();
        int shieldDmgBefore = subject.getOffhandItem().getDamageValue();
        subject.hurt(level.damageSources().playerAttack(attacker), 6.0f);
        cancelIncoming = false;
        BlockModLogger.info("PROBE", "ev", "HurtResult", "label", "cancelIncoming",
                "before", before, "after", subject.getHealth(), "delta", before - subject.getHealth(),
                "shieldDmgBefore", shieldDmgBefore, "shieldDmgAfter", subject.getOffhandItem().getDamageValue());
    }

    private static void phaseDisableVanillaBlock() {
        subject.setHealth(20.0f);
        disableVanillaBlock = true;
        float before = subject.getHealth();
        int shieldDmgBefore = subject.getOffhandItem().getDamageValue();
        subject.hurt(level.damageSources().playerAttack(attacker), 6.0f);
        disableVanillaBlock = false;
        BlockModLogger.info("PROBE", "ev", "HurtResult", "label", "disableVanillaBlock",
                "before", before, "after", subject.getHealth(), "delta", before - subject.getHealth(),
                "shieldDmgBefore", shieldDmgBefore, "shieldDmgAfter", subject.getOffhandItem().getDamageValue());
        subject.stopUsingItem();
        subject.setItemInHand(InteractionHand.OFF_HAND, ItemStack.EMPTY);
    }

    private static void phaseProjectile() {
        Arrow arrow = EntityType.ARROW.create(level);
        if (arrow == null) {
            BlockModLogger.error("PROBE", "ev", "Projectile", "error", "arrow create failed");
            return;
        }
        arrow.setPos(subject.getX() + 2.0, subject.getY() + 1.0, subject.getZ());
        arrow.setOwner(attacker);
        DamageSource source = level.damageSources().arrow(arrow, attacker);
        logDamageSource("arrow", source);
        subject.setHealth(20.0f);
        float before = subject.getHealth();
        subject.hurt(source, 4.0f);
        BlockModLogger.info("PROBE", "ev", "HurtResult", "label", "arrow",
                "before", before, "after", subject.getHealth(), "delta", before - subject.getHealth());
    }

    private static void phaseExplosion() {
        DamageSource source = level.damageSources().explosion(attacker, attacker);
        logDamageSource("explosion", source);
        subject.setHealth(20.0f);
        float before = subject.getHealth();
        subject.hurt(source, 3.0f);
        BlockModLogger.info("PROBE", "ev", "HurtResult", "label", "explosion",
                "before", before, "after", subject.getHealth(), "delta", before - subject.getHealth());
    }

    private static void phaseFallAndEat() {
        DamageSource fallSource = level.damageSources().fall();
        logDamageSource("fall", fallSource);
        subject.setHealth(20.0f);
        float before = subject.getHealth();
        subject.hurt(fallSource, 2.0f);
        BlockModLogger.info("PROBE", "ev", "HurtResult", "label", "fall",
                "before", before, "after", subject.getHealth(), "delta", before - subject.getHealth());

        // Start fires synchronously on the fake player; FoodData is player-only, so the
        // pre/post nutrition comparison rides on the player's food level.
        attacker.getFoodData().setFoodLevel(17);
        ItemStack apple = new ItemStack(Items.APPLE);
        attacker.setItemInHand(InteractionHand.MAIN_HAND, apple);
        attacker.startUsingItem(InteractionHand.MAIN_HAND);
        attacker.stopUsingItem();

        // full eat on the subject: drive its tick manually so the countdown is
        // deterministic (natural server ticking continues in the background).
        subject.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(Items.APPLE));
        subject.startUsingItem(InteractionHand.MAIN_HAND);
        BlockModLogger.info("PROBE", "ev", "EatAttempt", "usingAfterStart", subject.isUsingItem(),
                "duration", subject.getUseItem().getUseDuration(subject));
        for (int i = 0; i < 40 && subject.isUsingItem(); i++) {
            try {
                subject.tick();
            } catch (Throwable t) {
                BlockModLogger.error("PROBE", "ev", "FakePlayerTick", "subject", "tick", "threw", t.toString());
                break;
            }
        }
        BlockModLogger.info("PROBE", "ev", "EatAttempt", "stillUsing", subject.isUsingItem(),
                "ticksUsing", subject.getTicksUsingItem(),
                "resultItem", subject.getUseItem().getItem().toString());
        subject.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
    }

    private static void phaseJumpEvent() {
        LivingEvent.LivingJumpEvent jumpEvent = new LivingEvent.LivingJumpEvent(subject);
        BlockModLogger.info("PROBE", "ev", "LivingJumpEvent", "cancelable", jumpEvent instanceof ICancellableEvent,
                "class", jumpEvent.getClass().getName());
    }

    private static void phaseHurtAndBreak() {
        ItemStack shieldCopy = new ItemStack(Items.SHIELD);
        Consumer<net.minecraft.world.item.Item> onBreak =
                item -> BlockModLogger.info("PROBE", "ev", "HurtAndBreak", "breakCallback", "fired");
        BlockModLogger.info("PROBE", "ev", "HurtAndBreak", "before", shieldCopy.getDamageValue());
        shieldCopy.hurtAndBreak(5, level, attacker, onBreak);
        BlockModLogger.info("PROBE", "ev", "HurtAndBreak", "after", shieldCopy.getDamageValue());
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
        BlockModLogger.info("PROBE", "ev", "UseItem.Start", "entity", nameOf(event.getEntity()),
                "item", event.getItem().getItem().toString(), "hand", event.getHand(),
                "duration", event.getDuration(), "count", event.getItem().getCount(),
                "foodLevel", isApple && event.getEntity() == attacker ? attacker.getFoodData().getFoodLevel() : "-");
    }

    @SubscribeEvent
    static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        boolean isApple = event.getItem().is(Items.APPLE) || event.getItem().isEmpty();
        BlockModLogger.info("PROBE", "ev", "UseItem.Finish", "entity", nameOf(event.getEntity()),
                "resultCount", event.getItem().getCount(), "isEmpty", event.getItem().isEmpty(),
                "note", isApple ? "if count=0/empty, finishUsingItem ran BEFORE this event" : "");
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
        attacker.setPos(spawn.x, spawn.y, spawn.z);
        subject.setPos(spawn.x + 2.0, spawn.y, spawn.z);
        face(subject, spawn.x, spawn.z); // subject faces the attacker so frontal blocking can pass
    }

    private static void face(LivingEntity entity, double x, double z) {
        double dx = x - entity.getX();
        double dz = z - entity.getZ();
        float yaw = (float) Math.toDegrees(Math.atan2(-dx, -dz));
        entity.setYRot(yaw);
        entity.setYHeadRot(yaw);
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
