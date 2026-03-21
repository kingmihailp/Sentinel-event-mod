package com.example.meteormod.item;

import com.example.meteormod.MeteorMod;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredBlock;
import net.neoforged.neoforge.registries.DeferredRegister;

public class ModBlocks {

    public static final DeferredRegister.Blocks BLOCKS =
            DeferredRegister.createBlocks(MeteorMod.MOD_ID);

    public static final DeferredBlock<Block> SENTINEL_METAL_BLOCK =
            BLOCKS.registerSimpleBlock("sentinelmetal_block",
                    BlockBehaviour.Properties.of()
                            .requiresCorrectToolForDrops()
                            .strength(5.0f, 6.0f)
                            .sound(SoundType.COPPER));

    /** The meteor visual block — indestructible, blast-proof, no item, no creative tab. */
    public static final DeferredBlock<Block> SUSPICIOUS_PACKAGE =
            BLOCKS.registerSimpleBlock("suspicious_package",
                    BlockBehaviour.Properties.of()
                            .strength(-1.0F, 3600000.0F)
                            .noLootTable()
                            .sound(SoundType.WOOD));
}
