package com.example.meteormod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.Vec3;

public class TurretBulletEntity extends ThrowableProjectile {

    private static final int   MAX_TICKS = 60;
    private static final float SPEED     = 3.0f;  // blocks per tick

    public TurretBulletEntity(EntityType<? extends TurretBulletEntity> type, Level level) {
        super(type, level);
        this.setNoGravity(true);
    }

    /** Convenience constructor used by SentinelHoverEntity.shootTurrets(). */
    public TurretBulletEntity(Level level, LivingEntity owner, Vec3 origin, Vec3 direction) {
        this(ModEntities.TURRET_BULLET.get(), level);
        this.setOwner(owner);
        this.setPos(origin);
        this.setDeltaMovement(direction.normalize().scale(SPEED));
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    public void tick() {
        Vec3 vel = getDeltaMovement();  // save before ThrowableProjectile applies drag
        super.tick();
        setDeltaMovement(vel);          // restore – bullet travels at constant speed

        if (tickCount > MAX_TICKS) discard();
    }

    // ── Hit callbacks ─────────────────────────────────────────────────────

    @Override
    protected void onHitEntity(EntityHitResult result) {
        if (!level().isClientSide()) {
            Entity target = result.getEntity();
            if (target instanceof LivingEntity living) {
                // 8–12 hearts = 16–24 damage points
                float damage = 16.0f + random.nextFloat() * 8.0f;
                LivingEntity attacker = getOwner() instanceof LivingEntity le ? le : null;
                living.hurt(damageSources().mobProjectile(this, attacker), damage);
                living.setRemainingFireTicks(40);
            }
        }
        discard();
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        if (!level().isClientSide()) {
            // Place soul fire on the face the bullet hit (if empty)
            BlockPos placePos = result.getBlockPos().relative(result.getDirection());
            if (level().isEmptyBlock(placePos)) {
                level().setBlock(placePos, Blocks.SOUL_FIRE.defaultBlockState(),
                        net.minecraft.world.level.block.Block.UPDATE_ALL);
            }
        }
        discard();
    }

    // ── Misc ──────────────────────────────────────────────────────────────

    /** Don't hit the hover vehicle or its pilot. */
    @Override
    protected boolean canHitEntity(Entity entity) {
        if (entity == getOwner()) return false;
        if (entity instanceof SentinelHoverEntity) return false;
        return super.canHitEntity(entity);
    }

    @Override
    public boolean shouldBeSaved() { return false; }
}
