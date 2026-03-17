package com.example.meteormod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.Goal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.minecraft.core.particles.ParticleTypes;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumSet;
import java.util.List;

public class SentinelEntity extends PathfinderMob implements GeoEntity {

    // ── Synced data ──────────────────────────────────────────────────────────
    private static final EntityDataAccessor<Boolean> SCANNING =
            SynchedEntityData.defineId(SentinelEntity.class, EntityDataSerializers.BOOLEAN);

    // ── Animations (names match keys in sentinel.animation.json) ────────────
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");
    private static final RawAnimation ANIM_SCAN = RawAnimation.begin().thenLoop("scan");

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    // ── Scan state ───────────────────────────────────────────────────────────
    private static final int SCAN_DURATION     = 60;   // ticks of active scanning
    private static final int SCAN_COOLDOWN_MIN = 120;  // min ticks between scans
    private static final int SCAN_COOLDOWN_RND = 100;  // random extra ticks

    private int    scanTimer        = 0;
    private int    scanCooldown     = SCAN_COOLDOWN_MIN;
    private BlockPos scanBlockPos   = null;
    private Entity   scanEntityTarget = null;

    // ────────────────────────────────────────────────────────────────────────

    public SentinelEntity(EntityType<? extends SentinelEntity> type, Level level) {
        super(type, level);
        this.moveControl = new FlyingMoveControl(this, 20, true);
        this.setNoGravity(true);
    }

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        super.defineSynchedData(builder);
        builder.define(SCANNING, false);
    }

    // ── Flying navigation ────────────────────────────────────────────────────
    @Override
    protected PathNavigation createNavigation(Level level) {
        FlyingPathNavigation nav = new FlyingPathNavigation(this, level);
        nav.setCanOpenDoors(false);
        nav.setCanFloat(true);
        nav.setCanPassDoors(true);
        return nav;
    }

    // ── Attributes ───────────────────────────────────────────────────────────
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,     30.0)
                .add(Attributes.MOVEMENT_SPEED,  0.18)   // slow, bee-like
                .add(Attributes.FLYING_SPEED,    0.6)    // required by FlyingMoveControl
                .add(Attributes.ATTACK_DAMAGE,   4.0)
                .add(Attributes.FOLLOW_RANGE,   16.0)
                .add(Attributes.ARMOR,           2.0);
    }

    // ── Goals ────────────────────────────────────────────────────────────────
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(2, new RandomFlyWanderGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // No fall damage for a flying entity
    @Override
    public void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    // ── Main tick ─────────────────────────────────────────────────────────────
    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) return;
        ServerLevel serverLevel = (ServerLevel) level();

        // Nozzle fire trail every 2 ticks
        if (tickCount % 2 == 0) {
            spawnNozzleParticles(serverLevel);
        }

        // ── Scanning state machine ────────────────────────────────────────────
        if (scanTimer > 0) {
            entityData.set(SCANNING, true);
            scanTimer--;

            if (scanBlockPos != null) {
                // Block scan: look at block, emit particles, optionally break at end
                Vec3 target = Vec3.atCenterOf(scanBlockPos);
                getLookControl().setLookAt(target.x, target.y, target.z, 30f, 30f);

                if (scanTimer % 5 == 0) {
                    spawnScanParticles(serverLevel, target);
                }

                if (scanTimer == 0) {
                    BlockState state = serverLevel.getBlockState(scanBlockPos);
                    if (!state.isAir() && state.getFluidState().isEmpty()) {
                        if (random.nextBoolean()) {
                            // Pick up (break without drop)
                            serverLevel.destroyBlock(scanBlockPos, false);
                        }
                        // else: leave the block alone
                    }
                    scanBlockPos = null;
                }

            } else if (scanEntityTarget != null) {
                // Entity scan: look at entity, emit particles
                if (!scanEntityTarget.isAlive()) {
                    scanEntityTarget = null;
                    scanTimer = 0;
                } else {
                    getLookControl().setLookAt(scanEntityTarget, 30f, 30f);
                    if (scanTimer % 5 == 0) {
                        spawnScanParticles(serverLevel, scanEntityTarget.getEyePosition());
                    }
                }
            } else {
                scanTimer = 0;
            }

            if (scanTimer == 0) {
                entityData.set(SCANNING, false);
                scanCooldown = SCAN_COOLDOWN_MIN + random.nextInt(SCAN_COOLDOWN_RND);
            }

        } else {
            // Waiting between scans
            entityData.set(SCANNING, false);
            if (--scanCooldown <= 0) {
                startScan(serverLevel);
            }
        }
    }

    // ── Find a target and begin scan ─────────────────────────────────────────
    private void startScan(ServerLevel serverLevel) {
        // 40% chance: scan a nearby entity
        if (random.nextFloat() < 0.4f) {
            List<Entity> nearby = serverLevel.getEntities(this, getBoundingBox().inflate(8.0));
            if (!nearby.isEmpty()) {
                scanEntityTarget = nearby.get(random.nextInt(nearby.size()));
                scanBlockPos     = null;
                scanTimer        = SCAN_DURATION;
                return;
            }
        }

        // Find a nearby solid block
        BlockPos center = blockPosition();
        for (int attempt = 0; attempt < 15; attempt++) {
            BlockPos candidate = center.offset(
                    random.nextInt(9) - 4,
                    random.nextInt(5) - 2,
                    random.nextInt(9) - 4
            );
            BlockState state = serverLevel.getBlockState(candidate);
            if (!state.isAir() && state.getFluidState().isEmpty()) {
                scanBlockPos     = candidate;
                scanEntityTarget = null;
                scanTimer        = SCAN_DURATION;
                return;
            }
        }

        // Nothing found — short retry delay
        scanCooldown = 40;
    }

    // ── Blue wave from the eye (front face of model, negative Z) ─────────────
    private void spawnScanParticles(ServerLevel serverLevel, Vec3 target) {
        // Eye: ~0.3 blocks in front of entity center, slightly below eye height
        float  yaw  = (float) Math.toRadians(getYRot());
        double fwdX = -Math.sin(yaw);
        double fwdZ =  Math.cos(yaw);

        double eyeX = getX() + fwdX * 0.3;
        double eyeY = getEyeY() - 0.15;
        double eyeZ = getZ() + fwdZ * 0.3;

        // Direction toward scan target, spread into a small cone
        Vec3 dir = target.subtract(eyeX, eyeY, eyeZ).normalize().scale(0.35);

        // count=0 → offsets used as exact velocity (directional particles)
        for (int i = 0; i < 8; i++) {
            double vx = dir.x + (random.nextDouble() - 0.5) * 0.12;
            double vy = dir.y + (random.nextDouble() - 0.5) * 0.12;
            double vz = dir.z + (random.nextDouble() - 0.5) * 0.12;
            serverLevel.sendParticles(ParticleTypes.SOUL_FIRE_FLAME,
                    eyeX, eyeY, eyeZ, 0, vx, vy, vz, 1.0);
        }
    }

    // ── Fire trail from the nozzle (back of model, positive Z) ───────────────
    private void spawnNozzleParticles(ServerLevel serverLevel) {
        // Nozzle: ~0.45 blocks behind entity center, near the bottom
        float  yaw  = (float) Math.toRadians(getYRot());
        double backX =  Math.sin(yaw);
        double backZ = -Math.cos(yaw);

        double nx = getX() + backX * 0.45;
        double ny = getY() + 0.2;
        double nz = getZ() + backZ * 0.45;

        serverLevel.sendParticles(ParticleTypes.FLAME,
                nx, ny, nz, 2, 0.04, 0.04, 0.04, 0.02);
    }

    // ── GeckoLib ─────────────────────────────────────────────────────────────
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "main", 5, state -> {
            if (entityData.get(SCANNING)) {
                return state.setAndContinue(ANIM_SCAN);
            }
            return state.setAndContinue(ANIM_IDLE);
        }));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animCache;
    }

    // ── NBT ───────────────────────────────────────────────────────────────────
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
        tag.putInt("ScanTimer",    scanTimer);
        tag.putInt("ScanCooldown", scanCooldown);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        scanTimer    = tag.getInt("ScanTimer");
        scanCooldown = tag.getInt("ScanCooldown");
    }

    // ── Inner goal: random 3-D flying wander (like bee) ──────────────────────
    static class RandomFlyWanderGoal extends Goal {

        private final SentinelEntity sentinel;

        RandomFlyWanderGoal(SentinelEntity sentinel) {
            this.sentinel = sentinel;
            setFlags(EnumSet.of(Flag.MOVE));
        }

        @Override
        public boolean canUse() {
            return !sentinel.getMoveControl().hasWanted()
                    && sentinel.random.nextInt(10) == 0;
        }

        @Override
        public boolean canContinueToUse() {
            return sentinel.getMoveControl().hasWanted();
        }

        @Override
        public void start() {
            double x = sentinel.getX() + (sentinel.random.nextDouble() - 0.5) * 10.0;
            double y = sentinel.getY() + (sentinel.random.nextDouble() - 0.5) * 4.0 + 1.5;
            double z = sentinel.getZ() + (sentinel.random.nextDouble() - 0.5) * 10.0;
            y = Math.max(y, sentinel.level().getMinBuildHeight() + 8.0);
            sentinel.getMoveControl().setWantedPosition(x, y, z, 0.8);
        }
    }
}
