package net.ic2reborn.block.entity;

import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.ic2reborn.menu.MachineMenu;
import net.ic2reborn.menu.MachineGuiType;
import net.ic2reborn.menu.layout.MachineLayout;
import net.ic2reborn.registry.IC2BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Generic IC2 machine block entity.
 *
 * This is intentionally still light-weight: it gives every machine a persistent
 * inventory (sized by its GUI layout) and synced GUI data. The inventory is exposed
 * to other mods through Fabric's ItemStorage lookup (see IC2Reborn). The processing
 * and energy logic can be plugged into this class in the next phase.
 */
public class MachineBlockEntity extends BlockEntity implements ExtendedMenuProvider<MachineGuiType> {
    public static final int DATA_ENERGY = 0;
    public static final int DATA_MAX_ENERGY = 1;
    public static final int DATA_PROGRESS = 2;
    public static final int DATA_MAX_PROGRESS = 3;
    public static final int DATA_COUNT = 4;

    private final MachineGuiType guiType;
    private final SimpleContainer inventory;

    private int energy = 0;
    private int maxEnergy = 10000;
    private int progress = 0;
    private int maxProgress = 200;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            return switch (index) {
                case DATA_ENERGY -> MachineBlockEntity.this.energy;
                case DATA_MAX_ENERGY -> MachineBlockEntity.this.maxEnergy;
                case DATA_PROGRESS -> MachineBlockEntity.this.progress;
                case DATA_MAX_PROGRESS -> MachineBlockEntity.this.maxProgress;
                default -> 0;
            };
        }

        @Override
        public void set(int index, int value) {
            switch (index) {
                case DATA_ENERGY -> MachineBlockEntity.this.energy = value;
                case DATA_MAX_ENERGY -> MachineBlockEntity.this.maxEnergy = value;
                case DATA_PROGRESS -> MachineBlockEntity.this.progress = value;
                case DATA_MAX_PROGRESS -> MachineBlockEntity.this.maxProgress = value;
                default -> {
                }
            }
        }

        @Override
        public int getCount() {
            return DATA_COUNT;
        }
    };

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(IC2BlockEntities.MACHINE.get(), pos, state);
        this.guiType = MachineGuiType.fromBlockId(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());

        MachineLayout layout = this.guiType.layout();
        this.inventory = new SimpleContainer(layout.slotCount()) {
            @Override
            public void setChanged() {
                super.setChanged();
                MachineBlockEntity.this.setChanged();
            }

            @Override
            public boolean canPlaceItem(int slot, ItemStack stack) {
                return !layout.isOutputSlot(slot);
            }
        };
    }

    public SimpleContainer getInventory() {
        return this.inventory;
    }

    public ContainerData getContainerData() {
        return this.data;
    }

    public MachineGuiType getGuiType() {
        return this.guiType;
    }

    public int getEnergy() {
        return this.energy;
    }

    public int getMaxEnergy() {
        return this.maxEnergy;
    }

    public int getProgress() {
        return this.progress;
    }

    public int getMaxProgress() {
        return this.maxProgress;
    }

    @Override
    public Component getDisplayName() {
        return Component.translatable(this.getBlockState().getBlock().getDescriptionId());
    }

    @Override
    public MachineGuiType getScreenOpeningData(ServerPlayer player) {
        return this.guiType;
    }

    @Override
    public AbstractContainerMenu createMenu(int containerId, Inventory playerInventory, Player player) {
        return new MachineMenu(containerId, playerInventory, this);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);

        ValueOutput.ValueOutputList items = output.childrenList("Items");
        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            ItemStack stack = this.inventory.getItem(slot);
            if (!stack.isEmpty()) {
                ValueOutput itemOutput = items.addChild();
                itemOutput.putInt("Slot", slot);
                itemOutput.store("Stack", ItemStack.OPTIONAL_CODEC, stack);
            }
        }

        output.putInt("Energy", this.energy);
        output.putInt("MaxEnergy", this.maxEnergy);
        output.putInt("Progress", this.progress);
        output.putInt("MaxProgress", this.maxProgress);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);

        for (int slot = 0; slot < this.inventory.getContainerSize(); slot++) {
            this.inventory.setItem(slot, ItemStack.EMPTY);
        }

        for (ValueInput itemInput : input.childrenListOrEmpty("Items")) {
            int slot = itemInput.getIntOr("Slot", -1);
            if (slot >= 0 && slot < this.inventory.getContainerSize()) {
                itemInput.read("Stack", ItemStack.OPTIONAL_CODEC)
                        .ifPresent(stack -> this.inventory.setItem(slot, stack));
            }
        }

        this.energy = input.getIntOr("Energy", 0);
        this.maxEnergy = input.getIntOr("MaxEnergy", 10000);
        this.progress = input.getIntOr("Progress", 0);
        this.maxProgress = input.getIntOr("MaxProgress", 200);
    }
}
