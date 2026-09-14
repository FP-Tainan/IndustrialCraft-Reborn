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

    /** Porta reforçada do IC2: abre só com redstone, como a de ferro, e aguenta explosões. */
    public static final RegistryObject<net.minecraft.world.level.block.DoorBlock> REINFORCED_DOOR =
            BLOCKS.register("reinforced_door", () -> new net.minecraft.world.level.block.DoorBlock(
                    net.minecraft.world.level.block.state.properties.BlockSetType.IRON,
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("reinforced_door"))
                            .sound(SoundType.METAL)
                            .strength(20.0f, 150.0f)
                            .requiresCorrectToolForDrops()
                            .noOcclusion()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));
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

    /** Plantação do IC2: varetas em terra arada (colocadas pela vareta de cultivo, sem item próprio). */
    public static final RegistryObject<net.ic2reborn.crop.CropBlock> CROP =
            BLOCKS.register("crop", () -> new net.ic2reborn.crop.CropBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("crop"))
                            .sound(SoundType.CROP)
                            .strength(0.8f)
                            .noCollision()
                            .noOcclusion()
                            .lightLevel(net.ic2reborn.crop.CropBlock::lightLevel)
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

    /** Dinamite colocada (vinculável ao controle remoto). */
    public static final RegistryObject<net.ic2reborn.block.DynamiteBlock> DYNAMITE =
            BLOCKS.register("dynamite", () -> new net.ic2reborn.block.DynamiteBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("dynamite"))
                            .sound(SoundType.GRASS)
                            .instabreak()
                            .noCollision()
                            .pushReaction(net.minecraft.world.level.material.PushReaction.DESTROY)));

    /** Espuma de construção (atravessável; endurece em parede). */
    public static final RegistryObject<net.ic2reborn.block.FoamBlock> FOAM =
            BLOCKS.register("foam", () -> new net.ic2reborn.block.FoamBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("foam"))
                            .sound(SoundType.WOOL)
                            .strength(0.01f, 10.0f)
                            .noCollision()
                            .noOcclusion()
                            .randomTicks()));

    /** Parede de espuma endurecida, pintável. */
    public static final RegistryObject<net.ic2reborn.block.WallBlock> WALL =
            BLOCKS.register("wall", () -> new net.ic2reborn.block.WallBlock(
                    BlockBehaviour.Properties.of()
                            .setId(BLOCKS.key("wall"))
                            .sound(SoundType.STONE)
                            .strength(3.0f, 30.0f)
                            .requiresCorrectToolForDrops()));
}
