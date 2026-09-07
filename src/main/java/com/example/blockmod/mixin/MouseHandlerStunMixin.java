package com.example.blockmod.mixin;

import com.example.blockmod.logic.MixinHooks;

import net.minecraft.client.Minecraft;
import net.minecraft.client.MouseHandler;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * FR-05 v2 — freezes the local camera while stunned. {@code turnPlayer} is the
 * private mouse-handler method every mouse-grabbed rotation flows through
 * (sensitivity scaling, smooth camera, the final {@code player.turn}); saving
 * it at HEAD pins the view without touching mouse state, so screens (chat,
 * inventory) and non-rotating keybinds (F5) keep working.
 */
@Mixin(MouseHandler.class)
public abstract class MouseHandlerStunMixin {
    @Inject(method = "turnPlayer(D)V", at = @At("HEAD"), cancellable = true)
    private void blockparry$noCameraRotationWhileStunned(double movementTime, CallbackInfo ci) {
        if (MixinHooks.isStunned(Minecraft.getInstance().player)) {
            ci.cancel();
        }
    }
}
