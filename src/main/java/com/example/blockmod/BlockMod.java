package com.example.blockmod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;

import com.example.blockmod.config.Config;
import com.example.blockmod.registry.ModAttachments;
import com.example.blockmod.registry.ModCreativeTabs;
import com.example.blockmod.registry.ModDataComponents;
import com.example.blockmod.registry.ModDataMaps;
import com.example.blockmod.registry.ModEffects;
import com.example.blockmod.registry.ModItems;

@Mod(BlockMod.MODID)
public final class BlockMod {
    public static final String MODID = "blockmod";

    public BlockMod(IEventBus modEventBus, ModContainer modContainer) {
        BlockModLogger.info("MOD_CONSTRUCT", "phase", "mod-construction");

        Config.register(modContainer);
        modEventBus.addListener(Config::onConfigLoad);

        ModDataComponents.DATA_COMPONENTS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModAttachments.ATTACHMENTS.register(modEventBus);
        ModEffects.EFFECTS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
        modEventBus.addListener(ModDataMaps::onRegisterDataMapTypes);
    }
}
