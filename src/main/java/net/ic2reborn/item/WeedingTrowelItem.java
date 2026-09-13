package net.ic2reborn.item;

import net.ic2reborn.crop.CropBlockEntity;
import net.ic2reborn.crop.CropCards;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.block.Block;

/** Espátula de capina do IC2: tira a erva daninha da vareta (e dá a erva, conforme o tamanho). */
public class WeedingTrowelItem extends Item {
    public WeedingTrowelItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof CropBlockEntity crop)
                || crop.getCrop() != CropCards.WEED) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        Block.popResource(context.getLevel(), context.getClickedPos(), new ItemStack(IC2Items.WEED.get(), crop.getSize()));
        crop.reset();
        crop.setSize(1);
        return InteractionResult.SUCCESS;
    }
}
