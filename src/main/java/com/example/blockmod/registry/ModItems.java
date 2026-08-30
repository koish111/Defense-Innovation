package com.example.blockmod.registry;

import java.util.List;

import com.example.blockmod.BlockMod;
import com.example.blockmod.data.GuardProfile;
import com.example.blockmod.data.ShieldType;
import com.example.blockmod.item.GuardShieldItem;

import net.minecraft.world.item.Item;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

/**
 * The guard equipment roster (Spec §13.1.1). The table's twelfth row is
 * {@code minecraft:shield}, which is classified through the {@code blockmod:guard_profile}
 * data map (T-11), so eleven items are registered here.
 */
public final class ModItems {
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(BlockMod.MODID);

    public static final DeferredItem<Item> WOODEN_BUCKLER = shield("wooden_buckler", 16, ShieldType.BUCKLER, 0.25f, -0.40f, 10, 0.0f);
    public static final DeferredItem<Item> IRON_BUCKLER = shield("iron_buckler", 64, ShieldType.BUCKLER, 0.35f, -0.40f, 10, 0.0f);
    public static final DeferredItem<Item> DIAMOND_BUCKLER = shield("diamond_buckler", 256, ShieldType.BUCKLER, 0.45f, -0.40f, 10, 0.0f);
    public static final DeferredItem<Item> NETHERITE_BUCKLER = shield("netherite_buckler", 512, ShieldType.BUCKLER, 0.50f, -0.40f, 10, 0.0f);

    public static final DeferredItem<Item> REINFORCED_IRON_SHIELD = shield("reinforced_iron_shield", 532, ShieldType.MEDIUM, 0.55f, -0.70f, 0, 0.0f);
    public static final DeferredItem<Item> DIAMOND_SHIELD = shield("diamond_shield", 1033, ShieldType.MEDIUM, 0.60f, -0.70f, 0, 0.0f);
    public static final DeferredItem<Item> NETHERITE_SHIELD = shield("netherite_shield", 2043, ShieldType.MEDIUM, 0.65f, -0.70f, 0, 0.0f);

    public static final DeferredItem<Item> WOODEN_GREAT_SHIELD = shield("wooden_great_shield", 233, ShieldType.GREAT, 0.50f, -0.90f, 0, 0.15f);
    public static final DeferredItem<Item> IRON_GREAT_SHIELD = shield("iron_great_shield", 512, ShieldType.GREAT, 0.65f, -0.90f, 0, 0.30f);
    public static final DeferredItem<Item> DIAMOND_GREAT_SHIELD = shield("diamond_great_shield", 1532, ShieldType.GREAT, 0.70f, -0.90f, 0, 0.40f);
    public static final DeferredItem<Item> NETHERITE_GREAT_SHIELD = shield("netherite_great_shield", 3221, ShieldType.GREAT, 0.75f, -0.90f, 0, 0.50f);

    /** All registered guard shields, in roster order — used by the creative tab and verification. */
    public static List<DeferredHolder<Item, ? extends Item>> allShields() {
        return List.copyOf(ITEMS.getEntries());
    }

    private static DeferredItem<Item> shield(String name, int durability, ShieldType type, float gb,
            float moveSpeedMalus, int parryWindowTicks, float powerGuardBonus) {
        if (gb < GuardProfile.MIN_GB || gb > GuardProfile.MAX_GB) {
            // FR-19: reject out-of-range guard strength at registration time.
            throw new IllegalStateException(
                    "guard_strength " + gb + " for item '" + BlockMod.MODID + ":" + name + "' is outside [" + GuardProfile.MIN_GB + ", " + GuardProfile.MAX_GB + "]");
        }
        return ITEMS.register(name, () -> new GuardShieldItem(new Item.Properties()
                .stacksTo(1)
                .durability(durability)
                .component(ModDataComponents.GUARD_PROFILE.get(),
                        new GuardProfile(type, gb, parryWindowTicks, moveSpeedMalus, powerGuardBonus, true))));
    }

    private ModItems() {}
}
