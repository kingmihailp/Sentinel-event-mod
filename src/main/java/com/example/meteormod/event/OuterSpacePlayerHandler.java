package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.entity.SentinelHoverEntity;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/**
 * Handles outer-space physics and hazards for players:
 *  – No gravity (player floats in place) when not riding a hover
 *  – Gradual freezing when not riding a hover
 *  – Freeze damage once fully frozen (1 HP every 2 s)
 *  – Hover riding suppresses all of the above
 */
@EventBusSubscriber(modid = MeteorMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class OuterSpacePlayerHandler {

    private static final ResourceKey<Level> OUTER_SPACE = ResourceKey.create(
            Registries.DIMENSION,
            ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "outer_space"));

    /** Freeze damage interval: every 40 ticks (2 s) once fully frozen. */
    private static final int FREEZE_DAMAGE_INTERVAL = 40;
    /** Freeze damage amount (HP). */
    private static final float FREEZE_DAMAGE_AMOUNT  = 1.0f;
    /**
     * Freeze ticks added per post-tick. Vanilla subtracts 2/tick when the entity
     * is not in powder snow, so the net gain is +1/tick → ~7 s to reach full freeze.
     */
    private static final int FREEZE_RATE = 3;

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        boolean inSpace = player.level().dimension().equals(OUTER_SPACE);
        boolean onHover = player.getVehicle() instanceof SentinelHoverEntity;

        if (inSpace && !onHover) {
            // ── No gravity ───────────────────────────────────────────────
            player.setNoGravity(true);

            // ── Freeze ───────────────────────────────────────────────────
            // NOTE: Post fires after Entity.tick() which already decremented ticksFrozen by 2
            // (vanilla logic: not in powder snow → -2/tick). We read the post-decrement value,
            // increment it, and update the local var so the damage check uses the new value.
            int threshold = player.getTicksRequiredToFreeze();
            int frozen    = player.getTicksFrozen();

            if (frozen < threshold) {
                frozen = Math.min(frozen + FREEZE_RATE, threshold);
                player.setTicksFrozen(frozen);
            }

            // Deal freeze damage once fully frozen
            if (frozen >= threshold && (player.tickCount % FREEZE_DAMAGE_INTERVAL) == 0) {
                player.hurt(player.damageSources().freeze(), FREEZE_DAMAGE_AMOUNT);
            }

        } else {
            // Restore gravity when on hover or outside the dimension
            if (player.isNoGravity()) {
                player.setNoGravity(false);
            }
            // Freeze ticks will naturally decrease at vanilla's -2/tick rate
        }
    }
}
