package com.example.meteormod.client;

import com.example.meteormod.sound.ModSounds;
import net.minecraft.client.resources.sounds.AbstractTickableSoundInstance;
import net.minecraft.client.resources.sounds.SoundInstance;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

/**
 * Looping ambient sound that plays while the player has the scope overlay active.
 * The OGG file is expected to be exactly 36 seconds long; looping = true
 * causes the sound engine to restart it automatically after each play-through.
 */
@OnlyIn(Dist.CLIENT)
public class SentinelCannonAmbientSound extends AbstractTickableSoundInstance {

    private final Player player;

    public SentinelCannonAmbientSound(Player player) {
        super(ModSounds.SENTINEL_CANNON_TARGET_AMBIENT.get(), SoundSource.PLAYERS,
                SoundInstance.createUnseededRandom());
        this.player = player;
        this.looping = true;
        this.delay = 0;
        this.volume = 1.0f;
        this.pitch = 1.0f;
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
        this.attenuation = Attenuation.NONE; // full volume, no distance falloff
    }

    @Override
    public void tick() {
        if (!ScopeOverlay.active || !ScopeOverlay.holdsCannon(player)) {
            stop();
            return;
        }
        // Follow the player so positional audio stays attached
        this.x = player.getX();
        this.y = player.getY();
        this.z = player.getZ();
    }

    @Override
    public boolean canPlaySound() {
        return ScopeOverlay.active && ScopeOverlay.holdsCannon(player);
    }

    @Override
    public boolean canStartSilent() {
        return true;
    }
}
