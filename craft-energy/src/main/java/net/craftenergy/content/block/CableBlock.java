package net.craftenergy.content.block;

import net.craftenergy.fabric.CraftEnergyApi;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;

/**
 * Cabo de energia do Craft Energy.
 *
 * <p>Na rede elétrica ele é o {@link CableType} dele (registrado no lookup em
 * {@code CraftEnergyContent}). Visualmente conecta em outros cabos, em qualquer bloco que
 * seja nó de energia naquela face, e em blocos da tag {@code craftenergy:connects_to_cables}.
 */
public class CableBlock extends Block {
    /** Blocos que os cabos desenham conexão mesmo sem ainda serem nós de energia. */
    public static final TagKey<Block> CONNECTS_TO_CABLES = TagKey.create(Registries.BLOCK,
            Identifier.fromNamespaceAndPath(CraftEnergyApi.MOD_ID, "connects_to_cables"));

    public static final BooleanProperty NORTH = BlockStateProperties.NORTH;
    public static final BooleanProperty EAST = BlockStateProperties.EAST;
    public static final BooleanProperty SOUTH = BlockStateProperties.SOUTH;
    public static final BooleanProperty WEST = BlockStateProperties.WEST;
    public static final BooleanProperty UP = BlockStateProperties.UP;
    public static final BooleanProperty DOWN = BlockStateProperties.DOWN;

    private final CableType type;
    private final VoxelShape core;
    private final VoxelShape northShape;
    private final VoxelShape eastShape;
    private final VoxelShape southShape;
    private final VoxelShape westShape;
    private final VoxelShape upShape;
    private final VoxelShape downShape;

    public CableBlock(Properties properties, CableType type, double min, double max) {
        super(properties);
        this.type = type;
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

    public CableType type() {
        return this.type;
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        BlockState state = this.defaultBlockState();
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        for (Direction direction : Direction.values()) {
            BlockPos neighbourPos = pos.relative(direction);
            state = state.setValue(propertyFor(direction),
                    canConnectTo(level, neighbourPos, level.getBlockState(neighbourPos), direction));
        }
        return state;
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction directionToNeighbour, BlockPos neighbourPos, BlockState neighbourState,
                                     RandomSource random) {
        return state.setValue(propertyFor(directionToNeighbour),
                canConnectTo(level, neighbourPos, neighbourState, directionToNeighbour));
    }

    @Override
    protected void onPlace(BlockState state, Level level, BlockPos pos, BlockState oldState, boolean movedByPiston) {
        super.onPlace(state, level, pos, oldState, movedByPiston);
        if (!oldState.is(state.getBlock())) {
            CraftEnergyApi.markChanged(level, pos);
        }
    }

    @Override
    protected void affectNeighborsAfterRemoval(BlockState state, ServerLevel level, BlockPos pos, boolean movedByPiston) {
        super.affectNeighborsAfterRemoval(state, level, pos, movedByPiston);
        CraftEnergyApi.markChanged(level, pos);
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

    private static boolean canConnectTo(LevelReader level, BlockPos neighbourPos, BlockState neighbourState, Direction direction) {
        if (neighbourState.getBlock() instanceof CableBlock) return true;
        if (neighbourState.is(CONNECTS_TO_CABLES)) return true;
        // durante a geração de mundo o LevelReader não é um Level; aí só vale o que já foi verificado acima
        return level instanceof Level realLevel
                && CraftEnergyApi.NODE.find(realLevel, neighbourPos, direction.getOpposite()) != null;
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
