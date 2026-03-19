package com.example.meteormod.item;

import com.example.meteormod.client.SentinelIconBEWLR;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.world.item.Item;
import net.neoforged.neoforge.client.extensions.common.IClientItemExtensions;

import java.util.function.Consumer;

/**
 * Invisible item used exclusively as the creative-tab icon.
 * Renders a live 3-D SentinelEntity (via {@link SentinelIconBEWLR}) whose
 * head tracks the mouse cursor — exactly like Alex's Mobs tab icons.
 *
 * The item is never added to the tab's display list, so players cannot
 * obtain it in normal gameplay.
 */
public class SentinelIconItem extends Item {

    public SentinelIconItem() {
        super(new Properties().stacksTo(1));
    }

    /** Provides the custom BEWLR for client-side rendering. */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private BlockEntityWithoutLevelRenderer renderer;

            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) {
                    Minecraft mc = Minecraft.getInstance();
                    renderer = new SentinelIconBEWLR(
                            mc.getBlockEntityRenderDispatcher(),
                            mc.getEntityModels());
                }
                return renderer;
            }
        });
    }
}
