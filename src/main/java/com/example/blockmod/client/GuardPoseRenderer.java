package com.example.blockmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.blockmod.BlockMod;
import com.example.blockmod.logic.GuardEquipmentResolver;
import com.example.blockmod.logic.GuardRules;
import com.example.blockmod.network.GuardPoseSyncPayload;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.registry.ModEffects;

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
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;
import net.neoforged.neoforge.client.event.RenderPlayerEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

import org.joml.Matrix4f;

/** Per-hand sword and shield poses driven by server-confirmed guard participation. */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class GuardPoseRenderer {
    private static final Map<UUID, GuardPoseSyncPayload> GUARD_POSES = new HashMap<>();
    private static final Matrix4f RIGHT_BLOCK = blockTransform(1.0F);
    private static final Matrix4f LEFT_BLOCK = blockTransform(-1.0F);

    public static void acceptSync(GuardPoseSyncPayload payload) {
        if (payload.mainHand().isEmpty() && payload.offHand().isEmpty()) {
            GUARD_POSES.remove(payload.playerId());
        } else {
            GUARD_POSES.put(payload.playerId(), payload);
        }
    }

    static boolean shouldPose(Player player, InteractionHand hand) {
        if (player == Minecraft.getInstance().player
                && (!ClientGuardState.isGuarding() || ClientGuardState.isDepleted())) {
            return false;
        }
        GuardPoseSyncPayload confirmed = GUARD_POSES.get(player.getUUID());
        if (confirmed == null || player.isSpectator() || !player.isAlive() || player.hasEffect(ModEffects.STUN)) {
            return false;
        }
        ItemStack held = player.getItemInHand(hand);
        int expectedSlot = hand == InteractionHand.MAIN_HAND
                ? GuardRules.SLOT_MAINHAND : GuardRules.SLOT_OFFHAND;
        return !held.isEmpty() && ItemStack.isSameItemSameComponents(confirmed.stack(hand), held)
                && (confirmed.powerGuarding()
                    || GuardEquipmentResolver.resolveSlot(player, ClientGuardState.swordBlocking()) == expectedSlot)
                && GuardEquipmentResolver.isGuardable(held, ClientGuardState.swordBlocking());
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

    @SubscribeEvent
    static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof Player player) {
            if (player == Minecraft.getInstance().player) {
                GUARD_POSES.clear();
            } else {
                GUARD_POSES.remove(player.getUUID());
            }
        }
    }

    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) { reset(); }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }

    @SubscribeEvent
    static void onClone(ClientPlayerNetworkEvent.Clone event) { GUARD_POSES.clear(); }

    private static void reset() {
        GUARD_POSES.clear();
        ClientGuardState.reset();
    }

    private static Matrix4f blockTransform(float side) {
        // The pre-1.9 blocking transform in modern item-model coordinates (snapshot 15w33b).
        return new Matrix4f().translation(-0.14142136F * side, 0.08F, 0.14142136F)
                .rotateX((float) Math.toRadians(-102.25F))
                .rotateY((float) Math.toRadians(13.365F * side))
                .rotateZ((float) Math.toRadians(78.05F * side));
    }

    private GuardPoseRenderer() {}
}
