package com.github.gamedev.visualenhancements.render;

import com.github.gamedev.visualenhancements.Timing;
import com.github.gamedev.visualenhancements.VisualModule;
import com.github.gamedev.visualenhancements.WeatherState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;

import java.util.List;

/**
 * Reflexo de "chão molhado": uma película translúcida e fria logo acima do topo
 * dos blocos expostos à chuva perto do jogador.
 *
 * CORREÇÃO DA COR AMARELADA: a tonalidade vinha daqui. A película usava um
 * dourado fixo (1.0, 0.85, 0.10) misturado por cima do cenário, o que tingia a
 * grama e a pedra de amarelo. Agora usa um branco azulado, que lembra o reflexo
 * do céu nublado na água.
 */
public final class WetGroundRenderer {
    // Branco azulado: reflexo do céu na superfície molhada
    private static final float R = 0.66f;
    private static final float G = 0.78f;
    private static final float B = 0.96f;
    private static final float MAX_ALPHA = 0.15f;
    /** Distância acima do bloco para evitar z-fighting. */
    private static final float Y_OFFSET = 0.01f;
    /** O efeito some suavemente entre estas distâncias (em blocos), sem borda dura. */
    private static final float FADE_START = 7.0f;
    private static final float FADE_END = 12.0f;

    private static final PipelineBatch BATCH = new PipelineBatch(Pipelines.WET_GROUND, "wet ground");

    private WetGroundRenderer() {
    }

    public static void render(WorldRenderContext context) {
        if (!VisualModule.WET_GROUND.isEnabled()) {
            return;
        }
        float intensity = WeatherState.groundIntensity();
        List<BlockPos> tiles = WeatherState.wetTiles();
        if (intensity < 0.01f || tiles.isEmpty()) {
            return;
        }

        PoseStack matrices = context.matrices();
        Vec3 cam = context.worldState().cameraRenderState.pos;
        Matrix4fc pose = matrices.last().pose();
        float time = (Timing.nowMs() % 600_000L) / 1000.0f;

        for (BlockPos pos : tiles) {
            // Coordenadas relativas à câmera, calculadas em double para não perder precisão
            double rx = pos.getX() + 0.5 - cam.x;
            double rz = pos.getZ() + 0.5 - cam.z;
            float dist = (float) Math.sqrt(rx * rx + rz * rz);
            float fade = 1.0f - smooth(FADE_START, FADE_END, dist);
            if (fade <= 0.01f) {
                continue;
            }

            float x0 = (float) (pos.getX() - cam.x);
            float y = (float) (pos.getY() + Y_OFFSET - cam.y);
            float z0 = (float) (pos.getZ() - cam.z);

            // Duas ondas lentas e defasadas: manchas de brilho que se movem, como poças
            float glint = 0.55f
                    + 0.28f * (float) Math.sin(time * 1.1f + pos.getX() * 0.7f + pos.getZ() * 0.9f)
                    + 0.17f * (float) Math.sin(time * 0.7f - pos.getX() * 1.3f + pos.getZ() * 0.4f);
            float a = MAX_ALPHA * intensity * fade * Math.max(0.15f, glint);

            BATCH.vertex(pose, x0, y, z0, R, G, B, a);
            BATCH.vertex(pose, x0, y, z0 + 1.0f, R, G, B, a);
            BATCH.vertex(pose, x0 + 1.0f, y, z0 + 1.0f, R, G, B, a);
            BATCH.vertex(pose, x0 + 1.0f, y, z0, R, G, B, a);
        }

        BATCH.flush(Minecraft.getInstance());
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = Math.max(0.0f, Math.min(1.0f, (x - edge0) / (edge1 - edge0)));
        return t * t * (3.0f - 2.0f * t);
    }

    public static void close() {
        BATCH.close();
    }
}
