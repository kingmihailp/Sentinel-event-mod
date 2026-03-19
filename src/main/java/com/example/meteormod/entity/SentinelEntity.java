package com.example.meteormod.entity;

import com.example.meteormod.MeteorMod;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.control.FlyingMoveControl;
import net.minecraft.world.entity.ai.goal.FloatGoal;
import net.minecraft.world.entity.ai.goal.LookAtPlayerGoal;
import net.minecraft.world.entity.ai.goal.RandomLookAroundGoal;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.navigation.FlyingPathNavigation;
import net.minecraft.world.entity.ai.navigation.PathNavigation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Explosion;
import net.minecraft.world.level.ExplosionDamageCalculator;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.phys.Vec3;
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

    // ── Captain flag ─────────────────────────────────────────────────────────
    // Set to true when spawned by a meteor. Killing a captain triggers a sentinel raid.
    private boolean captain = false;

    public void  setCaptain(boolean value) { captain = value; }
    public boolean isCaptain()             { return captain;  }

    // ── Scan particles: yellow → red dust transition ─────────────────────────
    private static final DustColorTransitionOptions SCAN_DUST =
            new DustColorTransitionOptions(
                    new Vector3f(1.0f, 0.9f, 0.0f),  // yellow
                    new Vector3f(1.0f, 0.05f, 0.0f), // red
                    1.5f);

    // Animation phase is read directly from GeckoLib's AnimatableManager so it is
    // always in sync with what GeoEntityRenderer computes — no manual tick tracking needed.

    // ── Client-side scan wave state ──────────────────────────────────────────
    // scanWaveAge counts up from 0 each tick while scanning; reset to -1 when done.
    // Direction is locked at scan start so the wave never bends mid-scan.
    private int   scanWaveAge   = -1;
    private float lockedScanYaw   = 0f;
    private float lockedScanPitch = 0f;

    // ── Combat state (server-side) ────────────────────────────────────────────
    private int combatTimer   = 0;  // ticks remaining in combat/shooting mode
    private int shootCooldown = 0;  // ticks until next shot
    private int shotCount     = 0;  // total shots fired — used to alternate cannons

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
                .add(Attributes.MAX_HEALTH,    40.0)
                .add(Attributes.MOVEMENT_SPEED, 0.18)
                .add(Attributes.FLYING_SPEED,   0.6)
                .add(Attributes.ATTACK_DAMAGE,  4.0)
                .add(Attributes.FOLLOW_RANGE,  30.0)
                .add(Attributes.ARMOR,          2.0);
    }

    // ── Loot table ────────────────────────────────────────────────────────────
    // Mob.getLootTable() is final; it calls getDefaultLootTable() when the mob's
    // own lootTable field is null (the normal case for non-NBT-overridden mobs).
    // Overriding getDefaultLootTable() here guarantees the correct key regardless
    // of how EntityType.getDefaultLootTable() resolves the registry lookup.
    @Override
    protected ResourceKey<LootTable> getDefaultLootTable() {
        return ResourceKey.create(Registries.LOOT_TABLE,
                ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "entities/sentinel"));
    }

    // ── Goals ────────────────────────────────────────────────────────────────
    @Override
    protected void registerGoals() {
        this.goalSelector.addGoal(1, new FloatGoal(this));
        this.goalSelector.addGoal(3, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(4, new RandomLookAroundGoal(this));

        // Only retaliate against attackers — no autonomous aggro on sight
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
    }

    @Override
    public void checkFallDamage(double y, boolean onGround, BlockState state, BlockPos pos) {}

    @Override
    protected SoundEvent getHurtSound(DamageSource source) {
        return SoundEvents.BLAZE_HURT;
    }

    @Override
    protected SoundEvent getDeathSound() {
        return SoundEvents.BLAZE_DEATH;
    }

    // ── Combat: alert + shooting ──────────────────────────────────────────────
    @Override
    public boolean hurt(DamageSource source, float amount) {
        boolean wasInCombat = combatTimer > 0;
        if (!super.hurt(source, amount)) return false;
        if (!level().isClientSide()) {
            combatTimer = 300; // 15 seconds of shooting after last hit
            if (!wasInCombat) {
                // Play alert sound on first hit — distinctive elder-guardian shriek
                playSound(SoundEvents.ELDER_GUARDIAN_CURSE, 1.5f, 0.8f);
                shootCooldown = 5; // fire first shot quickly
            }
            // Always set the attacker as the primary target
            if (source.getEntity() instanceof LivingEntity le) setTarget(le);
        }
        return true;
    }

    /**
     * Death effect: burst of large smoke + small visual-only explosion.
     * ExplosionInteraction.NONE means no block or entity damage — just the
     * boom sound and flash effect visible to all nearby clients.
     */
    @Override
    public void die(DamageSource source) {
        super.die(source);
        if (!level().isClientSide() && level() instanceof ServerLevel serverLevel) {
            // Visual-only explosion: custom calculator disables entity and item damage
            // so dropped loot is never destroyed by the blast.
            serverLevel.explode(this,
                    Explosion.getDefaultDamageSource(serverLevel, this),
                    new ExplosionDamageCalculator() {
                        @Override
                        public boolean shouldDamageEntity(Explosion explosion, Entity entity) {
                            return false; // no damage to entities or dropped items
                        }
                    },
                    getX(), getEyeY(), getZ(),
                    2.5f, false, Level.ExplosionInteraction.NONE,
                    ParticleTypes.EXPLOSION,
                    ParticleTypes.EXPLOSION_EMITTER,
                    SoundEvents.GENERIC_EXPLODE);
            serverLevel.sendParticles(ParticleTypes.LARGE_SMOKE,
                    getX(), getEyeY(), getZ(),
                    30, 0.45, 0.45, 0.45, 0.04);

            // Captain killed by a player → start a sentinel raid for that player
            if (captain && source.getEntity() instanceof ServerPlayer killer) {
                com.example.meteormod.event.SentinelRaidManager.onCaptainKilled(killer, serverLevel);
            }
        }
    }

    /**
     * Called every server tick.
     *
     * Combat state machine:
     *  - Enters combat when hurt (combatTimer = 300).
     *  - While target is within 30 blocks AND has line of sight: combatTimer resets to
     *    300 each tick (never counts down), Sentinel shoots.
     *  - When target goes out of range or hides: combatTimer counts down (15 s = 300 t).
     *    If target reappears, combatTimer resets again.
     *  - When combatTimer reaches 0: target cleared, return to peaceful wandering.
     */
    private void tickCombat(ServerLevel serverLevel) {
        if (combatTimer <= 0 && getTarget() == null) return;

        LivingEntity target = getTarget();

        // Target dead or gone — give up immediately
        if (target == null || !target.isAlive()) {
            combatTimer = 0;
            setTarget(null);
            return;
        }

        // Line-of-sight + 30-block range check
        boolean inRange = distanceTo(target) <= 30.0;
        boolean canSee  = inRange && getSensing().hasLineOfSight(target);

        if (canSee) {
            // Player visible: keep combat active indefinitely, shoot
            combatTimer = 300;

            if (--shootCooldown <= 0) {
                shootCooldown = 12; // one burst every 12 ticks (~0.6 s)

                // Alternate: even shot = right cannon, odd shot = left cannon
                boolean rightCannon = (shotCount % 2 == 0);
                shotCount++;

                Vec3 cannon = getCannonWorldPos(rightCannon);
                Vec3 dir    = target.getEyePosition().subtract(cannon).normalize().scale(1.4);

                SentinelBulletEntity bullet = new SentinelBulletEntity(
                        ModEntities.SENTINEL_BULLET.get(), serverLevel);
                bullet.setOwner(this);
                bullet.setPos(cannon.x, cannon.y, cannon.z);
                bullet.setDeltaMovement(dir);
                serverLevel.addFreshEntity(bullet);

                playSound(SoundEvents.BLAZE_SHOOT, 0.7f, 1.1f + random.nextFloat() * 0.2f);
            }
        } else {
            // Player hidden or out of range: 15-second countdown, then give up
            combatTimer--;
            if (combatTimer <= 0) {
                combatTimer = 0;
                setTarget(null); // return to peaceful
            }
        }
    }

    /**
     * Pursuit flight toward target, keeping ~6-block combat distance.
     * Called from tickManualFlight() when in combat and not scanning.
     */
    private void tickPursuitFlight(LivingEntity target) {
        double dx   = target.getX() - getX();
        double dz   = target.getZ() - getZ();
        double dist = Math.sqrt(dx * dx + dz * dz); // horizontal distance

        Vec3 cur = getDeltaMovement();

        if (dist > 7.0) {
            // Chase — accelerate horizontally toward target
            double scale = 0.18 / dist;
            setDeltaMovement(
                cur.x * 0.75 + dx * scale * 0.25,
                cur.y * 0.65 + (target.getEyeY() - getEyeY()) * 0.04,
                cur.z * 0.75 + dz * scale * 0.25
            );
        } else {
            // At combat range — hover in place
            setDeltaMovement(cur.multiply(0.85, 0.75, 0.85));
        }

        // Always face the target during combat
        if (dist > 0.01) {
            setYRot((float) Math.toDegrees(Math.atan2(-dx, dz)));
            yHeadRot = getYRot();
            yBodyRot = getYRot(); // keep body in sync so GeckoLib model matches nozzle calc
        }
    }

    /**
     * Approximate world position of a cannon tip.
     * Model-space coords (absolute px): right=(-6.25, 1.5, -12.0), left=(6.25, 1.5, -12.0).
     * Uses body yaw only (no full animation transform needed for server-side spawning).
     * Height offset includes average idle-animation Y (≈0.65 blocks = ~10.5 px / 16).
     */
    private Vec3 getCannonWorldPos(boolean right) {
        double fx = right ? -6.25 : 6.25; // model-space X (px); –X = entity's right
        double fz = -12.0;                 // model-space Z (px); –Z = entity's forward
        double fy = 1.5 / 16.0 + 0.65;    // height above feet: base + avg anim offset

        float  yawRad = (float) Math.toRadians(getYRot());
        double sinYaw = Math.sin(yawRad), cosYaw = Math.cos(yawRad);

        return new Vec3(
                getX() + (fz / 16.0) * sinYaw - (fx / 16.0) * cosYaw,
                getY() + fy,
                getZ() - (fz / 16.0) * cosYaw - (fx / 16.0) * sinYaw
        );
    }

    // ── Main tick ─────────────────────────────────────────────────────────────
    @Override
    public void tick() {
        super.tick();

        if (level().isClientSide()) {
            // Nozzle trail every 2 ticks
            if (tickCount % 2 == 0) spawnClientNozzleParticles();
            // Scan cone-wave: lock direction at start, advance each tick, never loops
            if (entityData.get(SCANNING)) {
                if (scanWaveAge < 0) {
                    scanWaveAge = 0;
                    // Capture head direction once — wave won't bend even if head moves
                    lockedScanYaw   = getYHeadRot();
                    lockedScanPitch = getXRot();
                }
                spawnClientScanParticles();
                scanWaveAge++;
            } else {
                scanWaveAge = -1;
            }
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level();

        // Flight and combat run every tick
        tickManualFlight();
        tickCombat(serverLevel);

        // ── Abort scan and skip scanning entirely while in combat ─────────────
        if (combatTimer > 0) {
            if (scanTimer > 0) {
                scanTimer        = 0;
                scanBlockPos     = null;
                scanEntityTarget = null;
            }
            entityData.set(SCANNING, false);
            // Give the Sentinel a short scanning pause after combat ends
            scanCooldown = SCAN_COOLDOWN_MIN;
            return;
        }

        // ── Scanning state machine (peaceful only) ────────────────────────────
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

        // While scanning: decelerate and stop; scan state-machine controls look direction
        if (entityData.get(SCANNING)) {
            setDeltaMovement(getDeltaMovement().scale(0.8));
            return;
        }

        // While in combat: pursue the target instead of wandering
        if (combatTimer > 0) {
            LivingEntity target = getTarget();
            if (target != null && target.isAlive()) {
                tickPursuitFlight(target);
                return;
            }
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
                yBodyRot = getYRot(); // keep body in sync so GeckoLib model matches nozzle calc
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
     * Returns the idle-animation phase [0, 1) over the 6-second (120-tick) cycle.
     *
     * Reads directly from GeckoLib's AnimatableManager: lastUpdateTime and
     * firstTickTime are the same values GeoEntityRenderer uses to drive animation,
     * so this is always in sync with the rendered model — no drift possible.
     *
     * Fallback to tickCount % 120 before the entity's first render frame (isFirstTick).
     */
    private double idleAnimPhase() {
        AnimatableManager<?> mgr = animCache.getManagerForId(getId());
        if (mgr.isFirstTick()) return (tickCount % 120) / 120.0;
        // idle animation_length = 6 s = 120 ticks
        return ((mgr.getLastUpdateTime() - mgr.getFirstTickTime()) % 120.0) / 120.0;
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

        // Convert to world using entity yaw.
        // GeckoLib renders the model with rotLerp(partialTick, yBodyRotO, yBodyRot).
        // We use partialTick=0.5 as a mid-tick estimate; crucially we use yBodyRot so the
        // reference frame is identical to the renderer — no break during turns.
        float  yawRad = (float) Math.toRadians(Mth.rotLerp(0.5f, yBodyRotO, yBodyRot));
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
     * Yellow-to-red cone wave from the eye toward the locked scan direction.
     *
     * Why position-based instead of velocity-based:
     *   DustColorTransitionParticle (DustParticleBase) hard-codes velocity to 0 in
     *   its constructor — xd/yd/zd from addParticle() are ignored. Particles cannot
     *   be moved. Instead, each tick a new ring spawns 0.125 blocks further along the
     *   look direction; stationary particles accumulate into a visible travelling cone.
     *
     * Cone shape: ring radius = max(0.04, d * tan(14°)) so the wave is a narrow point
     *   at the eye and fans out to ~1.25 blocks wide at 5 blocks distance.
     *
     * Direction locked: lockedScanYaw/Pitch captured at scan start — the cone never
     *   bends even if the head turns (e.g. failed scan attempt, idle interpolation).
     *
     * Continuous: scanWaveAge increments every tick without looping for the duration
     *   of the scan, so there are no sudden resets or gaps in the wave.
     */
    private void spawnClientScanParticles() {
        // Animation-accurate eye position
        double[] ep = animWorldPos(0.0, 5.75, -5.75);
        double ex = ep[0], ey = ep[1], ez = ep[2];

        // Use LOCKED direction (set once at scan start) — never changes mid-scan
        float  yaw   = (float) Math.toRadians(lockedScanYaw);
        float  pitch = (float) Math.toRadians(lockedScanPitch);
        double lx = -Math.sin(yaw) * Math.cos(pitch);
        double ly = -Math.sin(pitch);
        double lz =  Math.cos(yaw) * Math.cos(pitch);

        // Wave-front distance: 0.125 blocks per tick → reaches ~7.5 blocks at end of 60-tick scan
        double d  = scanWaveAge * 0.125;
        double cx = ex + lx * d;
        double cy = ey + ly * d;
        double cz = ez + lz * d;

        // Cone radius grows linearly with distance (14° half-angle), minimum 0.04 at origin
        double coneR = Math.max(0.04, d * 0.249); // tan(14°) ≈ 0.249

        // Ring basis: right (horizontal, perpendicular to look yaw) + world up
        double rx = Math.cos(yaw), rz = Math.sin(yaw);

        final int COUNT = 16;
        for (int i = 0; i < COUNT; i++) {
            double angle = (2.0 * Math.PI / COUNT) * i;
            double ca = Math.cos(angle), sa = Math.sin(angle);
            level().addParticle(SCAN_DUST,
                    cx + rx * ca * coneR,
                    cy + sa * coneR,
                    cz + rz * ca * coneR,
                    0.0, 0.0, 0.0);
        }
    }

    // ── Public accessors ─────────────────────────────────────────────────────
    public boolean isScanning() {
        return entityData.get(SCANNING);
    }

    /**
     * Immediately put this sentinel into combat mode targeting {@code target}.
     * Used by {@link com.example.meteormod.event.SentinelRaidManager} when
     * spawning raid waves so every sentinel attacks at once without waiting to
     * be hit first.
     */
    public void startCombatAgainst(LivingEntity target) {
        setTarget(target);
        combatTimer   = 300; // 15 s of active combat
        shootCooldown = 5;   // fire first shot almost immediately
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
        tag.putBoolean("Captain",  captain);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
        scanTimer    = tag.getInt("ScanTimer");
        scanCooldown = tag.getInt("ScanCooldown");
        captain      = tag.getBoolean("Captain");
    }
}
