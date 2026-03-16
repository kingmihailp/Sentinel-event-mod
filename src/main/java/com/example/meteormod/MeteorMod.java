package com.example.meteormod;

import com.example.meteormod.entity.ModEntities;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.common.Mod;

@Mod(MeteorMod.MOD_ID)
public class MeteorMod {

    public static final String MOD_ID = "meteormod";

    public MeteorMod(IEventBus modEventBus) {
        ModEntities.ENTITIES.register(modEventBus);
    }
}
