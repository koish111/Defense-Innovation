package com.example.blockmod.handler;

import java.util.Map;
import java.util.WeakHashMap;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;
import com.example.blockmod.logic.StaminaService;
import com.example.blockmod.registry.ModAttachments;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.Consumable;
import net.minecraft.core.component.DataComponents;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * FR-03: finishing food on a full hunger bar restores stamina equal to the food's
 * nutrition. The fullness check MUST read the food level captured in
 * {@code UseItemEvent.Start} — by the time {@code .Finish} fires, the nutrition is
 * already applied and foodLevel would always read full (M0-verified timing).
 * {@code Finish} only fires on completion, so interrupted eating never restores.
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class FoodStaminaHandler {
    /** foodLevel captured at Start, keyed by player; WeakHashMap keeps it bounded by live players. */
    private static final Map<UUID, Integer> FOOD_LEVEL_AT_START = new WeakHashMap<>();

    @SubscribeEvent
    static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Config.foodRestoreStamina()) {
            return;
        }
        ItemStack stack = event.getItem();
        if (!stack.has(DataComponents.CONSUMABLE)) {
            FOOD_LEVEL_AT_START.remove(player.getUUID());
            return;
        }
        FOOD_LEVEL_AT_START.put(player.getUUID(), player.getFoodData().getFoodLevel());
    }

    @SubscribeEvent
    static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Config.foodRestoreStamina()) {
            return;
        }
        Integer captured = FOOD_LEVEL_AT_START.remove(player.getUUID());
        if (captured == null || captured < 20) {
            return; // hunger was not full when eating started
        }
        ItemStack stack = player.getUseItem();
        Consumable consumable = stack.get(DataComponents.CONSUMABLE);
        if (consumable == null || consumable.consumeSeconds() <= 0.0f && !stack.has(DataComponents.FOOD)) {
            return;
        }
        net.minecraft.world.food.FoodProperties food = stack.get(DataComponents.FOOD);
        if (food == null || food.nutrition() <= 0) {
            return;
        }
        StaminaService.addStamina(player, food.nutrition());
        BlockModLogger.info("FOOD_STAMINA", "player", player.getGameProfile().getName(),
                "nutrition", food.nutrition(), "foodAtStart", captured);
    }

    private FoodStaminaHandler() {}
}
