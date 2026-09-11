package com.example.blockmod.client;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;
import com.example.blockmod.network.StaminaSyncPayload;
import com.example.blockmod.registry.ModTags;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * First-person buckler raise animation (AGENTS.md §6.7, 2026-09-11 scaffold):
 * a purely visual flourish played once when the server confirms a guard entry
 * carrying a parry window — <strong>bucklers only</strong> (2026-09-11 ruling:
 * medium shields keep the vanilla guard pose and animate only on bash,
 * great shields are always vanilla). Never plays on heartbeats — the trigger is
 * the <em>transition</em> from "no parry window" to "parry window" in the
 * stamina sync, not the level. After the flourish the vanilla animation takes
 * over for the rest of the guard.
 *
 * <p>State hygiene: the visual timer is cleared on guard exit, depletion, stun
 * (the stun force-drop arrives as a guard exit) and equipment replacement (the
 * server force-drops the guard), plus the client lifecycle events below.
 * Never touches the server's parry window.
 *
 * <p>Sync hook: {@link ClientGuardState#acceptStaminaSync} calls
 * {@link #acceptStaminaSync} <strong>first</strong>, so the
 * {@code ClientGuardState} getters still return the previous packet's state
 * inside this handler — that ordering is what makes the transition detection
 * below correct.
 */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class BucklerParryAnimation {
    private static int remainingTicks; // animation countdown; 0 = idle

    /**
     * Called first inside {@link ClientGuardState#acceptStaminaSync} — the
     * {@code ClientGuardState} getters here still see the PREVIOUS sync.
     */
    public static void acceptStaminaSync(StaminaSyncPayload payload) {
        boolean windowNow = payload.guarding() && payload.parryRemainTicks() > 0;
        boolean windowBefore = ClientGuardState.isGuarding() && ClientGuardState.parryRemainTicks() > 0;
        if (windowNow && !windowBefore && !payload.depleted()) {
            remainingTicks = Config.bucklerAnimationTicks();
        }
        // guard exit covers depletion, the stun force-drop and equipment
        // replacement (the server force-drops the guard in all three cases)
        if (!payload.guarding() || payload.depleted()) {
            remainingTicks = 0;
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

    /** Respawn / dimension change: the guard mirror is rebuilt from the next sync. */
    @SubscribeEvent
    static void onLoggingIn(ClientPlayerNetworkEvent.LoggingIn event) {
        remainingTicks = 0;
    }

    @SubscribeEvent
    static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        remainingTicks = 0;
    }

    @SubscribeEvent
    static void onClone(ClientPlayerNetworkEvent.Clone event) {
        remainingTicks = 0;
    }

    @SubscribeEvent
    static void onRenderHand(RenderHandEvent event) {
        int duration = Config.bucklerAnimationTicks();
        if (remainingTicks <= 0 || duration <= 0) {
            return;
        }
        float partialTick = Math.clamp(event.getPartialTick(), 0.0f, 1.0f);
        float progress = Math.clamp((duration - remainingTicks + partialTick) / duration, 0.0f, 1.0f);
        float amplitude = (float) Math.sin(Math.PI * progress); // raise, then settle back
        GuardArmTransforms.renderWithTransform(event, ModTags.ITEMS_BUCKLERS,
                Config.bucklerAnimationLift(), Config.bucklerAnimationForward(),
                Config.bucklerAnimationPitch(), amplitude);
    }

    private BucklerParryAnimation() {}
}
