package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.client.RaidHudOverlay;
import com.example.meteormod.entity.ModEntities;
import com.example.meteormod.entity.SentinelEntity;
import com.example.meteormod.network.RaidUpdatePayload;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.fml.loading.FMLEnvironment;
import net.neoforged.neoforge.event.entity.EntityAttributeCreationEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

/**
 * Handles mod-bus events that are not client-specific.
 */
@EventBusSubscriber(modid = MeteorMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD)
public class ModEventHandler {

    @SubscribeEvent
    public static void onEntityAttributeCreation(EntityAttributeCreationEvent event) {
        event.put(ModEntities.SENTINEL.get(), SentinelEntity.createAttributes().build());
    }

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        PayloadRegistrar registrar = event.registrar(MeteorMod.MOD_ID);

        // RaidUpdatePayload: server → client only.
        // The handler references RaidHudOverlay (client-only), but is only invoked
        // on the client side by NeoForge, so it is safe in a both-sides handler class.
        registrar.playToClient(
                RaidUpdatePayload.TYPE,
                RaidUpdatePayload.STREAM_CODEC,
                (payload, ctx) -> ctx.enqueueWork(
                        () -> {
                            if (FMLEnvironment.dist == Dist.CLIENT) {
                                RaidHudOverlay.update(payload.active(), payload.completedWaves());
                            }
                        }
                )
        );
    }
}
