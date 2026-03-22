package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.client.ScopeOverlay;
import com.example.meteormod.client.SentinelCannonAmbientSound;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Client-side game-bus events (Dist.CLIENT only).
 */
@EventBusSubscriber(modid = MeteorMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public class ClientGameEventHandler {

    /** Currently playing ambient sound instance, or null if not active. */
    private static SentinelCannonAmbientSound ambientSound = null;
    private static boolean wasActive = false;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || event.getEntity() != mc.player) return;

        boolean scopeActive = ScopeOverlay.active && ScopeOverlay.holdsCannon(mc.player);

        if (scopeActive && !wasActive) {
            // Scope just turned on — start the looping ambient
            ambientSound = new SentinelCannonAmbientSound(mc.player);
            mc.getSoundManager().play(ambientSound);
        }
        // Stopping is handled inside SentinelCannonAmbientSound.tick() / canPlaySound()

        wasActive = scopeActive;
    }
}
