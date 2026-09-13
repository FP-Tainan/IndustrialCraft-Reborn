package net.ic2reborn.block;

import net.ic2reborn.block.entity.MachineBlockEntity;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.HorizontalDirectionalBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BooleanProperty;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.phys.BlockHitResult;


/**
 * Bloco de máquina orientável.
 *
 * Nesta fase ele também cria um BlockEntity genérico e abre uma GUI básica,
 * preparando as máquinas para inventário, progresso e energia nas próximas etapas.
 */
public class MachineBlock extends Block implements EntityBlock {

    public static final EnumProperty<Direction> FACING = HorizontalDirectionalBlock.FACING;
    /** Máquina trabalhando: troca para as texturas "_active" do IC2. */
    public static final BooleanProperty ACTIVE = BooleanProperty.create("active");

    public MachineBlock(Properties props) {
        super(props);
        this.registerDefaultState(this.stateDefinition.any()
                .setValue(FACING, Direction.NORTH)
                .setValue(ACTIVE, false));
    }

    @Override
    public BlockState getStateForPlacement(BlockPlaceContext ctx) {
        return this.defaultBlockState()
                .setValue(FACING, ctx.getHorizontalDirection().getOpposite());
    }

    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new MachineBlockEntity(pos, state);
    }

    @Override
    public MenuProvider getMenuProvider(BlockState state, Level level, BlockPos pos) {
        BlockEntity blockEntity = level.getBlockEntity(pos);
        return blockEntity instanceof MenuProvider provider ? provider : null;
    }

    @Override
    protected InteractionResult useWithoutItem(BlockState state, Level level, BlockPos pos, Player player, BlockHitResult hit) {
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider != null) {
                serverPlayer.openMenu(provider);
            }
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    protected InteractionResult useItemOn(ItemStack stack, BlockState state, Level level, BlockPos pos,
                                             Player player, InteractionHand hand, BlockHitResult hit) {
        if (stack.getItem() instanceof net.ic2reborn.item.MeterItem || stack.getItem() instanceof net.ic2reborn.item.WindMeterItem) return InteractionResult.PASS;
        // com a chave inglesa na mão, quem age é o item (girar/desmontar), não a GUI
        if (stack.getItem() instanceof net.ic2reborn.item.WrenchItem) {
            return InteractionResult.PASS;
        }
        if (!level.isClientSide() && player instanceof ServerPlayer serverPlayer) {
            MenuProvider provider = state.getMenuProvider(level, pos);
            if (provider != null) {
                serverPlayer.openMenu(provider);
            }
        }
        return InteractionResult.SUCCESS;
    }

    /** Só máquinas que já participam da rede elétrica precisam de tick no servidor. */
    @Override
    public <T extends BlockEntity> net.minecraft.world.level.block.entity.BlockEntityTicker<T> getTicker(
            Level level, BlockState state, net.minecraft.world.level.block.entity.BlockEntityType<T> type) {
        if (level.isClientSide() || type != net.ic2reborn.registry.IC2BlockEntities.MACHINE.get()) return null;

        String blockId = net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath();
        net.ic2reborn.energy.MachineEnergyProfile profile = net.ic2reborn.energy.MachineEnergyProfile.of(
                net.ic2reborn.menu.MachineGuiType.fromBlockId(blockId));
        if (!profile.ticks()) return null;

        @SuppressWarnings("unchecked")
        net.minecraft.world.level.block.entity.BlockEntityTicker<T> ticker =
                (net.minecraft.world.level.block.entity.BlockEntityTicker<T>)
                        (net.minecraft.world.level.block.entity.BlockEntityTicker<MachineBlockEntity>) MachineBlockEntity::serverTick;
        return ticker;
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(FACING, ACTIVE);
    }
}
