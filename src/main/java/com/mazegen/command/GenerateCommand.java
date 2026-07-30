package com.mazegen.command;

import com.mazegen.maze.MazeBuilder;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.minecraft.command.argument.BlockPosArgumentType;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

public final class GenerateCommand {

    private GenerateCommand() {}

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) ->
                dispatcher.register(CommandManager.literal("generate")
                        .requires(source -> source.hasPermissionLevel(2))
                        .then(CommandManager.literal("maze")
                                .then(CommandManager.argument("pos", BlockPosArgumentType.blockPos())
                                        .executes(GenerateCommand::run)))));
    }

    private static int run(com.mojang.brigadier.context.CommandContext<ServerCommandSource> ctx) {
        ServerCommandSource source = ctx.getSource();
        BlockPos center = BlockPosArgumentType.getBlockPos(ctx, "pos");

        if (!(source.getWorld() instanceof ServerWorld world)) {
            source.sendError(Text.literal("No se pudo obtener el mundo del servidor."));
            return 0;
        }

        MazeBuilder.generate(world, center, source);
        return 1;
    }
}
