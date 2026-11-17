package com.github.gamedev.visualenhancements;

import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

import java.util.ArrayList;
import java.util.List;

/**
 * Estado da chuva no cliente, atualizado a cada tick. As intensidades são
 * suavizadas para os efeitos aparecerem e sumirem aos poucos.
 */
public final class WeatherState {
    /** Raio (em blocos) ao redor do jogador onde o chão recebe o brilho. */
    private static final int WET_RADIUS = 12;
    private static final int REBUILD_INTERVAL_TICKS = 10;
    /** Fração aproximada do caminho percorrida por tick até o valor alvo. */
    private static final float SMOOTHING = 0.06f;

    private static final List<BlockPos> WET_TILES = new ArrayList<>();
    private static float groundIntensity = 0.0f;
    private static float screenIntensity = 0.0f;
    private static int rebuildCooldown = 0;

    private WeatherState() {
    }

    public static void tick(Minecraft client) {
        ClientLevel level = client.level;
        LocalPlayer player = client.player;
        if (level == null || player == null) {
            reset();
            return;
        }

        boolean raining = level.isRaining();
        BlockPos playerPos = player.blockPosition();
        boolean playerInRain = raining && level.isRainingAt(playerPos);

        groundIntensity += ((raining ? 1.0f : 0.0f) - groundIntensity) * SMOOTHING;
        screenIntensity += ((playerInRain ? 1.0f : 0.0f) - screenIntensity) * SMOOTHING;

        if (groundIntensity < 0.01f) {
            WET_TILES.clear();
            rebuildCooldown = 0;
            return;
        }

        // Enquanto chove, recalcula os blocos; ao parar, a lista antiga some junto com o fade
        if (raining && --rebuildCooldown <= 0) {
            rebuildWetTiles(level, playerPos);
            rebuildCooldown = REBUILD_INTERVAL_TICKS;
        }
    }

    private static void rebuildWetTiles(ClientLevel level, BlockPos center) {
        WET_TILES.clear();
        BlockPos.MutableBlockPos column = new BlockPos.MutableBlockPos();
        int radiusSq = WET_RADIUS * WET_RADIUS;

        for (int dx = -WET_RADIUS; dx <= WET_RADIUS; dx++) {
            for (int dz = -WET_RADIUS; dz <= WET_RADIUS; dz++) {
                if (dx * dx + dz * dz > radiusSq) {
                    continue;
                }
                column.set(center.getX() + dx, center.getY(), center.getZ() + dz);

                // Primeira posição livre acima do bloco mais alto da coluna
                BlockPos top = level.getHeightmapPos(Heightmap.Types.MOTION_BLOCKING, column);
                BlockPos floor = top.below();
                BlockState floorState = level.getBlockState(floor);

                // Só blocos com a face de cima completa (ignora água, folhas, lajes...)
                if (!floorState.isFaceSturdy(level, floor, Direction.UP)) {
                    continue;
                }
                // Respeita bioma (deserto/neve não molham) e exposição ao céu
                if (!level.isRainingAt(top)) {
                    continue;
                }
                WET_TILES.add(top);
            }
        }
    }

    private static void reset() {
        groundIntensity = 0.0f;
        screenIntensity = 0.0f;
        rebuildCooldown = 0;
        WET_TILES.clear();
    }

    public static float groundIntensity() {
        return groundIntensity;
    }

    public static float screenIntensity() {
        return screenIntensity;
    }

    public static List<BlockPos> wetTiles() {
        return WET_TILES;
    }
}
