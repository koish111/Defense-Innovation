package com.example.blockmod.client;
import com.example.blockmod.BlockMod;
import com.example.blockmod.logic.GuardEquipmentResolver;
import com.example.blockmod.data.ShieldType;

import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;

import org.joml.Matrix4f;

/** Continuous sword and shield guard presentation. */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class LegacyGuardPoseRenderer {
    private static final Matrix4f RIGHT_BLOCK = blockTransform(1.0F);
    private static final Matrix4f LEFT_BLOCK = blockTransform(-1.0F);
    private static boolean shouldPose(Player player, InteractionHand hand) {
        return !GuardPoseRenderer.hasCustomerAnimation(player) && GuardPoseRenderer.shouldPose(player, hand);
    }

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        var minecraft = Minecraft.getInstance();
        var player = minecraft.player;
        if (player == null || !shouldPose(player, event.getHand())
                || !GuardEquipmentResolver.isSword(event.getItemStack(), ClientGuardState.swordBlocking())
                || !ItemStack.isSameItemSameComponents(event.getItemStack(), player.getItemInHand(event.getHand()))) {
            return;
        }
        HumanoidArm arm = event.getHand() == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean right = arm == HumanoidArm.RIGHT;
        var pose = event.getPoseStack();
        pose.pushPose();
        try {
            // Standard first-person hand placement, shared guard-entry equip interpolation.
            pose.translate(right ? 0.56F : -0.56F,
                    -0.52F - 0.6F * ShieldGuardPose.raiseEquip(event.getPartialTick()), -0.72F);
            pose.mulPose(right ? RIGHT_BLOCK : LEFT_BLOCK);
            GuardSparks.captureFirstPerson(player, event.getHand(), event.getItemStack(),
                    right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    !right, pose);
            minecraft.gameRenderer.itemInHandRenderer.renderItem(player, event.getItemStack(),
                    right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                    !right, pose, event.getMultiBufferSource(), event.getPackedLight());
            event.setCanceled(true);
        } finally {
            pose.popPose();
        }
    }

    @SubscribeEvent
    static void onRenderPlayer(RenderPlayerEvent.Pre event) {
        poseArm(event, InteractionHand.MAIN_HAND);
        poseArm(event, InteractionHand.OFF_HAND);
    }

    private static void poseArm(RenderPlayerEvent.Pre event, InteractionHand hand) {
        if (!shouldPose(event.getEntity(), hand)) return;
        var model = event.getRenderer().getModel();
        var pose = GuardEquipmentResolver.typeOf(event.getEntity().getItemInHand(hand), ClientGuardState.swordBlocking())
                == ShieldType.SWORD ? SwordGuardArmPose.SWORD_BLOCK.getValue() : HumanoidModel.ArmPose.BLOCK;
        HumanoidArm arm = hand == InteractionHand.MAIN_HAND
                ? event.getEntity().getMainArm() : event.getEntity().getMainArm().getOpposite();
        if (arm == HumanoidArm.RIGHT) {
            model.rightArmPose = pose;
            if (model.leftArmPose.isTwoHanded()) model.leftArmPose = HumanoidModel.ArmPose.ITEM;
        } else {
            model.leftArmPose = pose;
            if (model.rightArmPose.isTwoHanded()) model.rightArmPose = HumanoidModel.ArmPose.ITEM;
        }
    }

    private static Matrix4f blockTransform(float side) {
        // The pre-1.9 blocking transform in modern item-model coordinates (snapshot 15w33b).
        return new Matrix4f().translation(-0.14142136F * side, 0.08F, 0.14142136F)
                .rotateX((float) Math.toRadians(-102.25F))
                .rotateY((float) Math.toRadians(13.365F * side))
                .rotateZ((float) Math.toRadians(78.05F * side));
    }

    private LegacyGuardPoseRenderer() {}
}
