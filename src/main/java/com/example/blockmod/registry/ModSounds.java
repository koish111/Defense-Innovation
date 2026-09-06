package com.example.blockmod.registry;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.Mth;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * Custom shield-behaviour sounds (Spec §13.1.3, sounds.json). Each sound event
 * key matches its sounds.json entry, so every cue is also playable via
 * {@code /playsound blockmod:<key>}.
 *
 * <p>Event points: guard enter (start_block, split by equipment class), blocked
 * hit (blocked, random 1/2 variant, split by equipment class), parry (parry,
 * split by equipment class), shield bash release (counter, hit or miss), power
 * guard activation (fortified_guard), stamina depletion (break, split by the
 * equipment held at the crossing).
 */
public final class ModSounds {
    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, BlockMod.MODID);

    public static final DeferredHolder<SoundEvent, SoundEvent> FORTIFIED_GUARD = register("fortified_guard");
    public static final DeferredHolder<SoundEvent, SoundEvent> METAL_SHIELD_BLOCKED = register("metal_shield_blocked");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_BLOCKED = register("sword_blocked");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_START_BLOCK = register("shield_start_block");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_START_BLOCK = register("sword_start_block");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_PARRY = register("shield_parry");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_PARRY = register("sword_parry");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_COUNTER = register("shield_counter");
    public static final DeferredHolder<SoundEvent, SoundEvent> SHIELD_BREAK = register("shield_break");
    public static final DeferredHolder<SoundEvent, SoundEvent> SWORD_BREAK = register("sword_break");

    private static DeferredHolder<SoundEvent, SoundEvent> register(String name) {
        return SOUND_EVENTS.register(name, () -> SoundEvent.createVariableRangeEvent(
                ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, name)));
    }

    /**
     * Plays a mod cue at the player's position with a random pitch jitter of
     * {@code [sound].pitch_jitter} (uniform ±, clamped to the 0.5–2.0 range)
     * so repeated cues are not monotone. Server-side playback only.
     */
    public static void play(ServerPlayer player, DeferredHolder<SoundEvent, SoundEvent> sound,
            float volume, float basePitch) {
        float jitter = Config.soundPitchJitter();
        float pitch = basePitch;
        if (jitter > 0f) {
            pitch = basePitch + (player.getRandom().nextFloat() - 0.5f) * 2.0f * jitter;
            pitch = Mth.clamp(pitch, 0.5f, 2.0f);
        }
        player.level().playSound(null, player.getX(), player.getY(), player.getZ(),
                sound.get(), player.getSoundSource(), volume, pitch);
    }

    private ModSounds() {}
}
