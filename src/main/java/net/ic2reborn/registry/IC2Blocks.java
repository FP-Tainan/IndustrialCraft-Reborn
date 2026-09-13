package net.ic2reborn.registry;

import net.craftenergy.content.block.OreBlock;
import net.craftenergy.registry.DeferredRegister;
import net.craftenergy.registry.RegistryObject;
import net.ic2reborn.IC2Reborn;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.state.BlockBehaviour;

/** Minérios exclusivos do IC2 Reborn. Estanho, seringueira e cabos ficam no Craft Energy. */
public class IC2Blocks {

    public static final DeferredRegister<Block> BLOCKS =
            DeferredRegister.create(Registries.BLOCK, IC2Reborn.MODID);

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
