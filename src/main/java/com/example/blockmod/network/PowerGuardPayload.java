package com.example.blockmod.network;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import com.example.blockmod.BlockMod;

/**
 * C2S power guard intent (FR-16 / §5.13): the client reports the Left Alt key
 * state. The server validates the GREAT-shield requirement and the stamina
 * precondition before arming {@code powerGuarding}.
 */
public record PowerGuardPayload(
        boolean active) implements CustomPacketPayload {

    public static final Type<PowerGuardPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "power_guard"));

    public static final StreamCodec<ByteBuf, PowerGuardPayload> STREAM_CODEC =
            StreamCodec.composite(ByteBufCodecs.BOOL, PowerGuardPayload::active, PowerGuardPayload::new);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
