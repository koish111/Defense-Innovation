package com.example.blockmod.client;

import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;

import net.neoforged.fml.common.asm.enumextension.EnumProxy;
import net.neoforged.neoforge.client.IArmPoseTransformer;

/** Bootstrap parameters for NeoForge's client-only arm-pose enum extension. */
public final class SwordGuardArmPose {
    public static final EnumProxy<HumanoidModel.ArmPose> SWORD_BLOCK = new EnumProxy<>(
            HumanoidModel.ArmPose.class, false, (IArmPoseTransformer) SwordGuardArmPose::apply);

    private static void apply(HumanoidModel<?> model, LivingEntity entity, HumanoidArm arm) {
        var part = arm == HumanoidArm.RIGHT ? model.rightArm : model.leftArm;
        // Minecraft 1.8's heldItem=3 pose: half the walk swing, a 54-degree raise and inward yaw.
        part.xRot = part.xRot * 0.5F - (float) (Math.PI * 3.0 / 10.0);
        part.yRot = (float) (Math.PI / 6.0) * (arm == HumanoidArm.RIGHT ? -1.0F : 1.0F);
        model.attackTime = 0.0F;
    }

    private SwordGuardArmPose() {}
}
