package com.example.blockmod.probe;

import com.mojang.serialization.Codec;

import com.example.blockmod.BlockMod;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.core.component.DataComponentType;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * TEMPORARY M0 probe (T-03) — remove at M1.
 * Verifies API-15: DeferredRegister.create over MOB_EFFECT / SOUND_EVENT / DATA_COMPONENT_TYPE.
 * Registration success at startup (no registry errors) is the pass condition.
 */
public final class ProbeRegistries {
    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(BuiltInRegistries.MOB_EFFECT, BlockMod.MODID);
    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, BlockMod.MODID);
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, BlockMod.MODID);

    public static final DeferredHolder<MobEffect, MobEffect> PROBE_EFFECT =
            EFFECTS.register("probe_effect", () -> new MobEffect(MobEffectCategory.NEUTRAL, 0x8844FF));
    public static final DeferredHolder<SoundEvent, SoundEvent> PROBE_SOUND =
            SOUNDS.register("probe_sound", () -> SoundEvent.createVariableRangeEvent(rl("probe_sound")));
    public static final DeferredHolder<DataComponentType<?>, DataComponentType<Integer>> PROBE_COMPONENT =
            COMPONENTS.register("probe_component", () -> DataComponentType.<Integer>builder().codec(Codec.INT).build());

    public static void registerAll(IEventBus modEventBus) {
        EFFECTS.register(modEventBus);
        SOUNDS.register(modEventBus);
        COMPONENTS.register(modEventBus);
    }

    private static ResourceLocation rl(String path) {
        return ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, path);
    }

    private ProbeRegistries() {}
}
