package com.example.blockmod.client;

import com.example.blockmod.mixin.ItemInHandRendererAccessor;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * Shared render path for the first-person shield animations (AGENTS.md §6.7):
 * cancels the vanilla hand render and re-renders the held item inside a
 * push/pop-scoped transform, so the other hand — which shares the same pose
 * matrix in NeoForge 1.21.1 — never moves. The vanilla
 * {@code ItemInHandRenderer#renderArmWithItem} is private (no access
 * transformer in NeoForge 21.1.248) and is opened by the invoker mixin.
 */
final class GuardArmTransforms {

    /**
     * Applies the transform to whichever hand holds an item of {@code requiredTag}
     * and re-renders it. {@code amplitude} is the eased 0→1→0 envelope; a
     * near-zero amplitude is a no-op (the vanilla render stays untouched).
     */
    static boolean renderWithTransform(RenderHandEvent event, TagKey<Item> requiredTag,
            float lift, float forward, float pitchDegrees, float amplitude) {
        if (amplitude <= 1.0e-4f) {
            return false;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty() || !stack.is(requiredTag)) {
            return false; // only the hand actually holding the animated shield moves
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.player instanceof AbstractClientPlayer player)) {
            return false;
        }
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(0.0, lift * amplitude, -forward * amplitude); // -Z = toward the screen centre
        poseStack.mulPose(Axis.XP.rotationDegrees(-pitchDegrees * amplitude));
        event.setCanceled(true); // vanilla must not render this hand a second time untransformed
        ((ItemInHandRendererAccessor) minecraft.gameRenderer.itemInHandRenderer)
                .blockparry$renderArmWithItem(player, event.getPartialTick(), event.getInterpolatedPitch(),
                        event.getHand(), event.getSwingProgress(), stack, event.getEquipProgress(),
                        poseStack, event.getMultiBufferSource(), event.getPackedLight());
        poseStack.popPose();
        return true;
    }

    private GuardArmTransforms() {}
}
