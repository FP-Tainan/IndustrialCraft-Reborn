package net.ic2reborn.client.screen;

import net.craftenergy.api.EnergyUnits;
import net.ic2reborn.IC2Reborn;
import net.ic2reborn.menu.MeterMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Tela do medidor ({@code GuiToolMeter}): quatro botões de modo à direita (a textura marca o
 * escolhido), média, máximo e mínimo à esquerda e "zerar" embaixo.
 */
public class MeterScreen extends AbstractContainerScreen<MeterMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "textures/gui/guitooleumeter.png");
    private static final int TEXT_COLOR = 0x20EB3E;
    /** Botões na ordem entregue, gerado, corrente, tensão: (112,55) (132,55) (112,75) (132,75). */
    private static final int[][] BUTTONS = {{112, 55}, {132, 55}, {112, 75}, {132, 75}};
    /** Faixa da textura (u=176) que desenha cada modo marcado. */
    private static final int[] MODE_V = {0, 40, 120, 80};
    private static final String[] MODE_KEYS = {"delivered", "generated", "current", "voltage"};
    private static final String[] MODE_FALLBACKS = {"Delivered", "Generated", "Current", "Voltage"};

    public MeterScreen(MeterMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 217);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
        int mode = Math.max(0, Math.min(3, this.menu.getMode()));
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos + 112, this.topPos + 55, 176, MODE_V[mode], 40, 40, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        int mode = Math.max(0, Math.min(3, this.menu.getMode()));
        text(graphics, Component.translatableWithFallback("gui.ic2reborn.meter.mode", "Mode:"), 115, 43);
        text(graphics, Component.translatableWithFallback("gui.ic2reborn.meter.avg", "Avg:"), 15, 41);
        text(graphics, Component.literal(format(mode, this.menu.getAverage())), 15, 51);
        text(graphics, Component.translatableWithFallback("gui.ic2reborn.meter.max_min", "Max/Min"), 15, 64);
        text(graphics, Component.literal(format(mode, this.menu.getMaximum())), 15, 74);
        text(graphics, Component.literal(format(mode, this.menu.getMinimum())), 15, 84);
        text(graphics, Component.translatableWithFallback("gui.ic2reborn.meter.cycle", "Cycle: %s s", this.menu.getCount() / 20), 15, 100);
        text(graphics, Component.translatableWithFallback("gui.ic2reborn.meter.reset", "Reset"), 39, 114);
        text(graphics, Component.translatableWithFallback("gui.ic2reborn.meter." + MODE_KEYS[mode], MODE_FALLBACKS[mode]), 105, 100);
    }

    private static String format(int mode, double value) {
        return switch (mode) {
            case MeterMenu.MODE_CURRENT -> EnergyUnits.formatCurrent(value);
            case MeterMenu.MODE_VOLTAGE -> EnergyUnits.formatVoltage(value);
            default -> EnergyUnits.formatPower(value);
        };
    }

    private void text(GuiGraphicsExtractor graphics, Component component, int x, int y) {
        graphics.text(this.font, component, x, y, 0xFF000000 | TEXT_COLOR, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            for (int mode = 0; mode < BUTTONS.length; mode++) {
                if (this.isHovering(BUTTONS[mode][0], BUTTONS[mode][1], 20, 20, event.x(), event.y())) {
                    this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, mode);
                    return true;
                }
            }
            if (this.isHovering(26, 111, 58, 13, event.x(), event.y())) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, MeterMenu.BUTTON_RESET);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }
}
