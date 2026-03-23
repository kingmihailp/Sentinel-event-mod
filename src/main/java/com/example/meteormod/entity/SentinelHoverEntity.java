package com.example.meteormod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SentinelHoverEntity extends Entity implements GeoEntity {

    private static final float SPEED    = 0.25f;
    private static final float FRICTION = 0.80f;
    private static final float GRAVITY  = 0.04f;

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

    // ── Movement state accessor (used by animation controllers) ──────────

    public byte getMoveState() {
        return this.entityData.get(MOVE_STATE);
    }

    // ── Collision / push behaviour ────────────────────────────────────────

    @Override
    public boolean canCollideWith(Entity other) {
        return other.canBeCollidedWith() && other != this.getControllingPassenger();
    }

    @Override
    public boolean isPushable() { return false; }

    // ── Passenger API ─────────────────────────────────────────────────────

    @Nullable
    @Override
    public LivingEntity getControllingPassenger() {
        return this.getFirstPassenger() instanceof LivingEntity le ? le : null;
    }

    @Override
    public InteractionResult interact(Player player, InteractionHand hand) {
        if (player.isSecondaryUseActive()) return InteractionResult.PASS;
        if (!this.level().isClientSide()) {
            return player.startRiding(this) ? InteractionResult.CONSUME : InteractionResult.PASS;
        }
        return InteractionResult.SUCCESS;
    }

    // ── Tick / movement ───────────────────────────────────────────────────

    @Override
    public void tick() {
        super.tick();

        LivingEntity rider = this.getControllingPassenger();
        if (rider instanceof Player player) {
            this.setYRot(player.getYRot());
            this.yRotO = player.yRotO;

            float forward = player.zza;
            float strafe  = player.xxa;

            // Sync move direction for animations (server → client via SynchedEntityData)
            if (!this.level().isClientSide()) {
                byte state = (forward > 0) ? (byte) 1 : (forward < 0) ? (byte) 2 : (byte) 0;
                this.entityData.set(MOVE_STATE, state);
            }

            if (forward != 0f || strafe != 0f) {
                double yaw = Math.toRadians(this.getYRot());
                double dx  = (-Math.sin(yaw) * forward + Math.cos(yaw) * strafe) * SPEED;
                double dz  = ( Math.cos(yaw) * forward + Math.sin(yaw) * strafe) * SPEED;
                this.setDeltaMovement(dx, this.getDeltaMovement().y, dz);
            } else {
                Vec3 v = this.getDeltaMovement();
                this.setDeltaMovement(v.x * FRICTION, v.y, v.z * FRICTION);
            }
        } else {
            if (!this.level().isClientSide()) {
                this.entityData.set(MOVE_STATE, (byte) 0);
            }
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(v.x * FRICTION, v.y, v.z * FRICTION);
        }

        if (!this.onGround()) {
            this.setDeltaMovement(getDeltaMovement().add(0, -GRAVITY, 0));
        } else {
            this.setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    // ── GeckoLib ──────────────────────────────────────────────────────────

    /** Trigger the shoot recoil animation (call this when the cannons fire). */
    public void triggerShootAnimation() {
        this.triggerAnim("shoot_controller", "shoot");
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {

        // 1. Thruster vibration — plays continuously at all times
        controllers.add(new AnimationController<>(this, "thruster_controller", state ->
                state.setAndContinue(ANIM_THRUSTER)));

        // 2. Engine tilt — forward / backward / reset to neutral
        controllers.add(new AnimationController<>(this, "engines_controller", state -> {
            byte move = state.getAnimatable().getMoveState();
            if (move == 1) return state.setAndContinue(ANIM_ENG_FORWARD);
            if (move == 2) return state.setAndContinue(ANIM_ENG_BACKWARD);
            return PlayState.STOP; // neutral: bones reset to bind pose
        }));

        // 3. Shoot recoil — triggered externally via triggerShootAnimation()
        controllers.add(
                new AnimationController<>(this, "shoot_controller", state -> PlayState.STOP)
                        .triggerableAnim("shoot", ANIM_SHOOT)
        );
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return animCache; }
}
