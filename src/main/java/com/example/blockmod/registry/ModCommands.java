package com.example.blockmod.registry;

import com.example.blockmod.BlockMod;
import com.example.blockmod.logic.StaminaService;
import com.example.blockmod.network.SyncThrottler;
import com.example.blockmod.state.StaminaData;

import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import com.mojang.brigadier.arguments.FloatArgumentType;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

/**
 * FR-25 (subset): {@code /blockparry stamina get|set|fill} and {@code /blockparry
 * deplete}. Pulled forward from T-41 because the M2 checkpoint (G2) and the §9.4
 * stamina checks need them for manual verification; {@code debug} and {@code reload}
 * still land in M6.
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class ModCommands {
    @SubscribeEvent
    static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("blockparry")
                .then(Commands.literal("stamina")
                        .then(Commands.literal("get")
                                .executes(ctx -> get(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> get(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                        .then(Commands.literal("set")
                                .requires(src -> src.hasPermission(2))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .then(Commands.argument("value", FloatArgumentType.floatArg())
                                                .executes(ctx -> set(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        FloatArgumentType.getFloat(ctx, "value"))))))
                        .then(Commands.literal("fill")
                                .executes(ctx -> fill(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> fill(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("deplete")
                        .requires(src -> src.hasPermission(2))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> deplete(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static int get(CommandSourceStack source, ServerPlayer target) {
        StaminaData stamina = target.getData(ModAttachments.STAMINA.get());
        source.sendSuccess(() -> Component.literal(
                String.format("[BlockParry] %s stamina = %.2f / %.2f%s", target.getGameProfile().getName(),
                        stamina.stamina(), net.minecraft.world.entity.player.Player.MAX_ARMOR_STRENGTH * 0
                                + staminaMaxFor(target),
                        stamina.isDepleted() ? " (depleted)" : "")),
                false);
        return (int) (stamina.stamina() * 10);
    }

    private static float staminaMaxFor(ServerPlayer target) {
        return com.example.blockmod.config.Config.maxStamina();
    }

    private static int set(CommandSourceStack source, ServerPlayer target, float value) {
        StaminaService.setStamina(target, value);
        source.sendSuccess(() -> Component.literal(String.format(
                "[BlockParry] set %s stamina = %.2f", target.getGameProfile().getName(), value)), true);
        return 1;
    }

    private static int fill(CommandSourceStack source, ServerPlayer target) {
        float max = com.example.blockmod.config.Config.maxStamina();
        StaminaService.setStamina(target, max);
        source.sendSuccess(() -> Component.literal(String.format(
                "[BlockParry] filled %s stamina to %.2f", target.getGameProfile().getName(), max)), true);
        return 1;
    }

    private static int deplete(CommandSourceStack source, ServerPlayer target) {
        // §9.4 #29: deplete must behave exactly like being hit to zero — set to 0.0,
        // which is depleted by the ADR-18 rule (stamina <= 0) and recovers at 8/s.
        StaminaService.setStamina(target, 0.0f);
        SyncThrottler.forceSync(target);
        source.sendSuccess(() -> Component.literal(String.format(
                "[BlockParry] depleted %s", target.getGameProfile().getName())), true);
        return 1;
    }

    private ModCommands() {}
}
