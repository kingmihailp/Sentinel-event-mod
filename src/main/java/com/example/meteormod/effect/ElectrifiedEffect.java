package com.example.meteormod.effect;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class ElectrifiedEffect extends MobEffect {

    public ElectrifiedEffect() {
        super(MobEffectCategory.HARMFUL, 0x00AAFF);
    }

    /**
     * Spawn mini-lightning sparks periodically.
     * Movement freezing is handled every tick via EntityTickEvent in MeteorSpawnHandler.
     */
    @Override
    public void applyEffectTick(ServerLevel level, LivingEntity entity, int amplifier) {
        double w = entity.getBbWidth();
        double h = entity.getBbHeight();
        level.sendParticles(
                ParticleTypes.ELECTRIC_SPARK,
                entity.getX() + (entity.getRandom().nextDouble() - 0.5) * w,
                entity.getY() + entity.getRandom().nextDouble() * h,
                entity.getZ() + (entity.getRandom().nextDouble() - 0.5) * w,
                5, w * 0.3, h * 0.25, w * 0.3, 0.05
        );
    }
}
