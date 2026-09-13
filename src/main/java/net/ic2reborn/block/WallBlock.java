package net.ic2reborn.block;

import net.minecraft.world.item.DyeColor;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.StateDefinition;
import net.minecraft.world.level.block.state.properties.EnumProperty;

/** Parede de espuma endurecida do IC2 ({@code BlockWall}): nasce cinza-clara e o pintor muda a cor. */
public class WallBlock extends Block {
    public static final EnumProperty<DyeColor> COLOR = EnumProperty.create("color", DyeColor.class);

    public WallBlock(Properties properties) {
        super(properties);
        registerDefaultState(this.stateDefinition.any().setValue(COLOR, DyeColor.LIGHT_GRAY));
    }

    @Override
    protected void createBlockStateDefinition(StateDefinition.Builder<Block, BlockState> builder) {
        builder.add(COLOR);
    }
}
