package com.example.blockmod.logic;

import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.GuardProfile;
import com.example.blockmod.logic.GuardEquipmentResolver.GuardEquipment;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.item.enchantment.Enchantments;

/**
 * FR-10: guard durability cost. Only when the guard landed AND the blocked damage
 * reaches {@code min_damage_for_durability_loss}; cost is {@code floor(dmg) + 1};
 * swords (durability_loss=false) never pay. Unbreaking applies with the vanilla
 * probability inside {@code ItemStack#hurtAndBreak}. Parries never reach this
 * service (M4 keeps them cost-free).
 */
public final class DurabilityService {
    private DurabilityService() {}

    public static void consume(ServerPlayer player, GuardEquipment equipment, float blockedDamage) {
        GuardProfile profile = equipment.profile();
        if (!profile.durabilityLoss()) {
            return; // FR-10: swords never lose durability
        }
        if (blockedDamage < Config.minDamageForDurabilityLoss()) {
            return; // FR-10 acceptance 2: small hits are free
        }
        int amount = net.minecraft.util.Mth.floor(blockedDamage) + 1;
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        ItemStack stack = equipment.stack();
        stack.hurtAndBreak(amount, level, player,
                broken -> BlockModLogger.info("DURABILITY", "action", "broken",
                        "player", player.getGameProfile().getName(),
                        "item", broken.toString()));
        BlockModLogger.info("DURABILITY", "action", "consume", "player", player.getGameProfile().getName(),
                "amount", amount, "damage", blockedDamage,
                "damageLeft", stack.getDamageValue());
    }

    /** Unused placeholder removal guard — kept out of the hot path on purpose. */
    private static boolean hasUnbreaking(ItemStack stack, ServerPlayer player) {
        return EnchantmentHelper.getItemEnchantmentLevel(
                player.level().registryAccess()
                        .holderOrThrow(net.minecraft.core.registries.Registries.ENCHANTMENT)
                        .get(net.minecraft.resources.ResourceLocation.parse("minecraft:unbreaking"))
                        .value(), stack) > 0;
    }
}
