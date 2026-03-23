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

    // ── Sentinel cannon bullet ───────────────────────────────────────────────
    public static final DeferredHolder<EntityType<?>, EntityType<SentinelBulletEntity>> SENTINEL_BULLET =
            ENTITIES.register("sentinel_bullet", () ->
                    EntityType.Builder.<SentinelBulletEntity>of(SentinelBulletEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("meteormod:sentinel_bullet")
            );

    // ── Player EMP cannon bullet ─────────────────────────────────────────────
    public static final DeferredHolder<EntityType<?>, EntityType<EmpBulletEntity>> EMP_BULLET =
            ENTITIES.register("emp_bullet", () ->
                    EntityType.Builder.<EmpBulletEntity>of(EmpBulletEntity::new, MobCategory.MISC)
                            .sized(0.25f, 0.25f)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("meteormod:emp_bullet")
            );

    // ── Sentinel hover vehicle (rideable) ────────────────────────────────────
    public static final DeferredHolder<EntityType<?>, EntityType<SentinelHoverEntity>> SENTINEL_HOVER =
            ENTITIES.register("sentinel_hover", () ->
                    EntityType.Builder.<SentinelHoverEntity>of(SentinelHoverEntity::new, MobCategory.MISC)
                            .sized(2.0f, 0.8f)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("meteormod:sentinel_hover")
            );

    // ── Hover turret laser bullet ────────────────────────────────────────────
    public static final DeferredHolder<EntityType<?>, EntityType<TurretBulletEntity>> TURRET_BULLET =
            ENTITIES.register("turret_bullet", () ->
                    EntityType.Builder.<TurretBulletEntity>of(TurretBulletEntity::new, MobCategory.MISC)
                            .sized(0.15f, 0.15f)
                            .clientTrackingRange(64)
                            .updateInterval(1)
                            .build("meteormod:turret_bullet")
            );
}
