package com.example.meteormod.entity;

import com.example.meteormod.effect.ModEffects;
import com.example.meteormod.particle.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
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

    private static final float NORMAL_DAMAGE   = 4.0f;
    private static final float HEAVY_DAMAGE    = 16.0f;
    private static final int   EFFECT_DURATION = 200;

    public EmpBulletEntity(EntityType<? extends EmpBulletEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

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
        setDeltaMovement(vel);

        if (level().isClientSide()) {
            spawnLightningArcs();
        }

        if (tickCount > 100) discard();
    }

    /**
     * Spawns 2 branching lightning arms per tick using ELECTRIC_SPARK particles
     * placed along a zigzag path with random sub-branches — creates a "mini
     * lightning bolt" visual emanating from the projectile every frame.
     */
    private void spawnLightningArcs() {
        Vec3 origin = new Vec3(getX(), getY(), getZ());

        // Glowing core
        level().addParticle(ModParticles.EMP_BULLET.get(), getX(), getY(), getZ(), 0, 0, 0);

        // 2 lightning arms
        for (int arm = 0; arm < 2; arm++) {
            // Random initial direction
            Vec3 dir = new Vec3(
                    random.nextGaussian(),
                    random.nextGaussian(),
                    random.nextGaussian()
            ).normalize();

            Vec3 pos = origin;

            // 5 zigzag segments per arm
            for (int seg = 0; seg < 5; seg++) {
                double segLen = 0.07 + random.nextDouble() * 0.09;
                Vec3 jitter = new Vec3(
                        (random.nextDouble() - 0.5) * 0.14,
                        (random.nextDouble() - 0.5) * 0.14,
                        (random.nextDouble() - 0.5) * 0.14
                );
                Vec3 next = pos.add(dir.scale(segLen)).add(jitter);

                // Particle at the vertex with tiny velocity toward the next point
                Vec3 vDir = next.subtract(pos).scale(0.15);
                level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                        pos.x, pos.y, pos.z,
                        vDir.x, vDir.y, vDir.z);

                // Sub-branch at segment 2 (40% chance)
                if (seg == 2 && random.nextFloat() < 0.4f) {
                    Vec3 branchDir = new Vec3(
                            dir.x + (random.nextDouble() - 0.5) * 0.9,
                            dir.y + (random.nextDouble() - 0.5) * 0.9,
                            dir.z + (random.nextDouble() - 0.5) * 0.9
                    ).normalize();
                    Vec3 bPos = pos;
                    for (int b = 0; b < 3; b++) {
                        Vec3 bNext = bPos.add(branchDir.scale(0.06));
                        level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                                bPos.x, bPos.y, bPos.z, 0, 0, 0);
                        bPos = bNext;
                    }
                }

                pos = next;
            }
        }
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
