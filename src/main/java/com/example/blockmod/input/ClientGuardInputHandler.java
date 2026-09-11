package com.example.blockmod.input;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;
import com.example.blockmod.network.GuardInputPayload;
import com.example.blockmod.registry.ModEffects;
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
 *   <li>2026-09-11 ruling (guard interaction lockout): while the guard intent
 *       is live, ALL vanilla interactions (attack / dig / use / interact) and
 *       their swing animations are suppressed client-side; the server
 *       re-cancels authoritatively.</li>
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

        // FR-05: while stunned, guard intent is suppressed client-side — the server
        // re-validates and force-drops anyway (authoritative), this only avoids the
        // rejected-packet churn and the enter/exit flicker in the same tick window.
        boolean wantSend = desireGuard && wantsGuard(minecraft, player);

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

    /**
     * The would-send-guard predicate (minus the raw mouse desire), shared with
     * the FR-24 interaction suppression so both agree tick-by-tick:
     * <ul>
     *   <li>FR-05: a stunned player holds no guard;</li>
     *   <li>plausibility: synced item tags only (server re-validates);</li>
     *   <li>R-04 applies only while the SWORD is the would-be active guard
     *       equipment (FR-11: an offhand shield has top priority — the sword's
     *       block-target rule must never override the shield's guard, so a
     *       guardable offhand skips it) — block interaction stays vanilla.</li>
     * </ul>
     */
    static boolean wantsGuard(Minecraft minecraft, LocalPlayer player) {
        if (player.hasEffect(ModEffects.STUN)) {
            return false;
        }
        if (!plausiblyGuardable(player)) {
            return false;
        }
        return !(Config.swordGuardRequiresNoBlockTarget()
                && player.getMainHandItem().is(ItemTags.SWORDS)
                && !player.getOffhandItem().is(ModTags.ITEMS_GUARDABLE)
                && minecraft.hitResult instanceof net.minecraft.world.phys.BlockHitResult);
    }

    /**
     * FR-24 (2026-09-11 ruling): while the guard intent is live, every vanilla
     * interaction is suppressed client-side — attack, dig start AND dig
     * continuation, block use, entity interact, item use. One event covers all:
     * startAttack, continueAttack and startUseItem all gate through
     * {@code ClientHooks.onClickInput}. {@code setSwingHand(false)} is mandatory:
     * all three vanilla call sites still swing (and spray dig particles) on a
     * cancelled event whenever {@code shouldSwingHand()} is left true. The
     * server re-cancels authoritatively (ServerGuardInputHandler).
     */
    @SubscribeEvent
    static void onInteractionKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !desireGuard || !wantsGuard(minecraft, player)) {
            return;
        }
        event.setCanceled(true);
        event.setSwingHand(false);
    }

    /** Client-side plausibility gate: synced item tags only (server re-validates). */
    private static boolean plausiblyGuardable(LocalPlayer player) {
        // Swords only guard from the main hand (vanilla cannot raise an offhand sword).
        return player.getOffhandItem().is(ModTags.ITEMS_GUARDABLE)
                || player.getMainHandItem().is(ModTags.ITEMS_GUARDABLE)
                || player.getMainHandItem().is(ItemTags.SWORDS);
    }

    private ClientGuardInputHandler() {}
}
