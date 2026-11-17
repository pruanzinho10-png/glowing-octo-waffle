package com.github.gamedev.visualenhancements;

/**
 * Paletas da aura de impacto. Cada cor tem um tom "glow" (corpo do brilho) e um
 * tom "core" (centro mais claro), para o efeito ter profundidade sem saturar.
 */
public enum AuraColor {
    YELLOW("yellow", "Amarelo", 0xFFE8BC3A, 1.00f, 0.78f, 0.20f, 1.00f, 0.93f, 0.64f),
    PURPLE("purple", "Roxo", 0xFF8B5CF6, 0.55f, 0.30f, 0.95f, 0.80f, 0.66f, 1.00f),
    PINK("pink", "Rosa", 0xFFF0589F, 1.00f, 0.33f, 0.66f, 1.00f, 0.72f, 0.87f);

    public final String id;
    public final String label;
    /** Cor usada no menu (ARGB). */
    public final int uiColor;
    public final float glowR;
    public final float glowG;
    public final float glowB;
    public final float coreR;
    public final float coreG;
    public final float coreB;

    AuraColor(String id, String label, int uiColor,
              float glowR, float glowG, float glowB,
              float coreR, float coreG, float coreB) {
        this.id = id;
        this.label = label;
        this.uiColor = uiColor;
        this.glowR = glowR;
        this.glowG = glowG;
        this.glowB = glowB;
        this.coreR = coreR;
        this.coreG = coreG;
        this.coreB = coreB;
    }

    public static AuraColor byId(String id) {
        for (AuraColor color : values()) {
            if (color.id.equals(id)) {
                return color;
            }
        }
        return YELLOW;
    }
}
