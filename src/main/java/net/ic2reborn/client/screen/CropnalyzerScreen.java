package net.ic2reborn.client.screen;

import net.ic2reborn.IC2Reborn;
import net.ic2reborn.crop.CropCard;
import net.ic2reborn.crop.CropCards;
import net.ic2reborn.item.CropSeedItem;
import net.ic2reborn.menu.CropnalyzerMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/**
 * Tela do Cropnalyzer ({@code GuiCropnalyzer}): mostra no visor o que o nível de análise do saco
 * da direita revela — nome (1), nível e descobridor (2), atributos (3) e Gr/Ga/Re (4).
 */
public class CropnalyzerScreen extends AbstractContainerScreen<CropnalyzerMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "textures/gui/guicropnalyzer.png");
    private static final String[] ROMAN = {"0", "I", "II", "III", "IV", "V", "VI", "VII", "VIII", "IX", "X", "XI", "XII", "XIII", "XIV", "XV", "XVI"};
    private static final int WHITE = 0xFFFFFF;
    private static final int GROWTH_COLOR = 0xADFF2F;
    private static final int GAIN_COLOR = 0xEEC800;
    private static final int RESISTANCE_COLOR = 0x00CED1;

    public CropnalyzerScreen(CropnalyzerMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 223);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        text(graphics, this.title, 74, 11, 0x000000);

        CropSeedItem.CropSeed seed = CropSeedItem.data(this.menu.scannedSeed());
        if (seed == null) return;
        if (seed.scan() == 0) {
            text(graphics, Component.translatableWithFallback("gui.ic2reborn.cropnalyzer.unknown", "UNKNOWN"), 8, 37, WHITE);
            return;
        }
        CropCard card = CropCards.byId(seed.crop());
        if (card == null) return;

        text(graphics, card.name(), 8, 37, WHITE);
        if (seed.scan() >= 2) {
            int tier = card.properties().tier();
            String roman = tier >= 0 && tier < ROMAN.length ? ROMAN[tier] : Integer.toString(tier);
            text(graphics, Component.translatableWithFallback("gui.ic2reborn.cropnalyzer.tier", "Tier: %s", roman), 8, 50, WHITE);
            text(graphics, Component.translatableWithFallback("gui.ic2reborn.cropnalyzer.discovered", "Discovered by:"), 8, 73, WHITE);
            text(graphics, Component.literal(card.discoveredBy()), 8, 86, WHITE);
        }
        if (seed.scan() >= 3) {
            text(graphics, Component.literal(card.desc(0)), 8, 109, WHITE);
            text(graphics, Component.literal(card.desc(1)), 8, 122, WHITE);
        }
        if (seed.scan() >= 4) {
            text(graphics, Component.translatableWithFallback("gui.ic2reborn.cropnalyzer.growth", "Growth:"), 118, 37, GROWTH_COLOR);
            text(graphics, Component.literal(Integer.toString(seed.growth())), 118, 50, GROWTH_COLOR);
            text(graphics, Component.translatableWithFallback("gui.ic2reborn.cropnalyzer.gain", "Gain:"), 118, 73, GAIN_COLOR);
            text(graphics, Component.literal(Integer.toString(seed.gain())), 118, 86, GAIN_COLOR);
            text(graphics, Component.translatableWithFallback("gui.ic2reborn.cropnalyzer.resistance", "Resis.:"), 118, 109, RESISTANCE_COLOR);
            text(graphics, Component.literal(Integer.toString(seed.resistance())), 118, 122, RESISTANCE_COLOR);
        }
    }

    private void text(GuiGraphicsExtractor graphics, Component component, int x, int y, int color) {
        graphics.text(this.font, component, x, y, 0xFF000000 | color, false);
    }
}
