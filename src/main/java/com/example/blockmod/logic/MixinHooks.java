package com.example.blockmod.logic;

// NOTE: lives outside the mixin package — the Mixin subsystem forbids loading regular
// classes from a defined mixin package (IllegalClassLoadError), only @Mixin types may live there.

import com.example.blockmod.config.Config;
import com.example.blockmod.registry.ModEffects;

import net.minecraft.world.entity.LivingEntity;

/**
 * Bridges mixin-injected vanilla gates to mod state and configuration. Lives
 * OUTSIDE the mixin package (the Mixin subsystem forbids loading regular classes
 * from a defined mixin package) and is invoked from the mixins' handler methods.
 */
public final class MixinHooks {
    private MixinHooks() {}

    /**
     * D-01 (FR-03): allow eating at full hunger so the food→stamina restore can
     * trigger. When the server config is unavailable (the client side before a
     * world is open) the default-enabled value is assumed: the client may start
     * the use animation, and the server-side {@code Item#use} re-evaluates the
     * same gate with the real config before accepting the use.
     */
    public static boolean allowEatingAtFullHunger() {
        try {
            return Config.foodRestoreStamina();
        } catch (RuntimeException configNotLoaded) {
            return true;
        }
    }

    /**
     * FR-05: true while the entity carries {@code blockmod:stun}. The stun
     * mixins (aiStep isImmobile gate, swing cancels, camera freeze) read the
     * effect directly instead of going through events, so the freeze is
     * frame-accurate and works for any living entity, client and server alike.
     * Null-tolerant: the MouseHandler call site may fire with no player yet.
     */
    public static boolean isStunned(LivingEntity entity) {
        return entity != null && entity.hasEffect(ModEffects.STUN);
    }
}
