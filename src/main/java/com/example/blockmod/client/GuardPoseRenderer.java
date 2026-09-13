package com.example.blockmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;
import com.example.blockmod.logic.GuardEquipmentResolver;
import com.example.blockmod.logic.GuardRules;
import com.example.blockmod.network.BashWindupPayload;
import com.example.blockmod.network.GuardPoseSyncPayload;
import com.example.blockmod.registry.ModEffects;
import com.zigythebird.playeranim.animation.PlayerAnimResources;
import com.zigythebird.playeranim.animation.PlayerAnimationController;
import com.zigythebird.playeranim.api.PlayerAnimationAccess;
import com.zigythebird.playeranim.api.PlayerAnimationFactory;
import com.zigythebird.playeranimcore.animation.RawAnimation;
import com.zigythebird.playeranimcore.animation.layered.modifier.SpeedModifier;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonConfiguration;
import com.zigythebird.playeranimcore.api.firstPerson.FirstPersonMode;
import com.zigythebird.playeranimcore.enums.PlayState;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.event.entity.EntityLeaveLevelEvent;

/** Customer animations, driven exclusively by confirmed guard participation. */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class GuardPoseRenderer {
    private static final ResourceLocation LAYER = ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "guard");
    private static final Map<UUID, GuardPoseSyncPayload> GUARD_POSES = new HashMap<>();

    static void registerFactory() {
        PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(LAYER, 1000, Controller::new);
    }

    public static void acceptSync(GuardPoseSyncPayload payload) {
        if (payload.mainHand().isEmpty() && payload.offHand().isEmpty()) {
            GUARD_POSES.remove(payload.playerId());
        } else {
            GUARD_POSES.put(payload.playerId(), payload);
        }
        var level = Minecraft.getInstance().level;
        if (level != null && level.getPlayerByUUID(payload.playerId()) instanceof AbstractClientPlayer player
                && PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof Controller controller) {
            controller.updatePose();
        }
    }

    static boolean hasCustomerAnimation(Player player) {
        return player instanceof AbstractClientPlayer client
                && PlayerAnimationAccess.getPlayerAnimationLayer(client, LAYER) instanceof Controller controller
                && controller.visualActive;
    }

    static boolean shouldCapture(AbstractClientPlayer player, InteractionHand hand) {
        if (shouldPose(player, hand)) return true;
        return PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof Controller controller
                && controller.visualActive && controller.snapshot != null
                && !controller.snapshot.stack(hand).isEmpty()
                && ItemStack.isSameItemSameComponents(controller.snapshot.stack(hand), player.getItemInHand(hand));
    }

    static boolean shouldPose(Player player, InteractionHand hand) {
        if (player == Minecraft.getInstance().player
                && (!ClientGuardState.isGuarding() || ClientGuardState.isDepleted())) return false;
        var confirmed = GUARD_POSES.get(player.getUUID());
        if (confirmed == null || player.isSpectator() || !player.isAlive() || player.hasEffect(ModEffects.STUN)) return false;
        var held = player.getItemInHand(hand);
        int slot = hand == InteractionHand.MAIN_HAND ? GuardRules.SLOT_MAINHAND : GuardRules.SLOT_OFFHAND;
        return !held.isEmpty() && ItemStack.isSameItemSameComponents(confirmed.stack(hand), held)
                && (confirmed.powerGuarding()
                    || GuardEquipmentResolver.resolveSlot(player, ClientGuardState.swordBlocking()) == slot)
                && GuardEquipmentResolver.isGuardable(held, ClientGuardState.swordBlocking());
    }

    public static void acceptWindup(BashWindupPayload payload) {
        var level = Minecraft.getInstance().level;
        if (level == null || !(level.getPlayerByUUID(payload.playerId()) instanceof AbstractClientPlayer player)) return;
        if (PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof Controller controller) {
            controller.updatePose();
            if (!controller.pose.isEmpty()) {
                String side = controller.pose.endsWith("right") ? "right" : "left";
                String bash = controller.pose.startsWith("power_dual") ? "bash_dual_" + side : "bash_" + side;
                controller.play(bash, controller.pose.startsWith("power") ? controller.pose + "_hold" : null,
                        payload.durationTicks());
            }
        }
    }

    @SubscribeEvent
    static void onTick(ClientTickEvent.Post event) {
        var level = Minecraft.getInstance().level;
        if (level == null) return;
        for (var player : level.players()) {
            if (PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof Controller controller) {
                controller.updatePose();
                controller.advanceVisual();
            }
        }
    }

    private static void stop(AbstractClientPlayer player) {
        if (PlayerAnimationAccess.getPlayerAnimationLayer(player, LAYER) instanceof Controller controller) controller.clear();
    }

    @SubscribeEvent
    static void onEntityLeave(EntityLeaveLevelEvent event) {
        if (event.getLevel().isClientSide() && event.getEntity() instanceof AbstractClientPlayer player) {
            stop(player);
            GUARD_POSES.remove(player.getUUID());
            GuardSparks.remove(player.getUUID());
            if (player == Minecraft.getInstance().player) reset();
        }
    }

    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) { reset(); }
    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { reset(); }
    @SubscribeEvent
    static void onClone(ClientPlayerNetworkEvent.Clone event) { reset(); }

    private static void reset() {
        var level = Minecraft.getInstance().level;
        if (level != null) for (var player : level.players()) stop(player);
        GUARD_POSES.clear();
        GuardSparks.clear();
        ClientGuardState.reset();
    }

    private static final class Controller extends PlayerAnimationController {
        private String pose = "";
        private GuardPoseSyncPayload snapshot;
        private final SpeedModifier speed = new SpeedModifier(1);
        private int visualTicks;
        private boolean visualActive;
        private boolean finishesOnRelease;

        private Controller(AbstractClientPlayer player) {
            super(player, (controller, state, setter) -> PlayState.STOP);
            addModifierBefore(speed);
            setFirstPersonConfiguration(new FirstPersonConfiguration().setShowArmor(false)
                    .setShowRightArm(true).setShowLeftArm(true).setShowRightItem(true).setShowLeftItem(true));
        }

        private void updatePose() {
            if (player.isSpectator() || !player.isAlive() || player.hasEffect(ModEffects.STUN)
                    || (player == Minecraft.getInstance().player && ClientGuardState.isDepleted())
                    || !equipmentStillPresent()) {
                clear();
                return;
            }
            boolean main = shouldPose(player, InteractionHand.MAIN_HAND);
            boolean off = shouldPose(player, InteractionHand.OFF_HAND);
            if (!main && !off) {
                pose = "";
                if (!finishesOnRelease || visualTicks <= 0) clear();
                return;
            }
            var confirmed = GUARD_POSES.get(player.getUUID());
            int slot = GuardEquipmentResolver.resolveSlot(player, ClientGuardState.swordBlocking());
            var hand = slot == GuardRules.SLOT_OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
            var arm = hand == InteractionHand.MAIN_HAND ? player.getMainArm() : player.getMainArm().getOpposite();
            boolean right = arm == HumanoidArm.RIGHT;
            String next;
            if (confirmed.powerGuarding()) {
                next = main && off ? (right ? "power_dual_right" : "power_dual_left")
                        : (right ? "power_right" : "power_left");
            } else if (GuardEquipmentResolver.isSword(player.getItemInHand(hand), ClientGuardState.swordBlocking())) {
                // TODO: Replace the unchanged right-arm placeholder when the customer supplies the left sword parry.
                next = "sword_right";
            } else {
                next = right ? "shield_right" : "shield_left";
            }
            if (!next.equals(pose) || !sameEquipment(snapshot, confirmed)) {
                boolean loweringPowerGuard = pose.startsWith("power") && !confirmed.powerGuarding();
                pose = next;
                snapshot = confirmed;
                if (loweringPowerGuard) stopVisual();
                else play(next, confirmed.powerGuarding() ? next + "_hold" : null, 0);
                // Solo visual acceptance: each accepted raise previews the supplied effect.
                if (main) GuardSparks.request(player, InteractionHand.MAIN_HAND);
                if (off) GuardSparks.request(player, InteractionHand.OFF_HAND);
            }
        }

        private boolean equipmentStillPresent() {
            return snapshot == null
                    || ((snapshot.mainHand().isEmpty() || ItemStack.isSameItemSameComponents(snapshot.mainHand(), player.getMainHandItem()))
                    && (snapshot.offHand().isEmpty() || ItemStack.isSameItemSameComponents(snapshot.offHand(), player.getOffhandItem())));
        }

        private void advanceVisual() {
            if (visualTicks > 0 && --visualTicks == 0) {
                if (pose.startsWith("power")) speed.speed = 1;
                else stopVisual();
            }
        }

        private void play(String entry, String hold, int durationTicks) {
            var first = PlayerAnimResources.getAnimation(id(entry));
            var steady = hold == null ? null : PlayerAnimResources.getAnimation(id(hold));
            if (first == null || (hold != null && steady == null)) {
                BlockModLogger.error("GUARD_ANIMATION", "missing", entry + "/" + hold);
                return;
            }
            setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL);
            stopTriggeredAnimation();
            speed.speed = durationTicks > 0 ? (float) first.length() / durationTicks : 1;
            visualTicks = durationTicks > 0 ? durationTicks : (int) Math.ceil(first.length());
            finishesOnRelease = entry.startsWith("sword") || entry.startsWith("shield");
            visualActive = true;
            var sequence = RawAnimation.begin().thenPlay(first);
            triggerAnimation(steady == null ? sequence : sequence.thenLoop(steady));
        }

        private void clear() {
            pose = "";
            snapshot = null;
            stopVisual();
        }

        private void stopVisual() {
            visualTicks = 0;
            visualActive = false;
            finishesOnRelease = false;
            setFirstPersonMode(FirstPersonMode.NONE);
            stopTriggeredAnimation();
            stop();
        }

        private static boolean sameEquipment(GuardPoseSyncPayload a, GuardPoseSyncPayload b) {
            return a != null && ItemStack.isSameItemSameComponents(a.mainHand(), b.mainHand())
                    && ItemStack.isSameItemSameComponents(a.offHand(), b.offHand());
        }

        private static ResourceLocation id(String name) { return ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, name); }
    }

    private GuardPoseRenderer() {}
}
