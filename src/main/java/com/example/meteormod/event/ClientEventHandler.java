package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.client.HoverHudOverlay;
import com.example.meteormod.client.ModKeyBindings;
import com.example.meteormod.client.RaidHudOverlay;
import com.example.meteormod.client.ScopeOverlay;
import com.example.meteormod.client.renderer.EmpBulletRenderer;
import com.example.meteormod.client.renderer.MeteorRenderer;
import com.example.meteormod.client.renderer.OuterSpaceEffects;
import com.example.meteormod.client.renderer.SentinelBulletRenderer;
import com.example.meteormod.client.renderer.SentinelHoverRenderer;
import com.example.meteormod.client.renderer.SentinelRenderer;
import com.example.meteormod.client.renderer.TurretBulletRenderer;
import com.example.meteormod.entity.ModEntities;
import com.example.meteormod.particle.EmpParticle;
import com.example.meteormod.particle.ModParticles;
import com.example.meteormod.particle.SentinelBulletParticle;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.EntityRenderersEvent;
import net.neoforged.neoforge.client.event.RegisterDimensionSpecialEffectsEvent;
import net.neoforged.neoforge.client.event.RegisterGuiLayersEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.event.RegisterParticleProvidersEvent;

@EventBusSubscriber(modid = MeteorMod.MOD_ID, bus = EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientEventHandler {

    @SubscribeEvent
    public static void onRegisterRenderers(EntityRenderersEvent.RegisterRenderers event) {
        event.registerEntityRenderer(ModEntities.METEOR.get(), MeteorRenderer::new);
        event.registerEntityRenderer(ModEntities.SENTINEL.get(), SentinelRenderer::new);
        event.registerEntityRenderer(ModEntities.SENTINEL_BULLET.get(), SentinelBulletRenderer::new);
        event.registerEntityRenderer(ModEntities.EMP_BULLET.get(), EmpBulletRenderer::new);
        event.registerEntityRenderer(ModEntities.SENTINEL_HOVER.get(), SentinelHoverRenderer::new);
        event.registerEntityRenderer(ModEntities.TURRET_BULLET.get(), TurretBulletRenderer::new);
    }

    @SubscribeEvent
    public static void onRegisterParticles(RegisterParticleProvidersEvent event) {
        event.registerSpriteSet(ModParticles.SENTINEL_BULLET.get(), SentinelBulletParticle.Provider::new);
        event.registerSpriteSet(ModParticles.EMP_BULLET.get(), EmpParticle.Provider::new);
    }

    @SubscribeEvent
    public static void onRegisterGuiLayers(RegisterGuiLayersEvent event) {
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "raid_hud"),
                RaidHudOverlay::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "scope_overlay"),
                ScopeOverlay::render
        );
        event.registerAboveAll(
                ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "hover_hud"),
                HoverHudOverlay::render
        );
    }

    @SubscribeEvent
    public static void onRegisterKeyMappings(RegisterKeyMappingsEvent event) {
        event.register(ModKeyBindings.SCOPE_KEY);
    }

    @SubscribeEvent
    public static void onRegisterDimensionEffects(RegisterDimensionSpecialEffectsEvent event) {
        event.register(
                ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "outer_space"),
                new OuterSpaceEffects()
        );
    }
}
