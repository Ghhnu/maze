package com.mazegen.maze;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Deque;
import java.util.List;
import java.util.Random;

/**
 * Genera la "planta" lógica de un laberinto perfecto (sin bucles, siempre resoluble)
 * usando el algoritmo de backtracking aleatorio (randomized DFS) sobre una rejilla de celdas.
 * <p>
 * Cada celda ocupa 3 bloques (2 de pasillo + 1 de pared compartida con la vecina), así que
 * los pasillos resultantes siempre tienen 2 bloques de ancho.
 * <p>
 * {@link #open} es la rejilla a nivel de bloque: true = suelo transitable, false = pared sólida.
 */
public class MazeGrid {

    private static final int[] DX = {1, -1, 0, 0};
    private static final int[] DZ = {0, 0, 1, -1};
    // dir 0 = este (+X), 1 = oeste (-X), 2 = sur (+Z), 3 = norte (-Z)

    public final int cellCount;      // celdas por lado
    public final int blockSize;      // cellCount * 3 + 1
    public final boolean[][] open;   // [x][z] a nivel de bloque

    public int entranceCellX, entranceCellZ, entranceDir;
    public int exitCellX, exitCellZ, exitDir;

    public MazeGrid(int cellCount, long seed) {
        this.cellCount = cellCount;
        this.blockSize = cellCount * 3 + 1;
        this.open = new boolean[blockSize][blockSize];
        generate(seed);
    }

    private void generate(long seed) {
        Random rnd = new Random(seed);
        boolean[][] visited = new boolean[cellCount][cellCount];
        Deque<int[]> stack = new ArrayDeque<>();

        visited[0][0] = true;
        carveCellFloor(0, 0);
        stack.push(new int[]{0, 0});

        while (!stack.isEmpty()) {
            int[] cur = stack.peek();
            int cx = cur[0], cz = cur[1];

            List<Integer> dirs = new ArrayList<>(List.of(0, 1, 2, 3));
            Collections.shuffle(dirs, rnd);

            boolean carved = false;
            for (int d : dirs) {
                int nx = cx + DX[d];
                int nz = cz + DZ[d];
                if (nx < 0 || nz < 0 || nx >= cellCount || nz >= cellCount) continue;
                if (visited[nx][nz]) continue;

                visited[nx][nz] = true;
                carveCellFloor(nx, nz);
                carveConnection(cx, cz, d);
                stack.push(new int[]{nx, nz});
                carved = true;
                break;
            }
            if (!carved) stack.pop();
        }

        pickEntranceAndExit(rnd);
    }

    // Offset +1: la columna/fila 0 y la última quedan siempre reservadas como muro
    // perimetral (nunca se tallan), así el recinto queda cerrado por los 4 lados
    // salvo en los huecos explícitos de entrada y salida.
    private void carveCellFloor(int cx, int cz) {
        int bx = cx * 3 + 1, bz = cz * 3 + 1;
        open[bx][bz] = true;
        open[bx + 1][bz] = true;
        open[bx][bz + 1] = true;
        open[bx + 1][bz + 1] = true;
    }

    private void carveConnection(int cx, int cz, int dir) {
        int bx = cx * 3 + 1, bz = cz * 3 + 1;
        switch (dir) {
            case 0 -> { open[bx + 2][bz] = true; open[bx + 2][bz + 1] = true; } // este
            case 1 -> { open[bx - 1][bz] = true; open[bx - 1][bz + 1] = true; } // oeste
            case 2 -> { open[bx][bz + 2] = true; open[bx + 1][bz + 2] = true; } // sur
            case 3 -> { open[bx][bz - 1] = true; open[bx + 1][bz - 1] = true; } // norte
        }
    }

    private boolean cellsConnected(int cx, int cz, int dir) {
        int bx = cx * 3 + 1, bz = cz * 3 + 1;
        return switch (dir) {
            case 0 -> open[bx + 2][bz];
            case 1 -> open[bx - 1][bz];
            case 2 -> open[bx][bz + 2];
            case 3 -> open[bx][bz - 1];
            default -> false;
        };
    }

    private int[][] bfsDistances(int sx, int sz) {
        int[][] dist = new int[cellCount][cellCount];
        for (int[] row : dist) Arrays.fill(row, -1);
        Deque<int[]> q = new ArrayDeque<>();
        dist[sx][sz] = 0;
        q.add(new int[]{sx, sz});
        while (!q.isEmpty()) {
            int[] c = q.poll();
            int cx = c[0], cz = c[1];
            for (int d = 0; d < 4; d++) {
                int nx = cx + DX[d], nz = cz + DZ[d];
                if (nx < 0 || nz < 0 || nx >= cellCount || nz >= cellCount) continue;
                if (dist[nx][nz] != -1) continue;
                if (!cellsConnected(cx, cz, d)) continue;
                dist[nx][nz] = dist[cx][cz] + 1;
                q.add(new int[]{nx, nz});
            }
        }
        return dist;
    }

    /** La entrada siempre es la celda (0,0) con apertura hacia el norte del recinto. */
    private void pickEntranceAndExit(Random rnd) {
        entranceCellX = 0;
        entranceCellZ = 0;
        entranceDir = 3; // norte

        int[][] dist = bfsDistances(0, 0);
        int bestX = 0, bestZ = 0, bestDist = -1, bestDir = 2;

        for (int i = 0; i < cellCount; i++) {
            for (int j = 0; j < cellCount; j++) {
                boolean edge = (i == 0 || j == 0 || i == cellCount - 1 || j == cellCount - 1);
                if (!edge) continue;
                if (i == entranceCellX && j == entranceCellZ) continue;
                if (dist[i][j] > bestDist) {
                    bestDist = dist[i][j];
                    bestX = i;
                    bestZ = j;
                    if (i == 0) bestDir = 1;
                    else if (i == cellCount - 1) bestDir = 0;
                    else if (j == 0) bestDir = 3;
                    else bestDir = 2;
                }
            }
        }
        exitCellX = bestX;
        exitCellZ = bestZ;
        exitDir = bestDir;
    }

    public boolean isEntranceFloorBlock(int x, int z) {
        return isCellFloorBlock(x, z, entranceCellX, entranceCellZ);
    }

    public boolean isExitFloorBlock(int x, int z) {
        return isCellFloorBlock(x, z, exitCellX, exitCellZ);
    }

    private boolean isCellFloorBlock(int x, int z, int cx, int cz) {
        int bx = cx * 3 + 1, bz = cz * 3 + 1;
        return (x == bx || x == bx + 1) && (z == bz || z == bz + 1);
    }
}
