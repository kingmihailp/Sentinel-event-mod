package com.example.meteormod.item;

import com.example.meteormod.MeteorMod;
import net.minecraft.world.item.BlockItem;
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

    /** Crafting ingredient — common */
    public static final DeferredItem<Item> SENTINEL_METAL_NUGGET =
            ITEMS.registerSimpleItem("sentinelmetal_nugget",
                    new Item.Properties().rarity(Rarity.COMMON));

    /** Crafting ingredient — common */
    public static final DeferredItem<Item> SENTINEL_METAL_INGOT =
            ITEMS.registerSimpleItem("sentinelmetal_ingot",
                    new Item.Properties().rarity(Rarity.COMMON));

    /** BlockItem for the sentinel metal block */
    public static final DeferredItem<BlockItem> SENTINEL_METAL_BLOCK_ITEM =
            ITEMS.registerItem("sentinelmetal_block",
                    props -> new BlockItem(ModBlocks.SENTINEL_METAL_BLOCK.get(), props),
                    new Item.Properties().rarity(Rarity.COMMON));

    /**
     * Item form of the Suspicious Package block.
     * Renders as a 3-D block in hand and inventory.
     * Not added to any creative tab — obtain via /give only.
     */
    public static final DeferredItem<BlockItem> SUSPICIOUS_PACKAGE_ITEM =
            ITEMS.registerItem("suspicious_package",
                    props -> new BlockItem(ModBlocks.SUSPICIOUS_PACKAGE.get(), props),
                    new Item.Properties().rarity(Rarity.EPIC));

    /** Sentinel Cannon — fires EMP rounds that electrify targets for 10 s. */
    public static final DeferredItem<SentinelCannonItem> SENTINEL_CANNON =
            ITEMS.registerItem("sentinel_cannon", SentinelCannonItem::new,
                    new Item.Properties().rarity(Rarity.EPIC).stacksTo(1));

    /**
     * Internal item used only as the creative-tab icon.
     * Renders a live 3-D sentinel that follows the mouse cursor.
     * Never shown in the tab's item list.
     */
    public static final DeferredItem<Item> SENTINEL_TAB_ICON =
            ITEMS.register("sentinel_tab_icon", SentinelIconItem::new);
}
