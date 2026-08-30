package com.example.blockmod.data;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

import io.netty.buffer.ByteBuf;

import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * Per-item guard statistics attached to an ItemStack via the
 * {@code blockmod:guard_profile} data component (Spec §4.3.1). Third-party items
 * opt into the guard system by attaching this component; vanilla items are
 * covered by the {@code blockmod:guard_profile} item data map.
 *
 * @param type             shield class
 * @param guardStrength    base guard strength gb, clamped to [0.01, 0.95] by the resolver
 * @param parryWindowTicks 0 = cannot parry
 * @param moveSpeedMalus   move-speed multiplier delta while guarding (negative), 0 for none
 * @param powerGuardBonus  power-guard gb bonus, only meaningful for GREAT
 * @param durabilityLoss   whether blocking consumes item durability (swords: false)
 */
public record GuardProfile(
        ShieldType type,
        float guardStrength,
        int parryWindowTicks,
        float moveSpeedMalus,
        float powerGuardBonus,
        boolean durabilityLoss) {

    public static final Codec<GuardProfile> CODEC = RecordCodecBuilder.create(i -> i.group(
            ShieldType.CODEC.fieldOf("type").forGetter(GuardProfile::type),
            Codec.FLOAT.fieldOf("guard_strength").forGetter(GuardProfile::guardStrength),
            Codec.INT.optionalFieldOf("parry_window_ticks", 0).forGetter(GuardProfile::parryWindowTicks),
            Codec.FLOAT.optionalFieldOf("move_speed_malus", 0f).forGetter(GuardProfile::moveSpeedMalus),
            Codec.FLOAT.optionalFieldOf("power_guard_bonus", 0f).forGetter(GuardProfile::powerGuardBonus),
            Codec.BOOL.optionalFieldOf("durability_loss", true).forGetter(GuardProfile::durabilityLoss)
    ).apply(i, GuardProfile::new));

    public static final StreamCodec<ByteBuf, GuardProfile> STREAM_CODEC = StreamCodec.composite(
            ShieldType.STREAM_CODEC, GuardProfile::type,
            ByteBufCodecs.FLOAT, GuardProfile::guardStrength,
            ByteBufCodecs.VAR_INT, GuardProfile::parryWindowTicks,
            ByteBufCodecs.FLOAT, GuardProfile::moveSpeedMalus,
            ByteBufCodecs.FLOAT, GuardProfile::powerGuardBonus,
            ByteBufCodecs.BOOL, GuardProfile::durabilityLoss,
            GuardProfile::new);
}
