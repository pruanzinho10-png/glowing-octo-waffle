package com.github.gamedev.visualenhancements;

/** Módulos que podem ser ligados/desligados no menu. */
public enum VisualModule {
    RAIN_STREAKS("rain_streaks", "Chuva na tela", "Gotas de chuva caindo sobre a tela"),
    WET_GROUND("wet_ground", "Chão molhado", "Reflexo frio nos blocos expostos à chuva"),
    HIT_AURA("hit_aura", "Aura de impacto", "Energia que envolve o alvo atingido");

    private final String id;
    private final String title;
    private final String description;

    VisualModule(String id, String title, String description) {
        this.id = id;
        this.title = title;
        this.description = description;
    }

    public String id() {
        return id;
    }

    public String title() {
        return title;
    }

    public String description() {
        return description;
    }

    public boolean isEnabled() {
        return ModConfig.isEnabled(this);
    }

    public void setEnabled(boolean enabled) {
        ModConfig.setEnabled(this, enabled);
    }
}
