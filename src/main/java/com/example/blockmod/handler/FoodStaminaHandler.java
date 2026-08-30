package com.example.blockmod.handler;

import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.logic.StaminaService;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.food.FoodProperties;
import net.minecraft.core.component.DataComponents;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.living.LivingEntityUseItemEvent;

/**
 * FR-03: finishing food on a full hunger bar restores stamina equal to the food's
 * nutrition. The fullness check MUST use the food level captured in
 * {@code UseItemEvent.Start} — by the time {@code .Finish} fires, the nutrition is
 * already applied and foodLevel would always read full (M0-verified timing). The
 * nutrition is captured at Start too, because the Finish event carries the RESULT
 * stack, which may already be empty. {@code Finish} only fires on completion, so
 * interrupted eating never restores.
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class FoodStaminaHandler {
    private record CapturedFood(int foodLevelAtStart, int nutrition) {}

    /** Per-use capture keyed by player; WeakHashMap stays bounded by live players. */
    private static final Map<UUID, CapturedFood> CAPTURED = new WeakHashMap<>();

    @SubscribeEvent
    static void onUseItemStart(LivingEntityUseItemEvent.Start event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Config.foodRestoreStamina()) {
            return;
        }
        FoodProperties food = event.getItem().get(DataComponents.FOOD);
        if (food == null || food.nutrition() <= 0) {
            CAPTURED.remove(player.getUUID());
            return;
        }
        CAPTURED.put(player.getUUID(), new CapturedFood(player.getFoodData().getFoodLevel(), food.nutrition()));
    }

    @SubscribeEvent
    static void onUseItemFinish(LivingEntityUseItemEvent.Finish event) {
        if (!(event.getEntity() instanceof ServerPlayer player) || !Config.foodRestoreStamina()) {
            return;
        }
        CapturedFood captured = CAPTURED.remove(player.getUUID());
        if (captured == null || captured.foodLevelAtStart() < 20) {
            return; // hunger was not full when the eating started
        }
        StaminaService.addStamina(player, captured.nutrition());
        BlockModLogger.info("FOOD_STAMINA", "player", player.getGameProfile().getName(),
                "nutrition", captured.nutrition(), "foodAtStart", captured.foodLevelAtStart());
    }

    private FoodStaminaHandler() {}
}
