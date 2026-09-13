package net.craftenergy.content;

import net.craftenergy.content.block.CableBlock;
import net.craftenergy.content.block.CableType;
import net.craftenergy.content.block.OreBlock;
import net.craftenergy.content.block.RubberLeavesBlock;
import net.craftenergy.content.block.RubberLogDropBlock;
import net.craftenergy.content.block.RubberSaplingBlock;
import net.craftenergy.fabric.CraftEnergyApi;
import net.craftenergy.registry.DeferredRegister;
import net.craftenergy.registry.RegistryObject;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

import java.util.function.Supplier;

/** Blocos do Craft Energy: cabos, seringueira e estanho. */
public final class CEBlocks {
    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(Registries.BLOCK, CraftEnergyApi.MOD_ID);

    // ── cabos (nus 4 px, fibra 3 px, isolados/detector/divisor 6 px) ────
    public static final RegistryObject<CableBlock> CABLE_TIN = cable("cable_tin", CableType.TIN, () -> Blocks.IRON_BARS, 6.0, 10.0);
    public static final RegistryObject<CableBlock> CABLE_TIN_INSULATED = cable("cable_tin_insulated", CableType.TIN_INSULATED, () -> Blocks.WOOL.white(), 5.0, 11.0);
    public static final RegistryObject<CableBlock> CABLE_COPPER = cable("cable_copper", CableType.COPPER, () -> Blocks.IRON_BARS, 6.0, 10.0);
    public static final RegistryObject<CableBlock> CABLE_COPPER_INSULATED = cable("cable_copper_insulated", CableType.COPPER_INSULATED, () -> Blocks.WOOL.white(), 5.0, 11.0);
    public static final RegistryObject<CableBlock> CABLE_GOLD = cable("cable_gold", CableType.GOLD, () -> Blocks.IRON_BARS, 6.0, 10.0);
    public static final RegistryObject<CableBlock> CABLE_GOLD_INSULATED = cable("cable_gold_insulated", CableType.GOLD_INSULATED, () -> Blocks.WOOL.white(), 5.0, 11.0);
    public static final RegistryObject<CableBlock> CABLE_GOLD_DOUBLE_INSULATED = cable("cable_gold_double_insulated", CableType.GOLD_DOUBLE_INSULATED, () -> Blocks.WOOL.white(), 5.0, 11.0);
    public static final RegistryObject<CableBlock> CABLE_IRON = cable("cable_iron", CableType.IRON, () -> Blocks.IRON_BARS, 6.0, 10.0);
    public static final RegistryObject<CableBlock> CABLE_IRON_INSULATED = cable("cable_iron_insulated", CableType.IRON_INSULATED, () -> Blocks.WOOL.white(), 5.0, 11.0);
    public static final RegistryObject<CableBlock> CABLE_IRON_DOUBLE_INSULATED = cable("cable_iron_double_insulated", CableType.IRON_DOUBLE_INSULATED, () -> Blocks.WOOL.white(), 5.0, 11.0);
    public static final RegistryObject<CableBlock> CABLE_IRON_TRIPLE_INSULATED = cable("cable_iron_triple_insulated", CableType.IRON_TRIPLE_INSULATED, () -> Blocks.WOOL.white(), 5.0, 11.0);
    public static final RegistryObject<CableBlock> GLASS_FIBRE_CABLE = cable("glass_fibre_cable", CableType.GLASS_FIBRE, () -> Blocks.GLASS, 6.5, 9.5);
    public static final RegistryObject<CableBlock> CABLE_DETECTOR = cable("cable_detector", CableType.DETECTOR, () -> Blocks.IRON_BARS, 5.0, 11.0);
    public static final RegistryObject<CableBlock> CABLE_SPLITTER = cable("cable_splitter", CableType.SPLITTER, () -> Blocks.IRON_BARS, 5.0, 11.0);

    // ── seringueira ───────────────────────────────────────────────────────
    public static final RegistryObject<RubberLogDropBlock> RUBBER_LOG = BLOCKS.register("rubber_log",
            () -> new RubberLogDropBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("rubber_log"))
                    .sound(SoundType.WOOD)
                    .strength(2.0f, 2.0f)));

    public static final RegistryObject<RubberLeavesBlock> RUBBER_LEAVES = BLOCKS.register("rubber_leaves",
            () -> new RubberLeavesBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("rubber_leaves"))
                    .sound(SoundType.GRASS)
                    .strength(0.2f, 0.2f)
                    .noOcclusion()));

    public static final RegistryObject<RubberSaplingBlock> RUBBER_SAPLING = BLOCKS.register("rubber_sapling",
            () -> new RubberSaplingBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("rubber_sapling"))
                    .sound(SoundType.GRASS)
                    .strength(0.0f)
                    .noOcclusion()));

    public static final RegistryObject<Block> RUBBER_WOOD_PLANKS = BLOCKS.register("rubber_wood_planks",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.OAK_PLANKS)
                    .setId(BLOCKS.key("rubber_wood_planks"))
                    .strength(2.0f, 3.0f)));

    // ── estanho ───────────────────────────────────────────────────────────
    public static final RegistryObject<OreBlock> TIN_ORE = BLOCKS.register("tin_ore",
            () -> new OreBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("tin_ore"))
                    .sound(SoundType.STONE)
                    .strength(3.0f, 3.0f)
                    .requiresCorrectToolForDrops(),
                    () -> CEItems.RAW_TIN.get()));

    public static final RegistryObject<OreBlock> DEEPSLATE_TIN_ORE = BLOCKS.register("deepslate_tin_ore",
            () -> new OreBlock(BlockBehaviour.Properties.of()
                    .setId(BLOCKS.key("deepslate_tin_ore"))
                    .sound(SoundType.DEEPSLATE)
                    .strength(4.5f, 3.0f)
                    .requiresCorrectToolForDrops(),
                    () -> CEItems.RAW_TIN.get()));

    public static final RegistryObject<Block> TIN_BLOCK = BLOCKS.register("tin_block",
            () -> new Block(BlockBehaviour.Properties.ofFullCopy(Blocks.IRON_BLOCK)
                    .setId(BLOCKS.key("tin_block"))
                    .strength(3.5f, 6.0f)));

    private CEBlocks() {}

    private static RegistryObject<CableBlock> cable(String name, CableType type, Supplier<Block> base, double min, double max) {
        return BLOCKS.register(name, () -> new CableBlock(BlockBehaviour.Properties.ofFullCopy(base.get())
                .setId(BLOCKS.key(name))
                .strength(0.2f, 1.0f)
                .noOcclusion(), type, min, max));
    }
}
