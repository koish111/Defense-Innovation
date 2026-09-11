package com.example.blockmod.client;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;
import com.example.blockmod.registry.ModTags;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderHandEvent;

/**
 * First-person medium-shield bash push (AGENTS.md §6.7, 2026-09-11 ruling): a
 * forward-thrust flourish played once per server-confirmed bash attempt — the
 * server sends {@code BashWindupPayload} carrying its windup duration when it
 * arms the windup, so the push spans exactly the authoritative window.
 * Rejected attempts (cooldown, stamina, stun, non-medium) send nothing and
 * play nothing. The push renders <strong>on top of the vanilla blocking
 * pose</strong> (see {@link GuardArmTransforms}) and settles back onto the
 * steady {@link ShieldGuardPose} with no pop. The great shield (which cannot
 * bash) is never animated — the render path filters on the medium-shield tag.
 *
 * <p>State hygiene mirrors {@link BucklerParryAnimation}: reset on login,
 * logout, and player clone.
 */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class ShieldBashAnimation {
    private static int remainingTicks; // animation countdown; 0 = idle
    private static int durationTicks;  // server windup length carried by the payload

    /** Called from the payload handler when the server confirms a bash windup. */
    public static void acceptWindup(int serverDurationTicks) {
        if (serverDurationTicks > 0) {
            durationTicks = serverDurationTicks;
            remainingTicks = serverDurationTicks;
        }
    }

    @SubscribeEvent
    static void onClientTick(ClientTickEvent.Post event) {
        if (remainingTicks > 0) {
            remainingTicks--;
        }
    }

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

    @SubscribeEvent(priority = EventPriority.HIGH)
    static void onRenderHand(RenderHandEvent event) {
        if (remainingTicks <= 0 || durationTicks <= 0) {
            return;
        }
        float partialTick = Math.clamp(event.getPartialTick(), 0.0f, 1.0f);
        float progress = Math.clamp((durationTicks - remainingTicks + partialTick) / durationTicks, 0.0f, 1.0f);
        float amplitude = (float) Math.sin(Math.PI * progress); // thrust, then pull back onto the blocking pose
        GuardArmTransforms.renderGuardPose(event, ModTags.ITEMS_MEDIUM_SHIELDS,
                Config.bashAnimationLift(), Config.bashAnimationForward(),
                Config.bashAnimationPitch(), amplitude);
    }

    private ShieldBashAnimation() {}
}
