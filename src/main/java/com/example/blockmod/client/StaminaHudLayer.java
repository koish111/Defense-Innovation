package com.example.blockmod.client;

import com.example.blockmod.BlockMod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * FR-21: the stamina bar, fixed above the hunger bar (right side, hotbar width).
 * Four states: normal (blue), delayed (pulsing blue), depleted (solid red, no
 * flash, recovery arrow) and parry window (white outline). Everything is drawn
 * with flat fills — no textures, resolution independent.
 */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class StaminaHudLayer {
    private static final ResourceLocation EMPTY_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BlockMod.MODID, "textures/gui/hud/empty_stamina_bar.png");
    private static final ResourceLocation DEPLETED_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BlockMod.MODID, "textures/gui/hud/guard_break_stamina_bar.png");
    private static final ResourceLocation FILLED_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BlockMod.MODID, "textures/gui/hud/stamina_bar.png");
    private static final ResourceLocation REDUCING_TEXTURE = ResourceLocation.fromNamespaceAndPath(
            BlockMod.MODID, "textures/gui/hud/stamina_bar_reducing.png");

    private static final int TEXTURE_WIDTH = 90;
    private static final int TEXTURE_HEIGHT = 8;
    private static final int REDUCING_TEXTURE_WIDTH = 92;
    private static final int REDUCING_TEXTURE_HEIGHT = 10;
    private static final int BAR_WIDTH = 182;
    private static final int BAR_HEIGHT = 8;
    private static final int REDUCING_INSET = 1;
    private static final int REDUCING_WIDTH = BAR_WIDTH + REDUCING_INSET * 2;
    private static final int REDUCING_HEIGHT = BAR_HEIGHT + REDUCING_INSET * 2;
    private static final int FOOD_ROW_OFFSET = 39;  // vanilla food bar top offset from screen bottom
    private static final int BAR_GAP = 8;           // pixels between the stamina bar and the food row

    private static final int COLOR_OUTLINE = 0xFFF5F5F5;  // parry-window white outline

    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "stamina_hud"),
                StaminaHudLayer::render);
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        ClientGuardState.onClientTick(event);
    }

    private static void render(GuiGraphics graphics, DeltaTracker deltaTracker) {
        Minecraft minecraft = Minecraft.getInstance();
        Player player = minecraft.player;
        if (player == null || minecraft.options.hideGui || player.isSpectator()) {
            return;
        }
        float frameStamina = ClientGuardState.frameStamina(
                deltaTracker.getGameTimeDeltaPartialTick(false));
        if (Float.isNaN(frameStamina) || !ClientGuardState.shouldRenderHud()) {
            return; // no sync yet
        }

        int right = graphics.guiWidth() / 2 + 91;
        int y = graphics.guiHeight() - FOOD_ROW_OFFSET - BAR_GAP - BAR_HEIGHT;
        int left = right - BAR_WIDTH;

        boolean depleted = ClientGuardState.isDepleted();
        boolean parryWindow = ClientGuardState.parryRemainTicks() > 0;

        if (depleted) {
            blitScaled(graphics, DEPLETED_TEXTURE, left, y, BAR_WIDTH, BAR_HEIGHT,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);
        } else {
            blitScaled(graphics, EMPTY_TEXTURE, left, y, BAR_WIDTH, BAR_HEIGHT,
                    TEXTURE_WIDTH, TEXTURE_HEIGHT);

            float maxStamina = Math.max(ClientGuardState.maxStamina(), 0.001f);
            float fillRatio = Math.clamp(frameStamina / maxStamina, 0.0f, 1.0f);
            int fillWidth = Math.round(BAR_WIDTH * fillRatio);
            if (fillWidth > 0) {
                if (ClientGuardState.isReducing(frameStamina)) {
                    int reducingRight = fillWidth >= BAR_WIDTH
                            ? left - REDUCING_INSET + REDUCING_WIDTH
                            : left + fillWidth;
                    graphics.enableScissor(left - REDUCING_INSET, y - REDUCING_INSET,
                            reducingRight, y - REDUCING_INSET + REDUCING_HEIGHT);
                    blitScaled(graphics, REDUCING_TEXTURE, left - REDUCING_INSET,
                            y - REDUCING_INSET, REDUCING_WIDTH, REDUCING_HEIGHT,
                            REDUCING_TEXTURE_WIDTH, REDUCING_TEXTURE_HEIGHT);
                    graphics.disableScissor();
                } else {
                    graphics.enableScissor(left, y, left + fillWidth, y + BAR_HEIGHT);
                    blitScaled(graphics, FILLED_TEXTURE, left, y, BAR_WIDTH, BAR_HEIGHT,
                            TEXTURE_WIDTH, TEXTURE_HEIGHT);
                    graphics.disableScissor();
                }
            }
        }

        if (parryWindow) {
            // White outline stroke (1px) around the bar while a parry window is open.
            graphics.fill(left - 1, y - 1, right + 1, y, COLOR_OUTLINE);
            graphics.fill(left - 1, y + BAR_HEIGHT, right + 1, y + BAR_HEIGHT + 1, COLOR_OUTLINE);
            graphics.fill(left - 1, y, left, y + BAR_HEIGHT, COLOR_OUTLINE);
            graphics.fill(right, y, right + 1, y + BAR_HEIGHT, COLOR_OUTLINE);
        }

    }

    private static void blitScaled(GuiGraphics graphics, ResourceLocation texture,
            int x, int y, int width, int height,
            int textureWidth, int textureHeight) {
        graphics.blit(texture, x, y, width, height, 0.0f, 0.0f,
                textureWidth, textureHeight, textureWidth, textureHeight);
    }

    private StaminaHudLayer() {}
}
