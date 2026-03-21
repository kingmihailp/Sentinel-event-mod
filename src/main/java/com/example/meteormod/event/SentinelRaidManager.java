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
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
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
 * Persistence:
 *  - On player disconnect: completedWaves + spawnTimer saved to RaidSavedData
 *  - On player reconnect : state restored, HUD re-sent
 *  - On server restart   : same — world data survives between sessions
 *  - activeSentinels is NOT saved; if a wave was active it restarts after
 *    BETWEEN_WAVE_DELAY ticks.
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

    public  static final int TOTAL_WAVES        = 6;
    private static final int INTRO_DELAY        = 40;  // 2 s before first wave
    static         final int BETWEEN_WAVE_DELAY = 60;  // 3 s between waves

    // ── In-memory state (online players only) ────────────────────────────────
    private static final Map<UUID, SentinelRaid> raids = new HashMap<>();

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Called when a captain Sentinel is killed by a player.
     * Starts a new raid for that player if they don't have one already.
     */
    public static void onCaptainKilled(Player player, ServerLevel level) {
        UUID pid = player.getUUID();
        // Block if raid is active in memory OR saved on disk (player was offline)
        if (raids.containsKey(pid) || getSavedData(level).has(pid)) return;

        SentinelRaid raid = new SentinelRaid(pid);
        raids.put(pid, raid);
        syncToSavedData(level, pid, raid);
        sendHud(level, pid, 0, true, 0);
    }

    /** Clears all in-memory raid state (e.g. on world reload). */
    public static void reset() {
        raids.clear();
    }

    // ── Player login / logout ─────────────────────────────────────────────────

    /**
     * On login: restore any saved raid from disk and re-send the HUD.
     */
    @SubscribeEvent
    public static void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        UUID pid = player.getUUID();
        if (raids.containsKey(pid)) return; // already running (shouldn't happen)

        int[] state = getSavedData(overworld).take(pid);
        if (state == null) return;

        SentinelRaid raid    = new SentinelRaid(pid);
        raid.completedWaves  = state[0];
        raid.spawnTimer      = state[1];
        raid.waveActive      = false; // sentinels are gone; wave will re-spawn
        raids.put(pid, raid);

        sendHud(overworld, pid, raid.completedWaves, true, 0);
    }

    /**
     * On logout: persist raid state to disk and remove from in-memory map.
     */
    @SubscribeEvent
    public static void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        ServerLevel overworld = player.getServer().getLevel(Level.OVERWORLD);
        if (overworld == null) return;

        UUID pid = player.getUUID();
        SentinelRaid raid = raids.remove(pid);
        if (raid == null) return;

        // If a wave was in progress, restart it from scratch after the delay.
        int timer = raid.waveActive ? BETWEEN_WAVE_DELAY : raid.spawnTimer;
        getSavedData(overworld).put(pid, raid.completedWaves, timer);
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
            UUID        pid  = entry.getKey();
            SentinelRaid raid = entry.getValue();

            // Player offline — will be handled by onPlayerLogout; skip here.
            ServerPlayer player = level.getServer().getPlayerList().getPlayer(pid);
            if (player == null) continue;

            if (raid.waveActive) {
                // ── Wave in progress: check for remaining sentinels ──────────
                int beforeSize = raid.activeSentinels.size();
                raid.activeSentinels.removeIf(uuid -> {
                    Entity e = level.getEntity(uuid);
                    if (e == null) return true;
                    if (e instanceof LivingEntity le) return le.getHealth() <= 0;
                    return e.isRemoved();
                });
                int afterSize = raid.activeSentinels.size();

                // Send HUD update whenever enemy count drops
                if (afterSize != beforeSize && afterSize > 0) {
                    sendHud(level, pid, raid.completedWaves, true, afterSize);
                    raid.lastSentEnemies = afterSize;
                }

                if (raid.activeSentinels.isEmpty()) {
                    // Wave cleared
                    raid.waveActive = false;
                    raid.completedWaves++;

                    if (raid.completedWaves >= TOTAL_WAVES) {
                        // All 6 waves done — victory
                        sendHud(level, pid, raid.completedWaves, false, 0);
                        getSavedData(level).take(pid); // remove persisted data
                        it.remove();
                    } else {
                        raid.spawnTimer = BETWEEN_WAVE_DELAY;
                        sendHud(level, pid, raid.completedWaves, true, 0);
                        syncToSavedData(level, pid, raid);
                    }
                    raid.lastSentEnemies = 0;
                }

            } else {
                // ── Waiting for next wave spawn ──────────────────────────────
                if (raid.spawnTimer > 0) {
                    raid.spawnTimer--;
                } else {
                    spawnWave(raid, player, level);
                    syncToSavedData(level, pid, raid);
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
            double sy = player.getY() + 5.0;

            SentinelEntity s = new SentinelEntity(ModEntities.SENTINEL.get(), level);
            s.setPos(sx, sy, sz);
            s.setHealth(s.getMaxHealth());
            level.addFreshEntity(s);
            s.startCombatAgainst(player); // immediately target the player
            raid.activeSentinels.add(s.getUUID());
        }

        raid.waveActive = true;
        raid.lastSentEnemies = raid.activeSentinels.size();
        sendHud(level, raid.playerId, raid.completedWaves, true, raid.lastSentEnemies);
    }

    // ── Persistence helpers ───────────────────────────────────────────────────

    private static RaidSavedData getSavedData(ServerLevel level) {
        return level.getServer()
                    .getLevel(Level.OVERWORLD)
                    .getDataStorage()
                    .computeIfAbsent(RaidSavedData.FACTORY, RaidSavedData.NAME);
    }

    /** Mirror current in-memory raid state to SavedData (marks dirty → auto-saved). */
    private static void syncToSavedData(ServerLevel level, UUID pid, SentinelRaid raid) {
        int timer = raid.waveActive ? BETWEEN_WAVE_DELAY : raid.spawnTimer;
        getSavedData(level).put(pid, raid.completedWaves, timer);
    }

    // ── Aggro sharing ─────────────────────────────────────────────────────────

    /**
     * Called when {@code hitSentinel} is hurt during an active raid.
     * Every other living sentinel in the same raid immediately enters combat
     * targeting {@code attacker}.
     */
    public static void alertRaidSentinels(ServerLevel level,
                                          com.example.meteormod.entity.SentinelEntity hitSentinel,
                                          LivingEntity attacker) {
        UUID hitId = hitSentinel.getUUID();
        for (SentinelRaid raid : raids.values()) {
            if (!raid.activeSentinels.contains(hitId)) continue;
            for (UUID uuid : raid.activeSentinels) {
                if (uuid.equals(hitId)) continue;
                Entity e = level.getEntity(uuid);
                if (e instanceof com.example.meteormod.entity.SentinelEntity other
                        && other.isAlive()) {
                    other.startCombatAgainst(attacker);
                }
            }
            break; // sentinel can only be in one raid
        }
    }

    // ── Packet helper ─────────────────────────────────────────────────────────

    private static void sendHud(ServerLevel level, UUID pid, int completedWaves, boolean active, int enemiesLeft) {
        ServerPlayer player = level.getServer().getPlayerList().getPlayer(pid);
        if (player != null) {
            PacketDistributor.sendToPlayer(player, new RaidUpdatePayload(completedWaves, active, enemiesLeft));
        }
    }

    // ── Inner state class ─────────────────────────────────────────────────────

    private static final class SentinelRaid {
        final UUID playerId;
        int     completedWaves  = 0;
        boolean waveActive      = false;
        int     spawnTimer      = INTRO_DELAY;
        int     lastSentEnemies = 0;
        final Set<UUID> activeSentinels = new HashSet<>();

        SentinelRaid(UUID playerId) { this.playerId = playerId; }
    }
}
