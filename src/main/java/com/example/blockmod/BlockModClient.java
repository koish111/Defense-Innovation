package com.example.blockmod;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

import com.example.blockmod.registry.ModKeyMappings;

@Mod(value = BlockMod.MODID, dist = Dist.CLIENT)
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public class BlockModClient {
    @SubscribeEvent
    static void onClientSetup(FMLClientSetupEvent event) {
        BlockModLogger.info("CLIENT_SETUP", "dist", "client");
    }

    @SubscribeEvent
    static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyMappings.POWER_GUARD);
    }
}
