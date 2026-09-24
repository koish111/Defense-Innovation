package com.example.blockmod.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** 26.1 client-item condition replacing the old blocking model predicate. */
public record ShieldBlockPoseProperty() implements ConditionalItemModelProperty {
    public static final MapCodec<ShieldBlockPoseProperty> MAP_CODEC = MapCodec.unit(new ShieldBlockPoseProperty());

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity,
            int seed, ItemDisplayContext displayContext) {
        if (GuardArmTransforms.replicatingFirstPerson()) return false;
        if (entity instanceof Player player && GuardPoseRenderer.isConfirmedGuardShield(player, stack)) return true;
        return entity != null && entity.isUsingItem() && entity.getUseItem() == stack;
    }

    @Override
    public MapCodec<ShieldBlockPoseProperty> type() { return MAP_CODEC; }
}
