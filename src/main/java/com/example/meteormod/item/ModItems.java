package com.example.meteormod.item;

import com.example.meteormod.MeteorMod;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Rarity;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModItems {

    public static final DeferredRegister.Items ITEMS =
            DeferredRegister.createItems(MeteorMod.MOD_ID);

    /** 1-4 drops from regular sentinel */
    public static final DeferredItem<Item> SMALL_SENTINEL_SCRAP =
            ITEMS.registerSimpleItem("small_sentinel_scrap",
                    new Item.Properties().rarity(Rarity.COMMON));

    /** 0-1 drops from regular sentinel */
    public static final DeferredItem<Item> MEDIUM_SENTINEL_SCRAP =
            ITEMS.registerSimpleItem("medium_sentinel_scrap",
                    new Item.Properties().rarity(Rarity.COMMON));

    /** Rare (yellow name) — crafting ingredient, no drops yet */
    public static final DeferredItem<Item> LARGE_SENTINEL_SCRAP =
            ITEMS.registerSimpleItem("large_sentinel_scrap",
                    new Item.Properties().rarity(Rarity.RARE));

    /** Epic (purple name) — crafting ingredient, no drops yet */
    public static final DeferredItem<Item> WALKER_CORE =
            ITEMS.registerSimpleItem("walker_core",
                    new Item.Properties().rarity(Rarity.EPIC));
}
