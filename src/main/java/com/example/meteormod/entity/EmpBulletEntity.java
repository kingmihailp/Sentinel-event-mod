package com.example.meteormod.entity;

import com.example.meteormod.effect.ModEffects;
import com.example.meteormod.particle.ModParticles;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class EmpBulletEntity extends ThrowableProjectile {

    /** 2 hearts for regular targets */
    private static final float NORMAL_DAMAGE   = 4.0f;
    /** 8 hearts for Iron Golems and Sentinels */
    private static final float HEAVY_DAMAGE    = 16.0f;
    /** 10 seconds (200 ticks) of electrification */
    private static final int   EFFECT_DURATION = 200;

    public EmpBulletEntity(EntityType<? extends EmpBulletEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    /** Used when fired by the player's cannon item. */
    public static EmpBulletEntity create(Level level, LivingEntity shooter) {
        EmpBulletEntity bullet = new EmpBulletEntity(ModEntities.EMP_BULLET.get(), level);
        bullet.setOwner(shooter);
        bullet.setPos(shooter.getX(), shooter.getEyeY() - 0.1, shooter.getZ());
        return bullet;
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        Vec3 vel = getDeltaMovement();
        super.tick();
        setDeltaMovement(vel); // ThrowableProjectile applies gravity — override to keep no-gravity

        if (level().isClientSide()) {
            // EMP spark trail — electric blue particles
            level().addParticle(ModParticles.EMP_BULLET.get(),
                    getX(), getY(), getZ(), 0, 0, 0);
            level().addParticle(ModParticles.EMP_BULLET.get(),
                    getX() + (random.nextDouble() - 0.5) * 0.1,
                    getY() + (random.nextDouble() - 0.5) * 0.1,
                    getZ() + (random.nextDouble() - 0.5) * 0.1,
                    0, 0, 0);
        }

        if (tickCount > 100) discard();
    }

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide() && result.getEntity() instanceof LivingEntity target) {
            Entity owner = getOwner();
            boolean heavyTarget = target instanceof IronGolem || target instanceof SentinelEntity;
            float damage = heavyTarget ? HEAVY_DAMAGE : NORMAL_DAMAGE;

            target.hurt(
                    damageSources().mobProjectile(this, owner instanceof LivingEntity le ? le : null),
                    damage
            );
            target.addEffect(new MobEffectInstance(ModEffects.ELECTRIFIED, EFFECT_DURATION, 0));
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        discard();
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }
}
