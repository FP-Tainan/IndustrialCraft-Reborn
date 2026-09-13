package net.ic2reborn.item;

import net.ic2reborn.block.entity.TeleporterBlockEntity;
import net.ic2reborn.registry.IC2Components;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.function.Consumer;

/**
 * Transmissor de frequência do IC2: clique num teletransportador guarda o destino; clique no outro
 * liga os dois (ida e volta). Botão direito no ar esquece o destino.
 */
public class FrequencyTransmitterItem extends Item {
    public FrequencyTransmitterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof TeleporterBlockEntity teleporter)) {
            return InteractionResult.PASS;
        }
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        ItemStack stack = context.getItemInHand();
        BlockPos here = context.getClickedPos().immutable();
        BlockPos stored = stack.get(IC2Components.TELEPORT_TARGET.get());
        if (stored == null) {
            stack.set(IC2Components.TELEPORT_TARGET.get(), here);
            message(player, "linked", "Frequency Transmitter linked to Teleporter.");
        } else if (stored.equals(here)) {
            message(player, "self", "Can't link Teleporter to itself.");
        } else if (here.equals(teleporter.getTarget())) {
            message(player, "unchanged", "Teleportation link unchanged.");
        } else if (context.getLevel().getBlockEntity(stored) instanceof TeleporterBlockEntity other) {
            teleporter.setTarget(stored);
            other.setTarget(here);
            message(player, "established", "Teleportation link established.");
        } else {
            stack.remove(IC2Components.TELEPORT_TARGET.get());
            message(player, "lost", "Stored Teleporter is gone; transmitter unlinked.");
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player instanceof ServerPlayer serverPlayer && stack.has(IC2Components.TELEPORT_TARGET.get())) {
            stack.remove(IC2Components.TELEPORT_TARGET.get());
            message(serverPlayer, "unlinked", "Frequency Transmitter unlinked");
        }
        return InteractionResult.SUCCESS;
    }

    private static void message(ServerPlayer player, String key, String fallback) {
        player.sendSystemMessage(Component.translatableWithFallback("message.ic2reborn.frequency_transmitter." + key, fallback));
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        BlockPos stored = stack.get(IC2Components.TELEPORT_TARGET.get());
        if (stored != null) {
            tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.frequency_transmitter", "Target: %s, %s, %s",
                    stored.getX(), stored.getY(), stored.getZ()).withStyle(ChatFormatting.GRAY));
        }
    }
}
