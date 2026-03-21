package com.example.meteormod.entity;

import com.example.meteormod.particle.ModParticles;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class SentinelBulletEntity extends ThrowableProjectile {

    public SentinelBulletEntity(EntityType<? extends SentinelBulletEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    // ThrowableProjectile does not override defineSynchedData in NeoForge 1.21.1,
    // so calling super would hit the abstract Entity method directly (illegal).
    // Bullet has no synced data of its own.
    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
    }

    @Override
    public void tick() {
        // Save velocity before super.tick() applies ThrowableProjectile's 0.99 drag
        Vec3 vel = getDeltaMovement();

        super.tick(); // moves entity, runs hit detection, scales velocity by inertia

        // Restore pre-tick velocity: bullet travels at constant speed (no drag)
        setDeltaMovement(vel);

        // Animated sentinel_bullet particle trail, visible on the client
        if (level().isClientSide()) {
            level().addParticle(ModParticles.SENTINEL_BULLET.get(),
                    getX(), getY(), getZ(), 0, 0, 0);
            level().addParticle(ModParticles.SENTINEL_BULLET.get(),
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
