package com.example.meteormod.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;

public class SentinelBulletEntity extends ThrowableProjectile {

    public SentinelBulletEntity(EntityType<? extends SentinelBulletEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    /** No drag — bullet keeps constant speed throughout its lifetime. */
    @Override
    protected float getInertia() {
        return 1.0f;
    }

    @Override
    public void tick() {
        super.tick();

        // Blue soul-fire particle trail, visible on the client
        if (level().isClientSide()) {
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    getX(), getY(), getZ(), 0, 0, 0);
            level().addParticle(ParticleTypes.SOUL_FIRE_FLAME,
                    getX() + (random.nextDouble() - 0.5) * 0.12,
                    getY() + (random.nextDouble() - 0.5) * 0.12,
                    getZ() + (random.nextDouble() - 0.5) * 0.12,
                    0, 0, 0);
        }

        if (tickCount > 80) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide() && result.getEntity() instanceof LivingEntity target) {
            Entity owner = getOwner();
            target.hurt(
                    damageSources().mobProjectile(this, owner instanceof LivingEntity le ? le : null),
                    3.0f   // 1.5 hearts
            );
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        discard();
    }

    /** Don't hit other Sentinels — only players and other mobs. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        return super.canHitEntity(entity) && !(entity instanceof SentinelEntity);
    }

    /** Bullets are transient — never saved to world NBT. */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
