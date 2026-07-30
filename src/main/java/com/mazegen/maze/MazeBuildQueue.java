package com.mazegen.maze;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.minecraft.block.BlockState;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

/**
 * Coloca los bloques de un laberinto repartidos en varios ticks del servidor para no
 * congelarlo con cientos de miles de colocaciones de golpe en el mismo tick.
 * <p>
 * Sencillo y de un solo hilo de trabajo: si se piden varios laberintos a la vez, se
 * procesan en orden de llegada (uno detrás de otro), no en paralelo.
 */
public final class MazeBuildQueue {

    public record BlockJob(BlockPos pos, BlockState state) {}

    private static final int BLOCKS_PER_TICK = 8000;

    private static final Deque<BlockJob> queue = new ArrayDeque<>();
    private static boolean registered = false;

    private static ServerWorld currentWorld;
    private static ServerPlayerEntity currentPlayer;
    private static long jobStartMillis;
    private static int jobTotalBlocks;
    private static int jobPlacedSoFar;

    private MazeBuildQueue() {}

    public static void submit(ServerWorld world, List<BlockJob> jobs, ServerPlayerEntity player) {
        ensureRegistered();
        queue.addAll(jobs);
        currentWorld = world;
        currentPlayer = player;
        jobStartMillis = System.currentTimeMillis();
        jobTotalBlocks = jobs.size();
        jobPlacedSoFar = 0;
    }

    private static void ensureRegistered() {
        if (registered) return;
        registered = true;
        ServerTickEvents.END_SERVER_TICK.register(server -> tick());
    }

    private static void tick() {
        if (queue.isEmpty() || currentWorld == null) return;

        int placedThisTick = 0;
        while (!queue.isEmpty() && placedThisTick < BLOCKS_PER_TICK) {
            BlockJob job = queue.poll();
            currentWorld.setBlockState(job.pos(), job.state(), 3);
            placedThisTick++;
            jobPlacedSoFar++;
        }

        if (queue.isEmpty()) {
            double seconds = (System.currentTimeMillis() - jobStartMillis) / 1000.0;
            if (currentPlayer != null && currentPlayer.isAlive()) {
                currentPlayer.sendMessage(
                        Text.literal(String.format(
                                "§6[MazeGen] §fLaberinto completado: §a%d §fbloques en §a%.1fs§f.",
                                jobTotalBlocks, seconds)),
                        false);
            }
            currentWorld = null;
            currentPlayer = null;
        }
    }
}
