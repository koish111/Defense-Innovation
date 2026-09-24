package com.example.blockmod.client;

import com.mojang.serialization.MapCodec;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.item.properties.conditional.ConditionalItemModelProperty;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

/** Binary buckler raise condition; the final variant preserves the original displays. */
public record BucklerGuardRaiseProperty() implements ConditionalItemModelProperty {
    public static final MapCodec<BucklerGuardRaiseProperty> MAP_CODEC = MapCodec.unit(new BucklerGuardRaiseProperty());

    @Override
    public boolean get(ItemStack stack, @Nullable ClientLevel level, @Nullable LivingEntity entity,
            int seed, ItemDisplayContext displayContext) {
        return com.example.blockmod.config.Config.bucklerGuardRaiseEnabled() && entity instanceof Player player
                && GuardPoseRenderer.isConfirmedGuardShield(player, stack)
                && com.example.blockmod.logic.GuardEquipmentResolver.typeOf(stack, ClientGuardState.swordBlocking())
                    == com.example.blockmod.data.ShieldType.BUCKLER;
    }

    @Override
    public MapCodec<BucklerGuardRaiseProperty> type() { return MAP_CODEC; }
}
