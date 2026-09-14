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
            if (!image.visible().test(this.menu)) continue;
            graphics.blit(RenderPipelines.GUI_TEXTURED, image.texture(), x + image.x(), y + image.y(),
                    image.u(), image.v(), image.width(), image.height(), image.textureWidth(), image.textureHeight());
        }

        if (this.layout.isDynamic()) {
            drawSlotBackgrounds(graphics, x, y);
        }

        for (MachineLayout.TankDef tank : this.layout.tanks()) {
            drawTank(graphics, tank, x, y);
            if (this.isHovering(tank.x(), tank.y(), tank.width(), tank.height(), mouseX, mouseY)) {
                net.minecraft.world.level.material.Fluid fluid = this.menu.getFluid(tank.index());
                Component line = fluid == null || this.menu.getFluidAmount(tank.index()) <= 0
                        ? Component.translatableWithFallback("gui.ic2reborn.tank.empty", "Empty")
                        : net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getName(
                                net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid)).copy()
                                .append(": " + this.menu.getFluidAmount(tank.index()) + " / " + this.menu.getFluidCapacity(tank.index()) + " CL");
                graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(line), mouseX, mouseY);
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

    /**
     * TankGauge do IC2: fundo cheio (u=6), o fluido subindo de baixo para cima e a régua (u=38)
     * por cima; vazio usa u=70. Só há um tanque sincronizado por máquina.
     */
    private void drawTank(GuiGraphicsExtractor graphics, MachineLayout.TankDef tank, int x, int y) {
        net.minecraft.world.level.material.Fluid fluid = this.menu.getFluid(tank.index());
        boolean filled = fluid != null && this.menu.getFluidAmount(tank.index()) > 0;
        int tankX = x + tank.x();
        int tankY = y + tank.y();

        if (tank.style() == MachineLayout.TankStyle.NORMAL) {
            if (!filled) {
                blit(graphics, MachineLayout.COMMON_TEXTURE, tankX, tankY, 70, 100, 20, 55);
                return;
            }
            blit(graphics, MachineLayout.COMMON_TEXTURE, tankX, tankY, 6, 100, 20, 55);
            drawFluid(graphics, fluid, tankX + 4, tankY + 4, 12, 47, this.menu.getFluidRatio(tank.index()));
            blit(graphics, MachineLayout.COMMON_TEXTURE, tankX, tankY, 38, 100, 20, 55);
        } else if (filled) {
            drawFluid(graphics, fluid, tankX, tankY, tank.width(), tank.height(), this.menu.getFluidRatio(tank.index()));
        }
    }

    /** Textura "still" do fluido, repetida em quadrados e cortada na altura do nível. */
    private static void drawFluid(GuiGraphicsExtractor graphics, net.minecraft.world.level.material.Fluid fluid,
                                  int x, int y, int width, int height, double ratio) {
        int fluidHeight = (int) Math.round(height * ratio);
        if (fluidHeight <= 0) return;

        net.minecraft.client.renderer.texture.TextureAtlasSprite sprite = net.minecraft.client.Minecraft.getInstance()
                .getModelManager().getFluidStateModelSet().get(fluid.defaultFluidState()).stillMaterial().sprite();
        int color = 0xFF000000 | net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering.getColor(
                net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant.of(fluid));

        int top = y + height - fluidHeight;
        graphics.enableScissor(x, top, x + width, y + height);
        for (int tileY = y + height - width; tileY > top - width; tileY -= width) {
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, sprite, x, tileY, width, width, color);
        }
        graphics.disableScissor();
    }

    /** Gauge.drawBackground do IC2. */
    private void drawGauge(GuiGraphicsExtractor graphics, MachineLayout.GaugeDef gauge, int x, int y) {
        GaugeStyle style = gauge.style();
        double ratio = switch (gauge.source()) {
            case ENERGY -> this.menu.getEnergyRatio();
            case PROGRESS -> this.menu.getProgressRatio();
            case HEAT -> this.menu.getHeatRatio();
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

    /** Enlatadora: botão de modo (63, 81) cicla os 4 modos e o de setas (77, 64) troca os tanques, como no GuiCanner. */
    @Override
    public boolean mouseClicked(net.minecraft.client.input.MouseButtonEvent event, boolean doubleClick) {
        // GuiMetalFormer: botão (65, 53) alterna extrudar → laminar → cortar
        if (this.menu.getGuiType() == net.ic2reborn.menu.MachineGuiType.METAL_FORMER && event.button() == 0
                && this.isHovering(65, 53, 20, 20, event.x(), event.y())) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId,
                    net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_METAL_FORMER_MODE);
            return true;
        }
        int logisticsButton = event.button() == 0 ? logisticsButtonAt(event.x(), event.y()) : -1;
        if (logisticsButton >= 0) {
            this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, logisticsButton);
            return true;
        }
        if (this.menu.getGuiType() == net.ic2reborn.menu.MachineGuiType.CANNER && event.button() == 0) {
            int button = -1;
            if (this.isHovering(63, 81, 50, 14, event.x(), event.y())) {
                button = net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_CANNER_MODE + (this.menu.getMachineMode() + 1) % 4;
            } else if (this.isHovering(77, 64, 22, 13, event.x(), event.y())) {
                button = net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_SWAP_TANKS;
            }
            if (button >= 0) {
                this.minecraft.gameMode.handleInventoryButtonClick(this.menu.containerId, button);
                return true;
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    // ── logística ─────────────────────────────────────────────────────────
    private static final String[] DIRECTIONS = {"down", "up", "north", "south", "west", "east"};
    private static final String[] DIRECTION_FALLBACKS = {"Down", "Up", "North", "South", "West", "East"};
    private static final int[] REGULATOR_BUTTONS_X = {102, 112, 122, 132};

    private static net.minecraft.network.chat.MutableComponent directionName(int direction) {
        return Component.translatableWithFallback("gui.ic2reborn.direction." + DIRECTIONS[direction], DIRECTION_FALLBACKS[direction]);
    }

    private boolean isWeightedDistributor() {
        net.ic2reborn.menu.MachineGuiType type = this.menu.getGuiType();
        return type == net.ic2reborn.menu.MachineGuiType.WEIGHTED_ITEM_DISTRIBUTOR || type == net.ic2reborn.menu.MachineGuiType.WEIGHTED_FLUID_DISTRIBUTOR;
    }

    /** Posição (1–6) da direção na prioridade do distribuidor, ou 0 se ela não é saída. */
    private int priorityIndex(int direction) {
        int value = this.menu.getMachineMode();
        for (int i = 0; i < 6; i++) {
            int encoded = (value >> (3 * i)) & 7;
            if (encoded == 0) return 0;
            if (encoded - 1 == direction) return i + 1;
        }
        return 0;
    }

    private static int weightedButtonX(int direction) {
        return 8 + (direction % 2) * 82;
    }

    private static int weightedButtonY(int direction) {
        return 20 + (direction / 2) * 26;
    }

    private int logisticsButtonAt(double x, double y) {
        net.ic2reborn.menu.MachineGuiType type = this.menu.getGuiType();
        if (type == net.ic2reborn.menu.MachineGuiType.SORTING_MACHINE) {
            for (int d = 0; d < 6; d++) {
                if (this.isHovering(42, 18 + d * 20, 36, 18, x, y)) return net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_SORTING_DEFAULT + d;
            }
        } else if (isWeightedDistributor()) {
            for (int d = 0; d < 6; d++) {
                if (this.isHovering(weightedButtonX(d), weightedButtonY(d), 78, 20, x, y)) {
                    return net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_WEIGHTED_PRIORITY + d;
                }
            }
        } else if (type == net.ic2reborn.menu.MachineGuiType.FLUID_REGULATOR) {
            for (int i = 0; i < REGULATOR_BUTTONS_X.length; i++) {
                if (this.isHovering(REGULATOR_BUTTONS_X[i], 44, 9, 9, x, y)) return net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_REGULATOR + i;
                if (this.isHovering(REGULATOR_BUTTONS_X[i], 68, 9, 9, x, y)) return net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_REGULATOR + 4 + i;
            }
            if (this.isHovering(151, 44, 11, 9, x, y) || this.isHovering(151, 68, 11, 9, x, y)) {
                return net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_REGULATOR_MODE;
            }
        } else if (type == net.ic2reborn.menu.MachineGuiType.STEAM_GENERATOR) {
            for (int i = 0; i < 4; i++) {
                if (this.isHovering(92 + 10 * i, 162, 9, 9, x, y)) return net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_STEAM_WATER + i;
                if (this.isHovering(92 + 10 * i, 186, 9, 9, x, y)) return net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_STEAM_WATER + 4 + i;
                if (i < 3 && this.isHovering(23 + 10 * i, 25, 9, 9, x, y)) return net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_STEAM_PRESSURE + i;
                if (i < 3 && this.isHovering(23 + 10 * i, 49, 9, 9, x, y)) return net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_STEAM_PRESSURE + 3 + i;
            }
        } else if (type == net.ic2reborn.menu.MachineGuiType.SCANNER) {
            int uu = net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_UU;
            if (this.isHovering(102, 49, 12, 12, x, y)) return uu;
            if (this.isHovering(143, 49, 24, 12, x, y)) return uu + 1;
        } else if (type == net.ic2reborn.menu.MachineGuiType.REPLICATOR) {
            int uu = net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_UU;
            if (this.isHovering(80, 16, 9, 18, x, y)) return uu + 10;
            if (this.isHovering(109, 16, 9, 18, x, y)) return uu + 11;
            if (this.isHovering(75, 82, 16, 16, x, y)) return uu + 13;
            if (this.isHovering(92, 82, 16, 16, x, y)) return uu + 14;
            if (this.isHovering(109, 82, 16, 16, x, y)) return uu + 15;
        } else if (type == net.ic2reborn.menu.MachineGuiType.PATTERN_STORAGE) {
            int uu = net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_UU;
            if (this.isHovering(7, 19, 9, 18, x, y)) return uu + 20;
            if (this.isHovering(36, 19, 9, 18, x, y)) return uu + 21;
            if (this.isHovering(10, 37, 16, 8, x, y)) return uu + 22;
            if (this.isHovering(26, 37, 16, 8, x, y)) return uu + 23;
        } else if (type == net.ic2reborn.menu.MachineGuiType.ADVANCED_MINER) {
            int auto = net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_AUTOMATION;
            if (this.isHovering(133, 101, 36, 15, x, y)) return auto;
            if (this.isHovering(123, 27, 18, 15, x, y)) return auto + 1;
            if (this.isHovering(129, 45, 18, 15, x, y)) return auto + 2;
        } else if (type == net.ic2reborn.menu.MachineGuiType.ENERGY_O_MAT) {
            int auto = net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_AUTOMATION;
            for (int i = 0; i < 4; i++) {
                if (this.isHovering(102, 16 + 10 * i, 32, 10, x, y)) return auto + 10 + i;
                if (this.isHovering(134, 16 + 10 * i, 32, 10, x, y)) return auto + 14 + i;
            }
        } else if (type == net.ic2reborn.menu.MachineGuiType.FLUID_DISTRIBUTOR) {
            if (this.isHovering(117, 58, 19, 9, x, y)) return net.ic2reborn.block.entity.MachineBlockEntity.BUTTON_FLUID_DISTRIBUTOR_MODE;
        }
        return -1;
    }

    /** Triagem: ícone da rota padrão de cada face; distribuidores: botões de prioridade das saídas. */
    private void drawLogistics(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        net.ic2reborn.menu.MachineGuiType type = this.menu.getGuiType();
        if ((type == net.ic2reborn.menu.MachineGuiType.REPLICATOR || type == net.ic2reborn.menu.MachineGuiType.PATTERN_STORAGE)
                && this.menu.getHeat() > 0) {
            int itemX = type == net.ic2reborn.menu.MachineGuiType.REPLICATOR ? 91 : 152;
            int itemY = type == net.ic2reborn.menu.MachineGuiType.REPLICATOR ? 17 : 29;
            net.minecraft.world.item.ItemStack shown = new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(this.menu.getHeat() - 1));
            graphics.item(shown, itemX, itemY);
            if (this.isHovering(itemX, itemY, 16, 16, mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(shown.getHoverName()), mouseX, mouseY);
            }
        }
        if (type == net.ic2reborn.menu.MachineGuiType.REPLICATOR) {
            String[] tips = {"gui.ic2reborn.replicator.last", "gui.ic2reborn.replicator.next", "gui.ic2reborn.replicator.stop",
                    "gui.ic2reborn.replicator.single", "gui.ic2reborn.replicator.repeat"};
            String[] fallbacks = {"Previous pattern", "Next pattern", "Stop", "Replicate one", "Replicate continuously"};
            int[][] areas = {{80, 16, 9, 18}, {109, 16, 9, 18}, {75, 82, 16, 16}, {92, 82, 16, 16}, {109, 82, 16, 16}};
            for (int i = 0; i < areas.length; i++) {
                if (this.isHovering(areas[i][0], areas[i][1], areas[i][2], areas[i][3], mouseX, mouseY)) {
                    graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(Component.translatableWithFallback(tips[i], fallbacks[i])), mouseX, mouseY);
                }
            }
        }
        if (type == net.ic2reborn.menu.MachineGuiType.PATTERN_STORAGE) {
            String[] tips = {"gui.ic2reborn.pattern_storage.last", "gui.ic2reborn.pattern_storage.next", "gui.ic2reborn.pattern_storage.export",
                    "gui.ic2reborn.pattern_storage.import"};
            String[] fallbacks = {"Previous pattern", "Next pattern", "Copy pattern to crystal memory", "Import pattern from crystal memory"};
            int[][] areas = {{7, 19, 9, 18}, {36, 19, 9, 18}, {10, 37, 16, 8}, {26, 37, 16, 8}};
            for (int i = 0; i < areas.length; i++) {
                if (this.isHovering(areas[i][0], areas[i][1], areas[i][2], areas[i][3], mouseX, mouseY)) {
                    graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(Component.translatableWithFallback(tips[i], fallbacks[i])), mouseX, mouseY);
                }
            }
        }
        if (type == net.ic2reborn.menu.MachineGuiType.BATCH_CRAFTER && this.menu.getHeat() > 0) {
            net.minecraft.world.item.ItemStack result = new net.minecraft.world.item.ItemStack(
                    net.minecraft.core.registries.BuiltInRegistries.ITEM.byId(this.menu.getHeat() - 1));
            graphics.item(result, 94, 14);
            if (this.isHovering(94, 14, 16, 16, mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(result.getHoverName()), mouseX, mouseY);
            }
        }
        if (type == net.ic2reborn.menu.MachineGuiType.ENERGY_O_MAT) {
            String[] labels = {"-100k", "-10k", "-1k", "-100", "+100k", "+10k", "+1k", "+100"};
            for (int i = 0; i < labels.length; i++) {
                int buttonX = i < 4 ? 102 : 134;
                int buttonY = 16 + 10 * (i % 4);
                boolean hovered = this.isHovering(buttonX, buttonY, 32, 10, mouseX, mouseY);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace(
                        hovered ? "widget/button_highlighted" : "widget/button"), buttonX, buttonY, 32, 10);
                graphics.text(this.font, Component.literal(labels[i]), buttonX + (32 - this.font.width(labels[i])) / 2, buttonY + 1, 0xFFFFFFFF, true);
            }
        }
        if (type == net.ic2reborn.menu.MachineGuiType.ADVANCED_MINER) {
            String tip = null;
            String fallback = null;
            if (this.isHovering(133, 101, 36, 15, mouseX, mouseY)) {
                tip = "gui.ic2reborn.advanced_miner.reset";
                fallback = "Restart from the top";
            } else if (this.isHovering(123, 27, 18, 15, mouseX, mouseY)) {
                tip = "gui.ic2reborn.advanced_miner.mode";
                fallback = "Switch blacklist/whitelist";
            } else if (this.isHovering(129, 45, 18, 15, mouseX, mouseY)) {
                tip = (this.menu.getMachineMode() & 2) != 0 ? "gui.ic2reborn.advanced_miner.silk_on" : "gui.ic2reborn.advanced_miner.silk_off";
                fallback = (this.menu.getMachineMode() & 2) != 0 ? "Silk touch: on" : "Silk touch: off";
            }
            if (tip != null) {
                graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(Component.translatableWithFallback(tip, fallback)), mouseX, mouseY);
            }
        }
        if (type == net.ic2reborn.menu.MachineGuiType.NUCLEAR_REACTOR && this.isHovering(7, 136, 100, 13, mouseX, mouseY)) {
            graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(Component.translatableWithFallback(
                    "gui.ic2reborn.reactor.heat", "Core heat: %s / %s MMEV", this.menu.getHeat(), this.menu.getMaxHeat())), mouseX, mouseY);
        }
        if (type == net.ic2reborn.menu.MachineGuiType.STEAM_GENERATOR) {
            if (this.isHovering(13, 70, 9, 78, mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(Component.translatableWithFallback(
                        "gui.ic2reborn.steam_generator.heat", "System heat: %s °C", this.menu.getHeat() / 10.0)), mouseX, mouseY);
            } else if (this.isHovering(155, 61, 9, 60, mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(Component.translatableWithFallback(
                        "gui.ic2reborn.steam_generator.calcification", "Calcification: %s%%",
                        String.format(java.util.Locale.ROOT, "%.1f", this.menu.getProgressRatio() * 100))), mouseX, mouseY);
            }
        }
        if (type == net.ic2reborn.menu.MachineGuiType.SORTING_MACHINE && this.layout.background() != null) {
            for (int d = 0; d < 6; d++) {
                boolean on = (this.menu.getMachineMode() & (1 << d)) != 0;
                graphics.blit(RenderPipelines.GUI_TEXTURED, this.layout.background(), 60, 18 + d * 20,
                        on ? 230 : 212, on ? 32 : 33, 18, 18, ATLAS_SIZE, ATLAS_SIZE);
                if (this.isHovering(42, 18 + d * 20, 36, 18, mouseX, mouseY)) {
                    graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(
                            Component.translatableWithFallback("gui.ic2reborn.sorting.default_route", "Default route (%s): %s", directionName(d),
                                    on ? Component.translatableWithFallback("gui.ic2reborn.on", "on")
                                            : Component.translatableWithFallback("gui.ic2reborn.off", "off")),
                            Component.translatableWithFallback("gui.ic2reborn.sorting.hint", "Items without a filter go to the default routes")),
                            mouseX, mouseY);
                }
            }
        } else if (isWeightedDistributor()) {
            for (int d = 0; d < 6; d++) {
                int buttonX = weightedButtonX(d);
                int buttonY = weightedButtonY(d);
                boolean hovered = this.isHovering(buttonX, buttonY, 78, 20, mouseX, mouseY);
                graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace(
                        hovered ? "widget/button_highlighted" : "widget/button"), buttonX, buttonY, 78, 20);
                int index = priorityIndex(d);
                Component label = directionName(d).append(index > 0 ? ": " + index : ": -");
                graphics.text(this.font, label, buttonX + (78 - this.font.width(label)) / 2, buttonY + 6,
                        index > 0 ? 0xFFFFFFFF : 0xFFA0A0A0, true);
                if (hovered) {
                    graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(Component.translatableWithFallback(
                            "gui.ic2reborn.weighted.hint", "Click to add or remove this output; items go to the first one that accepts")),
                            mouseX, mouseY);
                }
            }
        }
    }
    private static final String[] METAL_FORMER_MODES = {"extruding", "rolling", "cutting"};
    private static final String[] METAL_FORMER_MODE_FALLBACKS = {"Extruding", "Rolling", "Cutting"};
    /** Ícones do botão no IC2: cabo de cobre, martelo e alicate. */
    private static final String[] METAL_FORMER_ICONS = {"craftenergy:cable_copper", "craftenergy:hammer", "craftenergy:cutter"};

    private static final String[] CANNER_MODES = {"bottle_solid", "empty_liquid", "bottle_liquid", "enrich_liquid"};
    private static final String[] CANNER_MODE_FALLBACKS = {"Can solids", "Empty container into tank", "Fill container from tank", "Enrich fluid"};

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.menu.getGuiType() == net.ic2reborn.menu.MachineGuiType.METAL_FORMER) {
            boolean hovered = this.isHovering(65, 53, 20, 20, mouseX, mouseY);
            graphics.blitSprite(RenderPipelines.GUI_TEXTURED, Identifier.withDefaultNamespace(
                    hovered ? "widget/button_highlighted" : "widget/button"), 65, 53, 20, 20);
            int mode = Math.floorMod(this.menu.getMachineMode(), METAL_FORMER_MODES.length);
            graphics.item(new net.minecraft.world.item.ItemStack(net.minecraft.core.registries.BuiltInRegistries.ITEM
                    .getOptional(Identifier.parse(METAL_FORMER_ICONS[mode]))
                    .orElse(net.minecraft.world.item.Items.BARRIER)), 67, 55);
            if (hovered) {
                graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(Component.translatableWithFallback(
                        "gui.ic2reborn.metal_former.mode." + METAL_FORMER_MODES[mode], METAL_FORMER_MODE_FALLBACKS[mode])), mouseX, mouseY);
            }
        }
        if (this.menu.getGuiType() == net.ic2reborn.menu.MachineGuiType.BLOCK_CUTTER) {
            if (this.menu.getMachineMode() == 1 && this.isHovering(63, 54, 30, 26, mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(Component.translatableWithFallback(
                        "gui.ic2reborn.block_cutter.blade_too_weak", "Blade too weak for this block")), mouseX, mouseY);
            } else if (this.isHovering(70, 34, 16, 16, mouseX, mouseY) && !this.menu.slots.get(3).hasItem()) {
                graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(Component.translatableWithFallback(
                        "gui.ic2reborn.block_cutter.blade", "Cutting blade")), mouseX, mouseY);
            }
        }
        if (this.menu.getGuiType() == net.ic2reborn.menu.MachineGuiType.CANNER) {
            int mode = Math.floorMod(this.menu.getMachineMode(), CANNER_MODES.length);
            if (this.isHovering(63, 81, 50, 14, mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(Component.translatableWithFallback(
                        "gui.ic2reborn.canner.mode." + CANNER_MODES[mode], CANNER_MODE_FALLBACKS[mode])), mouseX, mouseY);
            } else if (this.isHovering(77, 64, 22, 13, mouseX, mouseY)) {
                graphics.setComponentTooltipForNextFrame(this.font, java.util.List.of(
                        Component.translatableWithFallback("gui.ic2reborn.canner.swap", "Swap tanks")), mouseX, mouseY);
            }
        }

        drawLogistics(graphics, mouseX, mouseY);
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
