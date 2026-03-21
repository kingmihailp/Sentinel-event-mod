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
                    // SentinelIconItem renders a live 3-D sentinel via BEWLR
                    .icon(() -> new ItemStack(ModItems.SENTINEL_TAB_ICON.get()))
                    .displayItems((params, output) -> {
                        output.accept(ModItems.SMALL_SENTINEL_SCRAP.get());
                        output.accept(ModItems.MEDIUM_SENTINEL_SCRAP.get());
                        output.accept(ModItems.LARGE_SENTINEL_SCRAP.get());
                        output.accept(ModItems.WALKER_CORE.get());
                        output.accept(ModItems.SENTINEL_METAL_NUGGET.get());
                        output.accept(ModItems.SENTINEL_METAL_INGOT.get());
                        output.accept(ModItems.SENTINEL_METAL_BLOCK_ITEM.get());
                        output.accept(ModItems.SENTINEL_CANNON.get());
                    })
                    .build());
}
