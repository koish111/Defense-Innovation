package com.example.blockmod.logic;

import org.jetbrains.annotations.Nullable;

import com.example.blockmod.config.Config;
import com.example.blockmod.data.GuardProfile;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.registry.ModDataComponents;
import com.example.blockmod.registry.ModDataMaps;
import com.example.blockmod.registry.ModTags;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.SwordItem;

/**
 * FR-11 / Spec §5.12: resolves which held item (if any) performs the guard.
 *
 * <p>Slot classification per item:
 * <ol>
 *   <li>{@code guard_profile} stack component (third-party extension point E-01);</li>
 *   <li>{@code blockmod:guard_profile} item data map (how the vanilla shield is
 *       classified — T-11 datapack);</li>
 *   <li>mainhand {@code #minecraft:swords} → sword parameters (gb 0.20, no malus,
 *       no durability loss — Spec §13.1.1 / O-14);</li>
 *   <li>{@code #blockmod:guardable} shield-family tags → type-default shield
 *       parameters (Spec §5.12 step 3).</li>
 * </ol>
 * The offhand shield wins over the mainhand whenever it can guard (FR-11, ADR-10);
 * see {@link GuardRules#resolveEquipmentSlot} for the exact priority matrix.
 */
public final class GuardEquipmentResolver {
    /** Base sword guard strength — Spec §13.1.1 roster row + O-14 (unified for MVP). */
    public static final float SWORD_GB = 0.20f;

    // Type-default baselines for tag-classified shields without a profile (Spec §5.12
    // step 3). Each mirrors the roster's entry tier for its class (§13.1.1).
    private static final float BUCKLER_DEFAULT_GB = 0.25f;
    private static final float BUCKLER_DEFAULT_MALUS = -0.40f;
    private static final float MEDIUM_DEFAULT_GB = 0.40f;   // vanilla-shield baseline (T-11 datapack)
    private static final float MEDIUM_DEFAULT_MALUS = -0.70f;
    private static final float GREAT_DEFAULT_GB = 0.50f;
    private static final float GREAT_DEFAULT_MALUS = -0.90f;
    private static final float GREAT_DEFAULT_PG_BONUS = 0.15f;

    /** The resolved guard equipment: profile + the exact stack + which slot it sits in. */
    public record GuardEquipment(GuardProfile profile, ItemStack stack, EquipmentSlot slot) {
        public InteractionHand hand() {
            return slot == EquipmentSlot.OFFHAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
        }
    }

    private GuardEquipmentResolver() {}

    /** FR-11 / §5.12 resolution for a living entity; null = cannot guard. */
    @Nullable
    public static GuardEquipment resolve(LivingEntity player) {
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        ItemStack mainhand = player.getItemBySlot(EquipmentSlot.MAINHAND);
        int offClass = slotClass(offhand);
        int mainClass = slotClass(mainhand);
        int slot = GuardRules.resolveEquipmentSlot(offClass, mainClass);
        return switch (slot) {
            case GuardRules.SLOT_OFFHAND -> new GuardEquipment(profileOf(offhand), offhand, EquipmentSlot.OFFHAND);
            case GuardRules.SLOT_MAINHAND -> new GuardEquipment(profileOf(mainhand), mainhand, EquipmentSlot.MAINHAND);
            default -> null;
        };
    }

    /** Pure slot classification: none / profile / sword / shield-family (see class doc for the lookup order). */
    private static int slotClass(ItemStack stack) {
        if (stack.isEmpty()) {
            return GuardRules.EQUIP_NONE;
        }
        if (stack.get(ModDataComponents.GUARD_PROFILE.get()) != null
                || stack.getItemHolder().getData(ModDataMaps.GUARD_PROFILE) != null) {
            return GuardRules.EQUIP_PROFILE;
        }
        boolean sword = stack.is(ItemTags.SWORDS) || stack.getItem() instanceof SwordItem;
        if (sword) {
            return GuardRules.EQUIP_SWORD;
        }
        if (stack.is(ModTags.ITEMS_GUARDABLE)) {
            return GuardRules.EQUIP_SHIELD; // shield-family tags: offhand wins per FR-11
        }
        return GuardRules.EQUIP_NONE;
    }

    private static GuardProfile profileOf(ItemStack stack) {
        GuardProfile profile = stack.get(ModDataComponents.GUARD_PROFILE.get());
        if (profile != null) {
            return profile;
        }
        GuardProfile fromDataMap = stack.getItemHolder().getData(ModDataMaps.GUARD_PROFILE);
        if (fromDataMap != null) {
            return fromDataMap;
        }
        // No component and no data map: sword fallback (§5.12 step 4) or the
        // shield-family type defaults (§5.12 step 3).
        if (stack.is(ModTags.ITEMS_BUCKLERS)) {
            return new GuardProfile(ShieldType.BUCKLER, BUCKLER_DEFAULT_GB,
                    Config.bucklerParryWindow(), BUCKLER_DEFAULT_MALUS, 0.0f, true);
        }
        if (stack.is(ModTags.ITEMS_GREAT_SHIELDS)) {
            return new GuardProfile(ShieldType.GREAT, GREAT_DEFAULT_GB,
                    0, GREAT_DEFAULT_MALUS, GREAT_DEFAULT_PG_BONUS, true);
        }
        if (stack.is(ModTags.ITEMS_MEDIUM_SHIELDS)) {
            return new GuardProfile(ShieldType.MEDIUM, MEDIUM_DEFAULT_GB,
                    0, MEDIUM_DEFAULT_MALUS, 0.0f, true);
        }
        // Sword / plain guardable fallback (§5.12 step 4): sword parameters.
        return new GuardProfile(ShieldType.SWORD, SWORD_GB,
                Config.swordParryWindow(), 0.0f, 0.0f, false);
    }
}
