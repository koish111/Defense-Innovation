package com.example.blockmod.mixin;

import com.example.blockmod.logic.MixinHooks;

import net.minecraft.world.entity.player.Player;

import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * D-01 — the single approved Mixin of the MVP (designer approval 2026-08-30).
 *
 * <p>Vanilla gates eating behind {@code canEat = foodLevel < 20}, which makes the
 * FR-03 trigger (full-hunger food restores stamina) unreachable. When the
 * {@code food_restore_stamina} config is enabled, this opens the gate; the
 * stamina restore itself stays in {@code FoodStaminaHandler} (event side) and the
 * server re-evaluates this gate authoritatively inside {@code Item#use}.
 */
@Mixin(Player.class)
public abstract class PlayerCanEatMixin {
    @Inject(method = "canEat(Z)Z", at = @At("HEAD"), cancellable = true)
    private void blockparry$allowEatingAtFullHunger(boolean canAlwaysEat, CallbackInfoReturnable<Boolean> cir) {
        if (MixinHooks.allowEatingAtFullHunger()) {
            cir.setReturnValue(true);
        }
    }
}
