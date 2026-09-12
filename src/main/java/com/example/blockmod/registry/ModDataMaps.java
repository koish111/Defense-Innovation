package com.example.blockmod.registry;

import com.example.blockmod.BlockMod;
import com.example.blockmod.data.GuardProfile;

import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.datamaps.RegisterDataMapTypesEvent;
import net.neoforged.neoforge.registries.datamaps.DataMapType;

/**
 * Registry data maps (Spec §13.1.2). The {@code blockmod:guard_profile} item data
 * map lets datapacks assign guard statistics to vanilla items — FR-18/T-11 uses it
 * to classify {@code minecraft:shield} as a medium shield without touching vanilla
 * code. Removing the datapack file returns the vanilla shield to "unguardable".
 *
 * <p>Values are synced so client guard input and poses use the same equipment
 * classification as the authoritative server, including datapack extensions.
 */
public final class ModDataMaps {
    public static final DataMapType<Item, GuardProfile> GUARD_PROFILE = DataMapType.builder(
            ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "guard_profile"),
            Registries.ITEM,
            GuardProfile.CODEC)
            .synced(GuardProfile.CODEC, true)
            .build();

    /** Registers the data map type on the mod event bus (RegisterDataMapTypesEvent). */
    public static void onRegisterDataMapTypes(RegisterDataMapTypesEvent event) {
        event.register(GUARD_PROFILE);
    }

    private ModDataMaps() {}
}
