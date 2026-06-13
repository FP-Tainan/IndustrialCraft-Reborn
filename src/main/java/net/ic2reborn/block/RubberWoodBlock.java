package net.ic2reborn.block;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.util.StringRepresentable;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.RotatedPillarBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.BlockStateProperties;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;

/**
 * Tronco de borracha — estados: DRY, WET (tem resina), TAPPED (já coletado).
 * A regeneração de resina acontece via randomTick, sem precisar de event handler externo.
 */
public class RubberWoodBlock extends RotatedPillarBlock {

    public enum WoodType implements StringRepresentable {
        DRY("dry"), WET("wet"), TAPPED("tapped");

        private final String name;
        WoodType(String name) { this.name = name; }

        @Override
        public String getSerializedName() { return name; }
    }

    public static final EnumProperty<WoodType> WOOD_TYPE =
            EnumProperty.create("wood_type", WoodType.class);

    public static final Property<Direction> FACING = BlockStateProperties.HORIZONTAL_FACING;

    public RubberWoodBlock(Properties props) {
        // randomTicks enabled so TAPPED blocks can regenerate
        super(props.randomTicks());
        registerDefaultState(this.stateDefinition.any()
                .setValue(AXIS, Direction.Axis.Y)
                .setValue(WOOD_TYPE, WoodType.DRY)
                .setValue(FACING, Direction.NORTH));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        super.createBlockStateDefinition(builder);
        builder.add(WOOD_TYPE, FACING);
    }

    @Override
    public void randomTick(BlockState state, ServerLevel level, BlockPos pos, RandomSource random) {
        // Only TAPPED blocks regenerate — ~1/30 chance per random tick (~5 min average)
        if (state.getValue(WOOD_TYPE) == WoodType.TAPPED) {
            if (random.nextInt(30) == 0) {
                level.setBlock(pos, state.setValue(WOOD_TYPE, WoodType.WET), 3);
            }
        }
    }

    public boolean hasResin(BlockState state) {
        return state.getValue(WOOD_TYPE) == WoodType.WET;
    }

    public boolean isTapped(BlockState state) {
        return state.getValue(WOOD_TYPE) == WoodType.TAPPED;
    }
}
