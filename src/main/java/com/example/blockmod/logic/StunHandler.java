package com.example.blockmod.logic;

import com.example.blockmod.BlockMod;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.tick.EntityTickEvent;

/**
 * FR-05 / Spec §5.8: server-authoritative enforcement of the lockdown while a
 * living entity carries {@code blockmod:stun}. Stun is only ever applied by a
 * successful parry's counter; it is never a punishment for the defender.
 *
 * <p><b>Freeze model (v2).</b> The behaviour freeze itself lives in mixins:
 * {@code LivingEntityStunMixin} forces the {@code isImmobile()} branch of
 * {@code aiStep} — movement impulses zeroed, {@code serverAiStep} skipped (no
 * goals, no brain, no look control, no pathfinding) — and cancels
 * {@code LivingEntity#swing}; client-side only, {@code MouseHandlerStunMixin}
 * pins the camera and {@code LocalPlayerStunMixin} suppresses the swing packet.
 * {@code travel()} keeps running, so a frozen body still obeys external forces:
 * knockback, explosions, gravity. This handler deliberately does <b>not</b>
 * touch position or velocity — the earlier snapshot/zeroing approach cancelled
 * knockback and left AI animations running, which is exactly what v2 removes.
 *
 * <p>What remains here is what events express better than mixins: the per-tick
 * abort of in-progress item use ({@code stopUsingItem} interrupts a drawn bow
 * or a bite of food <i>without</i> the release effect — {@code releaseUsingItem}
 * would fire the arrow), the sprint reset, and the server-authoritative
 * cancellations. Client-side freezes are advisory (vanilla trust model: a
 * hacked client can send anything); the damage and interaction refusals below
 * are not bypassable. {@code setNoAi(true)} is deliberately not used — it does
 * nothing for players and would wipe the mob's AI state, losing aggro.
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class StunHandler {

    private static boolean isStunned(LivingEntity entity) {
        return MixinHooks.isStunned(entity);
    }

    /**
     * Per-tick cleanup for frozen entities, both sides: abort any in-progress
     * item use (a bow draw freezes mid-raise and never fires) and drop the
     * sprint state so the FOV kick and sprint sounds stop immediately.
     */
    @SubscribeEvent
    static void onEntityTickPre(EntityTickEvent.Pre event) {
        if (!(event.getEntity() instanceof LivingEntity entity) || !isStunned(entity)) {
            return;
        }
        if (entity.isUsingItem()) {
            entity.stopUsingItem();
        }
        if (entity instanceof Player player) {
            player.setSprinting(false);
        }
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
