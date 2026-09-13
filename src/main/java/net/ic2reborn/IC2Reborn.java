package net.ic2reborn;

import com.mojang.logging.LogUtils;
import net.craftenergy.registry.DeferredRegister;
import net.craftenergy.registry.RegistryObject;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2BlockEntities;
import net.ic2reborn.registry.IC2Blocks;
import net.ic2reborn.registry.IC2Items;
import net.ic2reborn.registry.IC2Menus;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;

public class IC2Reborn implements ModInitializer {

    public static final String MODID = "ic2reborn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    /** Cabos, seringueira, estanho, placas e ferramentas aparecem na aba do Craft Energy. */
    public static final RegistryObject<CreativeModeTab> IC2_TAB =
            TABS.register("ic2reborn_tab", () -> FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ic2reborn"))
                    .icon(() -> new ItemStack(IC2AutoItems.MACERATOR.get()))
                    .displayItems((params, output) -> {
                        output.accept(IC2Blocks.LEAD_ORE.get());
                        output.accept(IC2Blocks.DEEPSLATE_LEAD_ORE.get());
                        output.accept(IC2Blocks.URANIUM_ORE.get());
                        output.accept(IC2Blocks.DEEPSLATE_URANIUM_ORE.get());
                        output.accept(IC2Items.RAW_LEAD.get());
                        output.accept(IC2Items.RAW_URANIUM.get());
                        output.accept(IC2Items.INGOT_LEAD.get());
                        output.accept(IC2Items.INGOT_URANIUM.get());
                        output.accept(IC2Items.FILLED_TIN_CAN.get());
                        output.accept(IC2Items.BIO_CHAFF.get());
                        for (var item : java.util.List.of(IC2Items.PLATE_OBSIDIAN, IC2Items.COIN, IC2Items.FUEL_ROD, IC2Items.SLAG,
                                IC2Items.DUST_CLAY, IC2Items.CRUSHED_URANIUM, IC2Items.PURIFIED_URANIUM)) {
                            output.accept(item.get());
                        }
                        net.ic2reborn.fluid.IC2Fluids.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                        IC2AutoItems.ITEMS.getEntries().forEach(entry -> {
                            output.accept(entry.get());
                            // baterias recarregáveis aparecem vazias e carregadas, como no IC2
                            if (entry.get() instanceof net.craftenergy.content.item.BatteryItem battery && battery.isRechargeable()) {
                                output.accept(battery.charged());
                            }
                        });
                    })
                    .build());

    @Override
    public void onInitialize() {
        net.ic2reborn.fluid.IC2Fluids.FLUIDS.register();
        net.ic2reborn.fluid.IC2Fluids.BLOCKS.register();
        IC2Blocks.BLOCKS.register();
        IC2AutoBlocks.BLOCKS.register();
        IC2AutoItems.ITEMS.register();
        IC2Items.ITEMS.register();
        net.ic2reborn.fluid.IC2Fluids.ITEMS.register();
        IC2BlockEntities.BLOCK_ENTITY_TYPES.register();
        IC2Menus.MENUS.register();
        TABS.register();

        ItemStorage.SIDED.registerForBlockEntity(
                (machine, side) -> ContainerStorage.of(machine.getInventory(), side),
                IC2BlockEntities.MACHINE.get());

        // geradores, armazenamentos e máquinas entram na rede do Craft Energy
        net.craftenergy.fabric.CraftEnergyApi.NODE.registerForBlockEntity(
                (machine, face) -> machine.getEnergyNode(face),
                IC2BlockEntities.MACHINE.get());

        // tanques das máquinas (geotérmico, lavadora de minério) e células de fluido
        net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage.SIDED.registerForBlockEntity(
                (machine, side) -> machine.getFluidStorage(side),
                IC2BlockEntities.MACHINE.get());
        net.ic2reborn.fluid.MachineFluids.init();

        net.fabricmc.fabric.api.resource.v1.ResourceLoader.get(net.minecraft.server.packs.PackType.SERVER_DATA)
                .registerReloadListener(net.ic2reborn.recipe.MachineRecipes.ID, net.ic2reborn.recipe.MachineRecipes.INSTANCE);

        addWorldgen();
        LOGGER.info("IC2 Reborn carregado!");
    }

    /** Minérios exclusivos do IC2. Estanho e seringueiras são gerados pelo Craft Energy. */
    private static void addWorldgen() {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES, placedFeature("lead_ore"));
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES, placedFeature("uranium_ore"));
    }

    private static ResourceKey<PlacedFeature> placedFeature(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(MODID, name));
    }
}
