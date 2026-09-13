package com.example.blockmod.client;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.layers.ItemInHandLayer;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.ClientHooks;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import org.joml.Vector3f;
import org.mesdag.particlestorm.PSGameClient;
import org.mesdag.particlestorm.particle.ParticleEmitter;

/** Uses the same held-item transform path as vanilla, PAL and MoreRotation. */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class GuardSparks {
    private static final ResourceLocation EFFECT = ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "guard_sparks");
    private static final Map<UUID, Anchor[]> ANCHORS = new HashMap<>();
    private static final Vector3f FIRST_PERSON_POSITION = new Vector3f();

    @SubscribeEvent
    static void onSetup(FMLClientSetupEvent event) { event.enqueueWork(GuardPoseRenderer::registerFactory); }

    @SubscribeEvent
    static void onAddLayers(EntityRenderersEvent.AddLayers event) {
        var minecraft = Minecraft.getInstance();
        var capture = new ItemInHandRenderer(minecraft, minecraft.getEntityRenderDispatcher(), minecraft.getItemRenderer()) {
            private final Vector3f position = new Vector3f();

            @Override
            public void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                   boolean left, PoseStack pose, MultiBufferSource buffer, int light) {
                var player = (AbstractClientPlayer) entity;
                var arm = left ? HumanoidArm.LEFT : HumanoidArm.RIGHT;
                var hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                var model = minecraft.getItemRenderer().getModel(stack, player.level(), player, player.getId());
                ClientHooks.handleCameraTransforms(pose, model, context, left);
                pose.last().pose().transformPosition(position.set(0, 0, 0));
                var camera = minecraft.gameRenderer.getMainCamera().getPosition();
                var anchor = anchors(player.getUUID())[hand.ordinal()];
                anchor.x = camera.x + position.x;
                anchor.y = camera.y + position.y;
                anchor.z = camera.z + position.z;
                anchor.tick = player.level().getGameTime();
                if (anchor.pending && anchor.tick <= anchor.pendingUntil) emit(player, anchor);
                else anchor.pending = false;
            }
        };
        for (var skin : event.getSkins()) {
            PlayerRenderer renderer = event.getSkin(skin);
            if (renderer == null) continue;
            renderer.addLayer(new ItemInHandLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>(renderer, capture) {
                @Override
                protected void renderArmWithItem(LivingEntity entity, ItemStack stack, ItemDisplayContext context,
                                                 HumanoidArm arm, PoseStack pose, MultiBufferSource buffer, int light) {
                    var player = (AbstractClientPlayer) entity;
                    var hand = arm == player.getMainArm() ? InteractionHand.MAIN_HAND : InteractionHand.OFF_HAND;
                    if (GuardPoseRenderer.shouldCapture(player, hand)) {
                        super.renderArmWithItem(entity, stack, context, arm, pose, buffer, light);
                    }
                }
            });
        }
    }

    static void request(AbstractClientPlayer player, InteractionHand hand) {
        var anchor = anchors(player.getUUID())[hand.ordinal()];
        anchor.pending = true;
        anchor.pendingUntil = player.level().getGameTime() + 1;
        if (player.level().getGameTime() - anchor.tick <= 1) emit(player, anchor);
    }

    static void captureFirstPerson(AbstractClientPlayer player, InteractionHand hand, ItemStack stack,
                                   ItemDisplayContext context, boolean left, PoseStack pose) {
        var minecraft = Minecraft.getInstance();
        var camera = minecraft.gameRenderer.getMainCamera();
        pose.pushPose();
        try {
            var model = minecraft.getItemRenderer().getModel(stack, player.level(), player, player.getId());
            ClientHooks.handleCameraTransforms(pose, model, context, left);
            pose.last().pose().transformPosition(FIRST_PERSON_POSITION.set(0, 0, 0));
            FIRST_PERSON_POSITION.rotate(camera.rotation());
            var anchor = anchors(player.getUUID())[hand.ordinal()];
            anchor.x = camera.getPosition().x + FIRST_PERSON_POSITION.x;
            anchor.y = camera.getPosition().y + FIRST_PERSON_POSITION.y;
            anchor.z = camera.getPosition().z + FIRST_PERSON_POSITION.z;
            anchor.tick = player.level().getGameTime();
            if (anchor.pending && anchor.tick <= anchor.pendingUntil) emit(player, anchor);
            else anchor.pending = false;
        } finally {
            pose.popPose();
        }
    }

    private static void emit(AbstractClientPlayer player, Anchor anchor) {
        anchor.pending = false;
        if (!PSGameClient.LOADER.id2Emitter().containsKey(EFFECT)) {
            BlockModLogger.error("GUARD_SPARKS", "missing", EFFECT);
            return;
        }
        var emitter = new ParticleEmitter(player.level(), new Vec3(anchor.x, anchor.y, anchor.z), EFFECT);
        PSGameClient.LOADER.addEmitter(emitter, false);
    }

    private static Anchor[] anchors(UUID id) {
        return ANCHORS.computeIfAbsent(id, key -> new Anchor[] {new Anchor(), new Anchor()});
    }

    static void remove(UUID id) { ANCHORS.remove(id); }
    static void clear() { ANCHORS.clear(); }

    private static final class Anchor {
        private double x, y, z;
        private long tick = Long.MIN_VALUE / 2;
        private boolean pending;
        private long pendingUntil;
    }

    private GuardSparks() {}
}
