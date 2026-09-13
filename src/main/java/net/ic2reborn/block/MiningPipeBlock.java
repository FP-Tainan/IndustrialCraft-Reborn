package net.ic2reborn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/** Tubo de mineração do IC2: o cano fino que o minerador desce, e a ponta (bloco cheio) no fundo. */
public class MiningPipeBlock extends Block {
    /** Ponta do furo, onde está a broca. */
    public static final BooleanProperty TIP = BooleanProperty.create("tip");
    private static final VoxelShape PIPE_SHAPE = Block.box(6.0, 0.0, 6.0, 10.0, 16.0, 10.0);

    public MiningPipeBlock(Properties properties) {
        super(properties);
        this.registerDefaultState(this.stateDefinition.any().setValue(TIP, false));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return state.getValue(TIP) ? Shapes.block() : PIPE_SHAPE;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(TIP);
    }
}
