package net.ic2reborn.registry;

import net.ic2reborn.IC2Reborn;
import net.ic2reborn.block.*;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class IC2Blocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(ForgeRegistries.BLOCKS, IC2Reborn.MODID);

    public static final RegistryObject<RubberLogDropBlock> RUBBER_LOG =
            BLOCKS.register("rubber_log", () -> new RubberLogDropBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("rubber_log"))
                            .sound(SoundType.WOOD)
                            .strength(2.0f, 2.0f)));

    public static final RegistryObject<RubberLeavesBlock> RUBBER_LEAVES =
            BLOCKS.register("rubber_leaves", () -> new RubberLeavesBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("rubber_leaves"))
                            .sound(SoundType.GRASS)
                            .strength(0.2f, 0.2f)
                            .noOcclusion()));

    public static final RegistryObject<RubberSaplingBlock> RUBBER_SAPLING =
            BLOCKS.register("rubber_sapling", () -> new RubberSaplingBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("rubber_sapling"))
                            .sound(SoundType.GRASS)
                            .strength(0.0f)
                            .noOcclusion()));

    // iron ore = 3.0f hardness - mesma dureza
    public static final RegistryObject<OreBlock> TIN_ORE =
            BLOCKS.register("tin_ore", () -> new OreBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("tin_ore"))
                            .sound(SoundType.STONE)
                            .strength(3.0f, 3.0f)
                            .requiresCorrectToolForDrops(),
                    () -> IC2Items.RAW_TIN.get()));

    public static final RegistryObject<OreBlock> DEEPSLATE_TIN_ORE =
            BLOCKS.register("deepslate_tin_ore", () -> new OreBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("deepslate_tin_ore"))
                            .sound(SoundType.DEEPSLATE)
                            .strength(4.5f, 3.0f)
                            .requiresCorrectToolForDrops(),
                    () -> IC2Items.RAW_TIN.get()));

    public static final RegistryObject<OreBlock> LEAD_ORE =
            BLOCKS.register("lead_ore", () -> new OreBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("lead_ore"))
                            .sound(SoundType.STONE)
                            .strength(3.0f, 3.0f)
                            .requiresCorrectToolForDrops(),
                    () -> IC2Items.RAW_LEAD.get()));

    public static final RegistryObject<OreBlock> DEEPSLATE_LEAD_ORE =
            BLOCKS.register("deepslate_lead_ore", () -> new OreBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("deepslate_lead_ore"))
                            .sound(SoundType.DEEPSLATE)
                            .strength(4.5f, 3.0f)
                            .requiresCorrectToolForDrops(),
                    () -> IC2Items.RAW_LEAD.get()));

    public static final RegistryObject<OreBlock> URANIUM_ORE =
            BLOCKS.register("uranium_ore", () -> new OreBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("uranium_ore"))
                            .sound(SoundType.STONE)
                            .strength(3.0f, 3.0f)
                            .requiresCorrectToolForDrops(),
                    () -> IC2Items.RAW_URANIUM.get()));

    public static final RegistryObject<OreBlock> DEEPSLATE_URANIUM_ORE =
            BLOCKS.register("deepslate_uranium_ore", () -> new OreBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("deepslate_uranium_ore"))
                            .sound(SoundType.DEEPSLATE)
                            .strength(4.5f, 3.0f)
                            .requiresCorrectToolForDrops(),
                    () -> IC2Items.RAW_URANIUM.get()));
}
