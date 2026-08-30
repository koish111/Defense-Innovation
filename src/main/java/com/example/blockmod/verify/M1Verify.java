package com.example.blockmod.verify;

import java.util.Optional;

import com.example.blockmod.BlockMod;
import com.example.blockmod.BlockModLogger;
import com.example.blockmod.config.Config;
import com.example.blockmod.data.GuardProfile;
import com.example.blockmod.registry.ModAttachments;
import com.example.blockmod.registry.ModDataComponents;
import com.example.blockmod.registry.ModDataMaps;
import com.example.blockmod.registry.ModItems;
import com.example.blockmod.registry.ModTags;

import net.minecraft.core.HolderSet;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.server.ServerStartedEvent;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * TEMPORARY M1 verification aid (T-06/T-07/T-08/T-09/T-11 acceptance) — remove at M2.
 * Logs the roster with its profiles, the vanilla shield's data-map profile, tag
 * contents and a config snapshot once the server (and its datapacks) are up.
 */
@EventBusSubscriber(modid = BlockMod.MODID)
public final class M1Verify {
    @SubscribeEvent
    static void onServerStarted(ServerStartedEvent event) {
        BlockModLogger.info("M1VERIFY", "note", "=== roster ===");
        for (var shield : ModItems.allShields()) {
            Item item = shield.get();
            ResourceLocation id = BuiltInRegistries.ITEM.getKey(item);
            ItemStack stack = new ItemStack(item);
            GuardProfile profile = stack.get(ModDataComponents.GUARD_PROFILE.get());
            BlockModLogger.info("M1VERIFY", "item", id, "maxDamage", stack.get(DataComponents.MAX_DAMAGE),
                    "profile", profile);
        }

        GuardProfile vanillaShield = BuiltInRegistries.ITEM.get(ResourceLocation.parse("minecraft:shield"))
                .builtInRegistryHolder().getData(ModDataMaps.GUARD_PROFILE);
        BlockModLogger.info("M1VERIFY", "note", "=== vanilla shield data map ===", "profile", vanillaShield);

        BlockModLogger.info("M1VERIFY", "note", "=== tags ===");
        logItemTag("bucklers", ModTags.ITEMS_BUCKLERS);
        logItemTag("medium_shields", ModTags.ITEMS_MEDIUM_SHIELDS);
        logItemTag("great_shields", ModTags.ITEMS_GREAT_SHIELDS);
        logItemTag("guardable", ModTags.ITEMS_GUARDABLE);
        logItemTag("parryable", ModTags.ITEMS_PARRYABLE);
        var damageTypes = event.getServer().registryAccess().registryOrThrow(Registries.DAMAGE_TYPE);
        logDamageTag("guardable", damageTypes, ModTags.DAMAGE_GUARDABLE);
        logDamageTag("parryable", damageTypes, ModTags.DAMAGE_PARRYABLE);
        logDamageTag("guard_ignored", damageTypes, ModTags.DAMAGE_GUARD_IGNORED);
        BuiltInRegistries.ENTITY_TYPE.getTag(ModTags.BOSSES).ifPresentOrElse(
                set -> BlockModLogger.info("M1VERIFY", "tag", "bosses", "size", set.size(),
                        "values", set.stream().map(h -> h.unwrapKey().orElseThrow().location().toString()).toList()),
                () -> BlockModLogger.error("M1VERIFY", "tag", "bosses", "error", "missing"));

        BlockModLogger.info("M1VERIFY", "note", "=== attachments/effects/config ===");
        BlockModLogger.info("M1VERIFY",
                "staminaAttachment", NeoForgeRegistries.ATTACHMENT_TYPES.getKey(ModAttachments.STAMINA.get()),
                "guardStateAttachment", NeoForgeRegistries.ATTACHMENT_TYPES.getKey(ModAttachments.GUARD_STATE.get()),
                "stunRegistered", BuiltInRegistries.MOB_EFFECT.containsKey(ResourceLocation.parse("blockmod:stun")));
        BlockModLogger.info("M1VERIFY",
                "maxStamina", Config.maxStamina(),
                "swordParryWindow", Config.swordParryWindow(),
                "stunDuration", Config.stunDuration(),
                "minGb", Config.minGb(), "maxGb", Config.maxGb());
        BlockModLogger.info("M1VERIFY", "note", "=== complete ===");
    }

    private static void logItemTag(String name, TagKey<Item> tag) {
        Optional<HolderSet.Named<Item>> set = BuiltInRegistries.ITEM.getTag(tag);
        set.ifPresentOrElse(
                named -> BlockModLogger.info("M1VERIFY", "tag", name, "size", named.size(),
                        "values", named.stream().map(h -> h.unwrapKey().orElseThrow().location().toString()).toList()),
                () -> BlockModLogger.error("M1VERIFY", "tag", name, "error", "missing or empty"));
    }

    private static void logDamageTag(String name, net.minecraft.core.Registry<DamageType> registry, TagKey<DamageType> tag) {
        registry.getTag(tag).ifPresentOrElse(
                named -> BlockModLogger.info("M1VERIFY", "tag", name, "size", named.size(),
                        "values", named.stream().map(h -> h.unwrapKey().orElseThrow().location().toString()).toList()),
                () -> BlockModLogger.error("M1VERIFY", "tag", name, "error", "missing or empty"));
    }

    private M1Verify() {}
}
