package com.example.blockmod.registry;

import com.example.blockmod.BlockMod;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.CreativeModeTabs;
import net.minecraft.world.item.ItemStack;

import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

/** Creative tab {@code blockmod:main} (Spec §13.1.2), placed after the combat tab. */
public final class ModCreativeTabs {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, BlockMod.MODID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> MAIN =
            TABS.register("main", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.blockmod"))
                    .withTabsBefore(CreativeModeTabs.COMBAT)
                    .icon(() -> new ItemStack(ModItems.WOODEN_BUCKLER.get()))
                    .displayItems((parameters, output) -> {
                        // Roster order with the vanilla shield slotted between the netherite
                        // buckler and the reinforced iron shield (user ruling 2026-09-14).
                        boolean vanillaShieldPlaced = false;
                        for (var shield : ModItems.allShields()) {
                            if (!vanillaShieldPlaced && shield.get() == ModItems.REINFORCED_IRON_SHIELD.get()) {
                                output.accept(new ItemStack(net.minecraft.world.item.Items.SHIELD));
                                vanillaShieldPlaced = true;
                            }
                            output.accept(shield.get());
                        }
                    })
                    .build());

    private ModCreativeTabs() {}
}
