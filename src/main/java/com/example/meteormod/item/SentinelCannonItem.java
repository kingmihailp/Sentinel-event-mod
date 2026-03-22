package com.example.meteormod.item;

import com.example.meteormod.entity.EmpBulletEntity;
import com.example.meteormod.sound.ModSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SentinelCannonItem extends Item {

    /** 5 seconds = 100 ticks */
    private static final int COOLDOWN_TICKS = 100;

    public SentinelCannonItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);

        if (!level.isClientSide()) {
            EmpBulletEntity bullet = EmpBulletEntity.create(level, player);

            // Shoot in the direction the player is looking
            Vec3 dir = player.getViewVector(1.0f).scale(1.5);
            bullet.setDeltaMovement(dir);

            level.addFreshEntity(bullet);
            level.playSound(null, player.getX(), player.getY(), player.getZ(),
                    ModSounds.SENTINEL_CANNON_SHOOT, SoundSource.PLAYERS, 0.6f, 1.8f);
        }

        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        // consume() → InteractionResult.shouldSwing() == false → no arm-swing animation
        return InteractionResultHolder.consume(stack);
    }
}
