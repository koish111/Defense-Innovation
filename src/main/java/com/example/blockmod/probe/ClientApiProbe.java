package com.example.blockmod.probe;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;

import net.minecraft.resources.ResourceLocation;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;

/**
 * TEMPORARY M0 probe (T-03) — remove at M1. CLIENT ONLY.
 *
 * Verifies API-09 (RegisterGuiLayersEvent#registerAboveAll) by registering a no-op layer
 * during client mod loading, and API-08 (InputEvent.MouseButton.Pre) by logging any
 * captured button presses. The GUI layer draws nothing.
 */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class ClientApiProbe {
    @SubscribeEvent
    static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "probe_layer"),
                (guiGraphics, deltaTracker) -> { });
        BlockModLogger.info("PROBE", "ev", "RegisterGuiLayersEvent", "result", "registerAboveAll ok");
    }

    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        BlockModLogger.info("PROBE", "ev", "MouseButton.Pre",
                "button", event.getButton(), "action", event.getAction());
    }

    private ClientApiProbe() {}
}
