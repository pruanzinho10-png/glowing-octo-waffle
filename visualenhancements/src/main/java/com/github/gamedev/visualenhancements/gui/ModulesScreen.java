package com.github.gamedev.visualenhancements.gui;

import com.github.gamedev.visualenhancements.AuraColor;
import com.github.gamedev.visualenhancements.ModConfig;
import com.github.gamedev.visualenhancements.Timing;
import com.github.gamedev.visualenhancements.VisualModule;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Menu dos módulos visuais. Linguagem visual inspirada nas configurações do
 * Flick Launcher: fundo quase preto, um cartão com as linhas de configuração,
 * ícones em "chips", divisórias discretas, interruptores com bolinha clara e
 * controle segmentado em pílula. O amarelo aparece só nos estados ativos.
 *
 * Todo o desenho é feito em pixels reais da janela, com escala proporcional à
 * resolução, então o painel fica bem dimensionado de 480p a 4K.
 */
public class ModulesScreen extends Screen {
    private static final long OPEN_MS = 220L;
    private static final long CLOSE_MS = 150L;

    private static final VisualModule[] MODULES = VisualModule.values();
    private static final AuraColor[] COLORS = AuraColor.values();
    private static final int AURA_INDEX = VisualModule.HIT_AURA.ordinal();
    /** Altura (no tamanho de design) da área de opções que expande sob "Aura de impacto". */
    private static final int OPTIONS_H = 68;
    private static final String[] CATEGORY_LABELS = {"Todos", "Ambiente", "Combate"};

    // ---- Paleta ----
    private static final int SCRIM_TOP = 0xA8050507;
    private static final int SCRIM_BOTTOM = 0xCC000000;
    private static final int PANEL_TOP = 0xFF151517;
    private static final int PANEL_BOTTOM = 0xFF101012;
    private static final int PANEL_BORDER = 0xFF26262A;
    private static final int CARD_TOP = 0xFF1E1E21;
    private static final int CARD_BOTTOM = 0xFF19191C;
    private static final int CARD_BORDER = 0xFF2B2B30;
    private static final int CHIP_TOP = 0xFF2A2A2F;
    private static final int CHIP_BOTTOM = 0xFF242428;
    private static final int CHIP_BORDER = 0xFF35353B;
    private static final int TEXT = 0xFFF3F3F5;
    private static final int TEXT_DIM = 0xFF8E8E97;
    private static final int TEXT_LABEL = 0xFF7B7B84;
    private static final int ICON_OFF = 0xFF85858D;
    private static final int ACCENT = 0xFFE6BE4A;
    private static final int TRACK_OFF_TOP = 0xFF2F2F34;
    private static final int TRACK_OFF_BOTTOM = 0xFF29292E;
    private static final int TRACK_OFF_BORDER = 0xFF3B3B41;
    private static final int TRACK_ON_TOP = 0xFFD4AC3A;
    private static final int TRACK_ON_BOTTOM = 0xFFBE982C;
    private static final int KNOB_TOP = 0xFFFAFAFB;
    private static final int KNOB_BOTTOM = 0xFFE8E8EB;
    private static final int SEG_BG = 0xFF121214;
    private static final int SEG_BORDER = 0xFF2A2A2E;
    private static final int SEL_TOP = 0xFFF6F6F8;
    private static final int SEL_BOTTOM = 0xFFE4E4E8;
    private static final int SEL_TEXT = 0xFF131315;
    private static final int KEY_TOP = 0xFF1C1C1F;
    private static final int KEY_BOTTOM = 0xFF18181B;
    private static final int KEY_BORDER = 0xFF2E2E33;

    private record Rect(int x, int y, int w, int h) {
        boolean contains(double px, double py) {
            return px >= x && px < x + w && py >= y && py < y + h;
        }

        int right() {
            return x + w;
        }

        int bottom() {
            return y + h;
        }
    }

    private static final Rect EMPTY = new Rect(0, 0, 0, 0);

    // ---- Estado de animação ----
    private final float[] toggleAnim = new float[MODULES.length];
    private final float[] hoverAnim = new float[MODULES.length];
    private final float[] pressAnim = new float[MODULES.length];
    private final float[] moduleReveal = new float[MODULES.length];
    private final float[] categoryHover = new float[CATEGORY_LABELS.length];
    private int selectedCategory;
    private final float[] chipSel = new float[COLORS.length];
    private final float[] chipHover = new float[COLORS.length];
    private float auraExpand;
    private float closeHover;
    private float selX;
    private float selW;
    private boolean selInit;

    private final long openedAt = Timing.nowMs();
    private long lastFrame = openedAt;
    private boolean closing;
    private long closeStart;

    // ---- Layout (recalculado a cada frame) ----
    private float us = 1.0f;
    private double guiScale = 1.0;
    private int physW;
    private int physH;
    private int panelX;
    private int panelY;
    private int panelW;
    private int panelH;
    private float panelCenterY;
    private int pad;
    private int headerY;
    private int cardX;
    private int cardY;
    private int cardW;
    private int cardHeight;
    private int rowH;
    private int cardPad;
    private int optionsTop;
    private Rect closeRect = EMPTY;
    private final Rect[] categoryRect = new Rect[CATEGORY_LABELS.length];
    private final Rect[] rowRect = new Rect[MODULES.length];
    private final Rect[] toggleRect = new Rect[MODULES.length];
    private final Rect[] chipRect = new Rect[COLORS.length];
    private Rect segRect = EMPTY;

    public ModulesScreen() {
        super(Component.literal("Visual Enhancements"));
        for (int i = 0; i < MODULES.length; i++) {
            toggleAnim[i] = MODULES[i].isEnabled() ? 1.0f : 0.0f;
            rowRect[i] = EMPTY;
            toggleRect[i] = EMPTY;
            moduleReveal[i] = 1.0f;
        }
        for (int i = 0; i < COLORS.length; i++) {
            chipRect[i] = EMPTY;
        }
        for (int i = 0; i < categoryRect.length; i++) {
            categoryRect[i] = EMPTY;
        }
        auraExpand = MODULES[AURA_INDEX].isEnabled() ? 1.0f : 0.0f;
    }

    /** O jogo continua rodando por trás, então dá para ver os efeitos ao vivo. */
    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /** Esc ou botão de fechar: inicia a animação de saída; a tela fecha ao terminar. */
    @Override
    public void onClose() {
        if (!closing) {
            closing = true;
            closeStart = Timing.nowMs();
        }
    }

    // =====================================================================
    // Desenho
    // =====================================================================

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        Minecraft mc = Minecraft.getInstance();
        guiScale = mc.getWindow().getGuiScale();
        physW = mc.getWindow().getWidth();
        physH = mc.getWindow().getHeight();

        long now = Timing.nowMs();
        float dt = Math.min((now - lastFrame) / 1000.0f, 0.1f);
        lastFrame = now;

        // Visibilidade (abertura suave / fechamento rápido)
        float vis;
        if (closing) {
            float t = Ui.clamp01((now - closeStart) / (float) CLOSE_MS);
            if (t >= 1.0f) {
                this.minecraft.setScreen(null);
                return;
            }
            vis = 1.0f - t * t;
        } else {
            float t = Ui.clamp01((now - openedAt) / (float) OPEN_MS);
            vis = 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
        }

        // Expansão das opções da aura acompanha o estado do módulo
        float auraTarget = MODULES[AURA_INDEX].isEnabled() ? 1.0f : 0.0f;
        auraExpand += (auraTarget - auraExpand) * Ui.approach(dt, 14.0f);
        if (Math.abs(auraTarget - auraExpand) < 0.002f) {
            auraExpand = auraTarget;
        }

        computeLayout();

        double mx = mouseX * guiScale;
        double my = mouseY * guiScale;
        boolean mouseDown = mc.mouseHandler.isLeftPressed();
        updateAnimations(dt, mx, my, mouseDown);

        g.pose().pushMatrix();
        g.pose().scale((float) (1.0 / guiScale), (float) (1.0 / guiScale));

        // Fundo: escurece o jogo sem filtro colorido (vinheta vertical discreta)
        g.fillGradient(0, 0, physW, physH, Ui.a(SCRIM_TOP, vis), Ui.a(SCRIM_BOTTOM, vis));

        if (vis >= 0.03f) {
            float cx = panelX + panelW / 2.0f;
            g.pose().pushMatrix();
            float scale = 0.965f + 0.035f * vis;
            g.pose().translate(cx, panelCenterY + (1.0f - vis) * S(10));
            g.pose().scale(scale, scale);
            g.pose().translate(-cx, -panelCenterY);
            drawPanel(g, vis);
            g.pose().popMatrix();
        }

        g.pose().popMatrix();
    }

    private void drawPanel(GuiGraphics g, float al) {
        Ui.shadow(g, panelX, panelY, panelW, panelH, S(22), us, al);
        Ui.card(g, panelX, panelY, panelW, panelH, S(22), PANEL_TOP, PANEL_BOTTOM, PANEL_BORDER, al);

        drawHeader(g, al);

        // Rótulo da seção (como "Launcher" / "Appearance" no Flick)
        float labelSize = F(12.5f);
        Ui.text(g, Ui.semibold("Módulos"), cardX + S(4), headerY + S(44) + S(26) + S(12), labelSize,
                Ui.a(TEXT_LABEL, al));
        drawCategories(g, al);

        Ui.shadow(g, cardX, cardY, cardW, cardHeight, S(18), us * 0.5f, al * 0.6f);
        Ui.card(g, cardX, cardY, cardW, cardHeight, S(18), CARD_TOP, CARD_BOTTOM, CARD_BORDER, al);

        for (int i = 0; i < MODULES.length; i++) {
            if (matchesCategory(i) && moduleReveal[i] > 0.01f) {
                drawRow(g, i, al * moduleReveal[i]);
            }
        }
        if (showAuraOptions() && auraExpand > 0.01f) {
            drawAuraOptions(g, al * auraExpand * auraExpand);
        }

        drawFooter(g, al);
    }

    private void drawCategories(GuiGraphics g, float al) {
        for (int i = 0; i < categoryRect.length; i++) {
            Rect r = categoryRect[i];
            float selected = i == selectedCategory ? 1.0f : 0.0f;
            float hover = categoryHover[i];
            int top = Ui.lerp(0xFF1A1A1E, 0xFF393326, selected);
            int bottom = Ui.lerp(0xFF17171A, 0xFF2A261E, selected);
            int border = Ui.lerp(0xFF29292E, 0xFF76643A, selected);
            top = Ui.lerp(top, 0xFF34343A, hover * (1.0f - selected));
            bottom = Ui.lerp(bottom, 0xFF28282D, hover * (1.0f - selected));
            Ui.card(g, r.x(), r.y(), r.w(), r.h(), r.h() / 2, top, bottom, border, al);
            if (selected > 0.5f) {
                int dot = S(5);
                g.fill(r.x() + S(10), r.y() + (r.h() - dot) / 2,
                        r.x() + S(10) + dot, r.y() + (r.h() + dot) / 2, Ui.a(ACCENT, al));
            }
            String label = CATEGORY_LABELS[i] + "  " + categoryCount(i);
            Component text = selected > 0.5f ? Ui.semibold(label) : Ui.regular(label);
            float size = F(12.5f);
            float width = Ui.width(text, size);
            float x = selected > 0.5f ? r.x() + S(20) : r.x() + (r.w() - width) / 2.0f;
            Ui.text(g, text, x, Ui.baseline(r.y() + r.h() / 2.0f, size), size,
                    Ui.a(Ui.lerp(TEXT_DIM, TEXT, Math.max(selected, hover * 0.65f)), al));
        }
    }

    private int categoryCount(int category) {
        return switch (category) {
            case 1 -> 2;
            case 2 -> 1;
            default -> MODULES.length;
        };
    }

    private boolean matchesCategory(int moduleIndex) {
        return switch (selectedCategory) {
            case 1 -> moduleIndex == VisualModule.RAIN_STREAKS.ordinal()
                    || moduleIndex == VisualModule.WET_GROUND.ordinal();
            case 2 -> moduleIndex == AURA_INDEX;
            default -> true;
        };
    }

    private boolean showAuraOptions() {
        return selectedCategory == 0 || selectedCategory == 2;
    }

    private void drawHeader(GuiGraphics g, float al) {
        int chip = S(44);
        int chipX = panelX + pad;
        Ui.card(g, chipX, headerY, chip, chip, S(13), CHIP_TOP, CHIP_BOTTOM, CHIP_BORDER, al);
        int icon = S(22);
        Icons.draw(g, Icons.Icon.SPARKLE, chipX + (chip - icon) / 2, headerY + (chip - icon) / 2, icon,
                Ui.a(ACCENT, al));

        float textX = chipX + chip + S(15);
        Ui.text(g, Ui.semibold("Visual Enhancements"), textX, headerY + S(19), F(20.5f), Ui.a(TEXT, al));
        Ui.text(g, Ui.regular("Módulos visuais do cliente."), textX, headerY + S(37), F(13f), Ui.a(TEXT_DIM, al));

        // Botão de fechar (círculo com X)
        Rect c = closeRect;
        int bg = Ui.lerp(CHIP_BOTTOM, 0xFF34343A, closeHover);
        Ui.card(g, c.x(), c.y(), c.w(), c.h(), c.h() / 2, bg, bg, CHIP_BORDER, al);
        int xs = S(12);
        Icons.draw(g, Icons.Icon.CLOSE, c.x() + (c.w() - xs) / 2, c.y() + (c.h() - xs) / 2, xs,
                Ui.a(Ui.lerp(ICON_OFF, TEXT, closeHover), al));
    }

    private void drawRow(GuiGraphics g, int i, float al) {
        VisualModule module = MODULES[i];
        Rect r = rowRect[i];

        // Cartão da linha com trilho de estado, realce e resposta ao pressionar.
        float highlight = 0.025f + 0.035f * toggleAnim[i] + 0.055f * hoverAnim[i] + 0.035f * pressAnim[i];
        int rowFill = Ui.a(0xFFFFFFFF, highlight * al);
        Ui.roundRect(g, r.x() + S(6), r.y() + S(3), r.w() - S(12), r.h() - S(6), S(13), rowFill, rowFill);
        if (toggleAnim[i] > 0.01f) {
            float glow = toggleAnim[i] * (0.14f + 0.10f * (float) Math.sin(Timing.nowMs() / 420.0 + i));
            int railW = Math.max(1, S(2));
            int rail = Ui.a(ACCENT, glow * al);
            Ui.roundRect(g, r.x() + S(7), r.y() + S(15), railW, r.h() - S(30), railW, rail, rail);
        }

        // Chip com o ícone (cinza quando desligado, amarelo quando ligado)
        int chip = S(38);
        int chipX = r.x() + S(18);
        int chipY = r.y() + (r.h() - chip) / 2;
        Ui.card(g, chipX, chipY, chip, chip, S(11), CHIP_TOP, CHIP_BOTTOM, CHIP_BORDER, al);
        int icon = S(19);
        Icons.draw(g, iconFor(module), chipX + (chip - icon) / 2, chipY + (chip - icon) / 2, icon,
                Ui.a(Ui.lerp(ICON_OFF, ACCENT, toggleAnim[i]), al));

        float textX = chipX + chip + S(14);
        Ui.text(g, Ui.semibold(module.title()), textX, r.y() + S(31), F(15.5f), Ui.a(TEXT, al));
        Ui.text(g, Ui.regular(module.description()), textX, r.y() + S(49), F(12.5f), Ui.a(TEXT_DIM, al));

        if (toggleAnim[i] > 0.5f) {
            int dot = S(4);
            int dotX = Math.round(textX);
            int dotY = r.y() + S(59);
            g.fill(dotX, dotY, dotX + dot, dotY + dot, Ui.a(ACCENT, al));
            Ui.text(g, Ui.semibold("ATIVO"), dotX + dot + S(5), r.y() + S(63), F(9.5f), Ui.a(ACCENT, al));
        }

        drawToggle(g, toggleRect[i], toggleAnim[i], hoverAnim[i], pressAnim[i], al);

        // Divisória quase invisível entre linhas
        if (i < MODULES.length - 1) {
            g.fill(r.x() + S(18), r.bottom(), r.right() - S(18), r.bottom() + 1, Ui.a(0xFFFFFFFF, 0.05f * al));
        }
    }

    private void drawToggle(GuiGraphics g, Rect r, float on, float hover, float press, float al) {
        int top = Ui.lerp(TRACK_OFF_TOP, TRACK_ON_TOP, on);
        int bottom = Ui.lerp(TRACK_OFF_BOTTOM, TRACK_ON_BOTTOM, on);
        int border = Ui.lerp(TRACK_OFF_BORDER, TRACK_ON_BOTTOM, on);
        top = Ui.lerp(top, 0xFFFFFFFF, 0.07f * hover);
        bottom = Ui.lerp(bottom, 0xFFFFFFFF, 0.07f * hover);
        Ui.card(g, r.x(), r.y(), r.w(), r.h(), r.h() / 2, top, bottom, border, al);

        int inset = S(3);
        int d = r.h() - inset * 2;
        int squish = Math.round(S(4) * press);   // a bolinha "estica" ao clicar
        int kw = d + squish;
        int kx = r.x() + inset + Math.round((r.w() - inset * 2 - kw) * on);

        int shadow = Ui.a(0x55000000, al);
        Ui.roundRect(g, kx, r.y() + inset + 1, kw, d, d / 2, shadow, shadow);
        Ui.roundRect(g, kx, r.y() + inset, kw, d, d / 2, Ui.a(KNOB_TOP, al), Ui.a(KNOB_BOTTOM, al));
    }

    /** Opções da aura (cor), em controle segmentado com seleção deslizante. */
    private void drawAuraOptions(GuiGraphics g, float al) {
        float textX = rowRect[AURA_INDEX].x() + S(18) + S(38) + S(14);

        Ui.text(g, Ui.regular("Cor da aura"), textX, optionsTop + S(16), F(12.5f), Ui.a(TEXT_LABEL, al));

        Rect s = segRect;
        Ui.card(g, s.x(), s.y(), s.w(), s.h(), s.h() / 2, SEG_BG, SEG_BG, SEG_BORDER, al);

        // Pílula de seleção que desliza entre as opções
        int selH = s.h() - S(6);
        int pillTop = Ui.a(SEL_TOP, al);
        int pillBottom = Ui.a(SEL_BOTTOM, al);
        Ui.roundRect(g, Math.round(selX), s.y() + S(3), Math.round(selW), selH, selH / 2, pillTop, pillBottom);

        float size = F(12.5f);
        for (int i = 0; i < COLORS.length; i++) {
            Rect c = chipRect[i];
            if (chipHover[i] > 0.01f) {
                int hv = Ui.a(0xFFFFFFFF, 0.07f * chipHover[i] * (1.0f - chipSel[i]) * al);
                Ui.roundRect(g, c.x(), c.y(), c.w(), c.h(), c.h() / 2, hv, hv);
            }

            int dot = S(9);
            int dotX = c.x() + S(13);
            int dotY = c.y() + (c.h() - dot) / 2;
            int dotColor = Ui.a(COLORS[i].uiColor, al);
            Ui.roundRect(g, dotX, dotY, dot, dot, dot / 2, dotColor, dotColor);

            Component label = Ui.semibold(COLORS[i].label);
            int textColor = Ui.lerp(TEXT_DIM, SEL_TEXT, chipSel[i]);
            Ui.text(g, label, dotX + dot + S(7), Ui.baseline(c.y() + c.h() / 2.0f, size), size, Ui.a(textColor, al));
        }
    }

    private void drawFooter(GuiGraphics g, float al) {
        float keySize = F(11f);
        float labelSize = F(12.5f);
        Component esc = Ui.semibold("ESC");
        Component label = Ui.regular("para fechar");
        float escW = Ui.width(esc, keySize);
        float labelW = Ui.width(label, labelSize);

        int chipW = Math.round(escW) + S(16);
        int chipH = S(22);
        float total = chipW + S(8) + labelW;
        int startX = Math.round(panelX + (panelW - total) / 2.0f);
        int y = cardY + cardHeight + S(16);

        Ui.card(g, startX, y, chipW, chipH, S(7), KEY_TOP, KEY_BOTTOM, KEY_BORDER, al);
        Ui.text(g, esc, startX + (chipW - escW) / 2.0f, Ui.baseline(y + chipH / 2.0f, keySize), keySize,
                Ui.a(TEXT_DIM, al));
        Ui.text(g, label, startX + chipW + S(8), Ui.baseline(y + chipH / 2.0f, labelSize), labelSize,
                Ui.a(TEXT_LABEL, al));
    }

    private static Icons.Icon iconFor(VisualModule module) {
        switch (module) {
            case RAIN_STREAKS:
                return Icons.Icon.DROPLETS;
            case WET_GROUND:
                return Icons.Icon.WAVES;
            default:
                return Icons.Icon.SPARKLE;
        }
    }

    // =====================================================================
    // Animações e layout
    // =====================================================================

    private void updateAnimations(float dt, double mx, double my, boolean mouseDown) {
        for (int i = 0; i < MODULES.length; i++) {
            boolean shown = matchesCategory(i);
            float target = shown ? 1.0f : 0.0f;
            moduleReveal[i] += (target - moduleReveal[i]) * Ui.approach(dt, 12.0f);
            if (Math.abs(target - moduleReveal[i]) < 0.002f) moduleReveal[i] = target;
            boolean hovered = shown && rowRect[i].contains(mx, my);
            toggleAnim[i] += ((MODULES[i].isEnabled() ? 1.0f : 0.0f) - toggleAnim[i]) * Ui.approach(dt, 15.0f);
            hoverAnim[i] += ((hovered ? 1.0f : 0.0f) - hoverAnim[i]) * Ui.approach(dt, 18.0f);
            pressAnim[i] += (((hovered && mouseDown) ? 1.0f : 0.0f) - pressAnim[i]) * Ui.approach(dt, 30.0f);
        }

        closeHover += ((closeRect.contains(mx, my) ? 1.0f : 0.0f) - closeHover) * Ui.approach(dt, 18.0f);
        for (int i = 0; i < categoryRect.length; i++) {
            categoryHover[i] += ((categoryRect[i].contains(mx, my) ? 1.0f : 0.0f) - categoryHover[i]) * Ui.approach(dt, 18.0f);
        }

        int selected = ModConfig.auraColor().ordinal();
        boolean optionsActive = auraExpand > 0.7f;
        for (int i = 0; i < COLORS.length; i++) {
            chipSel[i] += ((i == selected ? 1.0f : 0.0f) - chipSel[i]) * Ui.approach(dt, 16.0f);
            boolean hovered = optionsActive && chipRect[i].contains(mx, my);
            chipHover[i] += ((hovered ? 1.0f : 0.0f) - chipHover[i]) * Ui.approach(dt, 18.0f);
        }

        // Pílula de seleção desliza até a opção escolhida
        Rect target = chipRect[selected];
        if (!selInit) {
            selX = target.x();
            selW = target.w();
            selInit = true;
        } else {
            selX += (target.x() - selX) * Ui.approach(dt, 18.0f);
            selW += (target.w() - selW) * Ui.approach(dt, 18.0f);
        }
    }

    /** Escala da interface: proporcional à janela, limitada para não ficar minúscula nem gigante. */
    private void computeLayout() {
        us = Math.max(0.75f, Math.min(2.0f, Math.min(physW / 900.0f, physH / 640.0f)));

        pad = S(26);
        panelW = Math.min(S(472), physW - S(24));
        int headerH = S(44);
        cardPad = S(6);
        rowH = S(70);

        int visibleCount = categoryCount(selectedCategory);
        boolean auraInView = selectedCategory == 0 || selectedCategory == 2;
        int optionsH = auraInView ? Math.round(S(OPTIONS_H) * auraExpand) : 0;
        cardHeight = cardPad * 2 + visibleCount * rowH + optionsH;
        int cardHeightMax = cardPad * 2 + visibleCount * rowH + (auraInView ? S(OPTIONS_H) : 0);

        int fixed = pad + headerH + S(26) + S(22) + S(12) + S(30) + S(16) + S(22) + pad;
        panelH = fixed + cardHeight;
        int panelHMax = fixed + cardHeightMax;   // centraliza pela altura máxima: o painel não "pula" ao expandir

        panelX = (physW - panelW) / 2;
        panelY = (physH - panelHMax) / 2;
        panelCenterY = panelY + panelHMax / 2.0f;

        headerY = panelY + pad;
        cardX = panelX + pad;
        cardW = panelW - pad * 2;
        int categoryY = headerY + headerH + S(26) + S(22) + S(12);
        int categoryH = S(30);
        int categoryGap = S(7);
        int tabsW = Math.min(cardW, S(378));
        int tabW = (tabsW - categoryGap * 2) / 3;
        int tabsX = cardX + (cardW - tabsW) / 2;
        for (int i = 0; i < categoryRect.length; i++) {
            categoryRect[i] = new Rect(tabsX + i * (tabW + categoryGap), categoryY, tabW, categoryH);
        }
        cardY = categoryY + categoryH + S(16);

        int closeSize = S(30);
        closeRect = new Rect(panelX + panelW - pad - closeSize, headerY + (headerH - closeSize) / 2, closeSize, closeSize);

        int slot = 0;
        for (int i = 0; i < MODULES.length; i++) {
            if (!matchesCategory(i)) {
                rowRect[i] = EMPTY;
                toggleRect[i] = EMPTY;
                continue;
            }
            int ry = cardY + cardPad + slot++ * rowH;
            rowRect[i] = new Rect(cardX, ry, cardW, rowH);
            int tw = S(42);
            int th = S(24);
            toggleRect[i] = new Rect(cardX + cardW - S(18) - tw, ry + (rowH - th) / 2, tw, th);
        }

        // Controle segmentado (cor da aura)
        optionsTop = cardY + cardPad + visibleCount * rowH;
        int textX = cardX + S(18) + S(38) + S(14);
        int segPad = S(3);
        int segH = S(34);
        int segY = optionsTop + S(26);
        float size = F(12.5f);

        int[] widths = new int[COLORS.length];
        int total = 0;
        for (int i = 0; i < COLORS.length; i++) {
            widths[i] = S(13) + S(9) + S(7) + Math.round(Ui.width(Ui.semibold(COLORS[i].label), size)) + S(15);
            total += widths[i];
        }
        segRect = new Rect(textX, segY, total + segPad * 2, segH);
        int cx = textX + segPad;
        for (int i = 0; i < COLORS.length; i++) {
            chipRect[i] = new Rect(cx, segY + segPad, widths[i], segH - segPad * 2);
            cx += widths[i];
        }
    }

    private int S(float designPx) {
        return Math.round(designPx * us);
    }

    private float F(float designPx) {
        return designPx * us;
    }

    // =====================================================================
    // Interação
    // =====================================================================

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (closing) {
            return true;
        }
        if (event.button() == 0) {
            double mx = event.x() * guiScale;
            double my = event.y() * guiScale;

            if (closeRect.contains(mx, my)) {
                onClose();
                return true;
            }

            for (int i = 0; i < categoryRect.length; i++) {
                if (categoryRect[i].contains(mx, my)) {
                    selectedCategory = i;
                    return true;
                }
            }

            if (auraExpand > 0.7f && showAuraOptions()) {
                for (int i = 0; i < COLORS.length; i++) {
                    if (chipRect[i].contains(mx, my)) {
                        ModConfig.setAuraColor(COLORS[i]);
                        return true;
                    }
                }
            }

            for (int i = 0; i < MODULES.length; i++) {
                if (moduleReveal[i] > 0.85f && rowRect[i].contains(mx, my)) {
                    MODULES[i].setEnabled(!MODULES[i].isEnabled());
                    return true;
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
