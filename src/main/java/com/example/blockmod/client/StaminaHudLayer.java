package com.example.blockmod.client;

import com.example.blockmod.BlockMod;

import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.Util;
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
    private static final int BAR_WIDTH = 182;   // matches the hotbar/food row width
    private static final int BAR_HEIGHT = 5;
    private static final int FOOD_ROW_OFFSET = 39;  // vanilla food bar top offset from screen bottom
    private static final int BAR_GAP = 8;           // pixels between the stamina bar and the food row

    private static final int COLOR_BACK = 0xCC101018;
    private static final int COLOR_BORDER = 0xFF202028;
    private static final int COLOR_NORMAL = 0xFF2F6FD0;   // deep blue fill
    private static final int COLOR_DEPLETED = 0xFFD0302A; // solid red, no flashing (v2.0)
    private static final int COLOR_OUTLINE = 0xFFF5F5F5;  // parry-window white outline
    private static final int COLOR_DEPLETED_TEXT = 0xFFFF6A5E;

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
        if (Float.isNaN(ClientGuardState.displayStamina())) {
            return; // no sync yet
        }

        int right = graphics.guiWidth() / 2 + 91;
        int y = graphics.guiHeight() - FOOD_ROW_OFFSET - BAR_GAP - BAR_HEIGHT;
        int left = right - BAR_WIDTH;

        // Display stamina may legitimately be negative while depleted; only clamp the top.
        float display = Math.min(ClientGuardState.displayStamina(), ClientGuardState.maxStamina());
        boolean depleted = ClientGuardState.isDepleted();
        boolean parryWindow = ClientGuardState.parryRemainTicks() > 0;

        graphics.fill(left - 1, y - 1, right + 1, y + BAR_HEIGHT + 1, COLOR_BORDER);
        graphics.fill(left, y, right, y + BAR_HEIGHT, COLOR_BACK);

        float fillRatio = Math.max(0.0f, Math.min(display / Math.max(ClientGuardState.maxStamina(), 0.001f), 1.0f));
        int fillWidth = Math.round(BAR_WIDTH * fillRatio);
        if (fillWidth > 0) {
            int color = depleted ? COLOR_DEPLETED : COLOR_NORMAL;
            if (!depleted && inRegenDelay()) {
                color = withPulse(color); // 轻微脉动: "regen is on hold"
            }
            graphics.fill(left, y, left + fillWidth, y + BAR_HEIGHT, color);
        }

        if (parryWindow) {
            // White outline stroke (1px) around the bar while a parry window is open.
            graphics.fill(left - 1, y - 1, right + 1, y, COLOR_OUTLINE);
            graphics.fill(left - 1, y + BAR_HEIGHT, right + 1, y + BAR_HEIGHT + 1, COLOR_OUTLINE);
            graphics.fill(left - 1, y, left, y + BAR_HEIGHT, COLOR_OUTLINE);
            graphics.fill(right, y, right + 1, y + BAR_HEIGHT, COLOR_OUTLINE);
        }

        if (depleted) {
            // Upward arrow right of the bar: "recovering fast", drawn as text (no custom texture).
            graphics.drawString(minecraft.font, "▲", right + 4, y - 2, COLOR_DEPLETED_TEXT, false);
        }
    }

    /**
     * Client-side heuristic for the 延迟中 visual: stamina is positive, below max,
     * and not moving at the normal/paced regen speed. A precise flag would need a
     * payload change (server-defined state); the heuristic reads only synced values.
     */
    private static boolean inRegenDelay() {
        float stamina = ClientGuardState.targetStamina();
        return !ClientGuardState.isDepleted()
                && stamina < ClientGuardState.maxStamina()
                && stamina > 0f;
    }

    private static int withPulse(int baseColor) {
        long millis = Util.getMillis() % 1000L;
        float phase = millis < 500L ? millis / 500.0f : (1000L - millis) / 500.0f;
        int alpha = 150 + Math.round(70 * phase); // 150..220
        return (alpha << 24) | (baseColor & 0x00FFFFFF);
    }

    private StaminaHudLayer() {}
}
