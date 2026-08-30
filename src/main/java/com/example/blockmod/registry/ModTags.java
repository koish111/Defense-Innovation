package com.example.blockmod.registry;

import com.example.blockmod.BlockMod;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.item.Item;

/**
 * Tag keys owned by the mod (Spec §5.12 / §13.1.2). JSON contents live under
 * {@code data/blockmod/tags/**}.
 *
 * <p>Item tags are the fallback classification when a stack carries no
 * {@code guard_profile} component; damage-type tags are the guard/parry
 * whitelist the damage classifier reads; {@code bosses} feeds boss tracking.
 */
public final class ModTags {
    // item tags
    public static final TagKey<Item> ITEMS_GUARDABLE = itemTag("guardable");
    public static final TagKey<Item> ITEMS_PARRYABLE = itemTag("parryable");
    public static final TagKey<Item> ITEMS_BUCKLERS = itemTag("bucklers");
    public static final TagKey<Item> ITEMS_MEDIUM_SHIELDS = itemTag("medium_shields");
    public static final TagKey<Item> ITEMS_GREAT_SHIELDS = itemTag("great_shields");

    // damage type tags
    public static final TagKey<DamageType> DAMAGE_GUARDABLE = damageTypeTag("guardable");
    public static final TagKey<DamageType> DAMAGE_PARRYABLE = damageTypeTag("parryable");
    public static final TagKey<DamageType> DAMAGE_GUARD_IGNORED = damageTypeTag("guard_ignored");

    // entity type tags
    public static final TagKey<EntityType<?>> BOSSES = TagKey.create(Registries.ENTITY_TYPE,
            ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, "bosses"));

    private static TagKey<Item> itemTag(String path) {
        return ItemTags.create(ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, path));
    }

    private static TagKey<DamageType> damageTypeTag(String path) {
        return TagKey.create(Registries.DAMAGE_TYPE, ResourceLocation.fromNamespaceAndPath(BlockMod.MODID, path));
    }

    private ModTags() {}
}
