package com.example.meteormod.effect;

import com.example.meteormod.MeteorMod;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffect;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEffects {

    public static final DeferredRegister<MobEffect> EFFECTS =
            DeferredRegister.create(Registries.MOB_EFFECT, MeteorMod.MOD_ID);

    public static final DeferredHolder<MobEffect, MobEffect> ELECTRIFIED =
            EFFECTS.register("electrified", ElectrifiedEffect::new);
}
