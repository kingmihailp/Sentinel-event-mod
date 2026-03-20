package com.example.meteormod;

import com.example.meteormod.entity.ModEntities;
import com.example.meteormod.item.ModBlocks;
import com.example.meteormod.item.ModCreativeTabs;
import com.example.meteormod.item.ModItems;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(MeteorMod.MOD_ID)
public class MeteorMod {

    public static final String MOD_ID = "meteormod";

    public MeteorMod(IEventBus modEventBus) {
        ModEntities.ENTITIES.register(modEventBus);
        ModBlocks.BLOCKS.register(modEventBus);
        ModItems.ITEMS.register(modEventBus);
        ModCreativeTabs.TABS.register(modEventBus);
    }
}
