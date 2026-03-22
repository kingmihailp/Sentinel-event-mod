package com.example.meteormod.client.renderer;

import com.example.meteormod.client.model.SentinelHoverModel;
import com.example.meteormod.entity.SentinelHoverEntity;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

public class SentinelHoverRenderer extends GeoEntityRenderer<SentinelHoverEntity> {

    public SentinelHoverRenderer(EntityRendererProvider.Context context) {
        super(context, new SentinelHoverModel());
        this.shadowRadius = 0.6f;
    }
}
