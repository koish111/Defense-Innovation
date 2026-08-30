package com.example.blockmod.network;

import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

import com.example.blockmod.BlockMod;

import io.netty.buffer.ByteBuf;

/**
 * C2S shield bash trigger (FR-15 / §5.13): the client left-clicks while guarding
 * with a medium shield. Empty payload — all validation and numbers live server-side.
 */
public record ShieldBashPayload() implements CustomPacketPayload {
    public static final Type<ShieldBashPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "shield_bash"));

    public static final StreamCodec<ByteBuf, ShieldBashPayload> STREAM_CODEC =
            StreamCodec.unit(new ShieldBashPayload());

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
