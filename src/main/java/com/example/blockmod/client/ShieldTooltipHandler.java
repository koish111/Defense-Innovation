package com.example.blockmod.client;

import com.example.blockmod.BlockMod;
import com.example.blockmod.item.GuardShieldItem;

import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemTooltipEvent;

/**
 * Renders guard statistics for third-party shields that carry a
 * {@code blockmod:guard_profile} component (e.g. {@code minecraft:shield} via the
 * item data map). Guard items handle their own tooltip in
 * {@link GuardShieldItem#appendHoverText}; this covers the rest. Client-side only.
 */
@EventBusSubscriber(modid = BlockMod.MODID, value = Dist.CLIENT)
public final class ShieldTooltipHandler {
    @SubscribeEvent
    static void onItemTooltip(ItemTooltipEvent event) {
        if (event.getItemStack().getItem() instanceof GuardShieldItem) {
            return;
        }
        GuardShieldItem.appendGuardTooltip(event.getItemStack(), event.getToolTip());
    }

    private ShieldTooltipHandler() {}
}
