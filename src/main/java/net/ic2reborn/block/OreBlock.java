package net.ic2reborn.block;

import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.loot.LootParams;

import java.util.List;
import java.util.function.Supplier;

public class OreBlock extends Block {

    private final Supplier<Item> drop;

    public OreBlock(Properties props, Supplier<Item> drop) {
        super(props);
        this.drop = drop;
    }

    @Override
    public List<ItemStack> getDrops(BlockState state, LootParams.Builder params) {
        return List.of(new ItemStack(drop.get()));
    }
}
