package com.example.blockmod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import com.example.blockmod.client.BucklerGuardRaiseProperty;
import com.example.blockmod.client.ShieldBlockPoseProperty;
import com.example.blockmod.registry.ModKeyMappings;

@Mod(value = BlockMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public class BlockModClient {
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        BlockModLogger.info("CLIENT_SETUP", "dist", "client");

    }

    @SubscribeEvent
    static void onRegisterItemConditions(net.neoforged.neoforge.client.event.RegisterConditionalItemModelPropertyEvent event) {
        event.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(BlockMod.MODID, "blocking"),
                ShieldBlockPoseProperty.MAP_CODEC);
        event.register(net.minecraft.resources.Identifier.fromNamespaceAndPath(BlockMod.MODID, "guard_raise"),
                BucklerGuardRaiseProperty.MAP_CODEC);
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.registerCategory(ModKeyMappings.CATEGORY);
        event.register(ModKeyMappings.POWER_GUARD);
        event.register(ModKeyMappings.GUARD);
        event.register(ModKeyMappings.SHIELD_BASH);
        event.register(ModKeyMappings.PARRY);
    }
}
