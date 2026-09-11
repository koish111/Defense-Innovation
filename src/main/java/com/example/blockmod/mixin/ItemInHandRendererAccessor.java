package com.example.blockmod.mixin;

import com.mojang.blaze3d.vertex.PoseStack;

import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

/**
 * Opens the private first-person hand renderer so {@code RenderHandEvent}
 * handlers can cancel one hand and re-render it inside their own transform
 * scope (AGENTS.md §6.7 buckler raise animation). The vanilla method is
 * private and covered by no access transformer in NeoForge 21.1.248.
 */
@Mixin(ItemInHandRenderer.class)
public interface ItemInHandRendererAccessor {

    @Invoker("renderArmWithItem")
    void blockparry$renderArmWithItem(AbstractClientPlayer player, float partialTick, float pitch,
            InteractionHand hand, float swingProgress, ItemStack stack, float equipProgress,
            PoseStack poseStack, MultiBufferSource bufferSource, int packedLight);
}
