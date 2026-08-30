package com.example.blockmod.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.example.blockmod.BlockMod;

/**
 * S2C push of the client-relevant config subset (FR-20/FR-23). Sent on config load
 * and on every hot reload so the HUD scale and interpolation rules follow the
 * server without a relog. The client never reads the server config directly.
 */
public record ConfigSyncPayload(
        float maxStamina,
        float regenRate,
        float depletedRegenRate,
        float regenDelaySeconds) implements CustomPacketPayload {

    public static final Type<ConfigSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "config_sync"));

    public static final StreamCodec<ByteBuf, ConfigSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, ConfigSyncPayload::maxStamina,
            ByteBufCodecs.FLOAT, ConfigSyncPayload::regenRate,
            ByteBufCodecs.FLOAT, ConfigSyncPayload::depletedRegenRate,
            ByteBufCodecs.FLOAT, ConfigSyncPayload::regenDelaySeconds,
            ConfigSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
