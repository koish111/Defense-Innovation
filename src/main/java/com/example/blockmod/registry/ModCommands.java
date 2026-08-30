package com.example.blockmod.registry;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;
import com.example.blockmod.BlockModLogger;
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
                                .then(Commands.argument("value", FloatArgumentType.floatArg())
                                        .executes(ctx -> set(ctx.getSource(), ctx.getSource().getPlayerOrException(),
                                                FloatArgumentType.getFloat(ctx, "value")))
                                        .then(Commands.argument("player", EntityArgument.player())
                                                .executes(ctx -> set(ctx.getSource(),
                                                        EntityArgument.getPlayer(ctx, "player"),
                                                        FloatArgumentType.getFloat(ctx, "value"))))))
                        .then(Commands.literal("fill")
                                .executes(ctx -> fill(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                                .then(Commands.argument("player", EntityArgument.player())
                                        .executes(ctx -> fill(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))))
                .then(Commands.literal("deplete")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> deplete(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> deplete(ctx.getSource(), EntityArgument.getPlayer(ctx, "player")))))
                .then(Commands.literal("debug")
                        .requires(src -> src.hasPermission(2))
                        .executes(ctx -> debug(ctx.getSource(), ctx.getSource().getPlayerOrException()))
                        .then(Commands.argument("player", EntityArgument.player())
                                .executes(ctx -> debug(ctx.getSource(), EntityArgument.getPlayer(ctx, "player"))))));
    }

    private static int get(CommandSourceStack source, ServerPlayer target) {
        StaminaData stamina = target.getData(ModAttachments.STAMINA.get());
        float max = Config.maxStamina();
        source.sendSuccess(() -> Component.literal(String.format("[BlockParry] %s stamina = %.2f / %.2f%s",
                target.getGameProfile().getName(), stamina.stamina(), max,
                stamina.isDepleted() ? " (depleted)" : "")), false);
        return Math.round(stamina.stamina() * 10.0f);
    }

    private static int set(CommandSourceStack source, ServerPlayer target, float value) {
        StaminaService.setStamina(target, value);
        source.sendSuccess(() -> Component.literal(String.format(
                "[BlockParry] set %s stamina = %.2f", target.getGameProfile().getName(), value)), true);
        return 1;
    }

    private static int fill(CommandSourceStack source, ServerPlayer target) {
        float max = Config.maxStamina();
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

    /** FR-25 / T-41: prints the full server-side state snapshot for one player. */
    private static int debug(CommandSourceStack source, ServerPlayer target) {
        StaminaData stamina = target.getData(ModAttachments.STAMINA.get());
        com.example.blockmod.state.GuardStateData guard = target.getData(ModAttachments.GUARD_STATE.get());
        com.example.blockmod.logic.GuardEquipmentResolver.GuardEquipment equipment =
                com.example.blockmod.logic.GuardEquipmentResolver.resolve(target);
        long now = target.level().getGameTime();
        String[] snapshot = {
                "[BlockParry] debug snapshot for " + target.getGameProfile().getName() + " (tick " + now + ")",
                "  stamina = " + String.format("%.2f / %.2f", stamina.stamina(), Config.maxStamina())
                        + (stamina.isDepleted() ? "  [DEPLETED]" : ""),
                "  lastEventTick = " + stamina.lastEventTick(),
                "  guarding = " + guard.isGuarding() + "  hand = " + guard.guardHand(),
                "  parryWindowEnd = " + guard.parryWindowEndTick() + "  parryUsed = " + guard.isParryUsed()
                        + "  parryReadyTick = " + guard.parryReadyTick(),
                "  powerGuarding = " + guard.isPowerGuarding(),
                "  bashWindupEnd = " + guard.bashWindupEndTick() + "  bashReady = " + guard.bashReadyTick(),
                "  moveMalusId = " + guard.activeMoveMalusId(),
                "  equipment = " + (equipment == null ? "none" : equipment.stack() + " [" + equipment.profile() + "]"),
                "  moveSpeed = " + target.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MOVEMENT_SPEED).getValue(),
        };
        for (String line : snapshot) {
            String line0 = line;
            source.sendSuccess(() -> net.minecraft.network.chat.Component.literal(line0), false);
            BlockModLogger.info("DEBUG", "line", line0);
        }
        return 1;
    }

    private ModCommands() {}
}
