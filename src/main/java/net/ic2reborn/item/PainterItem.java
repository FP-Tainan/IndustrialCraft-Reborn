package net.ic2reborn.item;

import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.animal.sheep.Sheep;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.EnumProperty;
import net.minecraft.world.level.block.state.properties.Property;
import org.jetbrains.annotations.Nullable;

/**
 * Pintor do IC2 ({@code ItemToolPainter}): com tinta, muda a cor de paredes de espuma (e de
 * qualquer bloco com propriedade de cor) e de ovelhas. São 32 usos; depois volta a ser o pintor vazio.
 */
public class PainterItem extends Item {
    private final @Nullable DyeColor color;

    public PainterItem(Properties properties, @Nullable DyeColor color) {
        super(color == null ? properties.stacksTo(1) : properties.durability(32));
        this.color = color;
    }

    public @Nullable DyeColor color() {
        return this.color;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (this.color == null) return InteractionResult.PASS;
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        for (Property<?> property : state.getProperties()) {
            if (property.getValueClass() != DyeColor.class) continue;
            @SuppressWarnings("unchecked")
            EnumProperty<DyeColor> colorProperty = (EnumProperty<DyeColor>) property;
            if (state.getValue(colorProperty) == this.color || !colorProperty.getPossibleValues().contains(this.color)) {
                return InteractionResult.PASS;
            }
            if (!level.isClientSide()) {
                level.setBlock(pos, state.setValue(colorProperty, this.color), Block.UPDATE_ALL);
                wear(context.getPlayer(), context.getHand(), context.getItemInHand());
            }
            return InteractionResult.SUCCESS;
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResult interactLivingEntity(ItemStack stack, Player player, LivingEntity target, InteractionHand hand) {
        if (this.color == null || !(target instanceof Sheep sheep) || sheep.getColor() == this.color) return InteractionResult.PASS;
        if (!player.level().isClientSide()) {
            sheep.setColor(this.color);
            wear(player, hand, player.getItemInHand(hand));
        }
        return InteractionResult.SUCCESS;
    }

    private static void wear(@Nullable Player player, InteractionHand hand, ItemStack stack) {
        if (player == null) return;
        player.level().playSound(null, player.blockPosition(), SoundEvents.SLIME_SQUISH_SMALL, SoundSource.PLAYERS, 0.6F, 1.4F);
        if (player.getAbilities().instabuild) return;
        stack.hurtAndBreak(1, player, hand);
        if (stack.isEmpty()) player.setItemInHand(hand, new ItemStack(IC2AutoItems.PAINTER.get()));
    }
}
