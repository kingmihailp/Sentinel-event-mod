package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.entity.MeteorEntity;
import com.example.meteormod.entity.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = MeteorMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class MeteorSpawnHandler {

    // 5–8 minutes in ticks (20 ticks/sec × 60 sec/min)
    private static final int MIN_DELAY_TICKS = 5 * 60 * 20;  // 6 000
    private static final int MAX_DELAY_TICKS = 8 * 60 * 20;  // 9 600

    /** Per-dimension countdown — meteors only fire in the Overworld. */
    private static final Map<String, Integer> tickCounters = new HashMap<>();

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;

        // Only spawn in the Overworld
        if (!serverLevel.dimension().equals(Level.OVERWORLD)) return;

        // Only do anything if there are players online
        List<ServerPlayer> players = serverLevel.players();
        if (players.isEmpty()) return;

        String key = serverLevel.dimension().location().toString();
        int remaining = tickCounters.getOrDefault(key, MIN_DELAY_TICKS);
        remaining--;

        if (remaining <= 0) {
            spawnMeteorShower(serverLevel, players);
            // Randomize next wave delay: 5–8 minutes
            int delay = MIN_DELAY_TICKS
                    + serverLevel.random.nextInt(MAX_DELAY_TICKS - MIN_DELAY_TICKS + 1);
            tickCounters.put(key, delay);
        } else {
            tickCounters.put(key, remaining);
        }
    }

    private static void spawnMeteorShower(ServerLevel level, List<ServerPlayer> players) {
        // 3–6 meteors per shower
        int count = 3 + level.random.nextInt(4);

        for (int i = 0; i < count; i++) {
            // Pick a random player to target
            ServerPlayer target = players.get(level.random.nextInt(players.size()));

            // Random horizontal offset from the target (up to ±30 blocks)
            double offsetX = (level.random.nextDouble() - 0.5) * 60.0;
            double offsetZ = (level.random.nextDouble() - 0.5) * 60.0;

            double spawnX = target.getX() + offsetX;
            double spawnZ = target.getZ() + offsetZ;

            // Spawn just below the build height so the meteor is visible from far away
            double spawnY = level.getMaxBuildHeight() - 5.0;

            MeteorEntity meteor = new MeteorEntity(ModEntities.METEOR.get(), level);
            meteor.setPos(spawnX, spawnY, spawnZ);

            // Give an initial downward velocity so they start moving immediately
            meteor.setDeltaMovement(0.0, -1.0, 0.0);

            level.addFreshEntity(meteor);
        }
    }
}
