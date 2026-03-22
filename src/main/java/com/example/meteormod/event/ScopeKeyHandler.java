package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.client.ModKeyBindings;
import com.example.meteormod.client.ScopeOverlay;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.InputEvent;

/** Handles the scope toggle keybind on the GAME bus (client-only). */
@EventBusSubscriber(modid = MeteorMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ScopeKeyHandler {

    @SubscribeEvent
    public static void onKeyInput(InputEvent.Key event) {
        if (ModKeyBindings.SCOPE_KEY.consumeClick()) {
            ScopeOverlay.toggle();
        }
    }
}
