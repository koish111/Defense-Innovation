package com.example.blockmod.client;

import com.example.blockmod.registry.ModItems;

import net.minecraft.client.renderer.item.ClampedItemPropertyFunction;
import net.minecraft.client.renderer.item.ItemProperties;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Items;

/**
 * Third-person blocking presentation (2026-09-13 ruling): while the server
 * confirms a guard, every guard shield renders through the vanilla
 * {@code blocking=1} model override — the same raised blocking model and
 * display transforms a vanilla shield shows when it blocks — instead of the
 * idle held pose.
 *
 * <p>Guard never enters the vanilla use state (FR-24 cancels
 * {@code startUseItem}), so vanilla's own property can never fire. This class
 * re-registers the {@code blocking} item property on {@code minecraft:shield}
 * and every {@code blockmod} guard shield; the registration is a plain map
 * put, so it replaces the vanilla function, which is preserved verbatim as the
 * fallback. Mod shield models resolve the override to a {@code *_blocking}
 * variant that inherits their geometry and takes vanilla
 * {@code shield_blocking}'s third-person display transforms.
 *
 * <p>First-person guard posing is untouched: {@link GuardArmTransforms}
 * reports {@link GuardArmTransforms#replicatingFirstPerson()} while it
 * re-renders the hand, and the property returns 0 so the guard pipeline keeps
 * applying its own display-transform delta.
 *
 * <p>Bucklers are excluded (revert ruling 2026-09-13): a guarding buckler
 * keeps its idle third-person render, so their item properties are never
 * touched and no {@code *_blocking} variant exists for them.
 */
public final class ShieldBlockPoseProperty {

    private static final ResourceLocation BLOCKING = ResourceLocation.withDefaultNamespace("blocking");

    /** Re-registered {@code blocking}: guard posing OR the vanilla use condition. */
    private static final ClampedItemPropertyFunction GUARD_BLOCKING = (stack, level, entity, seed) -> {
        if (GuardArmTransforms.replicatingFirstPerson()) {
            return 0.0F; // the re-render applies the blocking pose itself
        }
        if (entity instanceof Player player && GuardPoseRenderer.isConfirmedGuardShield(player, stack)) {
            return 1.0F;
        }
        return entity != null && entity.isUsingItem() && entity.getUseItem() == stack ? 1.0F : 0.0F;
    };

    /** Registers the wrapper for the vanilla shield and every mod guard shield. */
    public static void register() {
        ItemProperties.register(Items.SHIELD, BLOCKING, GUARD_BLOCKING);
        for (var holder : ModItems.allShields()) {
            // Buckler revert ruling (2026-09-13): bucklers keep their
            // pre-feature third-person idle render — no blocking=1 override,
            // no *_blocking variant — so their properties stay untouched.
            if (holder.getId().getPath().endsWith("_buckler")) {
                continue;
            }
            ItemProperties.register(holder.get(), BLOCKING, GUARD_BLOCKING);
        }
    }

    private ShieldBlockPoseProperty() {}
}
