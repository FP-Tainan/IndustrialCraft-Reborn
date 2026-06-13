package net.ic2reborn.block;

import net.ic2reborn.world.RubberTreeGrower;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.SaplingBlock;
import net.minecraft.world.level.block.state.BlockState;

public class RubberSaplingBlock extends SaplingBlock {

    public RubberSaplingBlock(Properties props) {
        super(RubberTreeGrower.INSTANCE, props);
    }

    /**
     * Sobrescreve o crescimento da muda para gerar uma seringueira customizada.
     * Usa WorldGenLevel diretamente para colocar troncos e folhas,
     * sem depender de ResourceLocation ou TreeGrower.
     */
    @Override
    public void advanceTree(ServerLevel level, BlockPos pos, BlockState state, RandomSource random) {
        // Remove a muda
        level.removeBlock(pos, false);

        // Altura do tronco: 5-8 blocos
        int height = 5 + random.nextInt(4);

        // Gera o tronco
        for (int i = 0; i < height; i++) {
            BlockPos trunkPos = pos.above(i);
            if (level.isEmptyBlock(trunkPos) || level.getBlockState(trunkPos).canBeReplaced()) {
                // A cada bloco de tronco, pequena chance de ser WET (tem resina)
                RubberWoodBlock.WoodType type = (i > 1 && i < height - 1 && random.nextFloat() < 0.3f)
                        ? RubberWoodBlock.WoodType.WET
                        : RubberWoodBlock.WoodType.DRY;

                net.minecraft.core.Direction facing = net.minecraft.core.Direction.from2DDataValue(random.nextInt(4));

                level.setBlock(trunkPos,
                        net.ic2reborn.registry.IC2Blocks.RUBBER_LOG.get().defaultBlockState()
                                .setValue(RubberWoodBlock.WOOD_TYPE, type)
                                .setValue(RubberWoodBlock.FACING, facing)
                                .setValue(RubberWoodBlock.AXIS, net.minecraft.core.Direction.Axis.Y),
                        3);
            }
        }

        // Gera a copa de folhas (blob ao redor do topo do tronco)
        BlockPos top = pos.above(height - 1);
        BlockState leavesState = net.ic2reborn.registry.IC2Blocks.RUBBER_LEAVES.get().defaultBlockState();

        for (int dx = -2; dx <= 2; dx++) {
            for (int dz = -2; dz <= 2; dz++) {
                for (int dy = -1; dy <= 2; dy++) {
                    // Corta os cantos para dar forma arredondada
                    if (Math.abs(dx) == 2 && Math.abs(dz) == 2) continue;
                    if (dy == 2 && (Math.abs(dx) > 1 || Math.abs(dz) > 1)) continue;

                    BlockPos leafPos = top.offset(dx, dy, dz);
                    BlockState existing = level.getBlockState(leafPos);
                    if (existing.isAir() || existing.canBeReplaced()) {
                        level.setBlock(leafPos, leavesState, 3);
                    }
                }
            }
        }
    }
}
