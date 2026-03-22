package com.example.meteormod.entity;

import net.minecraft.nbt.CompoundTag;
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
import software.bernie.geckolib.animation.AnimationController;
import software.bernie.geckolib.animation.AnimatableManager;
import software.bernie.geckolib.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

public class SentinelHoverEntity extends Entity implements GeoEntity {

    private static final float SPEED     = 0.25f;   // blocks per tick at full input
    private static final float FRICTION  = 0.80f;   // speed decay when no input
    private static final float GRAVITY   = 0.04f;

    private static final RawAnimation ANIM_IDLE =
            RawAnimation.begin().thenLoop("idle");

    private final AnimatableInstanceCache animCache =
            GeckoLibUtil.createInstanceCache(this);

    public SentinelHoverEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.blocksBuilding = true;
    }

    // ── Required Entity overrides ─────────────────────────────────────────

    @Override
    protected void defineSynchedData(SynchedEntityData.Builder builder) {}

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {}

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {}

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

    /** Player sits 0.35 blocks above the entity's bottom (top of the body). */
    @Override
    protected double getPassengersRidingOffset() { return 0.35; }

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
            // Mirror yaw to the rider's facing direction
            this.setYRot(player.getYRot());
            this.yRotO = player.yRotO;

            // xxa = strafe, zza = forward (populated server-side via ServerboundPlayerInputPacket)
            float forward = player.zza;
            float strafe  = player.xxa;

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
            // Decelerate when unridden
            Vec3 v = this.getDeltaMovement();
            this.setDeltaMovement(v.x * FRICTION, v.y, v.z * FRICTION);
        }

        // Gravity
        if (!this.onGround()) {
            this.setDeltaMovement(getDeltaMovement().add(0, -GRAVITY, 0));
        } else {
            this.setDeltaMovement(getDeltaMovement().multiply(1, 0, 1));
        }

        this.move(MoverType.SELF, this.getDeltaMovement());
    }

    // ── GeckoLib ──────────────────────────────────────────────────────────

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "idle", state ->
                state.setAndContinue(ANIM_IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() { return animCache; }
}
