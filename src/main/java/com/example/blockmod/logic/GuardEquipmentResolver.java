package com.example.blockmod.logic;

import org.jetbrains.annotations.Nullable;

import com.example.blockmod.config.Config;
import com.example.blockmod.data.GuardProfile;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.data.SwordBlockingConfig;
import com.example.blockmod.registry.ModDataComponents;
import com.example.blockmod.registry.ModDataMaps;
import com.example.blockmod.registry.ModTags;

import net.minecraft.tags.ItemTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;

/**
 * FR-11 / Spec §5.12: resolves which held item (if any) performs the guard.
 *
 * <p>Slot classification per item:
 * <ol>
 *   <li>{@code guard_profile} stack component (third-party extension point E-01);</li>
 *   <li>{@code blockmod:guard_profile} item data map (how the vanilla shield is
 *       classified — T-11 datapack);</li>
 *   <li>{@code #minecraft:swords} in either hand → sword parameters (gb 0.20, no malus,
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
        return resolve(player, Config.swordBlocking());
    }

    @Nullable
    public static GuardEquipment resolve(LivingEntity player, SwordBlockingConfig swordBlocking) {
        ItemStack offhand = player.getItemBySlot(EquipmentSlot.OFFHAND);
        ItemStack mainhand = player.getItemBySlot(EquipmentSlot.MAINHAND);
        int slot = resolveSlot(player, swordBlocking);
        return switch (slot) {
            case GuardRules.SLOT_OFFHAND -> new GuardEquipment(profileOf(offhand), offhand, EquipmentSlot.OFFHAND);
            case GuardRules.SLOT_MAINHAND -> new GuardEquipment(profileOf(mainhand), mainhand, EquipmentSlot.MAINHAND);
            default -> null;
        };
    }

    /** Shared, allocation-free classification for input, active-guard validation and rendering. */
    public static int resolveSlot(LivingEntity player, SwordBlockingConfig swordBlocking) {
        return GuardRules.resolveEquipmentSlot(slotClass(player.getOffhandItem(), swordBlocking),
                slotClass(player.getMainHandItem(), swordBlocking));
    }

    public static boolean isSword(ItemStack stack, SwordBlockingConfig swordBlocking) {
        return slotClass(stack, swordBlocking) == GuardRules.EQUIP_SWORD;
    }

    public static boolean isGuardable(ItemStack stack, SwordBlockingConfig swordBlocking) {
        return slotClass(stack, swordBlocking) != GuardRules.EQUIP_NONE;
    }

    @Nullable
    public static ShieldType typeOf(ItemStack stack, SwordBlockingConfig swordBlocking) {
        int slotClass = slotClass(stack, swordBlocking);
        if (slotClass == GuardRules.EQUIP_NONE) return null;
        if (slotClass == GuardRules.EQUIP_SWORD) return ShieldType.SWORD;
        GuardProfile profile = configuredProfile(stack);
        if (profile != null) return profile.type();
        if (stack.is(ModTags.ITEMS_BUCKLERS)) return ShieldType.BUCKLER;
        if (stack.is(ModTags.ITEMS_GREAT_SHIELDS)) return ShieldType.GREAT;
        return ShieldType.MEDIUM;
    }

    public static boolean canPowerGuard(LivingEntity player, SwordBlockingConfig swordBlocking) {
        int slot = resolveSlot(player, swordBlocking);
        if (slot == GuardRules.SLOT_NONE) return false;
        ItemStack primary = slot == GuardRules.SLOT_OFFHAND ? player.getOffhandItem() : player.getMainHandItem();
        ItemStack secondary = slot == GuardRules.SLOT_OFFHAND ? player.getMainHandItem() : player.getOffhandItem();
        return GuardRules.powerGuardEquipmentAllowed(typeOf(primary, swordBlocking) == ShieldType.GREAT,
                isGuardable(secondary, swordBlocking));
    }

    @Nullable
    public static GuardProfile profileInHand(LivingEntity player, InteractionHand hand, SwordBlockingConfig swordBlocking) {
        ItemStack stack = player.getItemInHand(hand);
        return isGuardable(stack, swordBlocking) ? profileOf(stack) : null;
    }

    /** Validates a captured secondary profile without constructing per-tick fallback records. */
    public static boolean matchesProfile(ItemStack stack, GuardProfile profile, SwordBlockingConfig swordBlocking) {
        if (typeOf(stack, swordBlocking) != profile.type()) return false;
        GuardProfile configured = configuredProfile(stack);
        if (configured != null) return configured.equals(profile);
        return switch (profile.type()) {
            case SWORD -> profile.guardStrength() == SWORD_GB && profile.parryWindowTicks() == Config.swordParryWindow()
                    && profile.moveSpeedMalus() == 0.0F && profile.powerGuardBonus() == 0.0F && !profile.durabilityLoss();
            case BUCKLER -> profile.guardStrength() == BUCKLER_DEFAULT_GB
                    && profile.parryWindowTicks() == Config.bucklerParryWindow()
                    && profile.moveSpeedMalus() == BUCKLER_DEFAULT_MALUS && profile.powerGuardBonus() == 0.0F && profile.durabilityLoss();
            case MEDIUM -> profile.guardStrength() == MEDIUM_DEFAULT_GB && profile.parryWindowTicks() == 0
                    && profile.moveSpeedMalus() == MEDIUM_DEFAULT_MALUS && profile.powerGuardBonus() == 0.0F && profile.durabilityLoss();
            case GREAT -> profile.guardStrength() == GREAT_DEFAULT_GB && profile.parryWindowTicks() == 0
                    && profile.moveSpeedMalus() == GREAT_DEFAULT_MALUS
                    && profile.powerGuardBonus() == GREAT_DEFAULT_PG_BONUS && profile.durabilityLoss();
        };
    }

    @Nullable
    private static GuardProfile configuredProfile(ItemStack stack) {
        GuardProfile profile = stack.get(ModDataComponents.GUARD_PROFILE.get());
        return profile != null ? profile : stack.getItemHolder().getData(ModDataMaps.GUARD_PROFILE);
    }

    private static int slotClass(ItemStack stack, SwordBlockingConfig swordBlocking) {
        if (stack.isEmpty()) {
            return GuardRules.EQUIP_NONE;
        }
        GuardProfile profile = configuredProfile(stack);
        if (profile != null && profile.type() != ShieldType.SWORD) {
            return GuardRules.EQUIP_PROFILE;
        }
        if (profile == null && ModTags.isShieldItem(stack) && stack.is(ModTags.ITEMS_GUARDABLE)) {
            return GuardRules.EQUIP_SHIELD;
        }
        var itemId = stack.getItemHolder().getKey().location();
        boolean defaultSword = profile != null || stack.is(ItemTags.SWORDS) || stack.is(ModTags.ITEMS_GUARDABLE);
        return GuardRules.swordGuardAllowed(defaultSword, swordBlocking.includeSwordsTag(),
                swordBlocking.whitelist().contains(itemId), swordBlocking.blacklist().contains(itemId))
                ? GuardRules.EQUIP_SWORD : GuardRules.EQUIP_NONE;
    }

    private static GuardProfile profileOf(ItemStack stack) {
        GuardProfile profile = configuredProfile(stack);
        if (profile != null) {
            return profile;
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
