package com.example.meteormod.entity;

import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.animal.Bee;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class MeteorEntity extends Entity {

    private static final double GRAVITY = 0.08;
    private static final double MAX_FALL_SPEED = 2.0;
    private boolean exploded = false;

    public MeteorEntity(EntityType<?> entityType, Level level) {
        super(entityType, level);
        this.setNoGravity(true); // We handle gravity manually
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        // No synced data needed
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        // Nothing to load
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        // Nothing to save
    }

    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public void tick() {
        super.tick();

        // Apply gravity
        Vec3 velocity = this.getDeltaMovement();
        double newVY = Math.max(velocity.y - GRAVITY, -MAX_FALL_SPEED);
        this.setDeltaMovement(velocity.x, newVY, velocity.z);

        // Move the entity
        this.move(MoverType.SELF, this.getDeltaMovement());

        // Spawn trail particles every tick on server side
        if (this.level() instanceof ServerLevel serverLevel) {
            Vec3 pos = this.position();

            // Fire particles — main trail
            serverLevel.sendParticles(
                    ParticleTypes.FLAME,
                    pos.x, pos.y + 0.5, pos.z,
                    6, 0.25, 0.25, 0.25, 0.04
            );

            // Large smoke — upper trail behind the meteor
            serverLevel.sendParticles(
                    ParticleTypes.LARGE_SMOKE,
                    pos.x, pos.y + 1.0, pos.z,
                    4, 0.3, 0.3, 0.3, 0.01
            );

            // Small smoke — lingering cloud
            if (this.tickCount % 3 == 0) {
                serverLevel.sendParticles(
                        ParticleTypes.CAMPFIRE_COSY_SMOKE,
                        pos.x, pos.y + 1.5, pos.z,
                        2, 0.4, 0.4, 0.4, 0.005
                );
            }
        }

        // Discard if out of world bounds
        if (this.getY() < this.level().getMinBuildHeight() - 64) {
            this.discard();
            return;
        }

        // Check for ground collision
        if (!exploded && (this.onGround() || this.verticalCollision)) {
            exploded = true;
            if (!this.level().isClientSide()) {
                onImpact();
            }
            this.discard();
        }
    }

    private void onImpact() {
        Level level = this.level();
        double x = this.getX();
        double y = this.getY();
        double z = this.getZ();

        // Create a small explosion (destroys some blocks, deals damage)
        level.explode(
                this,
                x, y, z,
                2.5f,
                Level.ExplosionInteraction.BLOCK
        );

        // Spawn 1–4 bees near impact point
        if (level instanceof ServerLevel serverLevel) {
            int beeCount = 1 + serverLevel.random.nextInt(4);
            for (int i = 0; i < beeCount; i++) {
                Bee bee = new Bee(EntityType.BEE, level);
                double offsetX = (serverLevel.random.nextDouble() - 0.5) * 4.0;
                double offsetZ = (serverLevel.random.nextDouble() - 0.5) * 4.0;
                bee.setPos(x + offsetX, y + 1.0, z + offsetZ);
                bee.setHealth(bee.getMaxHealth());
                serverLevel.addFreshEntity(bee);
            }
        }
    }
}
