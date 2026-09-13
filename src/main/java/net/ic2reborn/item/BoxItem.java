package net.ic2reborn.item;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.ic2reborn.menu.BoxMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/** Caixa de ferramentas e caixa de contenção do IC2: botão direito abre o inventário da caixa. */
public class BoxItem extends Item {
    private final BoxMenu.Kind kind;

    public BoxItem(Properties properties, BoxMenu.Kind kind) {
        super(properties.stacksTo(1));
        this.kind = kind;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack stack = player.getItemInHand(hand);
            BoxMenu.OpenData data = new BoxMenu.OpenData(this.kind, hand);
            serverPlayer.openMenu(new ExtendedMenuProvider<BoxMenu.OpenData>() {
                @Override
                public BoxMenu.OpenData getScreenOpeningData(ServerPlayer opener) {
                    return data;
                }

                @Override
                public Component getDisplayName() {
                    return stack.getHoverName();
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                    return new BoxMenu(containerId, inventory, data);
                }
            });
        }
        return InteractionResult.SUCCESS;
    }
}
