package com.example.meteormod.item;

import com.example.meteormod.entity.EmpBulletEntity;
import com.example.meteormod.sound.ModSounds;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SentinelCannonItem extends Item {

    /** 5 seconds = 100 ticks */
    private static final int COOLDOWN_TICKS = 100;

    public static final int MAX_ENERGY = 1000;
    public static final int SHOT_COST  = 5;

    /** ARGB bar colour — 0xABFFF9 */
    private static final int BAR_COLOR = 0xABFFF9;

    public SentinelCannonItem(Properties properties) {
        super(properties);
    }

    // ── Energy helpers ────────────────────────────────────────────────────

    public static int getEnergy(ItemStack stack) {
        CompoundTag tag = stack.getOrDefault(DataComponents.CUSTOM_DATA, CustomData.EMPTY).copyTag();
        return tag.contains("Energy") ? tag.getInt("Energy") : MAX_ENERGY;
    }

    public static void setEnergy(ItemStack stack, int energy) {
        stack.update(DataComponents.CUSTOM_DATA, CustomData.EMPTY, existing -> {
            CompoundTag tag = existing.copyTag();
            tag.putInt("Energy", Mth.clamp(energy, 0, MAX_ENERGY));
            return CustomData.of(tag);
        });
    }

    // ── Energy bar ────────────────────────────────────────────────────────

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return true;
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return Math.round(getEnergy(stack) * 13.0f / MAX_ENERGY);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        return BAR_COLOR;
    }

    // ── Crafting ──────────────────────────────────────────────────────────

    /** Called when the player takes this item out of any crafting result slot. */
    @Override
    public void onCraftedBy(ItemStack stack, Level level, Player player) {
        setEnergy(stack, MAX_ENERGY);
    }

    // ── Use ───────────────────────────────────────────────────────────────

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (getEnergy(stack) < SHOT_COST) {
            return InteractionResultHolder.fail(stack);
        }

        if (!level.isClientSide()) {
            setEnergy(stack, getEnergy(stack) - SHOT_COST);

            EmpBulletEntity bullet = EmpBulletEntity.create(level, player);
            Vec3 dir = player.getViewVector(1.0f).scale(1.5);
            bullet.setDeltaMovement(dir);

            level.addFreshEntity(bullet);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.SENTINEL_CANNON_SHOOT, SoundSource.PLAYERS, 0.6f, 1.8f);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.consume(stack);
    }
}
