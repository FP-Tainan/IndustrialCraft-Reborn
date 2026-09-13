package net.ic2reborn.client.screen;

import net.ic2reborn.menu.MachineMenu;
import net.ic2reborn.menu.layout.GaugeStyle;
import net.ic2reborn.menu.layout.MachineLayout;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * IC2-style machine screen.
 *
 * Draws a MachineLayout the same way IC2 Experimental did: dynamic GUIs build their
 * panel, slots and gauges from common.png (GuiDefaultBackground, SlotGrid, Gauge),
 * textured GUIs blit one full background image and draw gauges on top.
 */
public class MachineScreen extends AbstractContainerScreen<MachineMenu> {
    private static final int ATLAS_SIZE = 256;

    private final MachineLayout layout;

    public MachineScreen(MachineMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, menu.getLayout().width(), menu.getLayout().height());
        this.layout = menu.getLayout();
    }

    // ── Background ─────────────────────────────────────────────────────────
    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);

        int x = this.leftPos;
        int y = this.topPos;

        if (this.layout.isDynamic()) {
            drawPanel(graphics, x, y);
        } else {
            blit(graphics, this.layout.background(), x, y, 0, 0, this.imageWidth, this.imageHeight);
        }

        // no IC2 as imagens do guidef vêm antes dos slots, que ficam por cima delas
        for (MachineLayout.ImageDef image : this.layout.images()) {
            graphics.blit(RenderPipelines.GUI_TEXTURED, image.texture(), x + image.x(), y + image.y(),
                    image.u(), image.v(), image.width(), image.height(), image.textureWidth(), image.textureHeight());
        }

        if (this.layout.isDynamic()) {
            drawSlotBackgrounds(graphics, x, y);
        }

        for (MachineLayout.TankDef tank : this.layout.tanks()) {
            // TankGauge vazio (fluidos ainda não implementados)
            if (tank.style() == MachineLayout.TankStyle.NORMAL) {
                blit(graphics, MachineLayout.COMMON_TEXTURE, x + tank.x(), y + tank.y(), 70, 100, 20, 55);
            }
        }

        for (MachineLayout.GaugeDef gauge : this.layout.gauges()) {
            drawGauge(graphics, gauge, x, y);

            if (gauge.source() == MachineLayout.GaugeSource.ENERGY
                    && this.isHovering(gauge.x(), gauge.y(), gauge.style().width, gauge.style().height, mouseX, mouseY)) {
                int power = Math.abs(this.menu.getPower());
                int voltage = this.menu.getVoltage();
                graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(
                        Component.literal(net.craftenergy.api.EnergyUnits.format(this.menu.getEnergyCWh(), "CWh")
                                + " / " + net.craftenergy.api.EnergyUnits.format(this.menu.getCapacityCWh(), "CWh")),
                        Component.literal(net.craftenergy.api.EnergyUnits.formatPower(power)
                                + " · " + net.craftenergy.api.EnergyUnits.formatVoltage(voltage)
                                + " · " + net.craftenergy.api.EnergyUnits.formatCurrent(net.craftenergy.api.EnergyUnits.current(power, voltage)))),
                        mouseX, mouseY);
            }
        }
    }

    /** GuiDefaultBackground: moldura de 9 partes tirada de common.png. */
    private void drawPanel(GuiGraphicsExtractor graphics, int x, int y) {
        int w = this.imageWidth;
        int h = this.imageHeight;
        Identifier tex = MachineLayout.COMMON_TEXTURE;

        blit(graphics, tex, x - 16, y - 16, 0, 0, 32, 32);
        blit(graphics, tex, x + w - 16, y - 16, 64, 0, 32, 32);
        blit(graphics, tex, x - 16, y + h - 16, 0, 64, 32, 32);
        blit(graphics, tex, x + w - 16, y + h - 16, 64, 64, 32, 32);

        for (int side = 0; side < 2; side++) {
            int edgeY = h * side - 16;
            int v = 64 * side;
            for (int edgeX = 16; edgeX < w - 16; edgeX += 32) {
                blit(graphics, tex, x + edgeX, y + edgeY, 32, v, Math.min(32, w - 16 - edgeX), 32);
            }
        }

        for (int side = 0; side < 2; side++) {
            int edgeX = w * side - 16;
            int u = 64 * side;
            for (int edgeY = 16; edgeY < h - 16; edgeY += 32) {
                blit(graphics, tex, x + edgeX, y + edgeY, u, 32, 32, Math.min(32, h - 16 - edgeY));
            }
        }

        for (int innerY = 16; innerY < h - 16; innerY += 32) {
            int height = Math.min(32, h - 16 - innerY);
            for (int innerX = 16; innerX < w - 16; innerX += 32) {
                blit(graphics, tex, x + innerX, y + innerY, 32, 32, Math.min(32, w - 16 - innerX), height);
            }
        }
    }

    /** SlotGrid: fundo dos slots da máquina e do inventário do jogador. */
    private void drawSlotBackgrounds(GuiGraphicsExtractor graphics, int x, int y) {
        Identifier tex = MachineLayout.COMMON_TEXTURE;
        MachineLayout.SlotStyle normal = MachineLayout.SlotStyle.NORMAL;

        for (MachineLayout.SlotDef slot : this.layout.slots()) {
            MachineLayout.SlotStyle style = slot.style();
            if (style.hasBackground) {
                blit(graphics, tex, x + slot.x(), y + slot.y(), style.u, style.v, style.width, style.height);
            }
        }

        int invX = x + this.layout.inventoryX();
        int invY = y + this.layout.inventoryY();
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                blit(graphics, tex, invX + col * 18, invY + row * 18, normal.u, normal.v, normal.width, normal.height);
            }
        }
        for (int col = 0; col < 9; col++) {
            blit(graphics, tex, invX + col * 18, invY + 58, normal.u, normal.v, normal.width, normal.height);
        }
    }

    /** Gauge.drawBackground do IC2. */
    private void drawGauge(GuiGraphicsExtractor graphics, MachineLayout.GaugeDef gauge, int x, int y) {
        GaugeStyle style = gauge.style();
        double ratio = switch (gauge.source()) {
            case ENERGY -> this.menu.getEnergyRatio();
            case PROGRESS -> this.menu.getProgressRatio();
            case NONE -> 0.0;
        };

        int gaugeX = x + gauge.x();
        int gaugeY = y + gauge.y();

        if (style.bgWidth > 0) {
            blit(graphics, style.texture(), gaugeX + style.bgX, gaugeY + style.bgY,
                    style.bgU, style.bgV, style.bgWidth, style.bgHeight);
        }
        if (ratio <= 0.0) return;

        boolean vertical = style.orientation.vertical;
        int size = vertical ? style.height : style.width;
        int renderSize = (int) Math.round(Math.min(ratio, 1.0) * size);
        if (renderSize <= 0) return;

        int u = style.u;
        int v = style.v;
        int width = style.width;
        int height = style.height;
        if (vertical) {
            if (style.orientation.reverse) {
                v += height - renderSize;
                gaugeY += height - renderSize;
            }
            height = renderSize;
        } else {
            if (style.orientation.reverse) {
                u += width - renderSize;
                gaugeX += width - renderSize;
            }
            width = renderSize;
        }
        blit(graphics, style.texture(), gaugeX, gaugeY, u, v, width, height);
    }

    private static void blit(GuiGraphicsExtractor graphics, Identifier texture, int x, int y, int u, int v, int width, int height) {
        graphics.blit(RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, ATLAS_SIZE, ATLAS_SIZE);
    }

    // ── Labels ─────────────────────────────────────────────────────────────
    /** Transformadores: os três botões de modo do IC2 (redstone, abaixa fixo, eleva fixo). */
    private static final String[] TRANSFORMER_MODES = {"redstone", "step_down", "step_up"};
    private static final String[] TRANSFORMER_MODE_FALLBACKS = {"Redstone = step-up", "Fixed step-down", "Fixed step-up"};

    private boolean isTransformer() {
        return this.menu.getGuiType().name().endsWith("_TRANSFORMER");
    }

    @Override
    protected void init() {
        super.init();
        if (!isTransformer()) return;

        for (int mode = 0; mode < TRANSFORMER_MODES.length; mode++) {
            int buttonId = mode;
            this.addRenderableWidget(net.minecraft.client.gui.components.Button.builder(
                            Component.translatableWithFallback("gui.ic2reborn.transformer.mode." + TRANSFORMER_MODES[mode],
                                    TRANSFORMER_MODE_FALLBACKS[mode]),
                            button -> this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, buttonId))
                    .bounds(this.leftPos + 7, this.topPos + 65 + mode * 20, 144, 20)
                    .build());
        }
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int textColor = 0xFF000000 | MachineLayout.TEXT_COLOR;

        if (isTransformer()) {
            // a chave inglesa ao lado do botão marca o modo atual, como no IC2
            graphics.item(new net.minecraft.world.item.ItemStack(net.ic2reborn.registry.IC2AutoItems.WRENCH.get()),
                    152, 67 + this.menu.getTransformerMode() * 20);
        }

        // GuiIC2: título centralizado em y = 6
        graphics.text(this.font, this.title, (this.imageWidth - this.font.width(this.title)) / 2, 6, textColor, false);

        if (this.layout.showInventoryTitle()) {
            graphics.text(this.font, this.playerInventoryTitle,
                    this.layout.inventoryX() + 1, this.layout.inventoryY() - 10, textColor, false);
        }

        for (MachineLayout.TextDef text : this.layout.texts()) {
            Component component = text.text().apply(this.menu);
            int textX = text.x();
            int textY = text.y();
            if (text.width() > 0) textX += (text.width() - this.font.width(component)) / 2;
            if (text.height() > 0) textY += (text.height() - 8) / 2;
            graphics.text(this.font, component, textX, textY, 0xFF000000 | text.color(), false);
        }
    }
}
