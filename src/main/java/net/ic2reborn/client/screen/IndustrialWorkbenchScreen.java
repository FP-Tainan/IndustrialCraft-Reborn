package net.ic2reborn.client.screen;

import net.ic2reborn.IC2Reborn;
import net.ic2reborn.menu.IndustrialWorkbenchMenu;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Inventory;

/** Tela da bancada industrial (fundo do IC2, 194×228). */
public class IndustrialWorkbenchScreen extends AbstractContainerScreen<IndustrialWorkbenchMenu> {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "textures/gui/guiindustrialworkbench.png");

    public IndustrialWorkbenchScreen(IndustrialWorkbenchMenu menu, Inventory playerInventory, Component title) {
        super(menu, playerInventory, title, 194, 228);
    }

    @Override
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractBackground(graphics, mouseX, mouseY, partialTick);
        graphics.blit(RenderPipelines.GUI_TEXTURED, TEXTURE, this.leftPos, this.topPos, 0, 0, this.imageWidth, this.imageHeight, 256, 256);
    }

    @Override
    protected void extractLabels(GuiGraphicsExtractor graphics, int mouseX, int mouseY) {
        graphics.text(this.font, this.title, 30, 32, 0xFF404040, false);
    }
}
