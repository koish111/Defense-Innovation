package com.example.blockmod.registry;

import com.mojang.blaze3d.platform.InputConstants;

import net.minecraft.client.KeyMapping;

import net.neoforged.neoforge.client.settings.KeyConflictContext;

import org.lwjgl.glfw.GLFW;

/**
 * Key bindings (Spec §13.1.2), all remappable in the vanilla controls screen.
 * Key mappings are plain objects in NeoForge 21.1 (no registry) —
 * {@code RegisterKeyMappingsEvent} puts them on the client controls screen.
 *
 * <ul>
 *   <li>{@code #POWER_GUARD} — default Left Ctrl (designer ruling 2026-09-07,
 *       previously Left Alt / ADR-01);</li>
 *   <li>{@code #GUARD} — hold to guard, default the vanilla use key (right
 *       mouse button, ruling 2026-09-15). While the guard binding IS the use
 *       key, the first press still delegates targeted interactions to vanilla
 *       (ClientGuardInputHandler); rebound away, it enters guard directly;</li>
 *   <li>{@code #SHIELD_BASH} — press to bash, default the vanilla attack key
 *       (left mouse button, ruling 2026-09-15), only meaningful while the
 *       guard intent is live;</li>
 *   <li>{@code #PARRY} — the raise-to-parry key, default also the vanilla use
 *       key (ruling 2026-09-15): it triggers the same guard entry as
 *       {@code #GUARD} (either key may be rebound independently); the server
 *       keeps deciding the parry window at guard entry. At the defaults both
 *       keys are right-click, so the control scheme is unchanged.</li>
 * </ul>
 */
public final class ModKeyMappings {
    public static final KeyMapping.Category CATEGORY = new KeyMapping.Category(
            net.minecraft.resources.Identifier.fromNamespaceAndPath("blockmod", "combat"));
    public static final KeyMapping POWER_GUARD = new KeyMapping(
            "key.blockmod.power_guard",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.KEYSYM,
            GLFW.GLFW_KEY_LEFT_CONTROL,
            CATEGORY);
    public static final KeyMapping GUARD = new KeyMapping(
            "key.blockmod.guard",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY);
    public static final KeyMapping SHIELD_BASH = new KeyMapping(
            "key.blockmod.shield_bash",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_LEFT,
            CATEGORY);
    public static final KeyMapping PARRY = new KeyMapping(
            "key.blockmod.parry",
            KeyConflictContext.IN_GAME,
            InputConstants.Type.MOUSE,
            GLFW.GLFW_MOUSE_BUTTON_RIGHT,
            CATEGORY);

    private ModKeyMappings() {}
}
