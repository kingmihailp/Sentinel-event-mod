package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.entity.ModEntities;
import com.example.meteormod.entity.SentinelEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;

/**
 * Handles mod-bus events that are not client-specific.
 */
@EventBusSubscriber(modid = MeteorMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModEventHandler {

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SENTINEL.get(), SentinelEntity.createAttributes().build());
    }
}
