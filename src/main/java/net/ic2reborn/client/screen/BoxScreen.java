package net.ic2reborn.client.screen;

import net.ic2reborn.IC2Reborn;
import net.ic2reborn.menu.BoxMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Telas da caixa de ferramentas e da caixa de contenção (fundos do IC2, sem textos). */
public class BoxScreen extends AbstractContainerScreen<BoxMenu> {
    private final Identifier texture;

    public BoxScreen(BoxMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 176, 166);
        this.texture = Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "textures/gui/" + menu.kind().texture);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, this.texture, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        if (this.menu.kind() == BoxMenu.Kind.TOOL_BOX) {
            graphics.text(this.font, this.title, 8, 6, 0xFF404040, false);
        }
    }
}
