package com.example.meteormod;

/**
 * Runtime configuration for the Meteor Mod.
 * Values here are NOT persisted to disk — they reset when the server restarts.
 * They are meant to be set via the /sentinelmod command during a session.
 */
public final class MeteorConfig {

    /**
     * Delay between meteor showers in ticks.
     * <ul>
     *   <li>Positive value → fixed interval in ticks between showers.</li>
     *   <li>{@code -1} → continuous mode: a new shower spawns every tick (i.e. non-stop).</li>
     * </ul>
     * Default is a random value between 5 and 8 minutes (6 000 – 9 600 ticks).
     */
    private static volatile int delayTicks = 6000; // default 5 minutes

    /** Minimum allowed delay (1 tick). */
    public static final int MIN_DELAY = 1;

    /** Maximum allowed delay (30 minutes in ticks). */
    public static final int MAX_DELAY = 30 * 60 * 20;

    private MeteorConfig() {}

    public static int getDelayTicks() {
        return delayTicks;
    }

    /**
     * Set the meteor shower delay.
     *
     * @param ticks positive tick count, or {@code -1} for continuous mode.
     * @throws IllegalArgumentException if the value is 0 or below -1.
     */
    public static void setDelayTicks(int ticks) {
        if (ticks != -1 && ticks < MIN_DELAY) {
            throw new IllegalArgumentException(
                    "Delay must be >= " + MIN_DELAY + " or -1 for continuous mode. Got: " + ticks);
        }
        delayTicks = ticks;
    }
}
