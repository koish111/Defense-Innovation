package com.example.blockmod.network;

import java.util.UUID;

import com.example.blockmod.BlockMod;

import net.minecraft.core.UUIDUtil;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;

/** S2C visual snapshots for both hands; an empty stack lowers that hand. */
public record GuardPoseSyncPayload(UUID playerId, ItemStack mainHand, ItemStack offHand,
        boolean powerGuarding) implements CustomPacketPayload {
    public GuardPoseSyncPayload {
        mainHand = mainHand.copy();
        offHand = offHand.copy();
    }

    public static final Type<GuardPoseSyncPayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "guard_pose_sync"));

    public static final StreamCodec<RegistryFriendlyByteBuf, GuardPoseSyncPayload> STREAM_CODEC = StreamCodec.composite(
            UUIDUtil.STREAM_CODEC, GuardPoseSyncPayload::playerId,
            ItemStack.OPTIONAL_STREAM_CODEC, GuardPoseSyncPayload::mainHand,
            ItemStack.OPTIONAL_STREAM_CODEC, GuardPoseSyncPayload::offHand,
            ByteBufCodecs.BOOL, GuardPoseSyncPayload::powerGuarding,
            GuardPoseSyncPayload::new);

    public ItemStack stack(InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND ? mainHand : offHand;
    }

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
