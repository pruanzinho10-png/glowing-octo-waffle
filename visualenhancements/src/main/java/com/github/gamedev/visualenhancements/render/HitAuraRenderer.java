package com.github.gamedev.visualenhancements.render;

import com.github.gamedev.visualenhancements.AuraColor;
import com.github.gamedev.visualenhancements.HitAuraManager;
import com.github.gamedev.visualenhancements.ModConfig;
import com.github.gamedev.visualenhancements.Timing;
import com.github.gamedev.visualenhancements.VisualModule;
import net.fabricmc.fabric.api.client.rendering.v1.world.WorldRenderContext;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

import java.util.List;

/**
 * Aura energética que envolve o corpo da entidade atingida.
 *
 * Três etapas (u = progresso de 0 a 1):
 *   1. surgimento gradual (fade-in);
 *   2. energia percorrendo o corpo dos pés à cabeça, com anéis, faíscas em
 *      espiral e um contorno luminoso que realça a silhueta;
 *   3. desaparecimento gradual.
 *
 * Tudo é calculado em função da altura e da largura da entidade e acompanha a
 * posição interpolada dela a cada frame. Cada aura usa cerca de 1.400 vértices.
 */
public final class HitAuraRenderer {
    private static final double MAX_DISTANCE_SQ = 64.0 * 64.0;

    private static final int SHELL_SEGMENTS = 18;
    private static final int SHELL_BANDS = 5;
    private static final int RING_SEGMENTS = 22;
    private static final int RING_COUNT = 3;
    private static final int SPARK_COUNT = 16;

    private static final float[] SHELL_COS = new float[SHELL_SEGMENTS + 1];
    private static final float[] SHELL_SIN = new float[SHELL_SEGMENTS + 1];
    private static final float[] RING_COS = new float[RING_SEGMENTS + 1];
    private static final float[] RING_SIN = new float[RING_SEGMENTS + 1];
    private static final float[] HEX_COS = new float[7];
    private static final float[] HEX_SIN = new float[7];
    private static final float[] SEED_A = new float[SPARK_COUNT];
    private static final float[] SEED_B = new float[SPARK_COUNT];

    /** Espaço de trabalho reutilizado (evita criar objetos a cada frame). */
    private static final float[] COLUMN_RIM = new float[SHELL_SEGMENTS + 1];

    private static final PipelineBatch BATCH = new PipelineBatch(Pipelines.HIT_AURA, "hit aura");

    static {
        for (int i = 0; i <= SHELL_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / SHELL_SEGMENTS;
            SHELL_COS[i] = (float) Math.cos(angle);
            SHELL_SIN[i] = (float) Math.sin(angle);
        }
        for (int i = 0; i <= RING_SEGMENTS; i++) {
            double angle = Math.PI * 2.0 * i / RING_SEGMENTS;
            RING_COS[i] = (float) Math.cos(angle);
            RING_SIN[i] = (float) Math.sin(angle);
        }
        for (int i = 0; i < 7; i++) {
            double angle = Math.PI * 2.0 * i / 6.0;
            HEX_COS[i] = (float) Math.cos(angle);
            HEX_SIN[i] = (float) Math.sin(angle);
        }
        // Sementes fixas: cada faísca tem sempre o mesmo "caráter" (sem Random por frame)
        for (int i = 0; i < SPARK_COUNT; i++) {
            SEED_A[i] = fract((float) Math.sin(i * 12.9898 + 1.7) * 43758.547f);
            SEED_B[i] = fract((float) Math.sin(i * 78.233 + 4.1) * 24634.635f);
        }
    }

    private HitAuraRenderer() {
    }

    public static void render(WorldRenderContext context) {
        if (!VisualModule.HIT_AURA.isEnabled()) {
            HitAuraManager.clear();
            return;
        }

        long now = Timing.nowMs();
        HitAuraManager.removeExpired(now);
        List<HitAuraManager.Aura> auras = HitAuraManager.active();
        if (auras.isEmpty()) {
            return;
        }

        Minecraft client = Minecraft.getInstance();
        float partialTick = client.getDeltaTracker().getGameTimeDeltaPartialTick(false);

        var camera = context.worldState().cameraRenderState;
        Vec3 camPos = camera.pos;
        Matrix4fc pose = context.matrices().last().pose();
        Vector3f right = new Vector3f(1.0f, 0.0f, 0.0f).rotate(camera.orientation);
        Vector3f up = new Vector3f(0.0f, 1.0f, 0.0f).rotate(camera.orientation);

        AuraColor color = ModConfig.auraColor();
        float time = (now % 600_000L) / 1000.0f;

        for (HitAuraManager.Aura aura : auras) {
            LivingEntity target = aura.target();
            if (target.isRemoved()) {
                continue;
            }
            Vec3 feet = target.getPosition(partialTick);
            double dx = feet.x - camPos.x;
            double dy = feet.y - camPos.y;
            double dz = feet.z - camPos.z;
            if (dx * dx + dy * dy + dz * dz > MAX_DISTANCE_SQ) {
                continue;
            }

            float u = Math.min((now - aura.startMs()) / (float) HitAuraManager.DURATION_MS, 1.0f);
            drawAura(pose, right, up, color, target, (float) dx, (float) dy, (float) dz, u, time);
        }

        BATCH.flush(client);
    }

    private static void drawAura(Matrix4fc pose, Vector3f right, Vector3f up, AuraColor color,
                                 LivingEntity target, float fx, float fy, float fz, float u, float time) {
        // Envelope geral: 1) surge, 2) permanece, 3) some
        float envelope = smooth(0.0f, 0.16f, u) * (1.0f - smooth(0.66f, 1.0f, u));
        if (envelope < 0.004f) {
            return;
        }

        float height = Math.max(0.4f, target.getBbHeight());
        float baseRadius = Math.min(target.getBbWidth() * 0.5f, 3.5f) + 0.14f;

        // Frente de energia subindo dos pés até a cabeça (altura normalizada, 0 = pés)
        float sweep = easeInOut(clamp01((u - 0.04f) / 0.70f));
        float sweepN = -0.10f + 1.20f * sweep;

        // Direção da entidade até a câmera (a câmera está na origem), para o brilho de borda
        float vx = -fx;
        float vz = -fz;
        float vLen = (float) Math.sqrt(vx * vx + vz * vz);
        if (vLen > 1.0e-4f) {
            vx /= vLen;
            vz /= vLen;
        }

        addShell(pose, color, fx, fy, fz, baseRadius, height, sweepN, envelope, time, vx, vz);
        addGroundPulse(pose, color, fx, fy, fz, baseRadius, u);
        addRings(pose, color, fx, fy, fz, baseRadius, height, u, envelope);
        addSparks(pose, right, up, color, fx, fy, fz, baseRadius, height, u, envelope, time);
    }

    /** Contorno luminoso (casca) em volta do corpo, mais forte na borda da silhueta e perto da frente de energia. */
    private static void addShell(Matrix4fc pose, AuraColor color, float fx, float fy, float fz,
                                 float baseRadius, float height, float sweepN, float envelope,
                                 float time, float vx, float vz) {
        // Brilho de borda: colunas de frente para a câmera ficam mais fracas, as da silhueta mais fortes
        for (int i = 0; i <= SHELL_SEGMENTS; i++) {
            float facing = Math.abs(SHELL_COS[i] * vx + SHELL_SIN[i] * vz);
            COLUMN_RIM[i] = 0.30f + 0.70f * (float) Math.pow(1.0f - facing, 1.3f);
        }

        for (int band = 0; band < SHELL_BANDS; band++) {
            float y0n = band / (float) SHELL_BANDS;
            float y1n = (band + 1) / (float) SHELL_BANDS;
            float r0 = radiusAt(baseRadius, y0n, time);
            float r1 = radiusAt(baseRadius, y1n, time);
            float glow0 = frontGlow(y0n, sweepN);
            float glow1 = frontGlow(y1n, sweepN);

            for (int i = 0; i < SHELL_SEGMENTS; i++) {
                float f0 = flicker(i, y0n, time);
                float f1 = flicker(i + 1, y0n, time);
                float f2 = flicker(i + 1, y1n, time);
                float f3 = flicker(i, y1n, time);

                vertex(pose, color, fx + SHELL_COS[i] * r0, fy + y0n * height, fz + SHELL_SIN[i] * r0,
                        glow0, envelope * (0.05f + 0.42f * glow0) * COLUMN_RIM[i] * f0);
                vertex(pose, color, fx + SHELL_COS[i + 1] * r0, fy + y0n * height, fz + SHELL_SIN[i + 1] * r0,
                        glow0, envelope * (0.05f + 0.42f * glow0) * COLUMN_RIM[i + 1] * f1);
                vertex(pose, color, fx + SHELL_COS[i + 1] * r1, fy + y1n * height, fz + SHELL_SIN[i + 1] * r1,
                        glow1, envelope * (0.05f + 0.42f * glow1) * COLUMN_RIM[i + 1] * f2);
                vertex(pose, color, fx + SHELL_COS[i] * r1, fy + y1n * height, fz + SHELL_SIN[i] * r1,
                        glow1, envelope * (0.05f + 0.42f * glow1) * COLUMN_RIM[i] * f3);
            }
        }
    }

    /** Onda de energia no chão, na primeira parte da animação. */
    private static void addGroundPulse(Matrix4fc pose, AuraColor color, float fx, float fy, float fz,
                                       float baseRadius, float u) {
        float t = clamp01(u / 0.30f);
        if (t >= 1.0f) {
            return;
        }
        float alpha = smooth(0.0f, 0.05f, u) * (1.0f - t) * 0.65f;
        float radius = lerp(0.15f, baseRadius + 0.85f, easeOut(t));
        addFlatRing(pose, color, fx, fy + 0.03f, fz, radius, 0.11f, alpha, 0.55f);
    }

    /** Anéis de energia subindo pelo corpo, defasados entre si. */
    private static void addRings(Matrix4fc pose, AuraColor color, float fx, float fy, float fz,
                                 float baseRadius, float height, float u, float envelope) {
        for (int k = 0; k < RING_COUNT; k++) {
            float rt = clamp01((u - 0.05f - 0.10f * k) / 0.60f);
            if (rt <= 0.0f || rt >= 1.0f) {
                continue;
            }
            float yn = easeOut(rt) * 1.02f;
            float alpha = envelope * (float) Math.sin(Math.PI * rt) * 0.55f;
            float radius = radiusAt(baseRadius, yn, 0.0f) + 0.07f;
            addFlatRing(pose, color, fx, fy + yn * height, fz, radius, 0.075f, alpha, 0.85f);
        }
    }

    /** Anel horizontal com bordas suaves (transparente nas pontas, forte no meio). */
    private static void addFlatRing(Matrix4fc pose, AuraColor color, float cx, float y, float cz,
                                    float radius, float halfWidth, float alpha, float coreMix) {
        float inner = Math.max(0.01f, radius - halfWidth);
        float outer = radius + halfWidth;
        for (int i = 0; i < RING_SEGMENTS; i++) {
            float c0 = RING_COS[i];
            float s0 = RING_SIN[i];
            float c1 = RING_COS[i + 1];
            float s1 = RING_SIN[i + 1];

            // inner -> mid
            vertex(pose, color, cx + c0 * inner, y, cz + s0 * inner, coreMix, 0.0f);
            vertex(pose, color, cx + c0 * radius, y, cz + s0 * radius, coreMix, alpha);
            vertex(pose, color, cx + c1 * radius, y, cz + s1 * radius, coreMix, alpha);
            vertex(pose, color, cx + c1 * inner, y, cz + s1 * inner, coreMix, 0.0f);
            // mid -> outer
            vertex(pose, color, cx + c0 * radius, y, cz + s0 * radius, coreMix, alpha);
            vertex(pose, color, cx + c0 * outer, y, cz + s0 * outer, coreMix, 0.0f);
            vertex(pose, color, cx + c1 * outer, y, cz + s1 * outer, coreMix, 0.0f);
            vertex(pose, color, cx + c1 * radius, y, cz + s1 * radius, coreMix, alpha);
        }
    }

    /** Faíscas luminosas girando em espiral e subindo ao redor do corpo. */
    private static void addSparks(Matrix4fc pose, Vector3f right, Vector3f up, AuraColor color,
                                  float fx, float fy, float fz, float baseRadius, float height,
                                  float u, float envelope, float time) {
        float sizeScale = clamp(height / 1.8f, 0.65f, 1.7f);

        for (int i = 0; i < SPARK_COUNT; i++) {
            float seedA = SEED_A[i];
            float seedB = SEED_B[i];

            float yn = fract(seedA + u * (0.85f + 0.60f * seedB));
            float life = (float) Math.sin(Math.PI * yn);
            float twinkle = 0.65f + 0.35f * (float) Math.sin(time * 9.0f + i * 5.3f);
            float alpha = envelope * 0.95f * life * twinkle;
            if (alpha < 0.01f) {
                continue;
            }

            float direction = (i % 2 == 0) ? 1.0f : -1.0f;
            float angle = (float) (Math.PI * 2.0) * (seedB + direction * time * (0.35f + 0.25f * seedA));
            float radius = radiusAt(baseRadius, yn, time) * (0.96f + 0.20f * seedB) + 0.05f;

            float px = fx + (float) Math.cos(angle) * radius;
            float py = fy + yn * height;
            float pz = fz + (float) Math.sin(angle) * radius;
            float size = (0.05f + 0.05f * seedA) * sizeScale;

            addGlow(pose, right, up, color, px, py, pz, size * 1.8f, alpha, 0.45f);   // halo suave
            addGlow(pose, right, up, color, px, py, pz, size * 0.6f, alpha * 0.9f, 1.0f); // núcleo claro
        }
    }

    /** Brilho radial voltado para a câmera: hexágono com centro forte e borda transparente. */
    private static void addGlow(Matrix4fc pose, Vector3f right, Vector3f up, AuraColor color,
                                float cx, float cy, float cz, float radius, float alpha, float coreMix) {
        for (int k = 0; k < 3; k++) {
            int i0 = k * 2;
            vertex(pose, color, cx, cy, cz, coreMix, alpha);
            billboardPoint(pose, color, right, up, cx, cy, cz, HEX_COS[i0], HEX_SIN[i0], radius, coreMix);
            billboardPoint(pose, color, right, up, cx, cy, cz, HEX_COS[i0 + 1], HEX_SIN[i0 + 1], radius, coreMix);
            billboardPoint(pose, color, right, up, cx, cy, cz, HEX_COS[i0 + 2], HEX_SIN[i0 + 2], radius, coreMix);
        }
    }

    private static void billboardPoint(Matrix4fc pose, AuraColor color, Vector3f right, Vector3f up,
                                       float cx, float cy, float cz, float cos, float sin,
                                       float radius, float coreMix) {
        float x = cx + (right.x * cos + up.x * sin) * radius;
        float y = cy + (right.y * cos + up.y * sin) * radius;
        float z = cz + (right.z * cos + up.z * sin) * radius;
        vertex(pose, color, x, y, z, coreMix, 0.0f);
    }

    // ---- Utilidades ----

    /** Raio do corpo em função da altura: levemente mais fino nas pontas, com leve respiração. */
    private static float radiusAt(float baseRadius, float yn, float time) {
        float shape = 0.90f + 0.10f * (float) Math.sin(Math.PI * clamp01(yn));
        float breathe = 1.0f + 0.025f * (float) Math.sin(time * 2.4f + yn * 6.0f);
        return baseRadius * shape * breathe;
    }

    /** Intensidade (0 a 1) do brilho em torno da frente de energia. */
    private static float frontGlow(float yn, float sweepN) {
        float d = (yn - sweepN) / 0.24f;
        return (float) Math.exp(-d * d);
    }

    /** Oscilação suave por coluna/altura, para o brilho parecer orgânico. */
    private static float flicker(int column, float yn, float time) {
        return 0.75f + 0.25f * (float) Math.sin(time * 3.2f + column * 1.7f + yn * 5.0f);
    }

    private static void vertex(Matrix4fc pose, AuraColor c, float x, float y, float z, float coreMix, float alpha) {
        float r = lerp(c.glowR, c.coreR, coreMix);
        float g = lerp(c.glowG, c.coreG, coreMix);
        float b = lerp(c.glowB, c.coreB, coreMix);
        BATCH.vertex(pose, x, y, z, r, g, b, clamp(alpha, 0.0f, 0.85f));
    }

    private static float lerp(float a, float b, float t) {
        return a + (b - a) * t;
    }

    private static float clamp(float v, float min, float max) {
        return Math.max(min, Math.min(max, v));
    }

    private static float clamp01(float v) {
        return clamp(v, 0.0f, 1.0f);
    }

    private static float fract(float v) {
        return v - (float) Math.floor(v);
    }

    private static float smooth(float edge0, float edge1, float x) {
        float t = clamp01((x - edge0) / (edge1 - edge0));
        return t * t * (3.0f - 2.0f * t);
    }

    private static float easeOut(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t);
    }

    private static float easeInOut(float t) {
        return 0.5f - 0.5f * (float) Math.cos(Math.PI * t);
    }

    public static void close() {
        BATCH.close();
    }
}
