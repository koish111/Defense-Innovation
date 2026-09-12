package com.example.blockmod.input;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;
import com.example.blockmod.client.ClientGuardState;
import com.example.blockmod.logic.GuardEquipmentResolver;
import com.example.blockmod.logic.GuardRules;
import com.example.blockmod.network.GuardInputPayload;
import com.example.blockmod.registry.ModEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
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
 *       tags, guard profiles and sword selection config are synced, and the
 *       shared equipment resolver covers both hands and datapack extensions;</li>
 *   <li>Native target interactions run before the guard item-use fallback.</li>
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
    private static boolean guardUseAccepted;
    private static boolean sentState;
    private static int ticksSinceSend;
    private static ItemStack sentGuardStack = ItemStack.EMPTY;

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
            guardUseAccepted = false;
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.getOverlay() != null) {
            ClientCombatInputHandler.updatePowerGuardIntent(player, false);
            desireGuard = false;
            sentState = false;
            ticksSinceSend = 0;
            sentGuardStack = ItemStack.EMPTY;
            return;
        }

        if (sentState && activeGuardStack(player) != sentGuardStack) {
            desireGuard = false;
            guardUseAccepted = false;
        }

        // FR-05: while stunned, guard intent is suppressed client-side — the server
        // re-validates and force-drops anyway (authoritative), this only avoids the
        // rejected-packet churn and the enter/exit flicker in the same tick window.
        boolean wantSend = desireGuard && guardUseAccepted && wantsGuard(minecraft, player);
        if (!wantSend) guardUseAccepted = false;

        boolean stateChanged = wantSend != sentState;
        boolean heartbeatDue = wantSend && ++ticksSinceSend >= Config.stateHeartbeatTicks();
        if (stateChanged || heartbeatDue) {
            PacketDistributor.sendToServer(new GuardInputPayload(wantSend, player.tickCount));
            sentState = wantSend;
            sentGuardStack = wantSend ? activeGuardStack(player) : ItemStack.EMPTY;
            ticksSinceSend = 0;
        }
        ClientCombatInputHandler.updatePowerGuardIntent(player, wantSend);
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
     *   <li>plausibility: synced equipment policy (server re-validates);</li>
     * </ul>
     */
    static boolean wantsGuard(Minecraft minecraft, LocalPlayer player) {
        if (minecraft.screen != null || !minecraft.isWindowActive() || player.hasEffect(ModEffects.STUN)) {
            return false;
        }
        return GuardEquipmentResolver.resolveSlot(player, ClientGuardState.swordBlocking()) != GuardRules.SLOT_NONE;
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
    @SubscribeEvent(priority = EventPriority.LOWEST)
    static void onInteractionKeyMapping(InputEvent.InteractionKeyMappingTriggered event) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        if (player == null || !desireGuard || !wantsGuard(minecraft, player)) {
            return;
        }
        if (guardUseAccepted) {
            event.setCanceled(true);
            event.setSwingHand(false);
            return;
        }
        if (!event.isUseItem() || !GuardEquipmentResolver.isGuardable(player.getItemInHand(event.getHand()), ClientGuardState.swordBlocking())
                || minecraft.gameMode == null || minecraft.level == null || player.isSpectator()) {
            return;
        }
        // Delegate the targeted interaction to vanilla exactly once. Its item-use fallback is
        // our guard intent; ItemCooldowns remain a visual overlay and cannot reject that fallback.
        boolean allowSwing = event.shouldSwingHand();
        event.setCanceled(true);
        event.setSwingHand(false);
        ItemStack stack = player.getItemInHand(event.getHand());
        if (!stack.isItemEnabled(minecraft.level.enabledFeatures())) return;
        guardUseAccepted = tryTargetInteraction(minecraft, player, event.getHand(), allowSwing);
    }

    /** Mirrors vanilla startUseItem's target-first ordering through public game-mode methods. */
    private static boolean tryTargetInteraction(Minecraft minecraft, LocalPlayer player,
            InteractionHand hand, boolean allowSwing) {
        HitResult target = minecraft.hitResult;
        if (target instanceof EntityHitResult hit) {
            var entity = hit.getEntity();
            if (!minecraft.level.getWorldBorder().isWithinBounds(entity.blockPosition())) return false;
            InteractionResult result = minecraft.gameMode.interactAt(player, entity, hit, hand);
            if (!result.consumesAction()) result = minecraft.gameMode.interact(player, entity, hand);
            if (result.consumesAction()) {
                if (result.shouldSwing() && allowSwing) player.swing(hand);
                return false;
            }
        } else if (target instanceof BlockHitResult hit && hit.getType() == HitResult.Type.BLOCK) {
            ItemStack stack = player.getItemInHand(hand);
            int count = stack.getCount();
            InteractionResult result = minecraft.gameMode.useItemOn(player, hand, hit);
            if (result.consumesAction()) {
                if (result.shouldSwing() && allowSwing) {
                    player.swing(hand);
                    if (!stack.isEmpty() && (stack.getCount() != count || minecraft.gameMode.hasInfiniteItems())) {
                        minecraft.gameRenderer.itemInHandRenderer.itemUsed(hand);
                    }
                }
                return false;
            }
            if (result == InteractionResult.FAIL) return false;
        }
        return true;
    }

    private static ItemStack activeGuardStack(LocalPlayer player) {
        return switch (GuardEquipmentResolver.resolveSlot(player, ClientGuardState.swordBlocking())) {
            case GuardRules.SLOT_MAINHAND -> player.getMainHandItem();
            case GuardRules.SLOT_OFFHAND -> player.getOffhandItem();
            default -> ItemStack.EMPTY;
        };
    }

    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) { resetInput(); }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) { resetInput(); }

    @SubscribeEvent
    static void onClone(ClientPlayerNetworkEvent.Clone event) { resetInput(); }

    private static void resetInput() {
        ClientCombatInputHandler.resetPowerGuardIntent();
        desireGuard = false;
        guardUseAccepted = false;
        sentState = false;
        ticksSinceSend = 0;
        sentGuardStack = ItemStack.EMPTY;
    }

    private ClientGuardInputHandler() {}
}
