package com.example.meteormod.event;

import com.example.meteormod.MeteorMod;
import com.example.meteormod.entity.ModEntities;
import com.example.meteormod.entity.SentinelEntity;
import com.example.meteormod.network.RaidUpdatePayload;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.tick.LevelTickEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.*;

/**
 * Server-side raid state machine.
 *
 * Flow:
 *  1. Captain Sentinel killed by player  → onCaptainKilled() → start raid
 *  2. 2-second intro delay, then wave 1 spawns (2-3 sentinels, 15-25 blocks away)
 *  3. Wave cleared (all sentinels dead)  → 3-second delay → next wave
 *  4. After wave 6 cleared              → raid ends, HUD hides
 *
 * The HUD shows 6 diamonds; defeated waves fade out (server sends completedWaves count).
 */
@EventBusSubscriber(modid = MeteorMod.MOD_ID, bus = EventBusSubscriber.Bus.GAME)
public class SentinelRaidManager {

    /** Sentinel count range per wave (min, max inclusive). Index = wave index (0-based). */
    private static final int[][] WAVE_COUNTS = {
            {2, 3},  // wave 1
            {4, 6},  // wave 2
            {6, 8},  // wave 3
            {8, 10}, // wave 4
            {2, 2},  // wave 5
            {1, 1},  // wave 6
    };

    private static final int INTRO_DELAY       = 40;  // 2 s before first wave
    private static final int BETWEEN_WAVE_DELAY = 60; // 3 s between waves

    // ── State (per player, server-side only) ──────────────────────────────────
    private static final Map<UUID, SentinelRaid> raids = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called when a captain Sentinel is killed by a player.
     * Starts a new raid for that player if they don't have one already.
     */
    public static void onCaptainKilled(Player player, ServerLevel level) {
        UUID pid = player.getUUID();
        if (raids.containsKey(pid)) return; // already in raid

        SentinelRaid raid = new SentinelRaid(pid);
        raids.put(pid, raid);
        sendHud(level, pid, 0, true);
    }

    /** Clears all raid state (e.g. on world reload). */
    public static void reset() {
        raids.clear();
    }

    // ── Tick handler ──────────────────────────────────────────────────────────

    @SubscribeEvent
    public static void onLevelTick(LevelTickEvent.Post event) {
        if (!(event.getLevel() instanceof ServerLevel level)) return;
        if (!level.dimension().equals(Level.OVERWORLD)) return;
        tick(level);
    }

    private static void tick(ServerLevel level) {
        Iterator<Map.Entry<UUID, SentinelRaid>> it = raids.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<UUID, SentinelRaid> entry = it.next();
            UUID pid   = entry.getKey();
            SentinelRaid raid = entry.getValue();

            // Drop raid if player disconnected
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(pid);
            if (player == null) {
                it.remove();
                continue;
            }

            if (raid.waveActive) {
                // ── Wave in progress: check for remaining sentinels ──────────
                raid.activeSentinels.removeIf(uuid -> {
                    Entity e = level.getEntity(uuid);
                    if (e == null) return true;                        // removed from world
                    if (e instanceof LivingEntity le) return le.getHealth() <= 0;
                    return e.isRemoved();
                });

                if (raid.activeSentinels.isEmpty()) {
                    // Wave cleared
                    raid.waveActive = false;
                    raid.completedWaves++;

                    if (raid.completedWaves >= WAVE_COUNTS.length) {
                        // All 6 waves done — victory
                        sendHud(level, pid, raid.completedWaves, false);
                        it.remove();
                    } else {
                        sendHud(level, pid, raid.completedWaves, true);
                        raid.spawnTimer = BETWEEN_WAVE_DELAY;
                    }
                }

            } else {
                // ── Waiting for next wave spawn ──────────────────────────────
                if (raid.spawnTimer > 0) {
                    raid.spawnTimer--;
                } else {
                    spawnWave(raid, player, level);
                }
            }
        }
    }

    // ── Wave spawning ─────────────────────────────────────────────────────────

    private static void spawnWave(SentinelRaid raid, ServerPlayer player, ServerLevel level) {
        int[] range = WAVE_COUNTS[raid.completedWaves];
        int count   = (range[0] == range[1])
                ? range[0]
                : range[0] + level.random.nextInt(range[1] - range[0] + 1);

        for (int i = 0; i < count; i++) {
            double angle  = level.random.nextDouble() * 2.0 * Math.PI;
            double radius = 15.0 + level.random.nextDouble() * 10.0;
            double sx = player.getX() + Math.cos(angle) * radius;
            double sz = player.getZ() + Math.sin(angle) * radius;
            double sy = player.getY() + 5.0; // above player, sentinels fly

            SentinelEntity s = new SentinelEntity(ModEntities.SENTINEL.get(), level);
            s.setPos(sx, sy, sz);
            s.setHealth(s.getMaxHealth());
            level.addFreshEntity(s);
            raid.activeSentinels.add(s.getUUID());
        }

        raid.waveActive = true;
    }

    // ── Packet helper ─────────────────────────────────────────────────────────

    private static void sendHud(ServerLevel level, UUID pid, int completedWaves, boolean active) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(pid);
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new RaidUpdatePayload(completedWaves, active));
        }
    }

    // ── Inner state class ─────────────────────────────────────────────────────

    private static final class SentinelRaid {
        final UUID playerId;
        int  completedWaves = 0;    // waves defeated so far (0–6)
        boolean waveActive  = false; // true while sentinels are alive
        int  spawnTimer     = INTRO_DELAY; // ticks until next wave
        final Set<UUID> activeSentinels = new HashSet<>();

        SentinelRaid(UUID playerId) { this.playerId = playerId; }
    }
}
