package com.example.blockmod.input;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;
import com.example.blockmod.network.GuardInputPayload;
import com.example.blockmod.registry.ModTags;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.tags.ItemTags;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.network.PacketDistributor;

/**
 * T-27 client side: captures the right-click hold state and reports it as intent
 * (FR-23). The client never decides guarding — it reports and heartbeats:
 *
 * <ul>
 *   <li>{@code MouseButton.Pre} button 1 press/release flips the desired state;</li>
 *   <li>state changes are sent immediately, a held guard is heartbeaten every
 *       {@code state_heartbeat_ticks} (R-07);</li>
 *   <li>the intent is only sent while a plausibly guardable item is held — item
 *       tags are synced to the client, so {@code #blockmod:guardable} +
 *       {@code #minecraft:swords} cover the roster, the vanilla shield and
 *       datapack extensions without reading the (unsynced) data map;</li>
 *   <li>R-04: while a sword is the active guard equipment (no guardable offhand),
 *       right-clicking a usable block stays vanilla (no guard intent) per
 *       {@code sword_guard_requires_no_block_target}; an offhand shield overrides
 *       it and always guards (FR-11).</li>
 * </ul>
 */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class ClientGuardInputHandler {
    private static final int BUTTON_USE = 1;
    private static final int ACTION_PRESS = 1;

    private static boolean desireGuard;
    private static boolean sentState;
    private static int ticksSinceSend;

    @SubscribeEvent
    static void onMouseButton(InputEvent.MouseButton.Pre event) {
        if (event.getButton() != BUTTON_USE) {
            return;
        }
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null) {
            return;
        }
        if (event.getAction() == ACTION_PRESS) {
            desireGuard = true;
        } else if (event.getAction() == 0) {
            desireGuard = false;
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getOverlay() != null) {
            desireGuard = false;
            sentState = false;
            ticksSinceSend = 0;
            return;
        }

        boolean wantSend = desireGuard && plausiblyGuardable(player);
        // R-04 applies only while the SWORD is the would-be active guard equipment
        // (FR-11: an offhand shield has top priority — the sword's block-target rule
        // must never override the shield's guard, so a guardable offhand skips it).
        if (wantSend && desireGuard && Config.swordGuardRequiresNoBlockTarget()
                && player.getMainHandItem().is(ItemTags.SWORDS)
                && !player.getOffhandItem().is(ModTags.ITEMS_GUARDABLE)
                && lookingAtBlock(minecraft)) {
            wantSend = false; // R-04: block interaction wins over sword guarding
        }

        boolean stateChanged = wantSend != sentState;
        boolean heartbeatDue = wantSend && ++ticksSinceSend >= Config.stateHeartbeatTicks();
        if (stateChanged || heartbeatDue) {
            PacketDistributor.sendToServer(new GuardInputPayload(wantSend, player.tickCount));
            sentState = wantSend;
            ticksSinceSend = 0;
        }
    }

    /** True while the client believes its guard intent is active (bash trigger gate). */
    static boolean isGuardIntentSent() {
        return sentState;
    }

    /** Client-side plausibility gate: synced item tags only (server re-validates). */
    private static boolean plausiblyGuardable(LocalPlayer player) {
        // Swords only guard from the main hand (vanilla cannot raise an offhand sword).
        return player.getOffhandItem().is(ModTags.ITEMS_GUARDABLE)
                || player.getMainHandItem().is(ModTags.ITEMS_GUARDABLE)
                || player.getMainHandItem().is(ItemTags.SWORDS);
    }

    private static boolean lookingAtBlock(Minecraft minecraft) {
        return minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult;
    }

    private ClientGuardInputHandler() {}
}
