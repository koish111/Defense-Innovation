package com.example.blockmod.client;

import com.example.blockmod.BlockMod;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.logic.GuardEquipmentResolver;
import com.example.blockmod.registry.ModItems;

import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;

/**
 * Third-person buckler guard-raise model property (2026-09-13 ruling): drives
 * the script-generated {@code *_raise_N} display variants through the vanilla
 * override mechanism. Bucklers never re-register the vanilla {@code blocking}
 * property (ShieldBlockPoseProperty keeps skipping them); this is a separate
 * {@code blockmod:guard_raise} property registered on bucklers only.
 *
 * <p>Instant raise (2026-09-13 revision): the property is a binary 0/1 —
 * 1.0 the moment the server-confirmed buckler guard presents, so the display
 * resolves straight to the final raised variant (the fully raised blocking
 * display with the arm-front calibration) with no transition frames.
 * First-person and GUI rendering never change: every generated variant copies
 * the idle model's non-third-person displays verbatim, and medium/great
 * shields keep their {@code blocking=1} path.
 */
public final class BucklerGuardRaiseProperty {

    private static final ResourceLocation GUARD_RAISE =
            ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "guard_raise");

    private static final ClampedItemPropertyFunction GUARD_RAISE_PROPERTY = (stack, level, entity, seed) -> {
        if (!Config.bucklerGuardRaiseEnabled() || !(entity instanceof Player player)) {
            return 0.0F;
        }
        if (!GuardPoseRenderer.isConfirmedGuardShield(player, stack)
                || GuardEquipmentResolver.typeOf(stack, ClientGuardState.swordBlocking()) != ShieldType.BUCKLER) {
            return 0.0F;
        }
        return 1.0F;
    };

    /** Registers the property on every buckler item (main thread, before model bake). */
    public static void register() {
        for (var holder : ModItems.allShields()) {
            if (holder.getId().getPath().endsWith("_buckler")) {
                ItemProperties.register(holder.get(), GUARD_RAISE, GUARD_RAISE_PROPERTY);
            }
        }
    }

    private BucklerGuardRaiseProperty() {}
}
