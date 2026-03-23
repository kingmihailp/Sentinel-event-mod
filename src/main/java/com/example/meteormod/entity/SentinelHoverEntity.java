package com.example.meteormod.entity;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.util.Mth;
import com.example.meteormod.item.ModItems;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityDimensions;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SentinelHoverEntity extends Entity implements GeoEntity {

    // ── Movement constants ────────────────────────────────────────────────
    private static final float  SPEED        = 0.25f;  // horizontal b/t at full input
    private static final float  FRICTION     = 0.80f;  // horizontal drag when no input
    private static final double HOVER_HEIGHT = 1.5;    // target blocks above ground
    private static final float  MAX_VY       = 0.40f;  // vertical speed cap

    // 0 = idle, 1 = forward, 2 = backward — synced to clients for animation
    private static final EntityDataAccessor<Byte> MOVE_STATE =
            SynchedEntityData.defineId(SentinelHoverEntity.class, EntityDataSerializers.BYTE);

    private static final RawAnimation ANIM_THRUSTER      = RawAnimation.begin().thenLoop("thruster");
    private static final RawAnimation ANIM_ENG_FORWARD   = RawAnimation.begin().thenPlayAndHold("top engines vpered");
    private static final RawAnimation ANIM_ENG_BACKWARD  = RawAnimation.begin().thenPlayAndHold("engines nazad");
    private static final RawAnimation ANIM_SHOOT         = RawAnimation.begin().thenPlay("shoot");

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    public SentinelHoverEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
        this.noPhysics = false;
    }

    // ── Required Entity overrides ─────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {
        builder.define(MOVE_STATE, (byte) 0);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

    // ── Targeting / interaction ───────────────────────────────────────────

    /** Must return true so players can ray-cast and right-click the entity. */
    @Override
    public boolean isPickable() { return true; }

    @Override
    public boolean canCollideWith(Entity other) {
        return other.canBeCollidedWith() && other != this.getControllingPassenger();
    }

    @Override
    public boolean isPushable() { return false; }

    /** Lower the passenger so the player sits inside/below the panel instead of on top. */
    @Override
    protected Vec3 getPassengerAttachmentPoint(Entity passenger, EntityDimensions dimensions, float partialTick) {
        return new Vec3(0.0, 0.2, 0.0);
    }

    // ── Movement state accessor (used by animation controllers) ──────────

    public byte getMoveState() {
        return this.entityData.get(MOVE_STATE);
    }

    // ── Passenger API ─────────────────────────────────────────────────────

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof LivingEntity le ? le : null;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) {
            // Shift+right-click: pick up the hover
            if (!this.level().isClientSide()) {
                ItemStack item = new ItemStack(ModItems.SENTINEL_HOVER.get());
                if (!player.getInventory().add(item)) {
                    this.spawnAtLocation(item);
                }
                this.discard();
            }
            return InteractionResult.sidedSuccess(this.level().isClientSide());
        }
        if (!this.level().isClientSide()) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    // ── Tick ──────────────────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        LivingEntity rider = this.getControllingPassenger();
        Vec3 vel = this.getDeltaMovement();

        // ── Horizontal ──────────────────────────────────────────────────
        if (rider instanceof Player player) {
            this.yRotO = this.getYRot();
            this.setYRot(player.getYRot());

            float forward = player.zza;
            float strafe  = player.xxa;

            if (!this.level().isClientSide()) {
                byte ms = (forward > 0) ? (byte) 1 : (forward < 0) ? (byte) 2 : (byte) 0;
                this.entityData.set(MOVE_STATE, ms);
            }

            if (forward != 0f || strafe != 0f) {
                double yaw = Math.toRadians(this.getYRot());
                double dx  = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * SPEED;
                double dz  = ( Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * SPEED;
                vel = new Vec3(dx, vel.y, dz);
            } else {
                vel = new Vec3(vel.x * FRICTION, vel.y, vel.z * FRICTION);
            }

            // ── Vertical (auto-hover via spring-damper) ──────────────────
            vel = new Vec3(vel.x, hoverVelocity(), vel.z);

        } else {
            // No rider — decelerate and settle toward hover height
            if (!this.level().isClientSide()) {
                this.entityData.set(MOVE_STATE, (byte) 0);
            }
            vel = new Vec3(vel.x * FRICTION, hoverVelocity(), vel.z * FRICTION);
        }

        this.setDeltaMovement(vel);
        this.move(MoverType.SELF, vel);

        // ── Turbine particles (client-side only) ─────────────────────────
        if (this.level().isClientSide()) {
            spawnTurbineParticles();
        }
    }

    // ── Hover physics helpers ─────────────────────────────────────────────

    /**
     * Spring-damper: smoothly drives the entity toward HOVER_HEIGHT above
     * the nearest solid block below.  Returns the new Y velocity.
     */
    private double hoverVelocity() {
        double groundY = groundBelow();
        double error   = (groundY + HOVER_HEIGHT) - this.getY();
        // spring (0.18) + damper (−0.45 × current vy)
        return Mth.clamp(error * 0.18 - this.getDeltaMovement().y * 0.45, -MAX_VY, MAX_VY);
    }

    /** Finds the Y of the first solid block within 12 blocks below the entity. */
    private double groundBelow() {
        for (int i = 0; i <= 12; i++) {
            BlockPos pos = BlockPos.containing(this.getX(), this.getY() - i - 0.1, this.getZ());
            if (!this.level().getBlockState(pos).isAir()) {
                return pos.getY() + 1.0;
            }
        }
        // Nothing found — stay at current height
        return this.getY() - HOVER_HEIGHT;
    }

    // ── Turbine particle FX ───────────────────────────────────────────────

    /**
     * Spawns SOUL_FIRE_FLAME (blue) particles from the four turbine rings
     * and the rear thrust nozzle.
     *
     * All offsets derived from the geo model (16 model-units = 1 block).
     * With GeckoLib's 180° Y-rotation convention:
     *   model −Z → entity forward, model +X → entity left.
     *
     * Turbines (top_engines / depth_engines): Y = 5 → 0.31 blocks above feet
     * Engine_back nozzle:                     Y = 12 → 0.75 blocks above feet
     */
    private void spawnTurbineParticles() {
        Vec3 fwd = Vec3.directionFromRotation(0, this.getYRot());           // entity forward
        Vec3 rgt = Vec3.directionFromRotation(0, this.getYRot() + 90f);     // entity right

        double bx = this.getX();
        double by = this.getY();
        double bz = this.getZ();

        // ── Four levitation turbines (SOUL_FIRE_FLAME pointing downward) ──
        double turbY = by + 0.31;
        double FW = 0.625;  // forward offset  (model z ≈ −10  → +0.625 forward)
        double BW = 0.813;  // backward offset (model z ≈ +13  → −0.813 forward)
        double LR = 0.813;  // lateral  offset (model x ≈ ±13 → ±0.813)

        // front-left
        flame(bx + fwd.x*FW + rgt.x*(-LR), turbY, bz + fwd.z*FW + rgt.z*(-LR),
              0, -0.05, 0);
        // front-right
        flame(bx + fwd.x*FW + rgt.x*( LR), turbY, bz + fwd.z*FW + rgt.z*( LR),
              0, -0.05, 0);
        // rear-left
        flame(bx + fwd.x*(-BW) + rgt.x*(-LR), turbY, bz + fwd.z*(-BW) + rgt.z*(-LR),
              0, -0.05, 0);
        // rear-right
        flame(bx + fwd.x*(-BW) + rgt.x*( LR), turbY, bz + fwd.z*(-BW) + rgt.z*( LR),
              0, -0.05, 0);

        // ── Rear thrust nozzle (Engine_back) — particles shoot backward ──
        double nx = bx + fwd.x * (-1.69);
        double ny = by + 0.75;
        double nz = bz + fwd.z * (-1.69);
        // shoot 1–2 particles per tick
        flame(nx, ny, nz, -fwd.x * 0.20, -0.01, -fwd.z * 0.20);
        if (this.random.nextBoolean()) {
            flame(nx, ny, nz, -fwd.x * 0.15, -0.02, -fwd.z * 0.15);
        }
    }

    private void flame(double x, double y, double z, double vx, double vy, double vz) {
        this.level().addParticle(
                ParticleTypes.SOUL_FIRE_FLAME,
                x + (this.random.nextFloat() - 0.5f) * 0.12,
                y,
                z + (this.random.nextFloat() - 0.5f) * 0.12,
                vx + (this.random.nextFloat() - 0.5f) * 0.02,
                vy,
                vz + (this.random.nextFloat() - 0.5f) * 0.02);
    }

    // ── GeckoLib ──────────────────────────────────────────────────────────

    /** Trigger the shoot recoil animation (call when the cannons fire). */
    public void triggerShootAnimation() {
        this.triggerAnim("shoot_controller", "shoot");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {

        // 1. Thruster vibration — always on
        controllers.add(new AnimationController<>(this, "thruster_controller", state ->
                state.setAndContinue(ANIM_THRUSTER)));

        // 2. Engine tilt — forward / backward / neutral
        controllers.add(new AnimationController<>(this, "engines_controller", state -> {
            byte move = state.getAnimatable().getMoveState();
            if (move == 1) return state.setAndContinue(ANIM_ENG_FORWARD);
            if (move == 2) return state.setAndContinue(ANIM_ENG_BACKWARD);
            return PlayState.STOP;
        }));

        // 3. Shoot recoil — triggered via triggerShootAnimation()
        controllers.add(
                new AnimationController<>(this, "shoot_controller", state -> PlayState.STOP)
                        .triggerableAnim("shoot", ANIM_SHOOT)
        );
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return animCache; }
}
