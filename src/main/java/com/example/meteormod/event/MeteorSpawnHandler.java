package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.MeteorConfig;
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

    // Spawn radius: meteors land 8–10 blocks from the targeted player
    private static final double MIN_RADIUS = 8.0;
    private static final double MAX_RADIUS = 10.0;

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

        int customDelay = MeteorConfig.getDelayTicks();

        // -1 means continuous: spawn every tick (well, every shower-interval tick = 1)
        if (customDelay == -1) {
            spawnMeteorShower(serverLevel, players);
            return;
        }

        String key = serverLevel.dimension().location().toString();
        int remaining = tickCounters.getOrDefault(key, customDelay);
        remaining--;

        if (remaining <= 0) {
            spawnMeteorShower(serverLevel, players);
            tickCounters.put(key, customDelay);
        } else {
            tickCounters.put(key, remaining);
        }
    }

    /** Called externally (e.g. from the command) to reset all running countdowns. */
    public static void resetCounters() {
        tickCounters.clear();
    }

    private static void spawnMeteorShower(ServerLevel level, List<ServerPlayer> players) {
        // 3–6 meteors per shower
        int count = 3 + level.random.nextInt(4);

        for (int i = 0; i < count; i++) {
            // Pick a random player to target
            ServerPlayer target = players.get(level.random.nextInt(players.size()));

            // Uniform random point inside an annulus [MIN_RADIUS, MAX_RADIUS]
            double angle = level.random.nextDouble() * 2.0 * Math.PI;
            double radius = MIN_RADIUS + level.random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
            double offsetX = Math.cos(angle) * radius;
            double offsetZ = Math.sin(angle) * radius;

            double spawnX = target.getX() + offsetX;
            double spawnZ = target.getZ() + offsetZ;

            // Spawn just below the build height so the trail is visible
            double spawnY = level.getMaxBuildHeight() - 5.0;

            MeteorEntity meteor = new MeteorEntity(ModEntities.METEOR.get(), level);
            meteor.setPos(spawnX, spawnY, spawnZ);
            meteor.setDeltaMovement(0.0, -1.0, 0.0);

            level.addFreshEntity(meteor);
        }
    }
}
