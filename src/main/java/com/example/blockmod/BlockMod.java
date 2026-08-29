package com.example.blockmod;

import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

import com.example.blockmod.probe.ProbeAttachments;
import com.example.blockmod.probe.ProbeModBus;
import com.example.blockmod.probe.ProbeRegistries;

@Mod(BlockMod.MODID)
public final class BlockMod {
    public static final String MODID = "blockmod";

    public BlockMod(IEventBus modEventBus) {
        BlockModLogger.info("MOD_CONSTRUCT", "phase", "mod-construction");

        // ===== TEMPORARY M0 probe registrations — remove at M1 (T-05..T-12) =====
        ProbeRegistries.registerAll(modEventBus);
        ProbeAttachments.ATTACHMENTS.register(modEventBus);
        modEventBus.addListener(ProbeModBus::onRegisterPayloadHandlers);
        // ===== end temporary probe =====
    }
}
