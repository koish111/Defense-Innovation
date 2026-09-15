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
        // Main thread (model bake reads the properties map): re-register the
        // vanilla "blocking" model property so guard shields block-pose, and
        // register the buckler guard-raise property for the raise variants.
        event.enqueueWork(ShieldBlockPoseProperty::register);
        event.enqueueWork(BucklerGuardRaiseProperty::register);
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyMappings.POWER_GUARD);
        event.register(ModKeyMappings.GUARD);
        event.register(ModKeyMappings.SHIELD_BASH);
        event.register(ModKeyMappings.PARRY);
    }
}
