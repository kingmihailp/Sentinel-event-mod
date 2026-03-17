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
import net.minecraft.Util;
import net.minecraft.core.particles.DustColorTransitionOptions;
import net.minecraft.core.particles.ParticleTypes;
import org.joml.Vector3f;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;

public class SentinelEntity extends PathfinderMob implements GeoEntity {

    // ── Synced data ──────────────────────────────────────────────────────────
    private static final EntityDataAccessor<Boolean> SCANNING =
            SynchedEntityData.defineId(SentinelEntity.class, EntityDataSerializers.BOOLEAN);

    private static final RawAnimation ANIM_IDLE = RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    // ── Scan particles: yellow → red dust transition ─────────────────────────
    private static final DustColorTransitionOptions SCAN_DUST =
            new DustColorTransitionOptions(
                    new Vector3f(1.0f, 0.9f, 0.0f),  // yellow
                    new Vector3f(1.0f, 0.05f, 0.0f), // red
                    1.5f);

    // Wall-clock start time for animation phase sync with GeckoLib
    private long spawnMs = -1;

    // ── Scan state ───────────────────────────────────────────────────────────
    private static final int SCAN_DURATION     = 60;
    private static final int SCAN_COOLDOWN_MIN = 120;
    private static final int SCAN_COOLDOWN_RND = 100;

    private int      scanTimer        = 0;
    private int      scanCooldown     = SCAN_COOLDOWN_MIN;
    private BlockPos scanBlockPos     = null;
    private Entity   scanEntityTarget = null;

    // ── Manual flight state ──────────────────────────────────────────────────
    private Vec3 flyTarget  = null;
    private int  retargetIn = 0;

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
            if (spawnMs < 0) spawnMs = Util.getMillis(); // init animation clock
            // Nozzle trail every 2 ticks
            if (tickCount % 2 == 0) spawnClientNozzleParticles();
            // Scan ring-wave pulse every 10 ticks while actively scanning
            if (entityData.get(SCANNING) && tickCount % 10 == 0) spawnClientScanParticles();
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level();

        // Flight first — scan logic may override head yaw afterward
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

    // ── Direct bee-like flight via deltaMovement ──────────────────────────────
    private void tickManualFlight() {
        setNoGravity(true);

        // While scanning: decelerate and stop so the entity looks at the target
        if (entityData.get(SCANNING)) {
            setDeltaMovement(getDeltaMovement().scale(0.8));
            return; // do NOT touch yHeadRot — scan logic controls the look direction
        }

        // Pick a new wander target
        if (flyTarget == null || --retargetIn <= 0 || distanceToSqr(flyTarget) < 3.0) {
            double tx = getX() + (random.nextDouble() - 0.5) * 14.0;
            // ±4 blocks vertically — allows proper up AND down movement
            double ty = getY() + (random.nextDouble() * 2.0 - 1.0) * 4.0;
            double tz = getZ() + (random.nextDouble() - 0.5) * 14.0;
            ty = Math.max(level().getMinBuildHeight() + 5.0,
                    Math.min(ty, level().getMaxBuildHeight() - 10.0));
            flyTarget  = new Vec3(tx, ty, tz);
            retargetIn = 50 + random.nextInt(70);
        }

        Vec3 toTarget = flyTarget.subtract(position());
        double dist   = toTarget.length();

        if (dist > 0.1) {
            double speed = Math.min(0.12, dist * 0.05 + 0.03);
            Vec3   dir   = toTarget.normalize().scale(speed);
            Vec3   cur   = getDeltaMovement();

            setDeltaMovement(
                cur.x * 0.75 + dir.x * 0.25,
                // Less inertia on Y so the entity actually descends/ascends
                cur.y * 0.55 + dir.y * 0.45,
                cur.z * 0.75 + dir.z * 0.25
            );

            // Face the direction of travel
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

    /**
     * Returns the idle-animation phase [0, 1) over the 6-second cycle.
     * Uses wall-clock time (same clock as GeckoLib) to avoid tick-rate drift.
     */
    private double idleAnimPhase() {
        if (spawnMs < 0) spawnMs = Util.getMillis();
        return ((Util.getMillis() - spawnMs) % 6000L) / 6000.0;
    }

    /**
     * Transforms a model-space point (in pixels) through the ALL-bone idle
     * animation (Y translation + X rotation) and returns world coordinates.
     *
     * Model→world axis mapping (GeckoLib/Minecraft entity rendering, 180° Y flip):
     *   model −Z → entity forward,  model −X → entity right,  model +Y → world up
     */
    private double[] animWorldPos(double mx, double my, double mz) {
        double phase = idleAnimPhase();
        // Catmullrom 0→21→0 px Y and 0→15→0° X — both approximate a half-cosine
        double animYpx  = 10.5 * (1.0 - Math.cos(2 * Math.PI * phase));
        double animXRad = Math.toRadians(7.5 * (1.0 - Math.cos(2 * Math.PI * phase)));

        // ALL-bone pivot (pixels)
        final double px = -0.00488, py = 5.31873, pz = 1.29378;

        // Translate to pivot-relative space
        double relY = my - py;
        double relZ = mz - pz;

        // Apply X rotation (tilts model forward/backward)
        double cosA = Math.cos(animXRad), sinA = Math.sin(animXRad);
        double rotY  = relY * cosA - relZ * sinA;
        double rotZ  = relY * sinA + relZ * cosA;

        // Final model-space coords with Y animation offset
        double fz = rotZ + pz;           // Z after rotation + pivot restore
        double fy = rotY + py + animYpx; // Y after rotation + pivot + anim
        double fx = (mx - px) + px;      // X unchanged (no X anim on ALL bone)

        // Convert to world using entity yaw
        float  yawRad = (float) Math.toRadians(getYRot());
        double sinYaw = Math.sin(yawRad), cosYaw = Math.cos(yawRad);

        return new double[]{
            getX() + (fz / 16.0) * sinYaw - (fx / 16.0) * cosYaw,
            getY() + fy / 16.0,
            getZ() - (fz / 16.0) * cosYaw - (fx / 16.0) * sinYaw
        };
    }

    /**
     * FLAME trail from the nozzle at the back of the model.
     * Nozzle model-space center: (0, 5.0, 10.0) px (cluster of cubes at z≈9-11).
     * Position is fully animation-aware — no drift vs GeckoLib.
     */
    private void spawnClientNozzleParticles() {
        double[] p = animWorldPos(0.0, 5.0, 10.0);
        for (int i = 0; i < 2; i++) {
            level().addParticle(ParticleTypes.FLAME, p[0], p[1], p[2],
                    (random.nextDouble() - 0.5) * 0.07,
                    (random.nextDouble() - 0.5) * 0.07,
                    (random.nextDouble() - 0.5) * 0.07);
        }
    }

    /**
     * Yellow-to-red dust_color_transition RING WAVE from the eye.
     * Eye model-space center: (0, 5.75, −5.75) px (innermost eye face plate).
     * 20 particles arranged in a ring perpendicular to the look direction,
     * all flying forward — creates a clearly visible expanding pulse wave.
     * Fires every 10 ticks so each ring is visually distinct.
     */
    private void spawnClientScanParticles() {
        // Animation-accurate eye position
        double[] ep = animWorldPos(0.0, 5.75, -5.75);
        double ex = ep[0], ey = ep[1], ez = ep[2];

        // Look direction from head yaw + pitch (synced from server)
        float  headYaw   = (float) Math.toRadians(getYHeadRot());
        float  headPitch = (float) Math.toRadians(getXRot());
        double lx = -Math.sin(headYaw) * Math.cos(headPitch);
        double ly = -Math.sin(headPitch);
        double lz =  Math.cos(headYaw) * Math.cos(headPitch);

        // Two perpendicular vectors that span the ring plane:
        // rightVec — horizontal, perpendicular to look direction
        // upVec    — world up (0, 1, 0)
        double rx = Math.cos(headYaw), ry = 0.0, rz = Math.sin(headYaw);

        final int   COUNT      = 20;
        final double FWD_SPEED = 0.60;
        final double RAD_SPEED = 0.15;
        final double RING_R    = 0.25; // spawn radius (blocks)

        for (int i = 0; i < COUNT; i++) {
            double angle = (2.0 * Math.PI / COUNT) * i;
            double ca = Math.cos(angle), sa = Math.sin(angle);

            // Radial direction in the (right, up) plane
            double rdx = rx * ca; // up component: (0, sa, 0)
            double rdy = sa;
            double rdz = rz * ca;

            level().addParticle(SCAN_DUST,
                    ex + rdx * RING_R, ey + rdy * RING_R, ez + rdz * RING_R,
                    lx * FWD_SPEED + rdx * RAD_SPEED,
                    ly * FWD_SPEED + rdy * RAD_SPEED,
                    lz * FWD_SPEED + rdz * RAD_SPEED);
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
