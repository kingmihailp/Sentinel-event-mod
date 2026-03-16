package com.example.meteormod.entity;

import com.example.meteormod.MeteorMod;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModEntities {

    public static final DeferredRegister<EntityType<?>> ENTITIES =
            DeferredRegister.create(BuiltInRegistries.ENTITY_TYPE, MeteorMod.MOD_ID);

    public static final DeferredHolder<EntityType<?>, EntityType<MeteorEntity>> METEOR =
            ENTITIES.register("meteor", () ->
                    EntityType.Builder.<MeteorEntity>of(MeteorEntity::new, MobCategory.MISC)
                            .sized(1.0f, 1.0f)
                            .clientTrackingRange(128)
                            .updateInterval(1)
                            .build("meteormod:meteor")
            );

    // ── Custom mob with GeckoLib model ──────────────────────────────────────
    // Adjust .sized() to match your model's dimensions.
    public static final DeferredHolder<EntityType<?>, EntityType<SentinelEntity>> SENTINEL =
            ENTITIES.register("sentinel", () ->
                    EntityType.Builder.<SentinelEntity>of(SentinelEntity::new, MobCategory.MONSTER)
                            .sized(0.6f, 1.8f)
                            .clientTrackingRange(64)
                            .build("meteormod:sentinel")
            );
}
