package net.ic2reborn.client;

import net.fabricmc.api.ClientModInitializer;
import net.ic2reborn.client.screen.MachineScreen;
import net.ic2reborn.registry.IC2Menus;
import net.minecraft.client.gui.screens.MenuScreens;

public class IC2ClientSetup implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(IC2Menus.MACHINE.get(), MachineScreen::new);
        MenuScreens.register(IC2Menus.CROPNALYZER.get(), net.ic2reborn.client.screen.CropnalyzerScreen::new);
        MenuScreens.register(IC2Menus.METER.get(), net.ic2reborn.client.screen.MeterScreen::new);
        MenuScreens.register(IC2Menus.BOX.get(), net.ic2reborn.client.screen.BoxScreen::new);
        MenuScreens.register(IC2Menus.INDUSTRIAL_WORKBENCH.get(), net.ic2reborn.client.screen.IndustrialWorkbenchScreen::new);
        ArmorClient.init();
        MagnetizerClient.init();
        MultimeterClient.init();
        net.ic2reborn.item.GuideBookItem.opener = () -> {
            net.minecraft.client.gui.screens.Screen guide = new net.ic2reborn.client.screen.GuideBookScreen();
            net.minecraft.client.Minecraft.getInstance().setScreenAndShow(guide);
        };
        net.fabricmc.fabric.api.client.rendering.v1.BlockEntityRendererRegistry.register(
                net.ic2reborn.registry.IC2BlockEntities.MACHINE.get(), RotorRenderer::new);
        // estrutura do forno de coque (IC2: addInformation)
        // tanque quebrado: fluido guardado no item
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            net.ic2reborn.fluid.StoredFluid stored = stack.get(net.ic2reborn.registry.IC2Components.STORED_FLUID.get());
            if (stored != null && !stored.variant().isBlank()) {
                lines.add(net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariantAttributes.getName(stored.variant()).copy()
                        .append(": " + stored.amount() * 1000 / net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants.BUCKET + " CL")
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        });
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            if (stack.is(net.ic2reborn.registry.IC2AutoItems.COKE_KILN.get())) {
                for (int line = 1; line <= 3; line++) {
                    lines.add(net.minecraft.network.chat.Component.translatable("tooltip.ic2reborn.coke_kiln." + line)
                            .withStyle(net.minecraft.ChatFormatting.GRAY));
                }
            }
        });

        // armazenamentos desmontados mostram a energia guardada no item
        net.fabricmc.fabric.api.client.item.v1.ItemTooltipCallback.EVENT.register((stack, context, flag, lines) -> {
            Long stored = stack.get(net.craftenergy.content.CEComponents.STORED_ENERGY.get());
            if (stored != null && !(stack.getItem() instanceof net.craftenergy.content.item.EnergyItem)) {
                lines.add(net.minecraft.network.chat.Component.translatableWithFallback("tooltip.ic2reborn.stored_energy",
                                "Stored energy: %s", net.craftenergy.api.EnergyUnits.formatEnergy(stored))
                        .withStyle(net.minecraft.ChatFormatting.GRAY));
            }
        });

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
