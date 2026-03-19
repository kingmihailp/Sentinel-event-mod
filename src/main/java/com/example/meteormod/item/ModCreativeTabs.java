package com.example.meteormod.item;

import com.example.meteormod.MeteorMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModCreativeTabs {

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MeteorMod.MOD_ID);

    public static final DeferredHolder<CreativeModeTab, CreativeModeTab> SENTINEL_TAB =
            TABS.register("sentinel_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.meteormod.sentinel_tab"))
                    // walker_core is the most iconic sentinel item; replace with a
                    // spawn egg if one is ever added.
                    .icon(() -> new ItemStack(ModItems.WALKER_CORE.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.SMALL_SENTINEL_SCRAP.get());
                        output.accept(ModItems.MEDIUM_SENTINEL_SCRAP.get());
                        output.accept(ModItems.LARGE_SENTINEL_SCRAP.get());
                        output.accept(ModItems.WALKER_CORE.get());
                    })
                    .build());
}
