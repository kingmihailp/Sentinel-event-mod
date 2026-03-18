package com.example.meteormod.event;

import com.example.meteormod.MeteorConfig;
import com.example.meteormod.MeteorMod;
import com.example.meteormod.entity.MeteorEntity;
import com.example.meteormod.entity.ModEntities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;

import java.util.ArrayDeque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;

@EventBusSubscriber(modid = MeteorMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class MeteorSpawnHandler {

    // Spawn radius: meteors land 8–10 blocks from the targeted player
    private static final double MIN_RADIUS = 8.0;
    private static final double MAX_RADIUS = 10.0;

    // Horizontal tilt speed range (blocks/tick)
    private static final double MIN_TILT = 0.10;
    private static final double MAX_TILT = 0.25;

    // Delay between individual meteors in a shower (ticks)
    private static final int MIN_SPACING = 15; // 0.75 s
    private static final int MAX_SPACING = 35; // 1.75 s

    /** Queued meteors waiting to be spawned one by one. */
    private static final Queue<PendingMeteor> pendingQueue = new ArrayDeque<>();

    /** Ticks until the next queued meteor is spawned. */
    private static int nextSpawnIn = 0;

    /** Per-dimension shower countdown (normal mode). */
    private static final Map<String, Integer> tickCounters = new HashMap<>();

    // ---------------------------------------------------------------------------

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel serverLevel)) return;
        if (!serverLevel.dimension().equals(Level.OVERWORLD)) return;

        List<ServerPlayer> players = serverLevel.players();
        if (players.isEmpty()) return;

        int customDelay = MeteorConfig.getDelayTicks();

        if (customDelay == -1) {
            // Continuous mode: refill the queue as soon as it drains
            if (pendingQueue.isEmpty()) {
                enqueueMeteorShower(serverLevel, players);
            }
        } else {
            // Timed mode: count down to next shower
            String key = serverLevel.dimension().location().toString();
            int remaining = tickCounters.getOrDefault(key, customDelay);
            remaining--;
            if (remaining <= 0) {
                enqueueMeteorShower(serverLevel, players);
                tickCounters.put(key, customDelay);
            } else {
                tickCounters.put(key, remaining);
            }
        }

        // Spawn the next queued meteor when its slot arrives
        if (!pendingQueue.isEmpty()) {
            if (nextSpawnIn <= 0) {
                PendingMeteor pm = pendingQueue.poll();
                spawnMeteor(serverLevel, pm);
                // Schedule the next one only if the queue still has entries
                if (!pendingQueue.isEmpty()) {
                    nextSpawnIn = MIN_SPACING + serverLevel.random.nextInt(MAX_SPACING - MIN_SPACING + 1);
                }
            } else {
                nextSpawnIn--;
            }
        }
    }

    /** Called from the command to reset all running state. */
    public static void resetCounters() {
        tickCounters.clear();
        pendingQueue.clear();
        nextSpawnIn = 0;
    }

    // ---------------------------------------------------------------------------

    /**
     * Computes spawn parameters for 1 meteor and adds it to the queue.
     * Nothing is actually spawned here — the meteor is released in
     * the tick handler above.
     */
    private static void enqueueMeteorShower(ServerLevel level, List<ServerPlayer> players) {
        int count = 1;

        for (int i = 0; i < count; i++) {
            ServerPlayer target = players.get(level.random.nextInt(players.size()));

            // Random point on annulus [MIN_RADIUS, MAX_RADIUS] around the player
            double spawnAngle = level.random.nextDouble() * 2.0 * Math.PI;
            double radius = MIN_RADIUS + level.random.nextDouble() * (MAX_RADIUS - MIN_RADIUS);
            double spawnX = target.getX() + Math.cos(spawnAngle) * radius;
            double spawnZ = target.getZ() + Math.sin(spawnAngle) * radius;
            double spawnY = level.getMaxBuildHeight() - 5.0;

            // Random tilt: small horizontal velocity in a random direction
            double tiltDir   = level.random.nextDouble() * 2.0 * Math.PI;
            double tiltSpeed = MIN_TILT + level.random.nextDouble() * (MAX_TILT - MIN_TILT);
            double vx = Math.cos(tiltDir) * tiltSpeed;
            double vz = Math.sin(tiltDir) * tiltSpeed;

            pendingQueue.add(new PendingMeteor(spawnX, spawnY, spawnZ, vx, -1.0, vz));
        }

        // First meteor should fire immediately on the next tick
        nextSpawnIn = 0;
    }

    private static void spawnMeteor(ServerLevel level, PendingMeteor pm) {
        MeteorEntity meteor = new MeteorEntity(ModEntities.METEOR.get(), level);
        meteor.setPos(pm.x, pm.y, pm.z);
        meteor.setDeltaMovement(pm.vx, pm.vy, pm.vz);
        level.addFreshEntity(meteor);
    }

    // ---------------------------------------------------------------------------

    private record PendingMeteor(double x, double y, double z, double vx, double vy, double vz) {}
}
