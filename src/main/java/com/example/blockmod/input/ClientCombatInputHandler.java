package com.example.blockmod.input;

import com.example.blockmod.BlockMod;
import com.example.blockmod.client.ClientGuardState;
import com.example.blockmod.logic.GuardEquipmentResolver;
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
import net.neoforged.neoforge.client.network.ClientPacketDistributor;

/**
 * T-36/T-37 client side: the combat intents beyond guarding.
 *
 * <ul>
 *   <li><b>Shield bash</b> (FR-15): a {@code SHIELD_BASH} press while the guard
 *       intent is sent with a medium shield triggers {@code shield_bash}; the
 *       server validates the cooldown and the windup. Ruling 2026-09-15: the
 *       key is remappable, defaulting to the vanilla attack key (left mouse
 *       button), so the default scheme is unchanged. Clicks are consumed per
 *       tick ({@code consumeClick}), which follows keyboard rebinding too.</li>
 *   <li><b>Power guard</b> (FR-16): the Left Ctrl binding (remappable) combines
 *       with guard intent and requires a great shield as the primary guard
 *       item (2026-09-13 ruling). Guard entry is sent first regardless of
 *       which physical key was pressed first.</li>
 * </ul>
 */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class ClientCombatInputHandler {
    private static boolean powerGuardActive;

    /**
     * Bash sampling runs in {@code ClientTickEvent.Pre}: clicks are drained
     * unconditionally (a click during a screen is discarded — the guard intent
     * cannot be live there), so no stale click survives a screen swap
     * ({@code KeyMapping#releaseAll} flushes the counters anyway).
     */
    @SubscribeEvent
    static void onClientTickPre(ClientTickEvent.Pre event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        while (ModKeyMappings.SHIELD_BASH.consumeClick()) {
            if (player == null || minecraft.getOverlay() != null
                    || !ClientGuardInputHandler.isGuardIntentSent()) {
                continue;
            }
            boolean mediumShield = player.getOffhandItem().is(ModTags.ITEMS_MEDIUM_SHIELDS)
                    || player.getMainHandItem().is(ModTags.ITEMS_MEDIUM_SHIELDS);
            if (mediumShield) {
                ClientPacketDistributor.sendToServer(new ShieldBashPayload());
            }
        }
    }

    static void updatePowerGuardIntent(LocalPlayer player, boolean guardingIntent) {
        if (player == null) {
            powerGuardActive = false;
            return;
        }
        boolean keyDown = ModKeyMappings.POWER_GUARD.isDown();
        boolean wantActive = guardingIntent && keyDown
                && GuardEquipmentResolver.canPowerGuard(player, ClientGuardState.swordBlocking());
        if (wantActive != powerGuardActive) {
            powerGuardActive = wantActive;
            ClientPacketDistributor.sendToServer(new PowerGuardPayload(wantActive));
        }
    }

    static void resetPowerGuardIntent() {
        powerGuardActive = false;
    }

    private ClientCombatInputHandler() {}
}
