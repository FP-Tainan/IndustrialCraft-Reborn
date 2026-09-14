package net.ic2reborn.block;

import net.craftenergy.content.CEItems;
import net.ic2reborn.block.entity.BarrelBlockEntity;
import net.ic2reborn.item.BoozeMugItem;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2BlockEntities;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/**
 * Barril de bebida do IC2: botão direito com água, trigo, lúpulo ou cana; a torneira (extrator de
 * seiva) numa lateral abre o barril, e com ela a caneca vazia tira uma dose. Bater no barril tira a torneira.
 */
public class BarrelBlock extends Block implements EntityBlock {
    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    public static final BooleanProperty TAPPED = BooleanProperty.create("tapped");

    public BarrelBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(FACING, Direction.NORTH).setValue(TAPPED, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext context) {
        return defaultBlockState().setValue(FACING, context.getHorizontalDirection().getOpposite());
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, TAPPED);
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new BarrelBlockEntity(pos, state);
    }

    @Override
    public <T extends BlockEntity> @Nullable BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide() || type != IC2BlockEntities.BARREL.get()) return null;
        @SuppressWarnings("unchecked")
        BlockEntityTicker<T> ticker = (BlockEntityTicker<T>) (BlockEntityTicker<BarrelBlockEntity>) BarrelBlockEntity::serverTick;
        return ticker;
    }

    private static boolean isIngredient(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET) || stack.is(IC2AutoItems.WATER_CELL.get()) || stack.is(Items.WHEAT)
                || stack.is(IC2Items.HOPS.get()) || stack.is(Items.SUGAR_CANE);
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos, Player player,
                                          InteractionHand hand, BlockHitResult hit) {
        if (!(level.getBlockEntity(pos) instanceof BarrelBlockEntity barrel)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        Direction side = hit.getDirection();
        if (!state.getValue(TAPPED) && side.getAxis().isHorizontal() && stack.is(CEItems.TREETAP.get())) {
            if (!level.isClientSide()) {
                if (!player.getAbilities().instabuild) stack.shrink(1);
                level.setBlock(pos, state.setValue(TAPPED, true).setValue(FACING, side), Block.UPDATE_ALL);
            }
            return InteractionResult.SUCCESS;
        }
        if (state.getValue(TAPPED) && stack.is(IC2AutoItems.MUG_EMPTY.get())) {
            if (level.isClientSide()) return InteractionResult.SUCCESS;
            if (barrel.isEmpty()) return InteractionResult.FAIL;
            ItemStack booze = BoozeMugItem.create(barrel.calculateValue());
            barrel.drainLiquid(1);
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (!player.getInventory().add(booze)) player.drop(booze, false);
            return InteractionResult.SUCCESS;
        }
        if (!isIngredient(stack)) return InteractionResult.TRY_WITH_EMPTY_HAND;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        return barrel.addIngredient(player, stack) ? InteractionResult.SUCCESS : InteractionResult.FAIL;
    }

    @Override
    protected void attack(BlockState state, Level level, BlockPos pos, Player player) {
        if (!level.isClientSide() && state.getValue(TAPPED)) {
            level.setBlock(pos, state.setValue(TAPPED, false), Block.UPDATE_ALL);
            Block.popResource(level, pos.above(), new ItemStack(CEItems.TREETAP.get()));
        }
    }
}
