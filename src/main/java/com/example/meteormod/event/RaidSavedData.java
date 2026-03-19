package com.example.meteormod.event;

import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Persists active sentinel raids to level data so they survive server
 * restarts and player reconnects.
 *
 * Stored in: <world>/data/sentinel_raids.dat
 *
 * Each entry stores:
 *   - completedWaves : how many waves have been cleared (0-5)
 *   - spawnTimer     : ticks remaining until the next wave spawns
 *
 * activeSentinels is intentionally NOT saved — after a restart those
 * entities are gone, so the current wave is restarted from scratch with
 * a short delay (BETWEEN_WAVE_DELAY).
 */
public class RaidSavedData extends SavedData {

    public static final String NAME = "sentinel_raids";

    public static final Factory<RaidSavedData> FACTORY =
            new Factory<>(RaidSavedData::new, RaidSavedData::load, null);

    /** keyed by player UUID */
    private final Map<UUID, int[]> data = new HashMap<>();

    private RaidSavedData() {}

    // ── Serialisation ─────────────────────────────────────────────────────────

    public static RaidSavedData load(CompoundTag tag, HolderLookup.Provider reg) {
        RaidSavedData d = new RaidSavedData();
        ListTag list = tag.getList("raids", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            CompoundTag e = list.getCompound(i);
            UUID pid           = e.getUUID("player");
            int  completedWaves = e.getInt("completedWaves");
            int  spawnTimer     = e.getInt("spawnTimer");
            if (completedWaves < SentinelRaidManager.TOTAL_WAVES) {
                d.data.put(pid, new int[]{completedWaves, spawnTimer});
            }
        }
        return d;
    }

    @Override
    public CompoundTag save(CompoundTag tag, HolderLookup.Provider reg) {
        ListTag list = new ListTag();
        for (Map.Entry<UUID, int[]> entry : data.entrySet()) {
            CompoundTag e = new CompoundTag();
            e.putUUID("player",        entry.getKey());
            e.putInt("completedWaves", entry.getValue()[0]);
            e.putInt("spawnTimer",     entry.getValue()[1]);
            list.add(e);
        }
        tag.put("raids", list);
        return tag;
    }

    // ── API ───────────────────────────────────────────────────────────────────

    /** Store / overwrite raid progress for a player. */
    public void put(UUID pid, int completedWaves, int spawnTimer) {
        data.put(pid, new int[]{completedWaves, spawnTimer});
        setDirty();
    }

    /** Remove and return [completedWaves, spawnTimer], or null if absent. */
    public int[] take(UUID pid) {
        int[] val = data.remove(pid);
        if (val != null) setDirty();
        return val;
    }

    /** True if a saved raid exists for this player. */
    public boolean has(UUID pid) {
        return data.containsKey(pid);
    }
}
