package com.example.blockmod.registry;

import com.example.blockmod.BlockMod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Status effects (Spec §13.1.2). {@code blockmod:stun} is the ONLY effect the mod
 * registers — v2.0 removed {@code guard_broken}; depletion is derived from stamina
 * and must never be represented by a MobEffect (clearable by milk).
 */
public final class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, BlockMod.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> STUN =
            EFFECTS.register("stun", StunEffect::new);

    /** No tick logic: the lockdown lives in {@code StunHandler}'s event handlers. */
    private static final class StunEffect extends MobEffect {
        private StunEffect() {
            super(MobEffectCategory.HARMFUL, 0xFFD700);
        }
    }

    private ModEffects() {}
}
