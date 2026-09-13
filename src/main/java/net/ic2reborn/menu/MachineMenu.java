package net.ic2reborn.menu;

import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.menu.layout.MachineLayout;
import net.ic2reborn.registry.IC2Menus;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

/**
 * Shared machine menu.
 *
 * Every machine uses the same MenuType; the server sends the MachineGuiType when
 * the screen opens, and the slot layout comes from that type's MachineLayout.
 */
public class MachineMenu extends AbstractContainerMenu {
    private static final int PLAYER_INVENTORY_SIZE = 27;
    private static final int HOTBAR_SIZE = 9;

    private final MachineBlockEntity blockEntity;
    private final ContainerData data;
    private final MachineGuiType guiType;
    private final MachineLayout layout;
    private final int machineSlotCount;
    private final int playerInventoryStart;
    private final int playerInventoryEnd;
    private final int hotbarStart;
    private final int hotbarEnd;

    /** Client constructor, created from the data sent by the ExtendedMenuType. */
    public MachineMenu(int containerId, Inventory playerInventory, MachineGuiType guiType) {
        this(containerId, playerInventory, new SimpleContainer(guiType.layout().slotCount()),
                new SimpleContainerData(MachineBlockEntity.DATA_COUNT), null, guiType);
    }

    /** Server constructor. */
    public MachineMenu(int containerId, Inventory playerInventory, MachineBlockEntity blockEntity) {
        this(containerId, playerInventory, blockEntity.getInventory(), blockEntity.getContainerData(),
                blockEntity, blockEntity.getGuiType());
    }

    private MachineMenu(int containerId, Inventory playerInventory, Container machineInventory,
                        ContainerData data, MachineBlockEntity blockEntity, MachineGuiType guiType) {
        super(IC2Menus.MACHINE.get(), containerId);

        this.guiType = guiType;
        this.layout = guiType.layout();
        if (machineInventory.getContainerSize() < this.layout.slotCount()) {
            throw new IllegalArgumentException("Machine inventory must have at least " + this.layout.slotCount() + " slots");
        }
        checkContainerDataCount(data, MachineBlockEntity.DATA_COUNT);

        this.blockEntity = blockEntity;
        this.data = data;

        for (int index = 0; index < this.layout.slotCount(); index++) {
            addMachineSlot(machineInventory, index, this.layout.slots().get(index));
        }
        this.machineSlotCount = this.layout.slotCount();
        this.playerInventoryStart = this.machineSlotCount;
        this.playerInventoryEnd = this.playerInventoryStart + PLAYER_INVENTORY_SIZE;
        this.hotbarStart = this.playerInventoryEnd;
        this.hotbarEnd = this.hotbarStart + HOTBAR_SIZE;

        addPlayerInventory(playerInventory, this.layout.inventoryX() + 1, this.layout.inventoryY() + 1);
        this.addDataSlots(data);
    }

    private void addMachineSlot(Container inv, int index, MachineLayout.SlotDef def) {
        this.addSlot(new Slot(inv, index, def.slotX(), def.slotY()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !def.output() && inv.canPlaceItem(this.getContainerSlot(), stack);
            }
        });
    }

    private void addPlayerInventory(Inventory playerInventory, int x, int y) {
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, x + col * 18, y + row * 18));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, x + col * 18, y + 58));
        }
    }

    public MachineGuiType getGuiType() {
        return this.guiType;
    }

    public MachineLayout getLayout() {
        return this.layout;
    }

    public int getEnergy() {
        return this.data.get(MachineBlockEntity.DATA_ENERGY);
    }

    public int getMaxEnergy() {
        return this.data.get(MachineBlockEntity.DATA_MAX_ENERGY);
    }

    public int getProgress() {
        return this.data.get(MachineBlockEntity.DATA_PROGRESS);
    }

    public int getMaxProgress() {
        return this.data.get(MachineBlockEntity.DATA_MAX_PROGRESS);
    }

    public double getEnergyRatio() {
        return ratio(getEnergy(), getMaxEnergy());
    }

    public double getProgressRatio() {
        return ratio(getProgress(), getMaxProgress());
    }

    private static double ratio(int value, int max) {
        return max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) value / max));
    }

    @Override
    public boolean stillValid(Player player) {
        if (this.blockEntity == null || this.blockEntity.getLevel() == null) {
            return true;
        }
        var pos = this.blockEntity.getBlockPos();
        return !this.blockEntity.isRemoved()
                && player.distanceToSqr(pos.getX() + 0.5D, pos.getY() + 0.5D, pos.getZ() + 0.5D) <= 64.0D;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int quickMovedSlotIndex) {
        ItemStack quickMovedStack = ItemStack.EMPTY;
        Slot quickMovedSlot = this.slots.get(quickMovedSlotIndex);

        if (quickMovedSlot != null && quickMovedSlot.hasItem()) {
            ItemStack rawStack = quickMovedSlot.getItem();
            quickMovedStack = rawStack.copy();

            if (quickMovedSlotIndex < this.machineSlotCount) {
                if (!this.moveItemStackTo(rawStack, this.playerInventoryStart, this.hotbarEnd, true)) {
                    return ItemStack.EMPTY;
                }
            } else if (!this.moveItemStackTo(rawStack, 0, this.machineSlotCount, false)) {
                if (quickMovedSlotIndex >= this.playerInventoryStart && quickMovedSlotIndex < this.playerInventoryEnd) {
                    if (!this.moveItemStackTo(rawStack, this.hotbarStart, this.hotbarEnd, false)) {
                        return ItemStack.EMPTY;
                    }
                } else if (quickMovedSlotIndex >= this.hotbarStart && quickMovedSlotIndex < this.hotbarEnd
                        && !this.moveItemStackTo(rawStack, this.playerInventoryStart, this.playerInventoryEnd, false)) {
                    return ItemStack.EMPTY;
                }
            }

            if (rawStack.isEmpty()) {
                quickMovedSlot.set(ItemStack.EMPTY);
            } else {
                quickMovedSlot.setChanged();
            }

            if (rawStack.getCount() == quickMovedStack.getCount()) {
                return ItemStack.EMPTY;
            }

            quickMovedSlot.onTake(player, rawStack);
        }

        return quickMovedStack;
    }
}
