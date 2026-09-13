package net.ic2reborn.client;

import net.fabricmc.api.ClientModInitializer;
import net.ic2reborn.client.screen.MachineScreen;
import net.ic2reborn.registry.IC2Menus;
import net.minecraft.client.gui.screens.MenuScreens;

public class IC2ClientSetup implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(IC2Menus.MACHINE.get(), MachineScreen::new);

        // fluidos do IC2 no mundo: textura parada e escorrendo, translúcidas
        for (net.ic2reborn.fluid.IC2Fluids.Entry entry : net.ic2reborn.fluid.IC2Fluids.all()) {
            net.minecraft.resources.Identifier still = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    net.ic2reborn.IC2Reborn.MODID, "block/fluid/" + entry.name() + "_still");
            net.minecraft.resources.Identifier flow = net.minecraft.resources.Identifier.fromNamespaceAndPath(
                    net.ic2reborn.IC2Reborn.MODID, "block/fluid/" + entry.name() + "_flow");
            net.fabricmc.fabric.api.client.render.fluid.v1.FluidRenderingRegistry.register(entry.fluid(), entry.flowing(),
                    new net.minecraft.client.renderer.block.FluidModel.Unbaked(
                            new net.minecraft.client.resources.model.sprite.Material(still, true),
                            new net.minecraft.client.resources.model.sprite.Material(flow, true), null, null));
        }
    }
}
