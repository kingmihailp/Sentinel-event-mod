package com.example.meteormod.network;

import com.example.meteormod.MeteorMod;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/** Client → Server: player pressed attack while riding the Sentinel Hover. */
public record ShootHoverPacket() implements CustomPacketPayload {

    public static final Type<ShootHoverPacket> TYPE =
            new Type<>(ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "shoot_hover"));

    /** Empty payload – server reads the player's look direction itself. */
    public static final StreamCodec<ByteBuf, ShootHoverPacket> STREAM_CODEC =
            StreamCodec.of((buf, pkt) -> {}, buf -> new ShootHoverPacket());

    @Override
    public Type<? extends CustomPacketPayload> type() { return TYPE; }
}
