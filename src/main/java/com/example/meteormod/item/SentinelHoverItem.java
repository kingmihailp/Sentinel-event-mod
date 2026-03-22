package com.example.meteormod.item;

import com.example.meteormod.entity.ModEntities;
import com.example.meteormod.entity.SentinelHoverEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

public class SentinelHoverItem extends Item {

    public SentinelHoverItem(Properties properties) {
        super(properties);
    }

    /**
     * Right-click the top face of any block to place the hover vehicle.
     * Consumed from stack unless the player is in creative.
     */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Level level = ctx.getLevel();
        BlockPos clicked = ctx.getClickedPos();

        // Only place on the top face of a block
        if (ctx.getClickedFace() != net.minecraft.core.Direction.UP) {
            return InteractionResult.PASS;
        }

        if (!level.isClientSide()) {
            SentinelHoverEntity hover =
                    new SentinelHoverEntity(ModEntities.SENTINEL_HOVER.get(), level);

            Vec3 pos = Vec3.atBottomCenterOf(clicked.above());
            hover.setPos(pos.x, pos.y, pos.z);
            if (ctx.getPlayer() != null) {
                hover.setYRot(ctx.getPlayer().getYRot());
            }

            level.addFreshEntity(hover);

            if (ctx.getPlayer() != null && !ctx.getPlayer().getAbilities().instabuild) {
                ctx.getItemInHand().shrink(1);
            }
        }

        return InteractionResult.sidedSuccess(level.isClientSide());
    }
}
