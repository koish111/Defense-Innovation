package com.example.blockmod.item;

import java.util.List;

import com.example.blockmod.data.GuardProfile;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.registry.ModDataComponents;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;

import org.jetbrains.annotations.Nullable;

/**
 * Base class for the roster's guard equipment. All statistics live in the
 * {@code blockmod:guard_profile} component on the stack; the class only adds the
 * FR-19 tooltip lines (text only, no client-only classes, safe on both sides).
 */
public class GuardShieldItem extends Item {
    public GuardShieldItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, List<Component> tooltip, TooltipFlag flag) {
        @Nullable GuardProfile profile = stack.get(ModDataComponents.GUARD_PROFILE.get());
        if (profile == null) {
            return;
        }
        int percent = Math.round(profile.guardStrength() * 100.0f);
        tooltip.add(Component.translatable("tooltip.blockmod.guard_strength", percent)
                .withStyle(tierColor(profile.guardStrength())));
        if (profile.type() == ShieldType.GREAT && profile.powerGuardBonus() > 0.0f) {
            int bonusPercent = Math.round(profile.powerGuardBonus() * 100.0f);
            tooltip.add(Component.translatable("tooltip.blockmod.power_guard", bonusPercent)
                    .withStyle(ChatFormatting.GOLD));
        }
        super.appendHoverText(stack, context, tooltip, flag);
    }

    /** FR-19 colour tiers: < 0.3 gray, 0.3..0.6 white, > 0.6 gold. */
    private static ChatFormatting tierColor(float gb) {
        if (gb < 0.3f) {
            return ChatFormatting.GRAY;
        }
        if (gb <= 0.6f) {
            return ChatFormatting.WHITE;
        }
        return ChatFormatting.GOLD;
    }
}
