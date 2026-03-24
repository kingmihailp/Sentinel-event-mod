package com.example.meteormod.command;

import com.example.meteormod.MeteorConfig;
import com.example.meteormod.MeteorMod;
import com.example.meteormod.entity.ModEntities;
import com.example.meteormod.entity.SentinelHoverEntity;
import com.example.meteormod.event.MeteorSpawnHandler;
import com.example.meteormod.item.ModItems;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.context.CommandContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.DimensionTransition;
import net.minecraft.world.phys.Vec3;

public class MeteorCommand {

    /**
     * Registers:
     *   /sentinelmod setmeteordelay <ticks>
     *
     * Special value -1 → continuous (meteors fall non-stop every tick).
     * Any positive integer → fixed interval in ticks between showers.
     */
    /** ResourceKey for the outer_space dimension. */
    public static final ResourceKey<Level> OUTER_SPACE =
            ResourceKey.create(Registries.DIMENSION,
                    ResourceLocation.fromNamespaceAndPath(MeteorMod.MOD_ID, "outer_space"));

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
                Commands.literal("sentinelmod")
                        .requires(src -> src.hasPermission(2)) // operator level
                        .then(Commands.literal("setmeteordelay")
                                .then(Commands.argument("ticks", IntegerArgumentType.integer(-1, MeteorConfig.MAX_DELAY))
                                        .executes(MeteorCommand::executeSetDelay)
                                )
                        )
                        .then(Commands.literal("travel")
                                .then(Commands.literal("outer_space")
                                        .executes(MeteorCommand::executeTravelOuterSpace)
                                )
                        )
        );
    }

    private static int executeSetDelay(CommandContext<CommandSourceStack> ctx) {
        int ticks = IntegerArgumentType.getInteger(ctx, "ticks");

        // 0 is meaningless — reject it
        if (ticks == 0) {
            ctx.getSource().sendFailure(
                    Component.literal("Invalid value: 0. Use -1 for continuous mode or a positive tick count.")
            );
            return 0;
        }

        try {
            MeteorConfig.setDelayTicks(ticks);
        } catch (IllegalArgumentException e) {
            ctx.getSource().sendFailure(Component.literal(e.getMessage()));
            return 0;
        }

        // Reset all running countdowns so the new delay takes effect immediately
        MeteorSpawnHandler.resetCounters();

        if (ticks == -1) {
            ctx.getSource().sendSuccess(
                    () -> Component.literal("[MeteorMod] Meteor shower set to CONTINUOUS mode — meteors will fall non-stop!"),
                    true
            );
        } else {
            double seconds = ticks / 20.0;
            double minutes = seconds / 60.0;
            String timeStr = minutes >= 1.0
                    ? String.format("%.1f min (%d ticks)", minutes, ticks)
                    : String.format("%.1f sec (%d ticks)", seconds, ticks);
            ctx.getSource().sendSuccess(
                    () -> Component.literal("[MeteorMod] Meteor shower delay set to " + timeStr),
                    true
            );
        }
        return 1;
    }

    private static int executeTravelOuterSpace(CommandContext<CommandSourceStack> ctx) {
        CommandSourceStack src = ctx.getSource();

        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("[MeteorMod] This command must be run by a player."));
            return 0;
        }

        ServerLevel targetLevel = src.getServer().getLevel(OUTER_SPACE);
        if (targetLevel == null) {
            src.sendFailure(Component.literal(
                    "[MeteorMod] Outer Space dimension is not loaded. Make sure the mod is installed correctly."));
            return 0;
        }

        // Check whether player has a hover in inventory or hotbar
        boolean hasHover = findAndRemoveHover(player);
        float yaw = player.getYRot();

        // Teleport player to y=65 in the void
        Vec3 spawnPos = new Vec3(0.5, 65.0, 0.5);
        player.changeDimension(new DimensionTransition(
                targetLevel,
                spawnPos,
                Vec3.ZERO,
                yaw,
                player.getXRot(),
                DimensionTransition.DO_NOTHING
        ));

        if (hasHover) {
            // Spawn hover at the same position and make the player ride it
            SentinelHoverEntity hover =
                    new SentinelHoverEntity(ModEntities.SENTINEL_HOVER.get(), targetLevel);
            hover.setPos(spawnPos.x, spawnPos.y, spawnPos.z);
            hover.setYRot(yaw);
            targetLevel.addFreshEntity(hover);
            player.startRiding(hover, true);

            src.sendSuccess(
                    () -> Component.literal("[MeteorMod] Launched into Outer Space on your hover."),
                    true
            );
        } else {
            src.sendSuccess(
                    () -> Component.literal("[MeteorMod] Ejected into Outer Space — no hover found. Good luck."),
                    true
            );
        }
        return 1;
    }

    /**
     * Searches the player's full inventory (hotbar + main) for one
     * Sentinel Hover item.  Removes it if found and returns {@code true}.
     */
    private static boolean findAndRemoveHover(ServerPlayer player) {
        Inventory inv = player.getInventory();
        for (int i = 0; i < inv.getContainerSize(); i++) {
            ItemStack stack = inv.getItem(i);
            if (!stack.isEmpty() && stack.is(ModItems.SENTINEL_HOVER.get())) {
                stack.shrink(1);
                return true;
            }
        }
        return false;
    }
}
