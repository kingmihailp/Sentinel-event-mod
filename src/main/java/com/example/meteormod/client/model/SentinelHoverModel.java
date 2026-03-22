package com.example.meteormod.client.model;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.entity.SentinelHoverEntity;
import net.minecraft.resources.ResourceLocation;
import software.bernie.geckolib.model.GeoModel;

public class SentinelHoverModel extends GeoModel<SentinelHoverEntity> {

    @Override
    public ResourceLocation getModelResource(SentinelHoverEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "geo/sentinel_hover.geo.json");
    }

    @Override
    public ResourceLocation getTextureResource(SentinelHoverEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "textures/entity/sentinel_hover.png");
    }

    @Override
    public ResourceLocation getAnimationResource(SentinelHoverEntity entity) {
        return ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "animations/sentinel_hover.animation.json");
    }
}
