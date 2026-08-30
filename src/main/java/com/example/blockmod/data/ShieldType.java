package com.example.blockmod.data;

import com.mojang.serialization.Codec;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.util.StringRepresentable;

/**
 * Guard equipment classes (Spec §4.3.1). Decides the parry window, move-speed
 * penalty tier and the class-exclusive ability of a shield or sword.
 */
public enum ShieldType implements StringRepresentable {
    SWORD("sword"),
    BUCKLER("buckler"),
    MEDIUM("medium"),
    GREAT("great");

    public static final Codec<ShieldType> CODEC = StringRepresentable.fromValues(ShieldType::values);
    public static final StreamCodec<ByteBuf, ShieldType> STREAM_CODEC =
            ByteBufCodecs.idMapper(i -> values()[i], ShieldType::ordinal);

    private final String name;

    ShieldType(String name) {
        this.name = name;
    }

    @Override
    public String getSerializedName() {
        return name;
    }
}
