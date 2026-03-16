package com.example.meteormod.client.model;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.entity.SentinelEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

/**
 * GeckoLib model for SentinelEntity.
 *
 * The three resource locations below point to:
 *  - geo/sentinel.geo.json          ← export from Blockbench ("Model for GeckoLib")
 *  - textures/entity/sentinel.png   ← your texture file
 *  - animations/sentinel.animation.json ← export from Blockbench ("Animations")
 *
 * All paths are relative to assets/meteormod/ inside the jar.
 */
public class SentinelModel extends GeoModel<SentinelEntity> {

    @Override
    public ResourceLocation getModelResource(SentinelEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "geo/sentinel.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SentinelEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "textures/entity/sentinel.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SentinelEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "animations/sentinel.animation.json");
    }
}
