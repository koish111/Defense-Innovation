package com.example.blockmod.data;

import java.util.HashSet;
import java.util.Set;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.resources.ResourceLocation;

/** Immutable item-selection snapshot shared by the server and its client mirrors. */
public record SwordBlockingConfig(
        boolean includeSwordsTag,
        Set<ResourceLocation> whitelist,
        Set<ResourceLocation> blacklist) {
    public static final SwordBlockingConfig DEFAULT = new SwordBlockingConfig(true, Set.of(), Set.of());

    private static final StreamCodec<ByteBuf, Set<ResourceLocation>> ITEM_IDS =
            ByteBufCodecs.collection(HashSet::new, ResourceLocation.STREAM_CODEC);
    public static final StreamCodec<ByteBuf, SwordBlockingConfig> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.BOOL, SwordBlockingConfig::includeSwordsTag,
            ITEM_IDS, SwordBlockingConfig::whitelist,
            ITEM_IDS, SwordBlockingConfig::blacklist,
            SwordBlockingConfig::new);

    public SwordBlockingConfig {
        whitelist = Set.copyOf(whitelist);
        blacklist = Set.copyOf(blacklist);
    }
}
