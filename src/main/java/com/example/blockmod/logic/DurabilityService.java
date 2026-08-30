package com.example.blockmod.logic;

import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.GuardProfile;
import com.example.blockmod.logic.GuardEquipmentResolver.GuardEquipment;
import com.example.blockmod.network.SyncThrottler;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.item.ItemStack;

/**
 * FR-10: guard durability cost. Only when the guard landed AND the blocked damage
 * reaches {@code min_damage_for_durability_loss}; cost is {@code floor(dmg) + 1};
 * swords (durability_loss=false) never pay. Unbreaking applies with the vanilla
 * probability inside {@code ItemStack#hurtAndBreak} (processDurabilityChange);
 * a broken shield walks the vanilla break flow (shrink + on-break callback) and
 * E-08 force-exits the guard state through the callback.
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
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        int amount = Mth.floor(blockedDamage) + 1;
        ItemStack stack = equipment.stack();
        stack.hurtAndBreak(amount, level, player, broken -> {
            // E-08: the shield is gone this tick — force-exit the guard state.
            var guardState = player.getData(com.example.blockmod.registry.ModAttachments.GUARD_STATE.get());
            guardState.setGuarding(false);
            MovementService.remove(player, guardState);
            SyncThrottler.forceSync(player);
            BlockModLogger.info("DURABILITY", "action", "shield_broken", "player", player.getGameProfile().getName());
        });
        BlockModLogger.info("DURABILITY", "action", "consume", "player", player.getGameProfile().getName(),
                "amount", amount, "blockedDamage", blockedDamage, "damageLeft", stack.getDamageValue());
    }
}
