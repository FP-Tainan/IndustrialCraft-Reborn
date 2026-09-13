package net.ic2reborn.menu.layout;

import net.minecraft.resources.Identifier;

/**
 * Estilos de gauge copiados do IC2 Experimental (ic2.core.gui.Gauge e EnergyGauge).
 *
 * u/v/width/height descrevem a parte "cheia" do gauge; bg* descrevem o fundo
 * desenhado atrás dele (relativo à posição do gauge). As coordenadas são de
 * common.png, exceto quando outra textura é indicada.
 */
public enum GaugeStyle {
    // ── energia (<energygauge style="bolt|bar">) ─────────────────────────
    BOLT(116, 65, 7, 13, Orientation.UP, -4, -1, 16, 16, 96, 64, null),
    BAR(132, 43, 24, 9, Orientation.RIGHT, -4, -11, 32, 32, 128, 0, null),

    // ── combustível / fluido ─────────────────────────────────────────────
    FUEL(112, 80, 13, 13, Orientation.UP, 0, 0, 16, 16, 96, 80, null),
    BUCKET(110, 111, 14, 16, Orientation.UP, 0, 0, 14, 16, 96, 111, null),
    PROGRESS_WIND(242, 91, 13, 13, Orientation.UP, 0, 0, 13, 13, 242, 63, null),

    // ── progresso ────────────────────────────────────────────────────────
    PROGRESS_ARROW(165, 16, 22, 15, Orientation.RIGHT, -5, 0, 32, 16, 160, 0, null),
    PROGRESS_CRUSH(165, 52, 21, 11, Orientation.RIGHT, -5, -3, 32, 16, 160, 32, null),
    PROGRESS_TRIANGLE(165, 80, 22, 15, Orientation.RIGHT, -5, 0, 32, 16, 160, 64, null),
    PROGRESS_DROP(165, 112, 22, 15, Orientation.RIGHT, -5, 0, 32, 16, 160, 96, null),
    PROGRESS_RECYCLER(133, 80, 18, 15, Orientation.RIGHT, -5, 0, 32, 16, 128, 64, null),
    PROGRESS_METAL_FORMER(200, 19, 46, 9, Orientation.RIGHT, -8, -3, 64, 16, 192, 0, null),
    PROGRESS_CENTRIFUGE(252, 33, 3, 28, Orientation.UP, -1, -1, 5, 30, 246, 32, null),
    HEAT_CENTRIFUGE(225, 54, 20, 4, Orientation.RIGHT, -1, -1, 22, 6, 224, 47, null),
    PROGRESS_ORE_WASHER(177, 118, 18, 18, Orientation.RIGHT, -1, -1, 20, 19, 102, 38, "guiorewashingplant.png"),
    PROGRESS_BLOCK_CUTTER(176, 15, 46, 17, Orientation.RIGHT, 0, 0, 46, 17, 55, 33, "guiblockcutter.png"),
    PROGRESS_CANNER(233, 0, 23, 14, Orientation.RIGHT, 0, 0, 0, 0, 0, 0, "guicanner.png"),
    HEAT_FERMENTER(177, 10, 40, 3, Orientation.RIGHT, 0, 0, 0, 0, 0, 0, "guifermenter.png"),
    PROGRESS_FERMENTER(177, 1, 40, 7, Orientation.RIGHT, 0, 0, 0, 0, 0, 0, "guifermenter.png");

    public enum Orientation {
        UP(true, true), DOWN(true, false), LEFT(false, true), RIGHT(false, false);

        public final boolean vertical;
        public final boolean reverse;

        Orientation(boolean vertical, boolean reverse) {
            this.vertical = vertical;
            this.reverse = reverse;
        }
    }

    public final int u;
    public final int v;
    public final int width;
    public final int height;
    public final Orientation orientation;
    public final int bgX;
    public final int bgY;
    public final int bgWidth;
    public final int bgHeight;
    public final int bgU;
    public final int bgV;
    private final Identifier texture;

    GaugeStyle(int u, int v, int width, int height, Orientation orientation,
               int bgX, int bgY, int bgWidth, int bgHeight, int bgU, int bgV, String texture) {
        this.u = u;
        this.v = v;
        this.width = width;
        this.height = height;
        this.orientation = orientation;
        this.bgX = bgX;
        this.bgY = bgY;
        this.bgWidth = bgWidth;
        this.bgHeight = bgHeight;
        this.bgU = bgU;
        this.bgV = bgV;
        this.texture = texture == null ? MachineLayout.COMMON_TEXTURE : MachineLayout.gui(texture);
    }

    public Identifier texture() {
        return this.texture;
    }
}
