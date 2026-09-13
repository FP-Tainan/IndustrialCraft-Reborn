package net.ic2reborn.menu;

import io.netty.buffer.ByteBuf;
import net.ic2reborn.effect.RadiationHandler;
import net.ic2reborn.item.BoxItem;
import net.ic2reborn.registry.IC2Menus;
import net.minecraft.core.NonNullList;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

/**
 * Caixa de ferramentas (9 ferramentas) e caixa de contenção (12 itens radioativos) do IC2: o
 * conteúdo fica guardado no próprio item e é salvo a cada mudança.
 */
public class BoxMenu extends AbstractContainerMenu {
    public enum Kind {
        TOOL_BOX(9, "guitoolbox.png"),
        CONTAINMENT_BOX(12, "guicontainmentbox.png");

        public final int size;
        public final String texture;

        Kind(int size, String texture) {
            this.size = size;
            this.texture = texture;
        }

        /** Caixa de ferramentas: itens que não empilham (ferramentas, armaduras); contenção: radioativos. */
        public boolean accepts(ItemStack stack) {
            if (stack.isEmpty() || stack.getItem() instanceof BoxItem || stack.has(DataComponents.CONTAINER)
                    || stack.has(DataComponents.BUNDLE_CONTENTS)) {
                return false;
            }
            return this == TOOL_BOX ? stack.getMaxStackSize() == 1 : RadiationHandler.isRadioactive(stack);
        }
    }

    /** O que o cliente precisa para montar a tela. */
    public record OpenData(Kind kind, InteractionHand hand) {
        public static final StreamCodec<ByteBuf, OpenData> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.VAR_INT.map(index -> Kind.values()[index], Kind::ordinal), OpenData::kind,
                CropnalyzerMenu.HAND_CODEC, OpenData::hand,
                OpenData::new);
    }

    private final Kind kind;
    private final InteractionHand hand;
    private final Player player;
    private final SimpleContainer container;
    private boolean loading;

    public BoxMenu(int containerId, Inventory inventory, OpenData data) {
        super(IC2Menus.BOX.get(), containerId);
        this.kind = data.kind();
        this.hand = data.hand();
        this.player = inventory.player;
        this.container = new SimpleContainer(this.kind.size) {
            @Override
            public void setChanged() {
                super.setChanged();
                save();
            }
        };
        if (!this.player.level().isClientSide()) load();

        if (this.kind == Kind.TOOL_BOX) {
            for (int col = 0; col < 9; col++) {
                addSlot(boxSlot(col, 8 + col * 18, 41));
            }
        } else {
            for (int index = 0; index < 12; index++) {
                addSlot(boxSlot(index, 53 + index % 4 * 18, 19 + index / 4 * 18));
            }
        }
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 84 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 142) {
                // a própria caixa não sai do lugar enquanto está aberta
                @Override
                public boolean mayPickup(Player player) {
                    return this.getItem() != player.getItemInHand(BoxMenu.this.hand) || !(this.getItem().getItem() instanceof BoxItem);
                }
            });
        }
    }

    private Slot boxSlot(int index, int x, int y) {
        return new Slot(this.container, index, x, y) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return BoxMenu.this.kind.accepts(stack);
            }
        };
    }

    public Kind kind() {
        return this.kind;
    }

    private ItemStack box() {
        return this.player.getItemInHand(this.hand);
    }

    private void load() {
        ItemContainerContents contents = box().getOrDefault(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        NonNullList<ItemStack> items = NonNullList.withSize(this.kind.size, ItemStack.EMPTY);
        contents.copyInto(items);
        this.loading = true;
        for (int i = 0; i < items.size(); i++) {
            this.container.setItem(i, items.get(i));
        }
        this.loading = false;
    }

    private void save() {
        if (this.loading || this.player.level().isClientSide()) return;
        ItemStack box = box();
        if (!(box.getItem() instanceof BoxItem)) return;
        java.util.List<ItemStack> items = new java.util.ArrayList<>(this.kind.size);
        for (int i = 0; i < this.kind.size; i++) {
            items.add(this.container.getItem(i).copy());
        }
        box.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(items));
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(this.hand).getItem() instanceof BoxItem;
    }

    @Override
    public void removed(Player player) {
        save();
        super.removed(player);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack raw = slot.getItem();
        ItemStack copy = raw.copy();
        int size = this.kind.size;
        if (index < size) {
            if (!moveItemStackTo(raw, size, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!this.kind.accepts(raw) || !moveItemStackTo(raw, 0, size, false)) {
            return ItemStack.EMPTY;
        }
        if (raw.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        save();
        return raw.getCount() == copy.getCount() ? ItemStack.EMPTY : copy;
    }
}
