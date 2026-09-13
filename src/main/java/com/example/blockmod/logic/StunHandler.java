package com.example.blockmod.logic;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;
import com.example.blockmod.registry.ModEffects;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.EffectParticleModificationEvent;
import net.neoforged.neoforge.event.entity.living.LivingDeathEvent;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;
import net.neoforged.neoforge.event.entity.living.LivingIncomingDamageEvent;
import net.neoforged.neoforge.event.entity.living.MobEffectEvent;
import net.neoforged.neoforge.event.entity.player.AttackEntityEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
 *
 * <p><b>Presentation (2026-09-13 ruling).</b> Stun never emits world potion
 * particles — vanilla builds the synced particle list from every visible effect
 * instance and {@link EffectParticleModificationEvent} is NeoForge's hook into
 * exactly that list, so the handler marks {@code blockmod:stun} invisible there;
 * the HUD icon ({@code showIcon}) is untouched. While stunned the entity also
 * carries the vanilla glow outline tinted red: vanilla colours the outline from
 * {@code Entity#getTeamColor()}, so the handler joins it to the {@code
 * blockmod_stun} scoreboard team (RED) and sets the glowing tag. Cleanup is
 * event-driven (see the {@code MobEffectEvent} / death / clone handlers below);
 * the pre-stun team is remembered in persistent data and restored afterwards.
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

    // ==================================================================
    // Presentation (2026-09-13 ruling): no particles, red glow outline.
    // ==================================================================

    /** Scoreboard team that tints the vanilla glow outline red while stunned. */
    private static final String STUN_TEAM_NAME = "blockmod_stun";
    /** Persistent-data tag remembering the entity's team before the stun join. */
    private static final String PREV_TEAM_KEY = "blockmod:prev_team";

    private static boolean isStunEffect(Holder<MobEffect> effect) {
        return effect.value() == ModEffects.STUN.get();
    }

    /**
     * Hides the world potion particles for {@code blockmod:stun}. Vanilla builds
     * the synced {@code DATA_EFFECT_PARTICLES} list from every visible instance;
     * the HUD icon ({@code showIcon}) is independent and stays visible.
     */
    @SubscribeEvent
    static void onEffectParticle(EffectParticleModificationEvent event) {
        if (isStunEffect(event.getEffect().getEffect())) {
            event.setVisible(false);
        }
    }

    /** Red outline lands with the effect; idempotent across stun refreshes. */
    @SubscribeEvent
    static void onStunAdded(MobEffectEvent.Added event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && isStunEffect(instance.getEffect())) {
            applyStunGlow(event.getEntity());
        }
    }

    /** Natural expiry (tick-out). */
    @SubscribeEvent
    static void onStunExpired(MobEffectEvent.Expired event) {
        MobEffectInstance instance = event.getEffectInstance();
        if (instance != null && isStunEffect(instance.getEffect())) {
            clearStunGlow(event.getEntity());
        }
    }

    /** Milk, {@code /effect clear} and manual removal all funnel through this event. */
    @SubscribeEvent
    static void onStunRemoved(MobEffectEvent.Remove event) {
        if (isStunEffect(event.getEffect())) {
            clearStunGlow(event.getEntity());
        }
    }

    /**
     * Death never fires the effect-removal events (the effect map dies with the
     * body), so the team membership would leak for every killed mob. Clean up here.
     */
    @SubscribeEvent
    static void onStunnedDeath(LivingDeathEvent event) {
        LivingEntity entity = event.getEntity();
        if (entity.hasGlowingTag() || entity.hasEffect(ModEffects.STUN)) {
            clearStunGlow(entity);
        }
    }

    /**
     * Death drops the effect map without Remove events, and dimension travel
     * rebuilds the player without copying the glowing flag — the clone reconciles
     * both directions. Team membership is keyed by player name and survives the
     * swap; the pre-stun team record does not, so it is copied across explicitly.
     */
    @SubscribeEvent
    static void onPlayerClone(PlayerEvent.Clone event) {
        var original = event.getOriginal();
        var now = event.getEntity();
        if (original.getPersistentData().contains(PREV_TEAM_KEY)) {
            now.getPersistentData().putString(PREV_TEAM_KEY, original.getPersistentData().getString(PREV_TEAM_KEY));
            original.getPersistentData().remove(PREV_TEAM_KEY);
        }
        if (now.hasEffect(ModEffects.STUN)) {
            applyStunGlow(now);
        } else if (original.hasGlowingTag() || now.hasGlowingTag()) {
            clearStunGlow(now);
        }
    }

    private static void applyStunGlow(LivingEntity entity) {
        if (entity.level().isClientSide() || !Config.stunRedOutline()) {
            return;
        }
        Scoreboard scoreboard = entity.level().getScoreboard();
        var data = entity.getPersistentData();
        if (!data.contains(PREV_TEAM_KEY)) {
            PlayerTeam prev = scoreboard.getPlayersTeam(entity.getScoreboardName());
            if (prev != null && !STUN_TEAM_NAME.equals(prev.getName())) {
                data.putString(PREV_TEAM_KEY, prev.getName());
            }
        }
        joinStunTeam(scoreboard, entity);
        ensureGlowing(entity);
    }

    private static void joinStunTeam(Scoreboard scoreboard, LivingEntity entity) {
        PlayerTeam team = scoreboard.getPlayerTeam(STUN_TEAM_NAME);
        if (team == null) {
            team = scoreboard.addPlayerTeam(STUN_TEAM_NAME);
        }
        if (team.getColor() != ChatFormatting.RED) {
            team.setColor(ChatFormatting.RED);
        }
        scoreboard.addPlayerToTeam(entity.getScoreboardName(), team);
    }

    private static void ensureGlowing(LivingEntity entity) {
        // setGlowingTag itself writes shared flag 6 from isCurrentlyGlowing().
        entity.setGlowingTag(true);
    }

    private static void clearStunGlow(LivingEntity entity) {
        if (entity.level().isClientSide()) {
            return;
        }
        Scoreboard scoreboard = entity.level().getScoreboard();
        var data = entity.getPersistentData();
        String prev = data.getString(PREV_TEAM_KEY);
        data.remove(PREV_TEAM_KEY);
        PlayerTeam stunTeam = scoreboard.getPlayerTeam(STUN_TEAM_NAME);
        PlayerTeam current = scoreboard.getPlayersTeam(entity.getScoreboardName());
        boolean inStunTeam = stunTeam != null && current == stunTeam;
        if (!prev.isEmpty()) {
            PlayerTeam prevTeam = scoreboard.getPlayerTeam(prev);
            if (prevTeam != null) {
                // addPlayerToTeam removes the entity from its current team first.
                scoreboard.addPlayerToTeam(entity.getScoreboardName(), prevTeam);
            } else if (inStunTeam) {
                scoreboard.removePlayerFromTeam(entity.getScoreboardName());
            }
        } else if (inStunTeam) {
            scoreboard.removePlayerFromTeam(entity.getScoreboardName());
        }
        if (!entity.hasEffect(MobEffects.GLOWING)) { // never clear a real vanilla glow
            entity.setGlowingTag(false);
        }
    }

    private StunHandler() {}
}
