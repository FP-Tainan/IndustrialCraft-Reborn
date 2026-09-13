package net.craftenergy.content;

import net.craftenergy.content.block.CableBlock;
import net.craftenergy.fabric.CraftEnergyApi;
import net.craftenergy.registry.DeferredRegister;
import net.craftenergy.registry.RegistryObject;
import net.fabricmc.fabric.api.biome.v1.BiomeModifications;
import net.fabricmc.fabric.api.biome.v1.BiomeSelectors;
import net.fabricmc.fabric.api.creativetab.v1.FabricCreativeModeTab;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.Biomes;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.GenerationStep;
import net.minecraft.world.level.levelgen.placement.PlacedFeature;

/** Conteúdo compartilhado do Craft Energy: registros, aba criativa, cabos na rede e geração de mundo. */
public final class CraftEnergyContent {
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, CraftEnergyApi.MOD_ID);

    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("craftenergy", () -> FabricCreativeModeTab.builder()
            .title(Component.translatable("itemGroup.craftenergy"))
            .icon(() -> new ItemStack(CEItems.CABLE_COPPER_INSULATED.get()))
            .displayItems((params, output) -> {
                CEItems.ITEMS.getEntries().forEach(entry -> output.accept(entry.get()));
                output.accept(Items.RESIN_CLUMP);
                output.accept(Items.RESIN_BRICK);
            })
            .build());

    private CraftEnergyContent() {}

    public static void init() {
        CEBlocks.BLOCKS.register();
        CEItems.ITEMS.register();
        TABS.register();

        registerCables();
        addWorldgen();
    }

    /** Cada bloco de cabo expõe o seu {@link net.craftenergy.content.block.CableType} em todas as faces. */
    private static void registerCables() {
        Block[] cables = CEBlocks.BLOCKS.getEntries().stream()
                .map(RegistryObject::get)
                .filter(block -> block instanceof CableBlock)
                .toArray(Block[]::new);

        CraftEnergyApi.NODE.registerForBlocks(
                (level, pos, state, blockEntity, face) -> state.getBlock() instanceof CableBlock cable ? cable.type() : null,
                cables);
    }

    private static void addWorldgen() {
        BiomeModifications.addFeature(BiomeSelectors.foundInOverworld(),
                GenerationStep.Decoration.UNDERGROUND_ORES, placedFeature("tin_ore"));

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
        return ResourceKey.create(Registries.PLACED_FEATURE, Identifier.fromNamespaceAndPath(CraftEnergyApi.MOD_ID, name));
    }
}
