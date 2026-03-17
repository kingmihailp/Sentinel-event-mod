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

        if (level().isClientSide()) {
            // Client-side visual particles — animation position is computed analytically
            // matching the idle/scan keyframes in sentinel.animation.json.
            if (tickCount % 2 == 0) spawnClientNozzleParticles();
            if (entityData.get(SCANNING) && tickCount % 5 == 0) spawnClientScanParticles();
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level();

        // ── Scanning state machine ────────────────────────────────────────────
        if (scanTimer > 0) {
            entityData.set(SCANNING, true);
            scanTimer--;

            if (scanBlockPos != null) {
                // Block scan: look at block, optionally break at end
                Vec3 target = Vec3.atCenterOf(scanBlockPos);
                getLookControl().setLookAt(target.x, target.y, target.z, 30f, 30f);

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
                // Entity scan: look at entity
                if (!scanEntityTarget.isAlive()) {
                    scanEntityTarget = null;
                    scanTimer = 0;
                } else {
                    getLookControl().setLookAt(scanEntityTarget, 30f, 30f);
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

    // ── Client-side particle helpers ──────────────────────────────────────────
    // Animation data from sentinel.animation.json:
    //   idle : ALL bone Y +21 px, X rot +15 deg — period 120 ticks (catmullrom ≈ sine)
    //   scan : HANDS bone rotX only — ALL bone does NOT move during scan
    //
    // Model constants (pixels):
    //   ALL pivot  : (-0.00488, 5.31873,  1.29378)
    //   Nozzle     :  (0,       4.0,      9.0)   — thruster geometry, model +Z = back
    //   Eye        :  (0,       5.5,     -5.5)   — front-face lens, model -Z = front

    /** Approximate ALL bone Y-offset (blocks) from the idle animation. */
    private float idleAnimY() {
        // scan animation plays while SCANNING=true; no ALL-bone Y movement during scan
        if (entityData.get(SCANNING)) return 0f;
        float phase = (tickCount % 120) / 120.0f;
        return (21.0f / 16.0f) * (float) Math.sin(Math.PI * phase);
    }

    /** Approximate ALL bone X-rotation (radians) from the idle animation. */
    private float idleAnimRotX() {
        if (entityData.get(SCANNING)) return 0f;
        float phase = (tickCount % 120) / 120.0f;
        return (float) Math.toRadians(15.0f * Math.sin(Math.PI * phase));
    }

    /**
     * Compute a model-space point's world position after applying
     * the ALL bone's animated translation + X rotation.
     *
     * @param modelY  absolute model Y of the point (pixels)
     * @param modelZ  absolute model Z of the point (pixels)
     * @return  double[3] = {worldX, worldY, worldZ}
     */
    private double[] bonePointToWorld(float modelY, float modelZ) {
        float allPivY  = 5.31873f;
        float allPivZ  = 1.29378f;
        float animY    = idleAnimY() * 16.0f;   // back to pixels
        float rotX     = idleAnimRotX();
        float cosR     = (float) Math.cos(rotX);
        float sinR     = (float) Math.sin(rotX);

        float dy = modelY - allPivY;
        float dz = modelZ - allPivZ;

        float finalY_px = (allPivY + animY) + dy * cosR - dz * sinR;
        float finalZ_px = allPivZ           + dy * sinR + dz * cosR;

        // model +Z = entity back;  world: x += sin(yaw)*z_m,  z -= cos(yaw)*z_m
        float yaw = (float) Math.toRadians(getYRot());
        return new double[]{
            getX() + (finalZ_px / 16.0) *  Math.sin(yaw),
            getY() +  finalY_px / 16.0,
            getZ() + (finalZ_px / 16.0) * -Math.cos(yaw)
        };
    }

    /** FLAME trail from the nozzle (model z=+9, y=4). */
    private void spawnClientNozzleParticles() {
        double[] p = bonePointToWorld(4.0f, 9.0f);
        for (int i = 0; i < 2; i++) {
            level().addParticle(ParticleTypes.FLAME, p[0], p[1], p[2],
                    (random.nextDouble() - 0.5) * 0.06,
                    (random.nextDouble() - 0.5) * 0.06,
                    (random.nextDouble() - 0.5) * 0.06);
        }
    }

    /** ELECTRIC_SPARK beam from the eye (model z=-5.5, y=5.5) toward look target. */
    private void spawnClientScanParticles() {
        double[] p = bonePointToWorld(5.5f, -5.5f);
        float headYaw   = (float) Math.toRadians(getYHeadRot());
        float headPitch = (float) Math.toRadians(getXRot());
        double lx = -Math.sin(headYaw) * Math.cos(headPitch);
        double ly = -Math.sin(headPitch);
        double lz =  Math.cos(headYaw) * Math.cos(headPitch);
        for (int i = 0; i < 8; i++) {
            level().addParticle(ParticleTypes.ELECTRIC_SPARK, p[0], p[1], p[2],
                    lx * 0.3 + (random.nextDouble() - 0.5) * 0.1,
                    ly * 0.3 + (random.nextDouble() - 0.5) * 0.1,
                    lz * 0.3 + (random.nextDouble() - 0.5) * 0.1);
        }
    }

    // ── Public accessor ───────────────────────────────────────────────────────
    public boolean isScanning() {
        return entityData.get(SCANNING);
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
            double x = sentinel.getX() + (sentinel.random.nextDouble() - 0.5) * 12.0;
            // Symmetric Y range: ±2 blocks around current height — bee-like horizontal exploration
            double y = sentinel.getY() + (sentinel.random.nextDouble() * 2.0 - 1.0) * 2.0;
            double z = sentinel.getZ() + (sentinel.random.nextDouble() - 0.5) * 12.0;
            // Keep within world bounds
            y = Math.max(sentinel.level().getMinBuildHeight() + 5.0,
                    Math.min(y, sentinel.level().getMaxBuildHeight() - 20.0));
            sentinel.getMoveControl().setWantedPosition(x, y, z, 0.8);
        }
    }
}
