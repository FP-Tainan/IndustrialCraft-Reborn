package net.ic2reborn.block.entity;

import net.craftenergy.api.EnergyUnits;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.ic2reborn.crop.CropBlockEntity;
import net.ic2reborn.crop.CropCard;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.fluid.MachineFluids;
import net.ic2reborn.item.HydrationCellItem;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import org.jetbrains.annotations.Nullable;

import java.util.List;

/**
 * Colheitadeira e Cropmatron do IC2 ({@code TileEntityCropHarvester}, {@code TileEntityCropmatron}):
 * a cada 10 ticks olham uma posição de uma área 9×3×9 em volta da máquina. A colheitadeira colhe
 * plantas maduras; o Cropmatron aplica fertilizante, água e herbicida e molha a terra arada.
 */
final class CropMachineLogic {
    static final int DISCHARGE = 0;
    static final int HARVEST_FIRST = 1;
    static final int HARVEST_LAST = 15;
    static final int FERTILIZER_FIRST = 1;
    static final int FERTILIZER_LAST = 7;
    static final int WEED_EX_IN = 8;
    static final int WEED_EX_OUT = 9;
    static final int WATER_IN = 10;

    private static final int SCAN_INTERVAL = 10;
    private static final long SCAN_ENERGY = EnergyUnits.fromCWh(1);
    private static final long DROP_ENERGY = EnergyUnits.fromCWh(20);
    private static final long CARE_ENERGY = EnergyUnits.fromCWh(10);
    private static final long MILLIBUCKET = FluidConstants.BUCKET / 1000;
    /** IC2: nas máquinas a célula de hidratação solta no máximo 180 mB por vez. */
    private static final int HYDRATION_PER_TICK = 180;

    private final MachineBlockEntity machine;
    private final boolean harvester;
    private final @Nullable Storage<FluidVariant> weedExInput;
    private int scanX = -4;
    private int scanY = -1;
    private int scanZ = -4;

    CropMachineLogic(MachineBlockEntity machine, boolean harvester) {
        this.machine = machine;
        this.harvester = harvester;
        SingleFluidStorage weedEx = machine.cropTank(1);
        this.weedExInput = weedEx == null ? null : new FilteringStorage<>(weedEx) {
            @Override
            protected boolean canInsert(FluidVariant resource) {
                return resource.getFluid() == IC2Fluids.WEED_EX.fluid();
            }

            @Override
            protected boolean canExtract(FluidVariant resource) {
                return false;
            }
        };
    }

    boolean tick(Level level) {
        boolean changed = !this.harvester && fillTanks();
        long minimum = this.harvester ? SCAN_ENERGY + DROP_ENERGY : SCAN_ENERGY + 3 * CARE_ENERGY;
        if (level.getGameTime() % SCAN_INTERVAL == 0 && this.machine.getStoredEnergy() >= minimum) {
            scan(level);
            changed = true;
        }
        return changed;
    }

    /** Anda uma posição na varredura (x, depois z, depois y) e trata o que achar lá. */
    void scan(Level level) {
        if (++this.scanX > 4) {
            this.scanX = -4;
            if (++this.scanZ > 4) {
                this.scanZ = -4;
                if (++this.scanY > 1) this.scanY = -1;
            }
        }
        this.machine.useEnergy(SCAN_ENERGY);
        BlockPos pos = this.machine.getBlockPos().offset(this.scanX, this.scanY, this.scanZ);
        if (this.harvester) {
            harvest(level, pos);
        } else {
            care(level, pos);
        }
    }

    // ── colheitadeira ─────────────────────────────────────────────────────
    private void harvest(Level level, BlockPos pos) {
        if (!(level.getBlockEntity(pos) instanceof CropBlockEntity crop) || crop.getCrop() == null || harvestSlotsFull()) return;
        CropCard card = crop.getCrop();
        if (crop.getSize() != card.optimalHarvestSize(crop) && crop.getSize() != card.maxSize()) return;
        List<ItemStack> drops = crop.performHarvest();
        if (drops == null) return;
        for (ItemStack drop : drops) {
            ItemStack rest = store(drop);
            if (!rest.isEmpty()) Block.popResource(level, this.machine.getBlockPos().above(), rest);
            this.machine.useEnergy(DROP_ENERGY);
        }
    }

    private boolean harvestSlotsFull() {
        Container inventory = this.machine.getInventory();
        for (int slot = HARVEST_FIRST; slot <= HARVEST_LAST; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty() || stack.getCount() < Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize())) return false;
        }
        return true;
    }

    /** Guarda nos slots de colheita; devolve o que não coube. */
    private ItemStack store(ItemStack drop) {
        Container inventory = this.machine.getInventory();
        for (int slot = HARVEST_FIRST; slot <= HARVEST_LAST && !drop.isEmpty(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, drop)) {
                int moved = Math.min(drop.getCount(), Math.min(stack.getMaxStackSize(), inventory.getMaxStackSize()) - stack.getCount());
                if (moved > 0) {
                    stack.grow(moved);
                    drop.shrink(moved);
                }
            }
        }
        for (int slot = HARVEST_FIRST; slot <= HARVEST_LAST && !drop.isEmpty(); slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, drop.split(Math.min(drop.getCount(), drop.getMaxStackSize())));
            }
        }
        inventory.setChanged();
        return drop;
    }

    // ── cropmatron ────────────────────────────────────────────────────────
    private void care(Level level, BlockPos pos) {
        SingleFluidStorage water = this.machine.cropTank(0);
        SingleFluidStorage weedEx = this.machine.cropTank(1);
        if (level.getBlockEntity(pos) instanceof CropBlockEntity crop) {
            int fertilizer = findFertilizer();
            if (fertilizer >= 0 && crop.applyFertilizer(false)) {
                this.machine.useEnergy(CARE_ENERGY);
                this.machine.getInventory().removeItem(fertilizer, 1);
            }
            if (water != null && water.amount > 0 && drain(water, crop.addWater(millibuckets(water)))) {
                this.machine.useEnergy(CARE_ENERGY);
            }
            if (weedEx != null && weedEx.amount > 0 && drain(weedEx, crop.addWeedEx(millibuckets(weedEx), false))) {
                this.machine.useEnergy(CARE_ENERGY);
            }
        } else if (water != null && water.amount > 0 && hydrateFarmland(level, pos, water)) {
            this.machine.useEnergy(CARE_ENERGY);
        }
    }

    private int findFertilizer() {
        Container inventory = this.machine.getInventory();
        for (int slot = FERTILIZER_FIRST; slot <= FERTILIZER_LAST; slot++) {
            if (inventory.getItem(slot).is(IC2AutoItems.FERTILIZER.get())) return slot;
        }
        return -1;
    }

    /** Terra arada seca recebe só a água que falta para a umidade máxima (7). */
    private boolean hydrateFarmland(Level level, BlockPos pos, SingleFluidStorage water) {
        BlockState state = level.getBlockState(pos);
        if (!state.is(Blocks.FARMLAND)) return false;
        int moisture = state.getValue(FarmlandBlock.MOISTURE);
        int amount = Math.min(millibuckets(water), 7 - moisture);
        if (amount <= 0) return false;
        drain(water, amount);
        level.setBlock(pos, state.setValue(FarmlandBlock.MOISTURE, moisture + amount), Block.UPDATE_CLIENTS);
        return true;
    }

    /** Células de herbicida e de hidratação nos slots de entrada (a água comum a máquina já trata). */
    private boolean fillTanks() {
        boolean changed = false;
        Container inventory = this.machine.getInventory();
        if (this.weedExInput != null) {
            changed = MachineFluids.drainIntoTank(inventory, WEED_EX_IN, WEED_EX_OUT, this.weedExInput);
        }
        SingleFluidStorage water = this.machine.cropTank(0);
        ItemStack cell = inventory.getItem(WATER_IN);
        if (water != null && cell.getItem() instanceof HydrationCellItem) {
            int room = (int) ((water.getCapacity() - water.amount) / MILLIBUCKET);
            int moved = HydrationCellItem.drain(cell, Math.min(HYDRATION_PER_TICK, room));
            if (moved > 0) {
                if (water.variant.isBlank()) water.variant = FluidVariant.of(Fluids.WATER);
                water.amount += moved * MILLIBUCKET;
                inventory.setItem(WATER_IN, cell);
                changed = true;
            }
        }
        return changed;
    }

    private static int millibuckets(SingleFluidStorage tank) {
        return (int) Math.min(Integer.MAX_VALUE, tank.amount / MILLIBUCKET);
    }

    private boolean drain(SingleFluidStorage tank, int millibuckets) {
        if (millibuckets <= 0) return false;
        tank.amount = Math.max(0, tank.amount - millibuckets * MILLIBUCKET);
        if (tank.amount == 0) tank.variant = FluidVariant.blank();
        this.machine.setChanged();
        return true;
    }
}
