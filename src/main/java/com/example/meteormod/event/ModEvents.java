package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.item.ModItems;
import com.example.meteormod.item.SentinelCannonItem;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.player.ItemCraftedEvent;

@EventBusSubscriber(modid = MeteorMod.MOD_ID)
public class ModEvents {

    /**
     * Fires on the server side whenever a player pulls a crafted item out of
     * the crafting output slot.  Used to ensure the Sentinel Cannon always
     * starts fully charged — both on first craft and after a battery recharge.
     */
    @SubscribeEvent
    public static void onItemCrafted(ItemCraftedEvent event) {
        var result = event.getCrafting();
        if (result.is(ModItems.SENTINEL_CANNON.get())) {
            SentinelCannonItem.setEnergy(result, SentinelCannonItem.MAX_ENERGY);
        }
    }
}
