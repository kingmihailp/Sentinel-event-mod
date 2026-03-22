package com.example.meteormod.entity;

import com.example.meteormod.effect.ModEffects;
import com.example.meteormod.particle.ModParticles;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LightningBolt;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.IronGolem;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;

public class EmpBulletEntity extends ThrowableProjectile {

    private static final float NORMAL_DAMAGE   = 4.0f;
    private static final float HEAVY_DAMAGE    = 16.0f;
    private static final int   EFFECT_DURATION = 200;

    /** Max number of enemies that receive chain lightning. */
    private static final int   CHAIN_LIMIT     = 5;
    /** Radius of chain lightning spread in blocks. */
    private static final double CHAIN_RADIUS   = 4.5;

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

    // ── On hit entity ───────────────────────────────────────────────────────

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide()
                && result.getEntity() instanceof LivingEntity target
                && level() instanceof ServerLevel serverLevel) {

            Entity owner = getOwner();
            applyEmpHit(serverLevel, target, owner);

            boolean raining    = serverLevel.isRaining();
            boolean inWater    = target.isInWater();
            boolean thundering = serverLevel.isThundering();

            // Rain or water → chain lightning to nearby enemies
            if (raining || inWater) {
                chainLightning(serverLevel, target, owner);
            }

            // Thunderstorm → real damaging lightning bolt at impact point
            if (thundering) {
                spawnLightning(serverLevel,
                        target.getX(), target.getY(), target.getZ(), false);
            }
        }
        discard();
    }

    // ── On hit block ────────────────────────────────────────────────────────

    @Override
    protected void onHitBlock(BlockHitResult result) {
        // Thunderstorm → real damaging lightning bolt where bullet hit ground
        if (!level().isClientSide()
                && level() instanceof ServerLevel serverLevel
                && serverLevel.isThundering()) {
            spawnLightning(serverLevel,
                    result.getBlockPos().getX() + 0.5,
                    result.getBlockPos().getY() + 1.0,
                    result.getBlockPos().getZ() + 0.5,
                    false);
        }
        discard();
    }

    // ── EMP damage + electrification ────────────────────────────────────────

    private void applyEmpHit(ServerLevel level, LivingEntity target, Entity owner) {
        boolean heavy = target instanceof IronGolem || target instanceof SentinelEntity;
        float damage  = heavy ? HEAVY_DAMAGE : NORMAL_DAMAGE;
        target.hurt(
                damageSources().mobProjectile(this, owner instanceof LivingEntity le ? le : null),
                damage
        );
        target.addEffect(new MobEffectInstance(ModEffects.ELECTRIFIED, EFFECT_DURATION, 0));
    }

    // ── Chain lightning ─────────────────────────────────────────────────────

    /**
     * Finds up to {@value CHAIN_LIMIT} living entities within {@value CHAIN_RADIUS}
     * blocks of the primary target, damages and electrifies them, and shows a
     * visual-only lightning bolt + particle arc between each pair.
     */
    private void chainLightning(ServerLevel level, LivingEntity origin, Entity owner) {
        List<LivingEntity> nearby = level.getEntitiesOfClass(
                LivingEntity.class,
                origin.getBoundingBox().inflate(CHAIN_RADIUS),
                e -> e != origin && e != owner && e.isAlive()
        );

        int count = 0;
        for (LivingEntity chained : nearby) {
            if (count++ >= CHAIN_LIMIT) break;

            // Damage + electrify the chained target
            boolean heavy = chained instanceof IronGolem || chained instanceof SentinelEntity;
            chained.hurt(damageSources().lightningBolt(), heavy ? HEAVY_DAMAGE : NORMAL_DAMAGE);
            chained.addEffect(new MobEffectInstance(ModEffects.ELECTRIFIED, EFFECT_DURATION, 0));

            // ELECTRIC_SPARK particle arc from origin → chained enemy (no lightning bolt here)
            spawnChainArc(level, origin, chained);
        }
    }

    /**
     * Draws a zigzag ELECTRIC_SPARK arc from {@code from} to {@code to},
     * broadcast to all clients via {@code ServerLevel.sendParticles}.
     */
    private void spawnChainArc(ServerLevel level, LivingEntity from, LivingEntity to) {
        Vec3 start = from.position().add(0, from.getBbHeight() * 0.5, 0);
        Vec3 end   = to.position().add(0, to.getBbHeight() * 0.5, 0);
        int steps  = Math.max(6, (int) (start.distanceTo(end) * 5));

        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            Vec3 p = start.lerp(end, t);
            // Random zigzag perpendicular jitter
            double jx = (random.nextDouble() - 0.5) * 0.5;
            double jy = (random.nextDouble() - 0.5) * 0.5;
            double jz = (random.nextDouble() - 0.5) * 0.5;
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    p.x + jx, p.y + jy, p.z + jz,
                    1, 0, 0, 0, 0.05);
        }
    }

    // ── Lightning bolt helper ───────────────────────────────────────────────

    /**
     * @param visualOnly {@code true} = decorative only; {@code false} = damages
     *                   entities, sets fire, plays thunder (thunderstorm effect).
     */
    private void spawnLightning(ServerLevel level, double x, double y, double z,
                                boolean visualOnly) {
        LightningBolt bolt = new LightningBolt(EntityType.LIGHTNING_BOLT, level);
        bolt.moveTo(x, y, z);
        bolt.setVisualOnly(visualOnly);
        level.addFreshEntity(bolt);
    }

    // ── Client-side mini-lightning trail ────────────────────────────────────

    private void spawnLightningArcs() {
        Vec3 origin = new Vec3(getX(), getY(), getZ());
        level().addParticle(ModParticles.EMP_BULLET.get(), getX(), getY(), getZ(), 0, 0, 0);

        for (int arm = 0; arm < 2; arm++) {
            Vec3 dir = new Vec3(
                    random.nextGaussian(),
                    random.nextGaussian(),
                    random.nextGaussian()
            ).normalize();
            Vec3 pos = origin;

            for (int seg = 0; seg < 5; seg++) {
                double segLen = 0.07 + random.nextDouble() * 0.09;
                Vec3 jitter = new Vec3(
                        (random.nextDouble() - 0.5) * 0.14,
                        (random.nextDouble() - 0.5) * 0.14,
                        (random.nextDouble() - 0.5) * 0.14
                );
                Vec3 next = pos.add(dir.scale(segLen)).add(jitter);
                Vec3 vDir = next.subtract(pos).scale(0.15);
                level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                        pos.x, pos.y, pos.z, vDir.x, vDir.y, vDir.z);

                if (seg == 2 && random.nextFloat() < 0.4f) {
                    Vec3 branchDir = new Vec3(
                            dir.x + (random.nextDouble() - 0.5) * 0.9,
                            dir.y + (random.nextDouble() - 0.5) * 0.9,
                            dir.z + (random.nextDouble() - 0.5) * 0.9
                    ).normalize();
                    Vec3 bPos = pos;
                    for (int b = 0; b < 3; b++) {
                        level().addParticle(ParticleTypes.ELECTRIC_SPARK,
                                bPos.x, bPos.y, bPos.z, 0, 0, 0);
                        bPos = bPos.add(branchDir.scale(0.06));
                    }
                }
                pos = next;
            }
        }
    }

    @Override
    public boolean shouldBeSaved() { return false; }
}
