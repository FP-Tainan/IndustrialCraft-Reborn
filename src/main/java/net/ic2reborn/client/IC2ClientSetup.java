package net.ic2reborn.client;

import net.fabricmc.api.ClientModInitializer;
import net.ic2reborn.client.screen.MachineScreen;
import net.ic2reborn.registry.IC2Menus;
import net.minecraft.client.gui.screens.MenuScreens;

public class IC2ClientSetup implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        MenuScreens.register(IC2Menus.MACHINE.get(), MachineScreen::new);
    }
}
