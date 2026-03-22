package com.example.meteormod.sound;

import com.example.meteormod.MeteorMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.neoforged.neoforge.registries.DeferredRegister;

import java.util.function.Supplier;

public class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUNDS =
            DeferredRegister.create(BuiltInRegistries.SOUND_EVENT, MeteorMod.MOD_ID);

    public static final Supplier<SoundEvent> SENTINEL_CANNON_SHOOT        = register("sentinel_cannon_shoot");
    public static final Supplier<SoundEvent> SENTINEL_CANNON_TARGET_AMBIENT = register("sentinel_cannon_target_ambient");

    private static Supplier<SoundEvent> register(String name) {
        ResourceLocation id = ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, name);
        return SOUNDS.register(name, () -> SoundEvent.createVariableRangeEvent(id));
    }
}
