package com.example.blockmod.registry;

import com.example.blockmod.client.ClientGuardState;
import com.example.blockmod.input.ServerGuardInputHandler;
import com.example.blockmod.network.ConfigSyncPayload;
import com.example.blockmod.network.GuardInputPayload;
import com.example.blockmod.network.PowerGuardPayload;
import com.example.blockmod.network.ShieldBashPayload;
import com.example.blockmod.network.StaminaSyncPayload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network protocol registration (Spec §5.13): S2C mirror payloads, the C2S guard
 * intent, and the M5 combat intents (shield bash, power guard). Handlers
 * referencing client classes are method references whose targets only load when
 * a payload actually arrives, so the dedicated server never touches client classes.
 */
public final class ModPayloads {
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(StaminaSyncPayload.TYPE, StaminaSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientGuardState.acceptStaminaSync(payload));
        registrar.playToClient(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientGuardState.acceptConfigSync(payload));
        registrar.playToServer(GuardInputPayload.TYPE, GuardInputPayload.STREAM_CODEC,
                (payload, context) -> ServerGuardInputHandler.handle(
                        (net.minecraft.server.level.ServerPlayer) context.player(), payload));
        registrar.playToServer(ShieldBashPayload.TYPE, ShieldBashPayload.STREAM_CODEC,
                (payload, context) -> {
                    net.minecraft.server.level.ServerPlayer player =
                            (net.minecraft.server.level.ServerPlayer) context.player();
                    com.example.blockmod.logic.ShieldBashService.handleTrigger(player,
                            player.level().getGameTime());
                });
        registrar.playToServer(PowerGuardPayload.TYPE, PowerGuardPayload.STREAM_CODEC,
                (payload, context) -> {
                    net.minecraft.server.level.ServerPlayer player =
                            (net.minecraft.server.level.ServerPlayer) context.player();
                    com.example.blockmod.logic.PowerGuardService.handleActivation(player,
                            payload.active(), player.level().getGameTime());
                });
    }

    private ModPayloads() {}
}
