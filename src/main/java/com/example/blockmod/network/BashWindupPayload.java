package com.example.blockmod.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.example.blockmod.BlockMod;

/**
 * S2C bash windup cue: the server confirmed a shield-bash attempt and armed the
 * windup; {@code durationTicks} is the server's {@code bash_windup_ticks} so the
 * client push animation spans exactly the authoritative windup. Sent once per
 * successful bash attempt (rejected attempts send nothing).
 */
public record BashWindupPayload(int durationTicks) implements CustomPacketPayload {

    public static final Type<BashWindupPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "bash_windup"));

    public static final StreamCodec<ByteBuf, BashWindupPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, BashWindupPayload::durationTicks,
            BashWindupPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
