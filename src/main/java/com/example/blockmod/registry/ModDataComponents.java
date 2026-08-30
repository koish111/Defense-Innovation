package com.example.blockmod.registry;

import com.example.blockmod.BlockMod;
import com.example.blockmod.data.GuardProfile;

import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.BuiltInRegistries;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Data component registry entries (Spec §13.1.2). */
public final class ModDataComponents {
    public static final DeferredRegister<DataComponentType<?>> DATA_COMPONENTS =
            DeferredRegister.create(BuiltInRegistries.DATA_COMPONENT_TYPE, BlockMod.MODID);

    public static final DeferredHolder<DataComponentType<?>, DataComponentType<GuardProfile>> GUARD_PROFILE =
            DATA_COMPONENTS.register("guard_profile", () -> DataComponentType.<GuardProfile>builder()
                    .persistent(GuardProfile.CODEC)
                    .networkSynchronized(GuardProfile.STREAM_CODEC)
                    .build());

    private ModDataComponents() {}
}
