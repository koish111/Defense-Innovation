package com.example.blockmod.client;

import com.example.blockmod.BlockMod;
import com.example.blockmod.network.StaminaSyncPayload;
import com.example.blockmod.registry.ModTags;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.world.InteractionHand;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Steady first-person guard pose (2026-09-11 ruling): while the server confirms
 * a guard, the active shield hand renders the <strong>vanilla shield blocking
 * pose</strong> for all three shield tiers. This is needed because guard never
 * sets {@code isUsingItem()} — the FR-24 interaction lockout cancels
 * {@code startUseItem} — so vanilla's own blocking render path (the
 * {@code blocking=1} model override selecting {@code shield_blocking.json},
 * plus the equip raise from {@code itemUsed}) can never fire on its own.
 * {@link GuardArmTransforms} replicates that path by hand instead.
 *
 * <p>Animation chain per tier (2026-09-11): buckler = entry thrust flourish
 * ({@link BucklerParryAnimation}) then this steady pose; medium shield = this
 * pose, thrust flourish on bash windup ({@link ShieldBashAnimation}), then
 * this pose again; great shield = this pose only.
 *
 * <p>The equip raise replays on every guard entry (vanilla {@code itemUsed}
 * behaviour): {@link #raiseEquip} interpolates 1→0 over {@link #RAISE_TICKS},
 * sliding the shield up from below exactly like the vanilla equip animation.
 *
 * <p>Sync hook: {@link ClientGuardState#acceptStaminaSync} calls
 * {@link #acceptStaminaSync} while the {@code ClientGuardState} getters still
 * return the previous packet's state — the guard false→true transition is what
 * restarts the raise.
 */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class ShieldGuardPose {
    /** Vanilla raise speed is 0.4 height/tick (≈2.5 ticks); 3 ticks keeps it smooth. */
    private static final int RAISE_TICKS = 3;

    private static int raiseRemainTicks;

    /**
     * Called from {@link ClientGuardState#acceptStaminaSync} while the
     * {@code ClientGuardState} getters still see the PREVIOUS sync.
     */
    public static void acceptStaminaSync(StaminaSyncPayload payload) {
        if (payload.guarding() && !ClientGuardState.isGuarding() && !payload.depleted()) {
            raiseRemainTicks = RAISE_TICKS;
        }
        // guard exit covers depletion, the stun force-drop and equipment replacement
        if (!payload.guarding() || payload.depleted()) {
            raiseRemainTicks = 0;
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (raiseRemainTicks > 0) {
            raiseRemainTicks--;
        }
    }

    /** Respawn / dimension change: the guard mirror is rebuilt from the next sync. */
    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        raiseRemainTicks = 0;
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        raiseRemainTicks = 0;
    }

    @SubscribeEvent
    static void onClone(ClientPlayerNetworkEvent.Clone event) {
        raiseRemainTicks = 0;
    }

    /**
     * 1→0 equip interpolation for the guard-entry raise (0 = settled blocking
     * pose). Shared with the flourish renders so they slide up on the same
     * trajectory.
     */
    static float raiseEquip(float partialTick) {
        if (raiseRemainTicks <= 0) {
            return 0.0F;
        }
        float clampedPartialTick = Math.clamp(partialTick, 0.0F, 1.0F);
        return Math.clamp((raiseRemainTicks - clampedPartialTick) / RAISE_TICKS, 0.0F, 1.0F);
    }

    /**
     * The hand the guard pose applies to — the guard's active shield with
     * offhand priority (FR-11, client mirror). {@code null} when no shield is
     * held; swords are posed separately by {@link GuardPoseRenderer}.
     */
    static InteractionHand activeShieldHand(AbstractClientPlayer player) {
        if (player.getOffhandItem().is(com.example.blockmod.registry.ModTags.ITEMS_GUARDABLE)
                && com.example.blockmod.registry.ModTags.isShieldItem(player.getOffhandItem())) {
            return InteractionHand.OFF_HAND;
        }
        if (com.example.blockmod.registry.ModTags.isShieldItem(player.getMainHandItem())) {
            return InteractionHand.MAIN_HAND;
        }
        return null;
    }

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        // NORMAL priority: the flourish handlers run first (HIGH) and cancel the
        // event for their envelope frames, so this steady pose never fights them.
        GuardArmTransforms.renderGuardPose(event, null, 0.0F, 0.0F, 0.0F, 0.0F);
    }

    private ShieldGuardPose() {}
}
