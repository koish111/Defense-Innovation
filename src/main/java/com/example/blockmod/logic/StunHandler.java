package com.example.blockmod.logic;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.blockmod.BlockMod;
import com.example.blockmod.registry.ModEffects;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * FR-05 / Spec §5.8: enforces the six-way lockdown while a living entity carries
 * {@code blockmod:stun}. Stun is only ever applied by a successful parry's counter;
 * it is never a punishment for the defender.
 *
 * <p>Movement is resolved after effect ticks, which is why the lockdown runs in
 * tick-event handlers instead of {@code MobEffect#applyEffectTick}, and why
 * {@code setNoAi(true)} is not used (no effect on players).
 *
 * <p><b>Mobs</b> freeze horizontally via a position snapshot: the Pre hook records
 * X/Z, the Post hook restores them and zeroes the horizontal velocity. The tick
 * itself keeps running — effect timers decay, invulnerability decays, and the AI
 * keeps targeting (no aggro loss) — the mob simply cannot go anywhere. Jump arcs
 * and falling (vertical motion) stay intact. A stunned entity's own attacks are
 * suppressed in {@link #onStunnedAttacker}.
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class StunHandler {
    /** Horizontal snapshot for the current tick, per stunned non-player entity. */
    private static final Map<UUID, double[]> PREV_HORIZONTAL = new HashMap<>();

    private static boolean isStunned(LivingEntity entity) {
        return entity.hasEffect(ModEffects.STUN);
    }

    @SubscribeEvent
    static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !isStunned(entity)) {
            return;
        }
        if (entity instanceof Player player) {
            // Player lockdown rows: zero residual drift (the real suppression lives in
            // ClientStunInputHandler on the client side), no jump impulse, no item use.
            double y = player.getDeltaMovement().y;
            player.setDeltaMovement(0.0, Math.min(y, 0.0), 0.0);
            player.setSprinting(false);
            if (player.isUsingItem()) {
                player.stopUsingItem();
            }
            return;
        }
        // Non-player entity: snapshot X/Z so the Post hook can undo the AI's move.
        PREV_HORIZONTAL.put(entity.getUUID(), new double[]{entity.getX(), entity.getZ()});
    }

    @SubscribeEvent
    static void onEntityTickPost(EntityTickEvent.Post event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || entity instanceof Player) {
            return;
        }
        double[] prev = PREV_HORIZONTAL.remove(entity.getUUID());
        if (prev == null) {
            return;
        }
        if (isStunned(entity)) {
            entity.setPos(prev[0], entity.getY(), prev[1]); // horizontal freeze, vertical physics intact
            Vec3 dm = entity.getDeltaMovement();
            entity.setDeltaMovement(0.0, dm.y, 0.0);
        }
        // stun ended: the entry is simply dropped — the entity resumes normal behaviour
    }

    /** FR-05 lockdown row 3 for mobs: a stunned entity's own attacks deal no damage. */
    @SubscribeEvent
    static void onStunnedAttacker(LivingIncomingDamageEvent event) {
        if (event.getSource().getEntity() instanceof LivingEntity attacker && isStunned(attacker)) {
            event.setCanceled(true);
        }
    }

    /** Lockdown row 3: no player attacks. */
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
