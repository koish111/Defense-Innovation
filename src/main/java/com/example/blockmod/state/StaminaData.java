package com.example.blockmod.state;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;

/**
 * Player stamina attachment ({@code blockmod:stamina}, serialized — Spec §4.3.2).
 * Stamina may go negative; depletion is a derived property so that no effect or
 * status flag can be milked or cleared away (v2.0).
 */
public final class StaminaData {
    public static final Codec<StaminaData> CODEC = RecordCodecBuilder.create(i -> i.group(
            Codec.FLOAT.fieldOf("stamina").forGetter(StaminaData::stamina),
            Codec.LONG.fieldOf("last_event_tick").forGetter(StaminaData::lastEventTick)
    ).apply(i, StaminaData::new));

    private float stamina;
    private long lastEventTick;

    public StaminaData(float stamina, long lastEventTick) {
        this.stamina = stamina;
        this.lastEventTick = lastEventTick;
    }

    public float stamina() {
        return stamina;
    }

    public void setStamina(float stamina) {
        this.stamina = stamina;
    }

    public long lastEventTick() {
        return lastEventTick;
    }

    public void setLastEventTick(long lastEventTick) {
        this.lastEventTick = lastEventTick;
    }

    /** v2.0: depletion is derived — no stored flag can ever desync from the number. */
    public boolean isDepleted() {
        return stamina <= 0f;
    }

    /** True while blocking and parrying are allowed (FR-04). */
    public boolean canDefend() {
        return stamina > 0f;
    }

    @Override
    public String toString() {
        return "StaminaData[stamina=" + stamina + ", lastEventTick=" + lastEventTick + "]";
    }
}
