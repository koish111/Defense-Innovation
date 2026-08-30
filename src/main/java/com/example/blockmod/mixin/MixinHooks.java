package com.example.blockmod.mixin;

import com.example.blockmod.config.Config;

/**
 * Bridges mixin-injected vanilla gates to mod configuration. Kept in the mixin
 * package next to its only callers.
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
}
