package net.ic2reborn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import net.minecraft.world.level.ScheduledTickAccess;

/**
 * IC2 cable block foundation.
 *
 * For now this block handles placement, selection shape and visual connection states.
 * The Forge Energy implementation can later be attached through a BlockEntity that
 * exposes ForgeCapabilities.ENERGY / IEnergyStorage.
 */
public class CableBlock extends Block {

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private final VoxelShape core;
    private final VoxelShape northShape;
    private final VoxelShape eastShape;
    private final VoxelShape southShape;
    private final VoxelShape westShape;
    private final VoxelShape upShape;
    private final VoxelShape downShape;

    public CableBlock(Properties properties) {
        this(properties, 5.0D, 11.0D);
    }

    public CableBlock(Properties properties, double min, double max) {
        super(properties);
        this.core = Block.box(min, min, min, max, max, max);
        this.northShape = Block.box(min, min, 0.0D, max, max, min);
        this.eastShape = Block.box(max, min, min, 16.0D, max, max);
        this.southShape = Block.box(min, min, max, max, max, 16.0D);
        this.westShape = Block.box(0.0D, min, min, min, max, max);
        this.upShape = Block.box(min, max, min, max, 16.0D, max);
        this.downShape = Block.box(min, 0.0D, min, max, min, max);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(NORTH, false)
                .setValue(EAST, false)
                .setValue(SOUTH, false)
                .setValue(WEST, false)
                .setValue(UP, false)
                .setValue(DOWN, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return updateConnections(this.defaultBlockState(), context.getLevel(), context.getClickedPos());
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        return state.setValue(propertyFor(directionToNeighbour), canConnectTo(neighbourState));
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return shapeFor(state);
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(NORTH, EAST, SOUTH, WEST, UP, DOWN);
    }

    private static BlockState updateConnections(BlockState state, LevelReader level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            state = state.setValue(propertyFor(direction), canConnectTo(level.getBlockState(pos.relative(direction))));
        }
        return state;
    }

    private static boolean canConnectTo(BlockState neighbourState) {
        return neighbourState.getBlock() instanceof CableBlock
                || neighbourState.getBlock() instanceof MachineBlock;
    }

    private static BooleanProperty propertyFor(Direction direction) {
        return switch (direction) {
            case NORTH -> NORTH;
            case EAST -> EAST;
            case SOUTH -> SOUTH;
            case WEST -> WEST;
            case UP -> UP;
            case DOWN -> DOWN;
        };
    }

    private VoxelShape shapeFor(BlockState state) {
        VoxelShape shape = this.core;

        if (state.getValue(NORTH)) shape = Shapes.or(shape, this.northShape);
        if (state.getValue(EAST)) shape = Shapes.or(shape, this.eastShape);
        if (state.getValue(SOUTH)) shape = Shapes.or(shape, this.southShape);
        if (state.getValue(WEST)) shape = Shapes.or(shape, this.westShape);
        if (state.getValue(UP)) shape = Shapes.or(shape, this.upShape);
        if (state.getValue(DOWN)) shape = Shapes.or(shape, this.downShape);

        return shape;
    }
}
