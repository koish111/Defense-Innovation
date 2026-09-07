package com.example.blockmod.mixin;

import com.example.blockmod.logic.MixinHooks;

import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FR-05 v2 — client half of the swing freeze. {@code LocalPlayer#swing} is the
 * only path that sends {@code ServerboundSwingPacket}; cancelling it at HEAD
 * kills both the local swing animation and the outgoing packet, so the stunned
 * player neither gestures nor tells the server about a swing. The mob /
 * other-players counterpart lives in {@code LivingEntityStunMixin}.
 */
@Mixin(LocalPlayer.class)
public abstract class LocalPlayerStunMixin {
    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;)V", at = @At("HEAD"), cancellable = true)
    private void blockparry$noSwingPacketWhileStunned(InteractionHand hand, CallbackInfo ci) {
        if (MixinHooks.isStunned((LocalPlayer) (Object) this)) {
            ci.cancel();
        }
    }
}
