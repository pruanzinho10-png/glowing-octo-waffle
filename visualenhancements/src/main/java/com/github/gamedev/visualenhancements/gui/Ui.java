package com.github.gamedev.visualenhancements.gui;

import com.github.gamedev.visualenhancements.VisualEnhancementsClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.Identifier;

import java.util.HashMap;
import java.util.Map;

/**
 * Ferramentas de desenho do menu. Tudo aqui trabalha em PIXELS REAIS da janela
 * (a tela aplica uma escala 1/guiScale antes de desenhar), por isso os cantos
 * arredondados e os textos ficam nítidos em qualquer resolução.
 */
public final class Ui {
    /** Tamanho declarado nos arquivos font/inter*.json. */
    public static final float FONT_SIZE = 11.0f;
    /** A linha de base das fontes TTF do Minecraft fica a 7 unidades do topo da linha. */
    public static final float BASELINE = 7.0f;

    private static final Style REGULAR = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath(VisualEnhancementsClient.MOD_ID, "inter")));
    private static final Style SEMIBOLD = Style.EMPTY.withFont(
            new FontDescription.Resource(Identifier.fromNamespaceAndPath(VisualEnhancementsClient.MOD_ID, "inter_semibold")));

    private static final Map<String, Component> REGULAR_CACHE = new HashMap<>();
    private static final Map<String, Component> SEMIBOLD_CACHE = new HashMap<>();

    private Ui() {
    }

    // ---------------- Texto ----------------

    public static Component regular(String text) {
        return REGULAR_CACHE.computeIfAbsent(text, t -> Component.literal(t).withStyle(REGULAR));
    }

    public static Component semibold(String text) {
        return SEMIBOLD_CACHE.computeIfAbsent(text, t -> Component.literal(t).withStyle(SEMIBOLD));
    }

    /** Largura do texto, em pixels, para o tamanho informado. */
    public static float width(Component text, float sizePx) {
        return Minecraft.getInstance().font.width(text) * sizePx / FONT_SIZE;
    }

    /** Desenha o texto com a linha de base em baselineY e altura de fonte sizePx. */
    public static void text(GuiGraphics g, Component text, float x, float baselineY, float sizePx, int color) {
        float k = sizePx / FONT_SIZE;
        g.pose().pushMatrix();
        g.pose().translate(x, baselineY - BASELINE * k);
        g.pose().scale(k, k);
        g.drawString(Minecraft.getInstance().font, text, 0, 0, color, false);
        g.pose().popMatrix();
    }

    /** Linha de base que centraliza visualmente um texto numa faixa centrada em centerY. */
    public static float baseline(float centerY, float sizePx) {
        return centerY + 0.35f * sizePx;
    }

    // ---------------- Formas ----------------

    /**
     * Retângulo arredondado com degradê vertical e bordas suavizadas (antialiasing).
     * As cores já devem vir com o alfa desejado.
     */
    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int radius, int top, int bottom) {
        if (w <= 0 || h <= 0) {
            return;
        }
        int r = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (r == 0) {
            fillV(g, x, y, x + w, y + h, top, bottom);
            return;
        }

        // Miolo (entre os arcos): uma única chamada
        int midTop = y + r;
        int midBottom = y + h - r;
        if (midBottom > midTop) {
            fillV(g, x, midTop, x + w, midBottom,
                    lerp(top, bottom, (float) r / h), lerp(top, bottom, (float) (h - r) / h));
        }

        // Cantos: linha a linha, com cobertura parcial no pixel da borda
        for (int i = 0; i < r; i++) {
            float dy = r - i - 0.5f;
            float inset = r - (float) Math.sqrt(r * r - dy * dy);
            int full = (int) Math.ceil(inset);
            float cover = full - inset;

            edgeRow(g, x, w, y + i, full, cover, lerp(top, bottom, (i + 0.5f) / h));
            edgeRow(g, x, w, y + h - 1 - i, full, cover, lerp(top, bottom, (h - i - 0.5f) / h));
        }
    }

    private static void edgeRow(GuiGraphics g, int x, int w, int rowY, int full, float cover, int color) {
        if (x + full < x + w - full) {
            g.fill(x + full, rowY, x + w - full, rowY + 1, color);
        }
        if (cover > 0.03f && full > 0) {
            int partial = a(color, cover);
            g.fill(x + full - 1, rowY, x + full, rowY + 1, partial);
            g.fill(x + w - full, rowY, x + w - full + 1, rowY + 1, partial);
        }
    }

    private static void fillV(GuiGraphics g, int x1, int y1, int x2, int y2, int top, int bottom) {
        if (top == bottom) {
            g.fill(x1, y1, x2, y2, top);
        } else {
            g.fillGradient(x1, y1, x2, y2, top, bottom);
        }
    }

    /** Cartão com borda fina de 1 px. */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int radius,
                            int top, int bottom, int border, float alpha) {
        roundRect(g, x, y, w, h, radius, a(border, alpha), a(border, alpha));
        roundRect(g, x + 1, y + 1, w - 2, h - 2, Math.max(0, radius - 1), a(top, alpha), a(bottom, alpha));
    }

    /** Sombra suave atrás de um painel (três camadas translúcidas). */
    public static void shadow(GuiGraphics g, int x, int y, int w, int h, int radius, float unit, float alpha) {
        int offset = Math.round(8 * unit);
        int[] spread = {Math.round(5 * unit), Math.round(11 * unit), Math.round(19 * unit)};
        float[] strength = {0.10f, 0.06f, 0.035f};
        for (int i = 0; i < spread.length; i++) {
            int c = a(0xFF000000, strength[i] * alpha);
            roundRect(g, x - spread[i], y - spread[i] + offset, w + spread[i] * 2, h + spread[i] * 2,
                    radius + spread[i], c, c);
        }
    }

    // ---------------- Cor e animação ----------------

    /** Multiplica o alfa de uma cor ARGB por um fator de 0 a 1. */
    public static int a(int argb, float factor) {
        int alpha = Math.round((argb >>> 24) * clamp01(factor));
        return (alpha << 24) | (argb & 0xFFFFFF);
    }

    public static int lerp(int from, int to, float t) {
        t = clamp01(t);
        int al = Math.round((from >>> 24) + ((to >>> 24) - (from >>> 24)) * t);
        int r = Math.round(((from >> 16) & 0xFF) + (((to >> 16) & 0xFF) - ((from >> 16) & 0xFF)) * t);
        int gr = Math.round(((from >> 8) & 0xFF) + (((to >> 8) & 0xFF) - ((from >> 8) & 0xFF)) * t);
        int b = Math.round((from & 0xFF) + ((to & 0xFF) - (from & 0xFF)) * t);
        return (al << 24) | (r << 16) | (gr << 8) | b;
    }

    public static float clamp01(float v) {
        return Math.max(0.0f, Math.min(1.0f, v));
    }

    /** Fração do caminho percorrida num quadro de duração dt (suavização exponencial independente do FPS). */
    public static float approach(float dt, float speed) {
        return 1.0f - (float) Math.exp(-dt * speed);
    }

    public static float smooth(float t) {
        t = clamp01(t);
        return t * t * (3.0f - 2.0f * t);
    }
}
