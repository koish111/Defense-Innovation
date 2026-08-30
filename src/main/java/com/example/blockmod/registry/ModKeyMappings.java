package com.example.blockmod.registry;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import net.neoforged.neoforge.client.settings.KeyConflictContext;

import org.lwjgl.glfw.GLFW;

/**
 * Key bindings (Spec §13.1.2): {@code blockmod.key.power_guard}, default Left Alt
 * (ADR-01), remappable in the vanilla controls screen. Key mappings are plain
 * objects in NeoForge 21.1 (no registry) — {@code RegisterKeyMappingsEvent} puts
 * them on the client controls screen.
 */
public final class ModKeyMappings {
    public static final KeyMapping POWER_GUARD = new KeyMapping(
            "key.blockmod.power_guard",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_ALT,
            "key.categories.blockmod");

    private ModKeyMappings() {}
}
