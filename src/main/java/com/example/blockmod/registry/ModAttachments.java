package com.example.blockmod.registry;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;
import com.example.blockmod.state.GuardStateData;
import com.example.blockmod.state.StaminaData;

import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Player attachments (Spec §13.1.2). {@code stamina} is serialized and survives
 * save/load; {@code guard_state} is transient by design (a relog resets windows,
 * cooldowns and the depletion edge — Spec §4.3.3).
 *
 * <p>Both attachments are server-side only. The default stamina value comes from
 * the server config, which is always loaded before a player can be attached on
 * the logical server; client code never reads these (it gets S2C mirrors).
 */
public final class ModAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENTS =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, BlockMod.MODID);

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<StaminaData>> STAMINA =
            ATTACHMENTS.register("stamina", () -> AttachmentType
                    .builder(() -> new StaminaData(Config.maxStamina(), 0L))
                    .serialize(StaminaData.CODEC)
                    .build());

    public static final DeferredHolder<AttachmentType<?>, AttachmentType<GuardStateData>> GUARD_STATE =
            ATTACHMENTS.register("guard_state", () -> AttachmentType
                    .builder(GuardStateData::new)
                    .build());

    private ModAttachments() {}
}
