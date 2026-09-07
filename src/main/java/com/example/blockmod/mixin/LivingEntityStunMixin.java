package com.example.blockmod.mixin;

import com.example.blockmod.logic.MixinHooks;

import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.llamalad7.mixinextras.injector.ModifyExpressionValue;

/**
 * FR-05 v2 — the behaviour freeze itself, side-agnostic (server + client).
 *
 * <ul>
 *   <li>{@code aiStep}'s single {@code isImmobile()} call is forced true while
 *       stunned. That branch zeroes {@code jumping}/{@code xxa}/{@code zza}
 *       <em>and</em> skips {@code serverAiStep()} — no goals, no brain, no look
 *       control, no navigation, and for {@code LocalPlayer} not even the
 *       input-to-impulse copy. The {@code travel()} right after it keeps
 *       running, so gravity, friction and external forces (knockback,
 *       explosions) still act on the frozen body: a true freeze, not a motion
 *       filter. This is exactly how vanilla immobilises sleeping entities, so
 *       the code path is battle-tested for players and mobs alike.</li>
 *   <li>{@link LivingEntity#swing(InteractionHand, boolean)} is cancelled, so a
 *       stunned entity starts no swing animation and broadcasts no
 *       {@code ClientboundAnimatePacket} (mob weapon swings, server-side swings
 *       other players would see).</li>
 * </ul>
 *
 * <p>Coverage: every vanilla {@code aiStep} override funnels through
 * {@code super.aiStep()} — surveyed 2026-09-07, sole exception
 * {@code EnderDragon} (fully custom flight; bosses are out of the parry-stun
 * scope). Physics forces always pass through; only self-initiated behaviour
 * freezes.
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityStunMixin {
    @ModifyExpressionValue(
        method = "aiStep()V",
        at = @At(
            value = "INVOKE",
            target = "Lnet/minecraft/world/entity/LivingEntity;isImmobile()Z"
        )
    )
    private boolean blockparry$stunIsImmobile(boolean original) {
        return original || MixinHooks.isStunned((LivingEntity) (Object) this);
    }

    @Inject(method = "swing(Lnet/minecraft/world/InteractionHand;Z)V", at = @At("HEAD"), cancellable = true)
    private void blockparry$noSwingWhileStunned(InteractionHand hand, boolean updateSelf, CallbackInfo ci) {
        if (MixinHooks.isStunned((LivingEntity) (Object) this)) {
            ci.cancel();
        }
    }
}
