package com.mazegen.maze;

import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.block.VineBlock;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.state.property.Properties;
import net.minecraft.text.Text;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Traduce una {@link MazeGrid} a una lista de bloques reales y la manda a construir.
 */
public final class MazeBuilder {

    /** Celdas por lado. cellCount * 3 + 1 = tamaño real en bloques (100 -> 301x301, ~300x300). */
    private static final int CELL_COUNT = 100;
    private static final int WALL_HEIGHT = 4;     // bloques de pared, de suelo+1 a suelo+WALL_HEIGHT
    private static final int TORCH_EVERY_CELLS = 3;
    private static final double VINE_CHANCE = 0.14;
    private static final int VINE_MIN_LEN = 1;
    private static final int VINE_MAX_LEN = 3;

    // 0=este,1=oeste,2=sur,3=norte -> Direction real de Minecraft
    private static final Direction[] DIR_TO_MC = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};

    private MazeBuilder() {}

    public static void generate(ServerWorld world, BlockPos center, ServerCommandSource source) {
        long seed = new Random().nextLong() ^ System.nanoTime();
        MazeGrid grid = new MazeGrid(CELL_COUNT, seed);

        int origX = center.getX() - grid.blockSize / 2;
        int origZ = center.getZ() - grid.blockSize / 2;
        int baseY = center.getY();

        List<MazeBuildQueue.BlockJob> jobs = new ArrayList<>(grid.blockSize * grid.blockSize * (WALL_HEIGHT + 2));
        Random rnd = new Random(seed ^ 0x9E3779B97F4A7C15L);

        // --- Suelo, paredes/aire y techo ---
        for (int x = 0; x < grid.blockSize; x++) {
            for (int z = 0; z < grid.blockSize; z++) {
                boolean open = grid.open[x][z];
                BlockPos worldPos = new BlockPos(origX + x, baseY, origZ + z);

                BlockState floorState;
                if (grid.isEntranceFloorBlock(x, z)) {
                    floorState = Blocks.RED_CONCRETE.getDefaultState();
                } else if (grid.isExitFloorBlock(x, z)) {
                    floorState = Blocks.GREEN_CONCRETE.getDefaultState();
                } else {
                    floorState = pickFloorMaterial(rnd);
                }
                jobs.add(new MazeBuildQueue.BlockJob(worldPos, floorState));

                if (open) {
                    for (int y = 1; y <= WALL_HEIGHT; y++) {
                        jobs.add(new MazeBuildQueue.BlockJob(worldPos.up(y), Blocks.AIR.getDefaultState()));
                    }
                } else {
                    BlockState wallState = pickWallMaterial(rnd);
                    for (int y = 1; y <= WALL_HEIGHT; y++) {
                        jobs.add(new MazeBuildQueue.BlockJob(worldPos.up(y), wallState));
                    }
                }

                // techo de cristal sobre todo el recinto
                jobs.add(new MazeBuildQueue.BlockJob(worldPos.up(WALL_HEIGHT + 1), Blocks.GLASS.getDefaultState()));
            }
        }

        // --- Antorchas y enredaderas en caras de pared que dan a un pasillo ---
        for (int x = 0; x < grid.blockSize; x++) {
            for (int z = 0; z < grid.blockSize; z++) {
                if (grid.open[x][z]) continue; // solo nos interesan columnas sólidas

                for (int d = 0; d < 4; d++) {
                    int nx = x + DX[d], nz = z + DZ[d];
                    if (nx < 0 || nz < 0 || nx >= grid.blockSize || nz >= grid.blockSize) continue;
                    if (!grid.open[nx][nz]) continue; // el vecino tiene que ser pasillo

                    BlockPos neighborBase = new BlockPos(origX + nx, baseY, origZ + nz);
                    Direction facingIntoRoom = DIR_TO_MC[d];       // hacia dónde "mira" la antorcha
                    Direction attachSide = facingIntoRoom.getOpposite(); // cara de la enredadera pegada a la pared

                    // Antorchas: patrón periódico y determinista según la celda, no 100% aleatorio.
                    if (Math.floorMod(x + z, TORCH_EVERY_CELLS * 3) == 0) {
                        BlockPos torchPos = neighborBase.up(2);
                        jobs.add(new MazeBuildQueue.BlockJob(torchPos,
                                Blocks.WALL_TORCH.getDefaultState().with(Properties.HORIZONTAL_FACING, facingIntoRoom)));
                    }

                    // Enredaderas: solo estético, en algunos tramos cerca del techo, nunca cubriendo todo.
                    if (rnd.nextDouble() < VINE_CHANCE) {
                        int len = VINE_MIN_LEN + rnd.nextInt(VINE_MAX_LEN - VINE_MIN_LEN + 1);
                        BlockState vineState = Blocks.VINE.getDefaultState().with(vineProperty(attachSide), true);
                        for (int i = 0; i < len; i++) {
                            int y = WALL_HEIGHT - i;
                            if (y < 1) break;
                            jobs.add(new MazeBuildQueue.BlockJob(neighborBase.up(y), vineState));
                        }
                    }
                }
            }
        }

        // --- Puertas exteriores en entrada y salida ---
        carveExteriorOpening(jobs, grid, origX, origZ, baseY, grid.entranceCellX, grid.entranceCellZ, grid.entranceDir);
        carveExteriorOpening(jobs, grid, origX, origZ, baseY, grid.exitCellX, grid.exitCellZ, grid.exitDir);

        ServerPlayerEntity player = source.getEntity() instanceof ServerPlayerEntity p ? p : null;
        source.sendFeedback(() -> Text.literal(String.format(
                "§6[MazeGen] §fConstruyendo laberinto de §a%dx%d §fen (%d, %d, %d)... (%d bloques)",
                grid.blockSize, grid.blockSize, center.getX(), center.getY(), center.getZ(), jobs.size())), false);

        MazeBuildQueue.submit(world, jobs, player);
    }

    /** Abre un hueco de 2 bloques de ancho en la pared exterior de una celda de borde, hacia fuera del recinto. */
    private static void carveExteriorOpening(List<MazeBuildQueue.BlockJob> jobs, MazeGrid grid,
                                              int origX, int origZ, int baseY, int cellX, int cellZ, int dir) {
        int bx = cellX * 3 + 1, bz = cellZ * 3 + 1;
        int[] localCoords;
        switch (dir) {
            case 0 -> localCoords = new int[]{bx + 2, bz, bx + 2, bz + 1}; // este
            case 1 -> localCoords = new int[]{bx - 1, bz, bx - 1, bz + 1}; // oeste
            case 2 -> localCoords = new int[]{bx, bz + 2, bx + 1, bz + 2}; // sur
            default -> localCoords = new int[]{bx, bz - 1, bx + 1, bz - 1}; // norte
        }
        int[][] pts = {{localCoords[0], localCoords[1]}, {localCoords[2], localCoords[3]}};
        for (int[] pt : pts) {
            BlockPos base = new BlockPos(origX + pt[0], baseY, origZ + pt[1]);
            for (int y = 1; y <= WALL_HEIGHT; y++) {
                jobs.add(new MazeBuildQueue.BlockJob(base.up(y), Blocks.AIR.getDefaultState()));
            }
        }
    }

    private static net.minecraft.state.property.BooleanProperty vineProperty(Direction side) {
        return switch (side) {
            case NORTH -> VineBlock.NORTH;
            case SOUTH -> VineBlock.SOUTH;
            case EAST -> VineBlock.EAST;
            case WEST -> VineBlock.WEST;
            default -> VineBlock.UP;
        };
    }

    private static BlockState pickWallMaterial(Random rnd) {
        double r = rnd.nextDouble();
        if (r < 0.40) return Blocks.STONE.getDefaultState();
        if (r < 0.75) return Blocks.COBBLESTONE.getDefaultState();
        return Blocks.MOSSY_COBBLESTONE.getDefaultState();
    }

    private static BlockState pickFloorMaterial(Random rnd) {
        double r = rnd.nextDouble();
        // "Pale Moss Block" no existe en 1.21.1 (llegó en 1.21.4); se sustituye por Calcita
        // para dar ese contraste de piedra clara/pálida entre el musgo.
        if (r < 0.35) return Blocks.MOSSY_COBBLESTONE.getDefaultState();
        if (r < 0.70) return Blocks.MOSS_BLOCK.getDefaultState();
        return Blocks.CALCITE.getDefaultState();
    }
}
