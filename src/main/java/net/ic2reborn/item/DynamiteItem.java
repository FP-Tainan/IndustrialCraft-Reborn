package net.ic2reborn.item;

import net.ic2reborn.entity.DynamiteEntity;
import net.minecraft.core.Direction;
import net.minecraft.core.Position;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.ProjectileItem;
import net.minecraft.world.level.Level;

/** Dinamite e dinamite grudenta do IC2: botão direito arremessa; o dispensador também atira. */
public class DynamiteItem extends Item implements ProjectileItem {
    public DynamiteItem(Properties properties) {
        super(properties.stacksTo(16));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), SoundEvents.SNOWBALL_THROW, SoundSource.PLAYERS, 0.5F,
                0.4F / (level.getRandom().nextFloat() * 0.4F + 0.8F));
        if (!level.isClientSide()) {
            DynamiteEntity dynamite = new DynamiteEntity(level, player, stack.copyWithCount(1));
            dynamite.shootFromRotation(player, player.getXRot(), player.getYRot(), 0.0F, 1.0F, 1.0F);
            level.addFreshEntity(dynamite);
        }
        if (!player.getAbilities().instabuild) stack.shrink(1);
        return InteractionResult.SUCCESS;
    }

    @Override
    public net.minecraft.world.InteractionResult useOn(net.minecraft.world.item.context.UseOnContext context) {
        if (context.getItemInHand().is(net.ic2reborn.registry.IC2AutoItems.DYNAMITE_STICKY.get())) return net.minecraft.world.InteractionResult.PASS;
        Level level = context.getLevel();
        net.minecraft.core.BlockPos pos = context.getClickedPos().relative(context.getClickedFace());
        net.minecraft.world.level.block.state.BlockState state = net.ic2reborn.registry.IC2Blocks.DYNAMITE.get().defaultBlockState();
        if (!level.getBlockState(pos).canBeReplaced() || !state.canSurvive(level, pos)) return net.minecraft.world.InteractionResult.FAIL;
        if (!level.isClientSide()) {
            level.setBlock(pos, state, net.minecraft.world.level.block.Block.UPDATE_ALL);
            if (context.getPlayer() == null || !context.getPlayer().getAbilities().instabuild) context.getItemInHand().shrink(1);
        }
        return net.minecraft.world.InteractionResult.SUCCESS;
    }

    @Override
    public Projectile asProjectile(Level level, Position position, ItemStack stack, Direction direction) {
        return new DynamiteEntity(level, position.x(), position.y(), position.z(), stack.copyWithCount(1));
    }
}
