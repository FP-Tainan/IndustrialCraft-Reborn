package net.ic2reborn.item;

import net.craftenergy.fabric.CraftEnergyApi;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.ic2reborn.menu.MeterMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Medidor de energia do IC2 ({@code ItemToolMeter}): clicado num cabo, gerador, máquina ou
 * armazenamento abre a tela que acompanha a rede daquele bloco.
 */
public class MeterItem extends Item {
    public MeterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static boolean isEnergyBlock(Level level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (CraftEnergyApi.NODE.find(level, pos, direction) != null) return true;
        }
        return false;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        if (!(context.getPlayer() instanceof ServerPlayer player)) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        if (!isEnergyBlock(context.getLevel(), pos)) {
            player.sendSystemMessage(Component.translatableWithFallback("message.ic2reborn.meter.not_energy", "Not an energy block"));
            return InteractionResult.SUCCESS;
        }
        player.openMenu(new ExtendedMenuProvider<BlockPos>() {
            @Override
            public BlockPos getScreenOpeningData(ServerPlayer opener) {
                return pos;
            }

            @Override
            public Component getDisplayName() {
                return context.getItemInHand().getHoverName();
            }

            @Override
            public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                return new MeterMenu(containerId, inventory, pos);
            }
        });
        return InteractionResult.SUCCESS;
    }
}
