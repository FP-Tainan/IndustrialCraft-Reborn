package net.ic2reborn;

import com.mojang.logging.LogUtils;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.ic2reborn.registry.DeferredRegister;
import net.ic2reborn.registry.IC2Blocks;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2BlockEntities;
import net.ic2reborn.registry.IC2Items;
import net.ic2reborn.registry.IC2Menus;
import net.ic2reborn.registry.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;
import org.slf4j.Logger;

public class IC2Reborn implements ModInitializer {

    public static final String MODID = "ic2reborn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> IC2_TAB =
            TABS.register("ic2reborn_tab", () -> FabricCreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ic2reborn"))
                    .icon(() -> new ItemStack(IC2Items.TREETAP.get()))
                    .displayItems((params, output) -> {
                        output.accept(IC2Items.TREETAP.get());
                        output.accept(IC2Blocks.RUBBER_LOG.get());
                        output.accept(IC2Blocks.RUBBER_LEAVES.get());
                        output.accept(IC2Blocks.RUBBER_SAPLING.get());
                        output.accept(Items.RESIN_CLUMP);
                        output.accept(Items.RESIN_BRICK);
                        output.accept(IC2Items.RUBBER.get());
                        output.accept(IC2Blocks.TIN_ORE.get());
                        output.accept(IC2Blocks.DEEPSLATE_TIN_ORE.get());
                        output.accept(IC2Blocks.LEAD_ORE.get());
                        output.accept(IC2Blocks.DEEPSLATE_LEAD_ORE.get());
                        output.accept(IC2Blocks.URANIUM_ORE.get());
                        output.accept(IC2Blocks.DEEPSLATE_URANIUM_ORE.get());
                        output.accept(IC2Items.RAW_TIN.get());
                        output.accept(IC2Items.RAW_LEAD.get());
                        output.accept(IC2Items.RAW_URANIUM.get());
                        output.accept(IC2Items.INGOT_TIN.get());
                        output.accept(IC2Items.INGOT_LEAD.get());
                        output.accept(IC2Items.INGOT_URANIUM.get());
                        IC2AutoItems.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                    })
                    .build());

    @Override
    public void onInitialize() {
        IC2Blocks.BLOCKS.register();
        IC2Items.ITEMS.register();
        IC2AutoBlocks.BLOCKS.register();
        IC2AutoItems.ITEMS.register();
        IC2BlockEntities.BLOCK_ENTITY_TYPES.register();
        IC2Menus.MENUS.register();
        TABS.register();

        ItemStorage.SIDED.registerForBlockEntity(
                (machine, side) -> ContainerStorage.of(machine.getInventory(), side),
                IC2BlockEntities.MACHINE.get());

        addWorldgen();
        LOGGER.info("IC2 Reborn — Módulo 1: Natureza carregado!");
    }

    /** Substitui os biome modifiers do Forge (data/ic2reborn/forge/biome_modifier). */
    private static void addWorldgen() {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES, placedFeature("tin_ore"));
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES, placedFeature("lead_ore"));
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES, placedFeature("uranium_ore"));

        BiomeModifications.addFeature(BiomeSelectors.includeByKey(
                        Biomes.FOREST, Biomes.BIRCH_FOREST, Biomes.OLD_GROWTH_BIRCH_FOREST, Biomes.DARK_FOREST,
                        Biomes.JUNGLE, Biomes.SPARSE_JUNGLE, Biomes.BAMBOO_JUNGLE,
                        Biomes.PLAINS, Biomes.SUNFLOWER_PLAINS,
                        Biomes.TAIGA, Biomes.OLD_GROWTH_PINE_TAIGA, Biomes.OLD_GROWTH_SPRUCE_TAIGA),
                GenerationStep.Decoration.VEGETAL_DECORATION, placedFeature("rubber_tree"));

        BiomeModifications.addFeature(BiomeSelectors.includeByKey(Biomes.SWAMP, Biomes.MANGROVE_SWAMP),
                GenerationStep.Decoration.VEGETAL_DECORATION, placedFeature("rubber_tree_swamp"));
    }

    private static ResourceKey<PlacedFeature> placedFeature(String name) {
        return ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(MODID, name));
    }
}
