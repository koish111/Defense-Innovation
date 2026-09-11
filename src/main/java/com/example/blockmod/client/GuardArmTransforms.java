package com.example.blockmod.client;

import com.example.blockmod.registry.ModTags;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.client.event.RenderHandEvent;

import org.joml.Matrix4f;
import org.joml.Quaternionf;

/**
 * Shared render path for the first-person shield animations (AGENTS.md §6.7).
 * Guard never sets {@code isUsingItem()} — the FR-24 interaction lockout
 * cancels {@code startUseItem} — so vanilla's blocking render (the
 * {@code blocking=1} model override selecting {@code shield_blocking.json},
 * raised by the {@code itemUsed} equip animation) can never fire. This class
 * replicates the vanilla {@code UseAnim.BLOCK} branch of
 * {@code ItemInHandRenderer#renderArmWithItem} by hand:
 * <ol>
 *   <li>{@code applyItemArmTransform} — the standard hand position, with the
 *       guard-entry equip interpolation ({@link ShieldGuardPose#raiseEquip})
 *       standing in for vanilla's equip height;</li>
 *   <li>optional flourish offsets (thrust/lift/pitch envelope);</li>
 *   <li>the first-person display-transform delta between
 *       {@code item/shield.json} and {@code item/shield_blocking.json}
 *       multiplied into the pose — turning whatever the model renders (its
 *       idle pose) into the vanilla blocking pose, without the model override
 *       and therefore for mod shields too;</li>
 *   <li>a re-render through the <em>public</em>
 *       {@link ItemInHandRenderer#renderItem} inside a push/pop scope, so the
 *       other hand — which shares the same pose matrix in NeoForge 1.21.1 —
 *       never moves.</li>
 * </ol>
 */
final class GuardArmTransforms {

    // ItemInHandRenderer#applyItemArmTransform constants (vanilla).
    private static final float ARM_POS_X = 0.56F;
    private static final float ARM_POS_Y = -0.52F;
    private static final float ARM_POS_Z = -0.72F;
    private static final float ARM_HEIGHT_SCALE = -0.6F;

    private static final float PIXEL = 1.0F / 16.0F; // model translation units

    /**
     * {@code shield_blocking.json} minus {@code shield.json}, first-person
     * display transforms (see {@link #blockDelta}).
     */
    private static final Matrix4f BLOCK_DELTA_RIGHT = blockDelta(false);
    private static final Matrix4f BLOCK_DELTA_LEFT = blockDelta(true);

    /**
     * Renders the vanilla blocking pose for the hand holding the guard's active
     * shield, with the flourish offsets applied on top. {@code requiredTag}
     * further filters which shield may be posed ({@code null} = any shield);
     * {@code amplitude} is the eased 0→1→0 flourish envelope — a near-zero
     * amplitude renders the plain steady pose.
     *
     * @return {@code true} when this hand's render was taken over
     *         (the event is then cancelled so vanilla does not draw it twice).
     */
    static boolean renderGuardPose(RenderHandEvent event, TagKey<Item> requiredTag,
            float lift, float forward, float pitchDegrees, float amplitude) {
        if (!ClientGuardState.isGuarding()) {
            return false;
        }
        ItemStack stack = event.getItemStack();
        boolean matches = requiredTag != null ? stack.is(requiredTag) : ModTags.isShieldItem(stack);
        if (!matches) {
            return false;
        }
        Minecraft minecraft = Minecraft.getInstance();
        if (!(minecraft.player instanceof AbstractClientPlayer player)) {
            return false;
        }
        // Pose only the guard's active shield hand (offhand priority, FR-11) —
        // this also keeps both-holds-shield from posing twice.
        if (ShieldGuardPose.activeShieldHand(player) != event.getHand()) {
            return false;
        }
        boolean mainHand = event.getHand() == InteractionHand.MAIN_HAND;
        HumanoidArm arm = mainHand ? player.getMainArm() : player.getMainArm().getOpposite();
        boolean right = arm == HumanoidArm.RIGHT;

        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        // vanilla UseAnim.BLOCK branch: standard hand position + equip interpolation
        poseStack.translate((right ? 1.0F : -1.0F) * ARM_POS_X,
                ARM_POS_Y + ARM_HEIGHT_SCALE * ShieldGuardPose.raiseEquip(event.getPartialTick()),
                ARM_POS_Z);
        if (amplitude > 1.0e-4F) {
            poseStack.translate(0.0F, lift * amplitude, -forward * amplitude); // -Z = toward the screen centre
            poseStack.mulPose(Axis.XP.rotationDegrees(-pitchDegrees * amplitude));
        }
        // idle model pose → vanilla blocking pose
        poseStack.last().pose().mul(right ? BLOCK_DELTA_RIGHT : BLOCK_DELTA_LEFT);

        event.setCanceled(true); // vanilla must not render this hand a second time untransformed
        minecraft.gameRenderer.itemInHandRenderer.renderItem(player, stack,
                right ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND : ItemDisplayContext.FIRST_PERSON_LEFT_HAND,
                !right, poseStack, event.getMultiBufferSource(), event.getPackedLight());
        poseStack.popPose();
        return true;
    }

    /**
     * Delta between the vanilla shield's idle and blocking first-person display
     * transforms ({@code item/shield.json} vs {@code item/shield_blocking.json},
     * {@code firstperson_righthand}/{@code firstperson_lefthand} entries).
     * Multiplying this into the pose before
     * {@link ItemInHandRenderer#renderItem} maps the model's idle first-person
     * pose onto the vanilla blocking pose.
     */
    private static Matrix4f blockDelta(boolean leftArm) {
        // shield.json idle: rotation [0,180,5], translation [-10,2,-10] (rh) / [10,0,-10] (lh), scale 1.25
        Matrix4f idle = firstPersonTransform(0.0F, 180.0F, 5.0F,
                leftArm ? 10.0F : -10.0F, leftArm ? 0.0F : 2.0F, -10.0F, 1.25F, leftArm);
        // shield_blocking.json: rotation [0,180,-5], translation [-15,5,-11] (rh) / [5,5,-11] (lh), scale 1.25
        Matrix4f blocking = firstPersonTransform(0.0F, 180.0F, -5.0F,
                leftArm ? 5.0F : -15.0F, 5.0F, -11.0F, 1.25F, leftArm);
        return blocking.mul(idle.invert(), new Matrix4f());
    }

    /**
     * Replicates {@code ItemTransform#apply}: translate → rotationXYZ → scale,
     * with the left-hand mirroring vanilla uses (translation.x, rotation.y and
     * rotation.z negated).
     */
    private static Matrix4f firstPersonTransform(float rotX, float rotY, float rotZ,
            float transX, float transY, float transZ, float scale, boolean leftArm) {
        int mirror = leftArm ? -1 : 1;
        Matrix4f matrix = new Matrix4f();
        matrix.translate(mirror * transX * PIXEL, transY * PIXEL, transZ * PIXEL);
        matrix.rotate(new Quaternionf().rotationXYZ(
                (float) Math.toRadians(rotX),
                (float) Math.toRadians(mirror * rotY),
                (float) Math.toRadians(mirror * rotZ)));
        matrix.scale(scale, scale, scale);
        return matrix;
    }

    private GuardArmTransforms() {}
}
