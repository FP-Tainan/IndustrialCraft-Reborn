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
                new SimpleContainerData(MachineBlockEntity.DATA_COUNT * 2), null, guiType);
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
        checkContainerDataCount(data, MachineBlockEntity.DATA_COUNT * 2);

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

        if (this.layout.hasInventory()) {
            addPlayerInventory(playerInventory, this.layout.inventoryX() + 1, this.layout.inventoryY() + 1);
        }
        // armazenamentos do IC2 (ContainerElectricBlock): armadura do jogador, para vestir e carregar
        if (guiType == MachineGuiType.BATBOX || guiType == MachineGuiType.CESU
                || guiType == MachineGuiType.MFE || guiType == MachineGuiType.MFSU) {
            net.minecraft.world.entity.EquipmentSlot[] armor = {net.minecraft.world.entity.EquipmentSlot.HEAD,
                    net.minecraft.world.entity.EquipmentSlot.CHEST, net.minecraft.world.entity.EquipmentSlot.LEGS,
                    net.minecraft.world.entity.EquipmentSlot.FEET};
            net.minecraft.resources.Identifier[] icons = {net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_HELMET,
                    net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_CHESTPLATE,
                    net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_LEGGINGS,
                    net.minecraft.world.inventory.InventoryMenu.EMPTY_ARMOR_SLOT_BOOTS};
            for (int col = 0; col < armor.length; col++) {
                this.addSlot(new net.minecraft.world.inventory.ArmorSlot(playerInventory, playerInventory.player, armor[col],
                        armor[col].getIndex(36), 8 + col * 18, 84, icons[col]));
            }
        }
        this.addDataSlots(data);
    }

    private void addMachineSlot(Container inv, int index, MachineLayout.SlotDef def) {
        this.addSlot(new Slot(inv, index, def.slotX(), def.slotY()) {
            @Override
            public boolean mayPlace(ItemStack stack) {
                return !def.output() && inv.canPlaceItem(this.getContainerSlot(), stack);
            }

            @Override
            public boolean mayPickup(Player player) {
                return !layout.isGhostSlot(this.getContainerSlot());
            }

            @Override
            public int getMaxStackSize() {
                return layout.isSingleSlot(this.getContainerSlot()) ? 1 : super.getMaxStackSize();
            }

            @Override
            public int getMaxStackSize(ItemStack stack) {
                return layout.isSingleSlot(this.getContainerSlot()) ? 1 : super.getMaxStackSize(stack);
            }
        });
    }

    private void addPlayerInventory(Inventory playerInventory, int x, int y) {
        int pitch = this.layout.inventoryPitch();
        for (int row = 0; row < 3; ++row) {
            for (int col = 0; col < 9; ++col) {
                this.addSlot(new Slot(playerInventory, col + row * 9 + 9, x + col * pitch, y + row * pitch));
            }
        }
        for (int col = 0; col < 9; ++col) {
            this.addSlot(new Slot(playerInventory, col, x + col * pitch, y + this.layout.hotbarOffset()));
        }
    }

    public MachineGuiType getGuiType() {
        return this.guiType;
    }

    public MachineLayout getLayout() {
        return this.layout;
    }

    /** Junta as duas metades de 16 bits de um campo sincronizado. */
    private int value(int field) {
        return (this.data.get(field * 2) & 0xFFFF) | ((this.data.get(field * 2 + 1) & 0xFFFF) << 16);
    }

    /** Energia guardada, em CWh. */
    public int getEnergyCWh() {
        return value(MachineBlockEntity.DATA_ENERGY);
    }

    /** Capacidade, em CWh. */
    public int getCapacityCWh() {
        return value(MachineBlockEntity.DATA_CAPACITY);
    }

    public int getProgress() {
        return value(MachineBlockEntity.DATA_PROGRESS);
    }

    public int getMaxProgress() {
        return value(MachineBlockEntity.DATA_MAX_PROGRESS);
    }

    /** CW que passaram pela máquina no último tick (negativo quando uma bateria descarrega). */
    public int getPower() {
        return value(MachineBlockEntity.DATA_POWER);
    }

    /** Tensão da máquina, em MV. */
    public int getVoltage() {
        return value(MachineBlockEntity.DATA_VOLTAGE);
    }

    public double getEnergyRatio() {
        return ratio(getEnergyCWh(), getCapacityCWh());
    }

    public double getProgressRatio() {
        return ratio(getProgress(), getMaxProgress());
    }

    private static double ratio(int value, int max) {
        return max <= 0 ? 0.0 : Math.max(0.0, Math.min(1.0, (double) value / max));
    }

    /** Fluido do tanque {@code tank} (0 = principal, 1 = saída), ou null se vazio. */
    public net.minecraft.world.level.material.@org.jetbrains.annotations.Nullable Fluid getFluid(int tank) {
        int id = value(MachineBlockEntity.DATA_FLUID + tank * MachineBlockEntity.DATA_PER_TANK) - 1;
        return id < 0 ? null : net.minecraft.core.registries.BuiltInRegistries.FLUID.byId(id);
    }

    /** Fluido no tanque, em mB. */
    public int getFluidAmount(int tank) {
        return value(MachineBlockEntity.DATA_FLUID_AMOUNT + tank * MachineBlockEntity.DATA_PER_TANK);
    }

    /** Capacidade do tanque, em mB. */
    public int getFluidCapacity(int tank) {
        return value(MachineBlockEntity.DATA_FLUID_CAPACITY + tank * MachineBlockEntity.DATA_PER_TANK);
    }

    public double getFluidRatio(int tank) {
        return ratio(getFluidAmount(tank), getFluidCapacity(tank));
    }

    /** Calor atual (forno de indução, centrífuga térmica). */
    public int getHeat() {
        return value(MachineBlockEntity.DATA_HEAT);
    }

    /** Calor máximo (indução) ou calor pedido pela receita (centrífuga). */
    public int getMaxHeat() {
        return value(MachineBlockEntity.DATA_MAX_HEAT);
    }

    public double getHeatRatio() {
        return ratio(getHeat(), getMaxHeat());
    }

    /** Modo configurado do transformador: 0 = redstone, 1 = abaixa, 2 = eleva. */
    public int getTransformerMode() {
        return value(MachineBlockEntity.DATA_MODE);
    }

    /** Modo da máquina: transformador (0–2) ou enlatadora (0–3). */
    public int getMachineMode() {
        return value(MachineBlockEntity.DATA_MODE);
    }

    /** Filtros da triagem: clicar copia o item da mão (sem gastar) ou limpa o filtro. */
    @Override
    public void clicked(int slotIndex, int button, net.minecraft.world.inventory.ContainerInput input, Player player) {
        if (slotIndex >= 0 && slotIndex < this.machineSlotCount && this.layout.isGhostSlot(slotIndex)) {
            if (input == net.minecraft.world.inventory.ContainerInput.PICKUP || input == net.minecraft.world.inventory.ContainerInput.QUICK_MOVE) {
                ItemStack carried = this.getCarried();
                if (this.blockEntity == null || this.blockEntity.canConfigure(player)) {
                    this.slots.get(slotIndex).set(carried.isEmpty() ? ItemStack.EMPTY : carried.copy());
                }
            }
            return;
        }
        super.clicked(slotIndex, button, input, player);
    }
    /** Botões da GUI (modos, trocar tanques) chegam aqui no servidor. */
    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (this.blockEntity != null && this.blockEntity.canConfigure(player) && this.blockEntity.handleMenuButton(id)) {
            return true;
        }
        return super.clickMenuButton(player, id);
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
            } else if (quickMovedSlotIndex >= this.hotbarEnd) {
                // armadura de volta para o inventário
                if (!this.moveItemStackTo(rawStack, this.playerInventoryStart, this.hotbarEnd, false)) {
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
