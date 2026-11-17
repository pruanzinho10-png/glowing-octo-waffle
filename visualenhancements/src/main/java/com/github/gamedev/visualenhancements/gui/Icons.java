package com.github.gamedev.visualenhancements.gui;

import net.minecraft.client.gui.GuiGraphics;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Ícones minimalistas desenhados por código (sem texturas). Cada ícone é uma
 * forma geométrica avaliada com supersampling 4x4 e guardada em cache por
 * tamanho, então o desenho por frame é só uma lista de pequenos retângulos.
 */
public final class Icons {
    public enum Icon {
        /** Estrela de quatro pontas: aura / energia. */
        SPARKLE,
        /** Duas gotas: chuva. */
        DROPLETS,
        /** Três ondas: água no chão. */
        WAVES,
        /** X de fechar. */
        CLOSE
    }

    private static final int SUPERSAMPLE = 4;
    /** Cache: ícone + tamanho -> lista de trechos [linha, x0, x1, nível de alfa 1..8]. */
    private static final Map<Integer, int[]> CACHE = new HashMap<>();

    private Icons() {
    }

    public static void draw(GuiGraphics g, Icon icon, int x, int y, int size, int argb) {
        if (size < 4) {
            return;
        }
        int[] runs = CACHE.computeIfAbsent(icon.ordinal() * 4096 + size, k -> rasterize(icon, size));
        for (int i = 0; i < runs.length; i += 4) {
            g.fill(x + runs[i + 1], y + runs[i], x + runs[i + 2], y + runs[i] + 1, Ui.a(argb, runs[i + 3] / 8.0f));
        }
    }

    private static int[] rasterize(Icon icon, int size) {
        List<Integer> out = new ArrayList<>();
        float[] row = new float[size];

        for (int py = 0; py < size; py++) {
            for (int px = 0; px < size; px++) {
                int hits = 0;
                for (int sy = 0; sy < SUPERSAMPLE; sy++) {
                    for (int sx = 0; sx < SUPERSAMPLE; sx++) {
                        float u = (px + (sx + 0.5f) / SUPERSAMPLE) / size;
                        float v = (py + (sy + 0.5f) / SUPERSAMPLE) / size;
                        if (inside(icon, u, v)) {
                            hits++;
                        }
                    }
                }
                row[px] = hits / (float) (SUPERSAMPLE * SUPERSAMPLE);
            }

            // Junta pixels vizinhos com o mesmo nível de alfa em um único trecho
            int px = 0;
            while (px < size) {
                int level = Math.round(row[px] * 8.0f);
                if (level <= 0) {
                    px++;
                    continue;
                }
                int end = px + 1;
                while (end < size && Math.round(row[end] * 8.0f) == level) {
                    end++;
                }
                out.add(py);
                out.add(px);
                out.add(end);
                out.add(level);
                px = end;
            }
        }

        int[] result = new int[out.size()];
        for (int i = 0; i < result.length; i++) {
            result[i] = out.get(i);
        }
        return result;
    }

    /** u e v vão de 0 a 1 dentro do ícone. */
    private static boolean inside(Icon icon, float u, float v) {
        switch (icon) {
            case SPARKLE:
                return star(u, v, 0.42f, 0.58f, 0.40f) || star(u, v, 0.80f, 0.22f, 0.18f);
            case DROPLETS:
                return drop(u, v, 0.36f, 0.52f, 0.36f) || drop(u, v, 0.77f, 0.67f, 0.22f);
            case WAVES:
                return wave(u, v, 0.30f) || wave(u, v, 0.52f) || wave(u, v, 0.74f);
            case CLOSE:
                return cross(u, v);
            default:
                return false;
        }
    }

    /** Astroide: estrela fina de quatro pontas. */
    private static boolean star(float u, float v, float cx, float cy, float radius) {
        float dx = Math.abs(u - cx);
        float dy = Math.abs(v - cy);
        return Math.sqrt(dx) + Math.sqrt(dy) <= Math.sqrt(radius);
    }

    /** Gota: círculo embaixo e cone até a ponta em cima. */
    private static boolean drop(float u, float v, float cx, float cy, float scale) {
        float x = (u - cx) / scale;
        float y = (v - cy) / scale;
        if (y < -1.0f || y > 1.0f) {
            return false;
        }
        boolean circle = x * x + (y - 0.32f) * (y - 0.32f) <= 0.66f * 0.66f;
        boolean cone = y <= 0.32f && Math.abs(x) <= 0.66f * (y + 1.0f) / 1.32f;
        return circle || cone;
    }

    private static boolean wave(float u, float v, float baseY) {
        if (u < 0.08f || u > 0.92f) {
            return false;
        }
        float y = baseY + 0.05f * (float) Math.sin((u - 0.05f) * Math.PI * 2.0 * 1.5);
        return Math.abs(v - y) <= 0.055f;
    }

    private static boolean cross(float u, float v) {
        if (u < 0.22f || u > 0.78f || v < 0.22f || v > 0.78f) {
            return false;
        }
        return Math.abs(u - v) <= 0.085f || Math.abs(u + v - 1.0f) <= 0.085f;
    }
}
