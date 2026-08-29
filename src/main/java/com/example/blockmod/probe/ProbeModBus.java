package com.example.blockmod.probe;

import io.netty.buffer.ByteBuf;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import net.neoforged.neoforge.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * TEMPORARY M0 probe (T-03) — remove at M1.
 * Verifies API-07: RegisterPayloadHandlersEvent + PayloadRegistrar#playToServer / playToClient / playBidirectional.
 * Registration happens once at mod loading; the log line is the pass condition.
 */
public final class ProbeModBus {
    public record ProbeC2SPayload(int value) implements CustomPacketPayload {
        public static final Type<ProbeC2SPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "probe_c2s"));
        public static final StreamCodec<ByteBuf, ProbeC2SPayload> STREAM =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, ProbeC2SPayload::value, ProbeC2SPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ProbeS2CPayload(int value) implements CustomPacketPayload {
        public static final Type<ProbeS2CPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "probe_s2c"));
        public static final StreamCodec<ByteBuf, ProbeS2CPayload> STREAM =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, ProbeS2CPayload::value, ProbeS2CPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public record ProbeBothPayload(int value) implements CustomPacketPayload {
        public static final Type<ProbeBothPayload> TYPE =
                new Type<>(ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "probe_both"));
        public static final StreamCodec<ByteBuf, ProbeBothPayload> STREAM =
                StreamCodec.composite(ByteBufCodecs.VAR_INT, ProbeBothPayload::value, ProbeBothPayload::new);

        @Override
        public Type<? extends CustomPacketPayload> type() {
            return TYPE;
        }
    }

    public static void onRegisterPayloadHandlers(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar("1");
        registrar.playToServer(ProbeC2SPayload.TYPE, ProbeC2SPayload.STREAM, (payload, context) -> { });
        registrar.playToClient(ProbeS2CPayload.TYPE, ProbeS2CPayload.STREAM, (payload, context) -> { });
        registrar.playBidirectional(ProbeBothPayload.TYPE, ProbeBothPayload.STREAM, (payload, context) -> { });
        BlockModLogger.info("PROBE", "ev", "RegisterPayloadHandlersEvent", "result", "playToServer+playToClient+playBidirectional registered");
    }

    private ProbeModBus() {}
}
