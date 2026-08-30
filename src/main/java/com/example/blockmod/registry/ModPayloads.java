package com.example.blockmod.registry;

import com.example.blockmod.client.ClientGuardState;
import com.example.blockmod.network.ConfigSyncPayload;
import com.example.blockmod.network.StaminaSyncPayload;

import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Network protocol registration (Spec §5.13). M2 wires the S2C mirror payloads;
 * M3/M5 add the C2S intent payloads onto the same registrar.
 *
 * <p>The client handlers are method references into {@link ClientGuardState}: the
 * referenced class only loads when a payload actually arrives, so the dedicated
 * server never touches client classes.
 */
public final class ModPayloads {
    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToClient(StaminaSyncPayload.TYPE, StaminaSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientGuardState.acceptStaminaSync(payload));
        registrar.playToClient(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC,
                (payload, context) -> ClientGuardState.acceptConfigSync(payload));
    }

    private ModPayloads() {}
}
