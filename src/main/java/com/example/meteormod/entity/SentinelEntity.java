package com.example.meteormod.entity;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.PathfinderMob;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.ai.goal.*;
import net.minecraft.world.entity.ai.goal.target.HurtByTargetGoal;
import net.minecraft.world.entity.ai.goal.target.NearestAttackableTargetGoal;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.animation.*;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Sentinel — custom mob with a GeckoLib model.
 *
 * HOW TO REPLACE THE PLACEHOLDER MODEL/ANIMATIONS:
 *  1. Open Blockbench, create your model and export it as
 *     "Model for GeckoLib" → src/main/resources/assets/meteormod/geo/sentinel.geo.json
 *  2. Create animations in Blockbench and export them to
 *     src/main/resources/assets/meteormod/animations/sentinel.animation.json
 *  3. Add your texture PNG to
 *     src/main/resources/assets/meteormod/textures/entity/sentinel.png
 *  4. Make sure the bone names in your model match the ones used in
 *     the animations (the placeholder uses: head, body, right_arm, left_arm,
 *     right_leg, left_leg).
 *  5. Update the animation IDs in the RawAnimation fields below if your
 *     Blockbench animations use different names.
 */
public class SentinelEntity extends PathfinderMob implements GeoEntity {

    // ── Animation IDs — must match the keys in sentinel.animation.json ──────
    // Change these strings to match your Blockbench animation names.
    private static final RawAnimation ANIM_IDLE   = RawAnimation.begin().thenLoop("animation.sentinel.idle");
    private static final RawAnimation ANIM_WALK   = RawAnimation.begin().thenLoop("animation.sentinel.walk");
    private static final RawAnimation ANIM_ATTACK = RawAnimation.begin().thenPlay("animation.sentinel.attack");

    private final AnimatableInstanceCache animCache = GeckoLibUtil.createInstanceCache(this);

    public SentinelEntity(EntityType<? extends SentinelEntity> type, Level level) {
        super(type, level);
    }

    // ── Attributes ───────────────────────────────────────────────────────────
    // Tweak these values to suit your mob's balance.
    public static AttributeSupplier.Builder createAttributes() {
        return PathfinderMob.createMobAttributes()
                .add(Attributes.MAX_HEALTH,     30.0)
                .add(Attributes.MOVEMENT_SPEED,  0.25)
                .add(Attributes.ATTACK_DAMAGE,   4.0)
                .add(Attributes.FOLLOW_RANGE,   16.0)
                .add(Attributes.ARMOR,           2.0);
    }

    // ── AI Goals ─────────────────────────────────────────────────────────────
    @Override
    protected void registerGoals() {
        // Self-preservation
        this.goalSelector.addGoal(0, new FloatGoal(this));

        // Combat
        this.goalSelector.addGoal(1, new MeleeAttackGoal(this, 1.0, true));

        // Wandering / idle
        this.goalSelector.addGoal(5, new WaterAvoidingRandomStrollGoal(this, 0.8));
        this.goalSelector.addGoal(6, new LookAtPlayerGoal(this, Player.class, 8.0f));
        this.goalSelector.addGoal(7, new RandomLookAroundGoal(this));

        // Targeting
        this.targetSelector.addGoal(1, new HurtByTargetGoal(this));
        this.targetSelector.addGoal(2, new NearestAttackableTargetGoal<>(this, Player.class, true));
    }

    // ── GeckoLib animation ───────────────────────────────────────────────────
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar registrar) {

        // Main locomotion controller: idle ↔ walk
        registrar.add(new AnimationController<>(this, "locomotion", 5, state -> {
            if (state.isMoving()) {
                return state.setAndContinue(ANIM_WALK);
            }
            return state.setAndContinue(ANIM_IDLE);
        }));

        // Attack controller — triggered externally via triggerAnim()
        // To play it call: entity.triggerAnim("attack_controller", "do_attack")
        registrar.add(
                new AnimationController<>(this, "attack_controller", 2, state -> PlayState.STOP)
                        .triggerableAnim("do_attack", ANIM_ATTACK)
        );
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return animCache;
    }

    // ── You can add custom logic below ───────────────────────────────────────

    @Override
    protected void actuallyHurt(net.minecraft.world.damagesource.DamageSource source, float amount) {
        super.actuallyHurt(source, amount);
        // Trigger a hit reaction here if you add a "hit" animation later:
        // this.triggerAnim("attack_controller", "do_hit");
    }

    @Override
    public boolean doHurtTarget(net.minecraft.world.entity.Entity target) {
        boolean hit = super.doHurtTarget(target);
        if (hit) {
            // Trigger the attack animation whenever a melee hit lands
            this.triggerAnim("attack_controller", "do_attack");
        }
        return hit;
    }

    // ── NBT (add custom fields here if needed) ───────────────────────────────
    @Override
    public void addAdditionalSaveData(CompoundTag tag) {
        super.addAdditionalSaveData(tag);
    }

    @Override
    public void readAdditionalSaveData(CompoundTag tag) {
        super.readAdditionalSaveData(tag);
    }
}
