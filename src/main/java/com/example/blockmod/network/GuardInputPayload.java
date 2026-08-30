package com.example.blockmod.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.example.blockmod.BlockMod;

/**
 * C2S guard intent (FR-23 / §5.13): the client reports right-click hold state.
 * The client sends intent only; the server validates (rate limit, guardable
 * equipment) and owns the authoritative {@code guarding} state.
 */
public record GuardInputPayload(
        boolean guarding,
        int clientTick) implements CustomPacketPayload {

    public static final Type<GuardInputPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "guard_input"));

    public static final StreamCodec<ByteBuf, GuardInputPayload> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, GuardInputPayload::guarding,
            ByteBufCodecs.VAR_INT, GuardInputPayload::clientTick,
            GuardInputPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
