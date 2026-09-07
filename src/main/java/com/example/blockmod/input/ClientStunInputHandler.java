package com.example.blockmod.input;

import com.example.blockmod.BlockMod;
import com.example.blockmod.registry.ModEffects;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.Input;
import net.minecraft.client.player.LocalPlayer;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * Client-side half of the stun lockdown (FR-05).
 *
 * <p>The behaviour freeze itself lives in the stun mixins:
 * {@code LivingEntityStunMixin} forces the isImmobile branch of {@code aiStep},
 * so motion impulses are zeroed (and never copied from the input) before
 * {@code travel} on both sides; {@code MouseHandlerStunMixin} pins the camera;
 * {@code LocalPlayerStunMixin} kills the swing. This handler covers what reads
 * the input object <em>directly</em> instead of the aiStep impulses — the
 * elytra start, creative flight toggle and riding-jump checks inside
 * {@code LocalPlayer#aiStep} — by zeroing the input at its source, which also
 * removes the jump impulse and the sprint state at the same time.
 *
 * <p>Click-to-act (attack / use / pick) is consumed here before any interaction
 * packet leaves the client, swing included. A hacked client bypasses every
 * client-side gate (vanilla's trust model); the server-authoritative refusals
 * for damage and interaction stay in {@code StunHandler}.
 */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class ClientStunInputHandler {
    @SubscribeEvent
    static void onMovementInput(MovementInputUpdateEvent event) {
        if (!event.getEntity().hasEffect(ModEffects.STUN)) {
            return;
        }
        Input input = event.getInput();
        input.forwardImpulse = 0.0F;
        input.leftImpulse = 0.0F;
        input.jumping = false;
        // Raw key booleans: LocalPlayer.rideTick feeds these straight into the
        // vehicle (boat paddle input), bypassing the aiStep impulses — zeroing
        // the impulses alone would leave a stunned rider steering.
        input.up = false;
        input.down = false;
        input.left = false;
        input.right = false;
    }

    /**
     * Behaviour-packet cut-off: attack / use / pick clicks are consumed while
     * stunned. NeoForge fires this from {@code ClientHooks#onClickInput} at the
     * head of {@code Minecraft#startAttack} / {@code #startUseItem}, so
     * cancellation stops the action before the client can even decide to swing
     * — and {@code setSwingHand(false)} removes the swing that would otherwise
     * follow the click.
     */
    @SubscribeEvent
    static void onClickInput(InputEvent.InteractionKeyMappingTriggered event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player != null && player.hasEffect(ModEffects.STUN)) {
            event.setCanceled(true);
            event.setSwingHand(false);
        }
    }

    private ClientStunInputHandler() {}
}
