package net.ic2reborn.registry;

import net.minecraft.core.registries.Registries;
import net.ic2reborn.IC2Reborn;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntityType;

import java.util.Set;

/** Block entities used by IC2 Reborn machines. */
public class IC2BlockEntities {
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITY_TYPES =
            DeferredRegister.create(Registries.BLOCK_ENTITY_TYPE, IC2Reborn.MODID);

    public static final RegistryObject<BlockEntityType<MachineBlockEntity>> MACHINE =
            BLOCK_ENTITY_TYPES.register("machine", () ->
                    new BlockEntityType<>(MachineBlockEntity::new, Set.of(autoBlocks())));

    private static Block[] autoBlocks() {
        return IC2AutoBlocks.BLOCKS.getEntries().stream()
                .map(RegistryObject::get)
                .toArray(Block[]::new);
    }
}
