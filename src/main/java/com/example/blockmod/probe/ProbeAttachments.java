package com.example.blockmod.probe;

import com.mojang.serialization.Codec;

import com.example.blockmod.BlockMod;

import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import net.neoforged.neoforge.attachment.AttachmentType;

import java.util.function.Supplier;

/**
 * TEMPORARY M0 probe (T-03) — remove at M1.
 * Verifies API-04: DeferredRegister over ATTACHMENT_TYPES + AttachmentType.builder(...).serialize(Codec).build().
 */
public final class ProbeAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BlockMod.MODID);

    public static final Supplier<AttachmentType<Integer>> PROBE_VALUE =
            ATTACHMENTS.register("probe_value", () -> AttachmentType.builder(() -> 0).serialize(Codec.INT).build());

    private ProbeAttachments() {}
}
