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

import java.util.List;

public class SentinelEntity extends PathfinderMob implements GeoEntity {

    // ── Synced data ──────────────────────────────────────────────────────────
    private static final EntityDataAccessor<Boolean> SCANNING =
            SynchedEntityData.defineId(SentinelEntity.class, EntityDataSerializers.BOOLEAN);

    // ── Animation — only idle (scan animation removed) ───────────────────────
    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    // ── Scan state ───────────────────────────────────────────────────────────
    private static final int SCAN_DURATION     = 60;
    private static final int SCAN_COOLDOWN_MIN = 120;
    private static final int SCAN_COOLDOWN_RND = 100;

    private int      scanTimer        = 0;
    private int      scanCooldown     = SCAN_COOLDOWN_MIN;
    private BlockPos scanBlockPos     = null;
    private Entity   scanEntityTarget = null;

    // ── Manual flight state ──────────────────────────────────────────────────
    // The goal system + FlyingPathNavigation is unreliable for open-world hovering.
    // We drive movement directly via deltaMovement instead.
    private Vec3 flyTarget    = null;
    private int  retargetIn   = 0;

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
                .add(Attributes.MAX_HEALTH,    30.0)
                .add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.FLYING_SPEED,   0.6)
                .add(Attributes.ATTACK_DAMAGE,  4.0)
                .add(Attributes.FOLLOW_RANGE,  16.0)
                .add(Attributes.ARMOR,          2.0);
    }

    // ── Goals ────────────────────────────────────────────────────────────────
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    @Override
    public void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    // ── Main tick ─────────────────────────────────────────────────────────────
    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            if (tickCount % 2 == 0) spawnClientNozzleParticles();
            if (entityData.get(SCANNING) && tickCount % 5 == 0) spawnClientScanParticles();
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level();

        // ── Bee-like flight driven directly via deltaMovement ─────────────────
        tickManualFlight();

        // ── Scanning state machine ────────────────────────────────────────────
        if (scanTimer > 0) {
            entityData.set(SCANNING, true);
            scanTimer--;

            if (scanBlockPos != null) {
                Vec3 target = Vec3.atCenterOf(scanBlockPos);
                getLookControl().setLookAt(target.x, target.y, target.z, 30f, 30f);

                if (scanTimer == 0) {
                    BlockState state = serverLevel.getBlockState(scanBlockPos);
                    if (!state.isAir() && state.getFluidState().isEmpty()) {
                        if (random.nextBoolean()) {
                            serverLevel.destroyBlock(scanBlockPos, false);
                        }
                    }
                    scanBlockPos = null;
                }

            } else if (scanEntityTarget != null) {
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
            entityData.set(SCANNING, false);
            if (--scanCooldown <= 0) {
                startScan(serverLevel);
            }
        }
    }

    // ── Direct flight: pick random nearby targets, lerp deltaMovement ─────────
    private void tickManualFlight() {
        setNoGravity(true);

        // Pick a new target when none exists, timer runs out, or we've arrived
        if (flyTarget == null || --retargetIn <= 0 || distanceToSqr(flyTarget) < 3.0) {
            double tx = getX() + (random.nextDouble() - 0.5) * 14.0;
            // ±1.5 blocks vertically — explores territory, not sky
            double ty = getY() + (random.nextDouble() * 2.0 - 1.0) * 1.5;
            double tz = getZ() + (random.nextDouble() - 0.5) * 14.0;
            ty = Math.max(level().getMinBuildHeight() + 5.0,
                    Math.min(ty, level().getMaxBuildHeight() - 10.0));
            flyTarget  = new Vec3(tx, ty, tz);
            retargetIn = 50 + random.nextInt(70); // 2.5–6 sec per target
        }

        Vec3 toTarget = flyTarget.subtract(position());
        double dist   = toTarget.length();

        if (dist > 0.1) {
            // Slow down smoothly as we approach; cap at bee-like 0.12 b/t
            double speed = Math.min(0.12, dist * 0.05 + 0.03);
            Vec3   dir   = toTarget.normalize().scale(speed);
            Vec3   cur   = getDeltaMovement();

            // Exponential smoothing — gives the floaty, inertia-heavy bee feel
            setDeltaMovement(
                cur.x * 0.75 + dir.x * 0.25,
                cur.y * 0.75 + dir.y * 0.25,
                cur.z * 0.75 + dir.z * 0.25
            );

            // Face the direction of travel (horizontal only)
            double hLen = Math.sqrt(dir.x * dir.x + dir.z * dir.z);
            if (hLen > 0.002) {
                setYRot((float) Math.toDegrees(Math.atan2(-dir.x, dir.z)));
                yHeadRot = getYRot();
            }
        }
    }

    // ── Find scan target ──────────────────────────────────────────────────────
    private void startScan(ServerLevel serverLevel) {
        if (random.nextFloat() < 0.4f) {
            List<Entity> nearby = serverLevel.getEntities(this, getBoundingBox().inflate(8.0));
            if (!nearby.isEmpty()) {
                scanEntityTarget = nearby.get(random.nextInt(nearby.size()));
                scanBlockPos     = null;
                scanTimer        = SCAN_DURATION;
                return;
            }
        }

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

        scanCooldown = 40;
    }

    // ── Client-side particle helpers ──────────────────────────────────────────
    // Animation constants (sentinel.animation.json):
    //   idle : ALL bone Y +21 px, X rot +15 deg, period 120 ticks
    // Model constants (absolute model coords, pixels):
    //   ALL pivot : (-0.00488, 5.31873, 1.29378)
    //   Nozzle    :  (0, 4.0, 9.0)   — back of model (+Z)
    //   Eye       :  (0, 5.5, -5.5)  — front of model (-Z)

    private float idleAnimY() {
        float phase = (tickCount % 120) / 120.0f;
        return (21.0f / 16.0f) * (float) Math.sin(Math.PI * phase);
    }

    private float idleAnimRotX() {
        float phase = (tickCount % 120) / 120.0f;
        return (float) Math.toRadians(15.0f * Math.sin(Math.PI * phase));
    }

    private double[] bonePointToWorld(float modelY, float modelZ) {
        float allPivY = 5.31873f;
        float allPivZ = 1.29378f;
        float animY   = idleAnimY() * 16.0f;
        float rotX    = idleAnimRotX();
        float cosR    = (float) Math.cos(rotX);
        float sinR    = (float) Math.sin(rotX);

        float dy = modelY - allPivY;
        float dz = modelZ - allPivZ;

        float finalY_px = (allPivY + animY) + dy * cosR - dz * sinR;
        float finalZ_px = allPivZ           + dy * sinR + dz * cosR;

        float yaw = (float) Math.toRadians(getYRot());
        return new double[]{
            getX() + (finalZ_px / 16.0) *  Math.sin(yaw),
            getY() +  finalY_px / 16.0,
            getZ() + (finalZ_px / 16.0) * -Math.cos(yaw)
        };
    }

    private void spawnClientNozzleParticles() {
        double[] p = bonePointToWorld(4.0f, 9.0f);
        for (int i = 0; i < 2; i++) {
            level().addParticle(ParticleTypes.FLAME, p[0], p[1], p[2],
                    (random.nextDouble() - 0.5) * 0.06,
                    (random.nextDouble() - 0.5) * 0.06,
                    (random.nextDouble() - 0.5) * 0.06);
        }
    }

    private void spawnClientScanParticles() {
        double[] p = bonePointToWorld(5.5f, -5.5f);
        float headYaw   = (float) Math.toRadians(getYHeadRot());
        float headPitch = (float) Math.toRadians(getXRot());
        double lx = -Math.sin(headYaw) * Math.cos(headPitch);
        double ly = -Math.sin(headPitch);
        double lz =  Math.cos(headYaw) * Math.cos(headPitch);
        for (int i = 0; i < 8; i++) {
            level().addParticle(ParticleTypes.NAUTILUS, p[0], p[1], p[2],
                    lx * 0.3 + (random.nextDouble() - 0.5) * 0.1,
                    ly * 0.3 + (random.nextDouble() - 0.5) * 0.1,
                    lz * 0.3 + (random.nextDouble() - 0.5) * 0.1);
        }
    }

    // ── Public accessor ───────────────────────────────────────────────────────
    public boolean isScanning() {
        return entityData.get(SCANNING);
    }

    // ── GeckoLib — always idle ────────────────────────────────────────────────
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {
        registrar.add(new AnimationController<>(this, "main", 5,
                state -> state.setAndContinue(ANIM_IDLE)));
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
}
