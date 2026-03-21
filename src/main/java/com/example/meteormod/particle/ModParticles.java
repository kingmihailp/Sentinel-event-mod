package com.example.meteormod.particle;

import com.example.meteormod.MeteorMod;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.core.registries.Registries;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModParticles {

    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(Registries.PARTICLE_TYPE, MeteorMod.MOD_ID);

    public static final DeferredHolder<ParticleType<?>, SimpleParticleType> SENTINEL_BULLET =
            PARTICLES.register("sentinel_bullet", () -> new SimpleParticleType(false));
}
