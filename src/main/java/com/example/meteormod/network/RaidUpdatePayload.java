package com.example.meteormod.network;

import com.example.meteormod.MeteorMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server→Client packet that updates the raid HUD.
 *
 * completedWaves – how many waves have been fully defeated (0–6).
 * active         – whether the raid is currently in progress.
 * enemiesLeft    – number of living sentinels remaining in the current wave.
 */
public record RaidUpdatePayload(int completedWaves, boolean active, int enemiesLeft)
        implements CustomPacketPayload {

    public static final Type<RaidUpdatePayload> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "raid_update"));

    public static final StreamCodec<ByteBuf, RaidUpdatePayload> STREAM_CODEC =
            StreamCodec.composite(
                    ByteBufCodecs.INT,  RaidUpdatePayload::completedWaves,
                    ByteBufCodecs.BOOL, RaidUpdatePayload::active,
                    ByteBufCodecs.INT,  RaidUpdatePayload::enemiesLeft,
                    RaidUpdatePayload::new
            );

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
