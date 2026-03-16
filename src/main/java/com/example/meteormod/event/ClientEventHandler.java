package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.client.renderer.MeteorRenderer;
import com.example.meteormod.client.renderer.SentinelRenderer;
import com.example.meteormod.entity.ModEntities;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;

@EventBusSubscriber(modid = MeteorMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.METEOR.get(), MeteorRenderer::new);
        event.registerEntityRenderer(ModEntities.SENTINEL.get(), SentinelRenderer::new);
    }
}
