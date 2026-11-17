package com.github.gamedev.visualenhancements.render;

import com.github.gamedev.visualenhancements.Timing;
import com.github.gamedev.visualenhancements.VisualModule;
import com.github.gamedev.visualenhancements.WeatherState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;

import java.util.Random;

/**
 * Gotículas de chuva na tela.
 *
 * O TAMANHO E A QUANTIDADE continuam os mesmos de antes (comprimento 8 a 22,
 * largura 0,4 a 0,9, 110 gotas). Mudou só a qualidade da renderização:
 *   - mais opacidade e uma curva de brilho com corpo (antes o topo sumia);
 *   - um contorno azul-escuro fino ao lado, que dá contraste contra céu e grama claros;
 *   - um pequeno reflexo branco azulado na ponta, que faz parecer água iluminada.
 */
public final class RainStreaksRenderer {
    private static final int STREAK_COUNT = 110;
    /** Inclinação da queda (radianos): o vento empurra a chuva para a direita. */
    private static final float ANGLE = 0.12f;

    // Tons frios
    private static final int BODY_RGB = 0xCCE6FF;      // azul-gelo claro
    private static final int EDGE_RGB = 0x3F5E82;      // contorno azul-aço
    private static final int HIGHLIGHT_RGB = 0xF2F8FF; // reflexo branco azulado

    private static final Random RANDOM = new Random();
    private static final Streak[] STREAKS = new Streak[STREAK_COUNT];
    private static boolean initialized = false;
    private static long lastFrameMs = 0L;

    private static final class Streak {
        float x;
        float y;
        float length;
        float width;
        float speed;
        float alpha;
    }

    private RainStreaksRenderer() {
    }

    public static void render(GuiGraphics graphics) {
        long now = Timing.nowMs();
        float dt = lastFrameMs == 0L ? 0.0f : Math.min((now - lastFrameMs) / 1000.0f, 0.1f);
        lastFrameMs = now;

        if (!VisualModule.RAIN_STREAKS.isEnabled()) {
            return;
        }
        float intensity = WeatherState.screenIntensity();
        if (intensity < 0.02f) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        int screenW = client.getWindow().getGuiScaledWidth();
        int screenH = client.getWindow().getGuiScaledHeight();

        if (!initialized) {
            for (int i = 0; i < STREAK_COUNT; i++) {
                STREAKS[i] = new Streak();
                respawn(STREAKS[i], screenW, screenH, true);
            }
            initialized = true;
        }

        float dirX = (float) Math.sin(ANGLE);
        float dirY = (float) Math.cos(ANGLE);

        for (Streak s : STREAKS) {
            s.x += dirX * s.speed * dt;
            s.y += dirY * s.speed * dt;
            if (s.y - s.length > screenH || s.x > screenW + 40) {
                respawn(s, screenW, screenH, false);
            }

            float a = s.alpha * intensity;
            if (a < 0.03f) {
                continue;
            }

            int len = Math.max(2, Math.round(s.length));
            int head = Math.max(1, Math.round(len * 0.35f));

            graphics.pose().pushMatrix();
            graphics.pose().translate(s.x, s.y);
            graphics.pose().rotate(-ANGLE);

            // 1) Contorno fino ao lado: contraste sem aumentar a gota
            graphics.pose().pushMatrix();
            graphics.pose().translate(0.55f, 0.0f);
            graphics.pose().scale(s.width, 1.0f);
            graphics.fillGradient(0, 0, 1, len, argb(0.0f, EDGE_RGB), argb(a * 0.42f, EDGE_RGB));
            graphics.pose().popMatrix();

            // 2) Corpo: sobe rápido até um brilho firme e fica forte até a ponta
            graphics.pose().pushMatrix();
            graphics.pose().scale(s.width, 1.0f);
            graphics.fillGradient(0, 0, 1, head, argb(0.0f, BODY_RGB), argb(a * 0.70f, BODY_RGB));
            graphics.fillGradient(0, head, 1, len, argb(a * 0.70f, BODY_RGB), argb(a, BODY_RGB));
            graphics.pose().popMatrix();

            // 3) Reflexo na ponta (iluminação na gota)
            graphics.pose().pushMatrix();
            graphics.pose().scale(s.width, 1.0f);
            graphics.fill(0, Math.max(0, len - 3), 1, len, argb(Math.min(1.0f, a * 1.05f), HIGHLIGHT_RGB));
            graphics.pose().popMatrix();

            graphics.pose().popMatrix();
        }
    }

    private static int argb(float alpha, int rgb) {
        int a = Math.max(0, Math.min(255, Math.round(alpha * 255.0f)));
        return (a << 24) | rgb;
    }

    private static void respawn(Streak s, int screenW, int screenH, boolean randomY) {
        // Mesmas faixas de tamanho de antes (o tamanho NÃO foi alterado)
        s.length = 8.0f + RANDOM.nextFloat() * 14.0f;
        s.width = 0.4f + RANDOM.nextFloat() * 0.5f;
        s.speed = screenH * (0.9f + RANDOM.nextFloat() * 0.9f);
        // Mais opaca que antes (era 0,25 a 0,60)
        s.alpha = 0.45f + RANDOM.nextFloat() * 0.35f;
        s.x = -0.1f * screenW + RANDOM.nextFloat() * screenW * 1.2f;
        s.y = randomY ? RANDOM.nextFloat() * screenH : -s.length;
    }
}
