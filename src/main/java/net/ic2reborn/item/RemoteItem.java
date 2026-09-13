package net.ic2reborn.item;

import net.ic2reborn.block.DynamiteBlock;
import net.ic2reborn.registry.IC2Components;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Controle remoto do IC2 ({@code ItemRemote}): clicado numa dinamite colocada vincula/desvincula;
 * no ar detona todas as dinamites vinculadas.
 */
public class RemoteItem extends Item {
    public RemoteItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static List<BlockPos> links(ItemStack stack) {
        return stack.getOrDefault(IC2Components.REMOTE_LINKS.get(), List.of());
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof DynamiteBlock)) return InteractionResult.PASS;
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        List<BlockPos> links = new ArrayList<>(links(stack));
        if (!state.getValue(DynamiteBlock.LINKED)) {
            links.add(pos.immutable());
            level.setBlock(pos, state.setValue(DynamiteBlock.LINKED, true), Block.UPDATE_ALL);
        } else if (links.remove(pos)) {
            level.setBlock(pos, state.setValue(DynamiteBlock.LINKED, false), Block.UPDATE_ALL);
        } else if (context.getPlayer() instanceof ServerPlayer player) {
            player.sendSystemMessage(Component.translatableWithFallback("message.ic2reborn.remote.not_linked",
                    "This dynamite stick is not linked to this remote, cannot unlink."));
        }
        stack.set(IC2Components.REMOTE_LINKS.get(), List.copyOf(links));
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide()) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        List<BlockPos> remaining = new ArrayList<>();
        for (BlockPos pos : links(stack)) {
            if (!level.isLoaded(pos)) {
                remaining.add(pos);
                continue;
            }
            BlockState state = level.getBlockState(pos);
            if (state.getBlock() instanceof DynamiteBlock && state.getValue(DynamiteBlock.LINKED)) {
                DynamiteBlock.ignite(level, pos, 40);
            }
        }
        stack.set(IC2Components.REMOTE_LINKS.get(), List.copyOf(remaining));
        level.playSound(null, player.blockPosition(), SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.8F, 1.5F);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        int count = links(stack).size();
        if (count > 0) {
            tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.remote", "Linked to %s dynamite", count)
                    .withStyle(ChatFormatting.GRAY));
        }
    }
}
