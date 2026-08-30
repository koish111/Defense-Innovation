package com.example.blockmod.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.example.blockmod.BlockMod;

/**
 * S2C stamina mirror update (FR-23). {@code stamina} is the raw server value which
 * may be negative; the client interpolates its display value toward it (ADR-04).
 */
public record StaminaSyncPayload(
        float stamina,
        float max,
        boolean depleted,
        boolean guarding,
        int parryRemainTicks) implements CustomPacketPayload {

    public static final Type<StaminaSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "stamina_sync"));

    public static final StreamCodec<ByteBuf, StaminaSyncPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, StaminaSyncPayload::stamina,
            ByteBufCodecs.FLOAT, StaminaSyncPayload::max,
            ByteBufCodecs.BOOL, StaminaSyncPayload::depleted,
            ByteBufCodecs.BOOL, StaminaSyncPayload::guarding,
            ByteBufCodecs.VAR_INT, StaminaSyncPayload::parryRemainTicks,
            StaminaSyncPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
