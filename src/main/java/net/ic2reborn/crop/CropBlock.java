package net.ic2reborn.crop;

import net.ic2reborn.registry.IC2BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LevelReader;
import net.minecraft.world.level.ScheduledTickAccess;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.IntegerProperty;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

/**
 * Bloco de plantação do IC2: varetas em cima de terra arada. O estado guarda só o que aparece
 * (planta, tamanho, vareta dupla); o resto fica no {@link CropBlockEntity}.
 */
public class CropBlock extends Block implements EntityBlock {
    public static final EnumProperty<CropKind> CROP = EnumProperty.create("crop", CropKind.class);
    public static final IntegerProperty SIZE = IntegerProperty.create("size", 1, 7);
    public static final BooleanProperty CROSSING = BooleanProperty.create("crossing");
    private static final VoxelShape SHAPE = Block.box(3.2, 0.0, 3.2, 12.8, 13.6, 12.8);

    public CropBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(CROP, CropKind.NONE).setValue(SIZE, 1).setValue(CROSSING, false));
    }

    /** Luz emitida pela planta (o trigo vermelho maduro brilha). */
    public static int lightLevel(BlockState state) {
        CropCard card = CropCards.byKind(state.getValue(CROP));
        return card == null ? 0 : card.emittedLight(state.getValue(SIZE));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(CROP, SIZE, CROSSING);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new CropBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != IC2BlockEntities.CROP.get()) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<CropBlockEntity>) CropBlockEntity::serverTick;
        return ticker;
    }

    @Override
    protected VoxelShape getShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return SHAPE;
    }

    @Override
    protected VoxelShape getCollisionShape(BlockState state, BlockGetter level, BlockPos pos, CollisionContext context) {
        return Shapes.empty();
    }

    @Override
    protected boolean canSurvive(BlockState state, LevelReader level, BlockPos pos) {
        return level.getBlockState(pos.below()).is(Blocks.FARMLAND);
    }

    @Override
    protected BlockState updateShape(BlockState state, LevelReader level, ScheduledTickAccess ticks, BlockPos pos,
                                     Direction direction, BlockPos neighbourPos, BlockState neighbourState, RandomSource random) {
        return direction == Direction.DOWN && !canSurvive(state, level, pos) ? Blocks.AIR.defaultBlockState() : state;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        // o Cropnalyzer lê a plantação (Item.useOn) em vez de colher
        if (stack.getItem() instanceof net.ic2reborn.item.CropnalyzerItem) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        if (level.getBlockEntity(pos) instanceof CropBlockEntity crop && crop.useItem(player, hand)) {
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.TRY_WITH_EMPTY_HAND;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return level.getBlockEntity(pos) instanceof CropBlockEntity crop && crop.harvestClick(player)
                ? InteractionResult.SUCCESS : InteractionResult.PASS;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && level.getBlockEntity(pos) instanceof CropBlockEntity crop) {
            crop.leftClick(player);
        }
    }

    @Override
    protected boolean isSignalSource(BlockState state) {
        return state.getValue(CROP) == CropKind.REDWHEAT;
    }

    @Override
    protected int getSignal(BlockState state, BlockGetter level, BlockPos pos, Direction direction) {
        CropCard card = CropCards.byKind(state.getValue(CROP));
        return card == null ? 0 : card.redstoneSignal(state.getValue(SIZE));
    }
}
