package com.example.blockmod.client;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;
import com.example.blockmod.mixin.ItemInHandRendererAccessor;
import com.example.blockmod.network.StaminaSyncPayload;
import com.example.blockmod.registry.ModTags;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * First-person buckler raise animation (AGENTS.md §6.7, 2026-09-11 scaffold):
 * a purely visual flourish played once when the server confirms a guard entry
 * carrying a parry window. Never plays on heartbeats — the trigger is the
 * <em>transition</em> from "no parry window" to "parry window" in the stamina
 * sync, not the level.
 *
 * <p>State hygiene: the visual timer is cleared on guard exit, depletion, stun
 * (the stun force-drop arrives as a guard exit) and equipment replacement (the
 * server force-drops the guard), plus the client lifecycle events below.
 * Never touches the server's parry window.
 *
 * <p>Rendering contract: {@code RenderHandEvent} shares its pose matrix across
 * both hands, so the transform is scoped with push/pop and the affected hand is
 * re-rendered through {@code ItemInHandRenderer#renderArmWithItem} (opened by
 * {@link ItemInHandRendererAccessor}) — the other hand never moves. Settings
 * live in {@code blockmod-client.toml} and never alter gameplay.
 *
 * <p>Sync hook: {@link ClientGuardState#acceptStaminaSync} calls
 * {@link #acceptStaminaSync} <strong>first</strong>, so the
 * {@code ClientGuardState} getters still return the previous packet's state
 * inside this handler — that ordering is what makes the transition detection
 * below correct.
 */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class BucklerParryAnimation {
    private static int remainingTicks; // animation countdown; 0 = idle

    /**
     * Called first inside {@link ClientGuardState#acceptStaminaSync} — the
     * {@code ClientGuardState} getters here still see the PREVIOUS sync.
     */
    public static void acceptStaminaSync(StaminaSyncPayload payload) {
        boolean windowNow = payload.guarding() && payload.parryRemainTicks() > 0;
        boolean windowBefore = ClientGuardState.isGuarding() && ClientGuardState.parryRemainTicks() > 0;
        if (windowNow && !windowBefore && !payload.depleted()) {
            remainingTicks = Config.bucklerAnimationTicks();
        }
        // guard exit covers depletion, the stun force-drop and equipment
        // replacement (the server force-drops the guard in all three cases)
        if (!payload.guarding() || payload.depleted()) {
            remainingTicks = 0;
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    /** Respawn / dimension change: the guard mirror is rebuilt from the next sync. */
    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        remainingTicks = 0;
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        remainingTicks = 0;
    }

    @SubscribeEvent
    static void onClone(ClientPlayerNetworkEvent.Clone event) {
        remainingTicks = 0;
    }

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        if (remainingTicks <= 0 || event.getHand() != InteractionHand.OFF_HAND) {
            return; // buckler lives in the off hand (FR-11: offhand guard has top priority)
        }
        int duration = Config.bucklerAnimationTicks();
        if (duration <= 0) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(ModTags.ITEMS_GUARDABLE)) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }

        float partialTick = Math.clamp(event.getPartialTick(), 0.0f, 1.0f);
        float elapsed = duration - remainingTicks + partialTick;
        float progress = Math.clamp(elapsed / duration, 0.0f, 1.0f);
        float amplitude = (float) Math.sin(Math.PI * progress); // raise, then settle back

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0,
                Config.bucklerAnimationLift() * amplitude,
                -Config.bucklerAnimationForward() * amplitude); // -Z = toward the screen centre
        poseStack.mulPose(Axis.XP.rotationDegrees(-Config.bucklerAnimationPitch() * amplitude));

        event.setCanceled(true); // vanilla must not render this hand a second time untransformed
        ((ItemInHandRendererAccessor) minecraft.gameRenderer.itemInHandRenderer)
                .blockparry$renderArmWithItem((AbstractClientPlayer) player, event.getPartialTick(),
                        event.getInterpolatedPitch(), InteractionHand.OFF_HAND, event.getSwingProgress(),
                        stack, event.getEquipProgress(), poseStack, event.getMultiBufferSource(),
                        event.getPackedLight());
        poseStack.popPose();
    }

    private BucklerParryAnimation() {}
}
