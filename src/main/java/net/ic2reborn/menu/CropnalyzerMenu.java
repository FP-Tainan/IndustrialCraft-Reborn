package net.ic2reborn.menu;

import io.netty.buffer.ByteBuf;
import net.craftenergy.content.item.EnergyItem;
import net.craftenergy.content.item.EnergyItems;
import net.ic2reborn.item.CropSeedItem;
import net.ic2reborn.item.CropnalyzerItem;
import net.ic2reborn.registry.IC2Components;
import net.ic2reborn.registry.IC2Menus;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.Container;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Tela do Cropnalyzer ({@code ContainerCropnalyzer}/{@code HandHeldCropnalyzer}): o saco de
 * sementes entra à esquerda, é analisado um nível por vez gastando energia do Cropnalyzer e sai à
 * direita; uma bateria no slot da direita recarrega o aparelho. Fechando a tela os itens voltam.
 */
public class CropnalyzerMenu extends AbstractContainerMenu {
    public static final int INPUT = 0;
    public static final int OUTPUT = 1;
    public static final int BATTERY = 2;
    private static final int SLOTS = 3;

    public static final StreamCodec<ByteBuf, InteractionHand> HAND_CODEC =
            ByteBufCodecs.BOOL.map(off -> off ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, hand -> hand == InteractionHand.OFF_HAND);

    private final Container container = new SimpleContainer(SLOTS);
    private final InteractionHand hand;
    private final Player player;

    public CropnalyzerMenu(int containerId, Inventory inventory, InteractionHand hand) {
        super(IC2Menus.CROPNALYZER.get(), containerId);
        this.hand = hand;
        this.player = inventory.player;

        addSlot(new Slot(this.container, INPUT, 8, 7) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof CropSeedItem;
            }
        });
        addSlot(new Slot(this.container, OUTPUT, 41, 7) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return false;
            }
        });
        addSlot(new Slot(this.container, BATTERY, 152, 7) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return stack.getItem() instanceof EnergyItem item && item.canDischarge(stack);
            }
        });

        // IC2: inventário do jogador a partir de 223 − 82
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 141 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 199) {
                // o próprio Cropnalyzer não sai do lugar enquanto a tela está aberta
                @Override
                public boolean mayPickup(Player player) {
                    return !(this.getItem().getItem() instanceof CropnalyzerItem) || this.getItem() != player.getItemInHand(CropnalyzerMenu.this.hand);
                }
            });
        }
    }

    public ItemStack analyzer() {
        return this.player.getItemInHand(this.hand);
    }

    /** Saco já analisado (slot da direita), para a tela mostrar os dados. */
    public ItemStack scannedSeed() {
        return this.container.getItem(OUTPUT);
    }

    @Override
    public void broadcastChanges() {
        if (!this.player.level().isClientSide()) tickScan();
        super.broadcastChanges();
    }

    /** Um tick do aparelho: recarrega pela bateria e analisa o saco da entrada (IC2: onTick). */
    public void tickScan() {
        ItemStack analyzer = analyzer();
        if (!(analyzer.getItem() instanceof CropnalyzerItem)) return;

        ItemStack battery = this.container.getItem(BATTERY);
        if (!battery.isEmpty()) {
            long need = EnergyItems.charge(analyzer, Long.MAX_VALUE, Integer.MAX_VALUE, true);
            if (need > 0) {
                long got = EnergyItems.discharge(battery, need, Integer.MAX_VALUE, false);
                if (got > 0) {
                    EnergyItems.charge(analyzer, got, Integer.MAX_VALUE, false);
                    this.container.setItem(BATTERY, battery);
                }
            }
        }

        ItemStack input = this.container.getItem(INPUT);
        if (!this.container.getItem(OUTPUT).isEmpty() || input.isEmpty()) return;
        CropSeedItem.CropSeed seed = CropSeedItem.data(input);
        if (seed == null) return;
        if (seed.scan() < 4) {
            if (!EnergyItems.use(analyzer, CropnalyzerItem.energyForLevel(seed.scan()))) return;
            input.set(IC2Components.CROP_SEED.get(),
                    new CropSeedItem.CropSeed(seed.crop(), seed.growth(), seed.gain(), seed.resistance(), seed.scan() + 1));
        }
        this.container.setItem(OUTPUT, input);
        this.container.setItem(INPUT, ItemStack.EMPTY);
    }

    @Override
    public boolean stillValid(Player player) {
        return player.getItemInHand(this.hand).getItem() instanceof CropnalyzerItem;
    }

    @Override
    public void removed(Player player) {
        super.removed(player);
        if (!player.level().isClientSide()) {
            clearContainer(player, this.container);
        }
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (slot == null || !slot.hasItem() || !slot.mayPickup(player)) return ItemStack.EMPTY;
        ItemStack raw = slot.getItem();
        ItemStack copy = raw.copy();
        if (index < SLOTS) {
            if (!moveItemStackTo(raw, SLOTS, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (raw.getItem() instanceof CropSeedItem) {
            if (!moveItemStackTo(raw, INPUT, INPUT + 1, false)) return ItemStack.EMPTY;
        } else if (raw.getItem() instanceof EnergyItem item && item.canDischarge(raw)) {
            if (!moveItemStackTo(raw, BATTERY, BATTERY + 1, false)) return ItemStack.EMPTY;
        } else {
            return ItemStack.EMPTY;
        }
        if (raw.isEmpty()) {
            slot.set(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return raw.getCount() == copy.getCount() ? ItemStack.EMPTY : copy;
    }
}
