package net.ic2reborn.registry;
import net.craftenergy.registry.DeferredRegister;
import net.craftenergy.registry.RegistryObject;

import net.minecraft.core.registries.Registries;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuType;
import net.ic2reborn.IC2Reborn;
import net.ic2reborn.menu.MachineGuiType;
import net.ic2reborn.menu.MachineMenu;
import net.minecraft.world.inventory.MenuType;

/** Menu registrations for IC2 Reborn GUIs. */
public class IC2Menus {
    public static final DeferredRegister<MenuType<?>> MENUS =
            DeferredRegister.create(Registries.MENU, IC2Reborn.MODID);

    /** Um único menu para todas as máquinas; o layout vem do MachineGuiType enviado ao cliente. */
    public static final RegistryObject<ExtendedMenuType<MachineMenu, MachineGuiType>> MACHINE =
            MENUS.register("machine", () -> new ExtendedMenuType<MachineMenu, MachineGuiType>(
                    MachineMenu::new, MachineGuiType.STREAM_CODEC));

    /** Cropnalyzer na mão; o cliente recebe qual mão abriu a tela. */
    public static final RegistryObject<ExtendedMenuType<net.ic2reborn.menu.CropnalyzerMenu, net.minecraft.world.InteractionHand>> CROPNALYZER =
            MENUS.register("cropnalyzer", () -> new ExtendedMenuType<net.ic2reborn.menu.CropnalyzerMenu, net.minecraft.world.InteractionHand>(
                    net.ic2reborn.menu.CropnalyzerMenu::new, net.ic2reborn.menu.CropnalyzerMenu.HAND_CODEC));

    /** Medidor de energia; o cliente recebe a posição do bloco medido. */
    public static final RegistryObject<ExtendedMenuType<net.ic2reborn.menu.MeterMenu, net.minecraft.core.BlockPos>> METER =
            MENUS.register("meter", () -> new ExtendedMenuType<net.ic2reborn.menu.MeterMenu, net.minecraft.core.BlockPos>(
                    net.ic2reborn.menu.MeterMenu::new, net.minecraft.core.BlockPos.STREAM_CODEC));

    /** Caixa de ferramentas e caixa de contenção. */
    public static final RegistryObject<ExtendedMenuType<net.ic2reborn.menu.BoxMenu, net.ic2reborn.menu.BoxMenu.OpenData>> BOX =
            MENUS.register("box", () -> new ExtendedMenuType<net.ic2reborn.menu.BoxMenu, net.ic2reborn.menu.BoxMenu.OpenData>(
                    net.ic2reborn.menu.BoxMenu::new, net.ic2reborn.menu.BoxMenu.OpenData.STREAM_CODEC));
}
