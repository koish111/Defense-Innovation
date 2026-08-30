package com.example.blockmod.input;

import com.example.blockmod.BlockMod;
import com.example.blockmod.network.PowerGuardPayload;
import com.example.blockmod.network.ShieldBashPayload;
import com.example.blockmod.registry.ModKeyMappings;
import com.example.blockmod.registry.ModTags;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * T-36/T-37 client side: the combat intents beyond guarding.
 *
 * <ul>
 *   <li><b>Shield bash</b> (FR-15): a left-click while the guard intent is sent
 *       with a medium shield triggers {@code shield_bash}; the server validates
 *       the cooldown and the windup.</li>
 *   <li><b>Power guard</b> (FR-16): the Left Alt binding (remappable) sends
 *       {@code power_guard} on state change; the active intent is only sent while
 *       a great shield is held (synced tag), the release always reports off.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class ClientCombatInputHandler {
    private static final int BUTTON_ATTACK = 0;

    private static boolean powerGuardActive;

    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != BUTTON_ATTACK || event.getAction() != 1) {
            return; // left-click press only
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !ClientGuardInputHandler.isGuardIntentSent()) {
            return;
        }
        boolean mediumShield = player.getOffhandItem().is(ModTags.ITEMS_MEDIUM_SHIELDS)
                || player.getMainHandItem().is(ModTags.ITEMS_MEDIUM_SHIELDS);
        if (mediumShield) {
            PacketDistributor.sendToServer(new ShieldBashPayload());
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            if (powerGuardActive) {
                powerGuardActive = false; // leaving the world disarms silently
            }
            return;
        }
        boolean keyDown = ModKeyMappings.POWER_GUARD.isDown();
        boolean wantActive = keyDown && isGreatShieldHeld(player);
        if (wantActive != powerGuardActive) {
            powerGuardActive = wantActive;
            PacketDistributor.sendToServer(new PowerGuardPayload(wantActive));
        }
    }

    /** Great shields only (synced tag); the server re-validates the profile. */
    private static boolean isGreatShieldHeld(LocalPlayer player) {
        return player.getOffhandItem().is(ModTags.ITEMS_GREAT_SHIELDS)
                || player.getMainHandItem().is(ModTags.ITEMS_GREAT_SHIELDS);
    }

    private ClientCombatInputHandler() {}
}
