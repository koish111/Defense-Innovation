package com.example.blockmod.logic;

import com.example.blockmod.BlockMod;
import com.example.blockmod.registry.ModEffects;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * FR-05 / Spec §5.8: enforces the six-way lockdown while a living entity carries
 * {@code blockmod:stun}. Stun is only ever applied by a successful parry's counter;
 * it is never a punishment for the defender.
 *
 * <p>Movement is resolved after effect ticks, which is why the lockdown runs in
 * {@code LivingTickEvent} handlers instead of {@code MobEffect#applyEffectTick},
 * and why {@code setNoAi(true)} is not used (no effect on players).
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class StunHandler {
    private static boolean isStunned(LivingEntity entity) {
        return entity.hasEffect(ModEffects.STUN);
    }

    /**
     * Lockdown rows 1, 2 and 5: zero horizontal speed, clamp upward Y, interrupt item use.
     * Runs on {@link EntityTickEvent.Pre} (1.21.1 has no separate LivingTickEvent) so the
     * zeroed delta movement governs this tick's movement resolution.
     */
    @SubscribeEvent
    static void onLivingTick(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !isStunned(entity)) {
            return;
        }
        double y = entity.getDeltaMovement().y;
        entity.setDeltaMovement(0.0, Math.min(y, 0.0), 0.0);
        entity.setSprinting(false);
        if (entity.isUsingItem()) {
            entity.stopUsingItem();
        }
    }

    /** Lockdown row 3: no attacks. */
    @SubscribeEvent
    static void onAttackEntity(AttackEntityEvent event) {
        Player attacker = event.getEntity();
        if (isStunned(attacker)) {
            event.setCanceled(true);
        }
    }

    /** Lockdown row 4: starting to use an item (eating, blocking, drawing a bow). */
    @SubscribeEvent
    static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    /** Lockdown row 6: no block or entity interaction. */
    @SubscribeEvent
    static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onEntityInteract(PlayerInteractEvent.EntityInteract event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    static void onEntityInteractSpecific(PlayerInteractEvent.EntityInteractSpecific event) {
        if (isStunned(event.getEntity())) {
            event.setCanceled(true);
        }
    }

    private StunHandler() {}
}
