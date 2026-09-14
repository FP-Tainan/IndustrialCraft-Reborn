package net.ic2reborn.block.entity;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.item.ContainerStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.fluid.MachineFluids;
import net.ic2reborn.menu.MachineGuiType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Armazenamento e logística do IC2: tanques, bomba, buffer de itens, máquina de triagem,
 * distribuidores com prioridade, envasadora, distribuidor e regulador de fluidos, condensador
 * e destilador solar. Os slots seguem a ordem dos layouts em {@code MachineLayouts}.
 */
final class LogisticsLogic {
    private static final long MB = FluidConstants.BUCKET / 1_000;

    // triagem: descarga 0, upgrades 1–3, buffer 4–14, filtros 15–56 (6 direções × 7)
    static final int SORTING_BUFFER_START = 4;
    static final int SORTING_BUFFER_END = 15;
    static final int SORTING_FILTER_START = 15;
    static final int SORTING_FILTERS_PER_SIDE = 7;
    /** IC2: 20 EU por item triado (1 EU = 500 CW por um tick). */
    private static final long SORTING_COST_PER_ITEM = 10_000;
    /** Regulador: 10 EU por operação. */
    private static final long REGULATOR_COST = 5_000;
    /** Condensador: 2 EU/t por ventoinha → 1.000 CW. */
    private static final long CONDENSER_POWER_PER_VENT = 1_000;
    private static final int PUMP_SEARCH_LIMIT = 1_024;

    private final MachineBlockEntity machine;
    private final MachineGuiType type;
    private int progress;
    private boolean working;
    /** Triagem: direções com rota padrão (bit = ordinal). */
    private int defaultRoutes;
    /** Distribuidores com prioridade: ordem das saídas. */
    private final List<Direction> priority = new ArrayList<>();
    private int regulatorAmount;
    private boolean regulatorPerTick;
    /** Distribuidor de fluido: false = só pela frente, true = divide entre as outras faces. */
    private boolean distribute;

    private LogisticsLogic(MachineBlockEntity machine, MachineGuiType type) {
        this.machine = machine;
        this.type = type;
    }

    static @Nullable LogisticsLogic create(MachineBlockEntity machine, MachineGuiType type) {
        return switch (type) {
            case TANK, PUMP, ITEM_BUFFER, SORTING_MACHINE, WEIGHTED_ITEM_DISTRIBUTOR, WEIGHTED_FLUID_DISTRIBUTOR,
                 FLUID_BOTTLER, FLUID_DISTRIBUTOR, FLUID_REGULATOR, CONDENSER, SOLAR_DISTILLER -> new LogisticsLogic(machine, type);
            default -> null;
        };
    }

    boolean working() {
        return this.working;
    }

    // ── slots ─────────────────────────────────────────────────────────────
    boolean canPlace(int slot, ItemStack stack) {
        return switch (this.type) {
            case SORTING_MACHINE -> slot < SORTING_FILTER_START;
            case CONDENSER -> slot < 4 || BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().endsWith("heat_vent");
            default -> true;
        };
    }

    /** Slots que o ejetor esvazia (null = as saídas normais). */
    int[] ejectSlots() {
        return switch (this.type) {
            case ITEM_BUFFER -> IntStream.range(0, 48).toArray();
            case SORTING_MACHINE, WEIGHTED_ITEM_DISTRIBUTOR -> new int[0];
            default -> null;
        };
    }

    /** Slots que o puxador enche (null = as entradas normais). */
    int[] pullSlots() {
        return switch (this.type) {
            case ITEM_BUFFER -> IntStream.range(0, 48).toArray();
            case SORTING_MACHINE -> IntStream.range(SORTING_BUFFER_START, SORTING_BUFFER_END).toArray();
            case WEIGHTED_ITEM_DISTRIBUTOR -> IntStream.range(0, 9).toArray();
            default -> null;
        };
    }

    // ── GUI ───────────────────────────────────────────────────────────────
    int mode() {
        return switch (this.type) {
            case SORTING_MACHINE -> this.defaultRoutes;
            case WEIGHTED_ITEM_DISTRIBUTOR, WEIGHTED_FLUID_DISTRIBUTOR -> encodePriority();
            case FLUID_REGULATOR -> this.regulatorAmount | (this.regulatorPerTick ? 1 << 12 : 0);
            case FLUID_DISTRIBUTOR -> this.distribute ? 1 : 0;
            default -> 0;
        };
    }

    private int encodePriority() {
        int value = 0;
        for (int i = 0; i < this.priority.size(); i++) {
            value |= (this.priority.get(i).get3DDataValue() + 1) << (3 * i);
        }
        return value;
    }

    private void decodePriority(int value) {
        this.priority.clear();
        for (int i = 0; i < 6; i++) {
            int dir = (value >> (3 * i)) & 7;
            if (dir == 0) break;
            Direction direction = Direction.from3DDataValue(dir - 1);
            if (!this.priority.contains(direction)) this.priority.add(direction);
        }
    }

    boolean handleButton(int id) {
        switch (this.type) {
            case SORTING_MACHINE -> {
                if (id < MachineBlockEntity.BUTTON_SORTING_DEFAULT || id >= MachineBlockEntity.BUTTON_SORTING_DEFAULT + 6) return false;
                this.defaultRoutes ^= 1 << (id - MachineBlockEntity.BUTTON_SORTING_DEFAULT);
            }
            case WEIGHTED_ITEM_DISTRIBUTOR, WEIGHTED_FLUID_DISTRIBUTOR -> {
                if (id < MachineBlockEntity.BUTTON_WEIGHTED_PRIORITY || id >= MachineBlockEntity.BUTTON_WEIGHTED_PRIORITY + 6) return false;
                Direction direction = Direction.from3DDataValue(id - MachineBlockEntity.BUTTON_WEIGHTED_PRIORITY);
                if (!this.priority.remove(direction)) this.priority.add(direction);
            }
            case FLUID_REGULATOR -> {
                int[] steps = {1_000, 100, 10, 1, -1_000, -100, -10, -1};
                if (id >= MachineBlockEntity.BUTTON_REGULATOR && id < MachineBlockEntity.BUTTON_REGULATOR + steps.length) {
                    this.regulatorAmount = Math.max(0, Math.min(1_000, this.regulatorAmount + steps[id - MachineBlockEntity.BUTTON_REGULATOR]));
                } else if (id == MachineBlockEntity.BUTTON_REGULATOR_MODE) {
                    this.regulatorPerTick = !this.regulatorPerTick;
                } else {
                    return false;
                }
            }
            case FLUID_DISTRIBUTOR -> {
                if (id != MachineBlockEntity.BUTTON_FLUID_DISTRIBUTOR_MODE) return false;
                this.distribute = !this.distribute;
            }
            default -> {
                return false;
            }
        }
        this.machine.setChanged();
        return true;
    }

    // ── tick ──────────────────────────────────────────────────────────────
    boolean tick(Level level) {
        this.working = false;
        return switch (this.type) {
            case TANK -> tickTank();
            case PUMP -> tickPump(level);
            case SORTING_MACHINE -> tickSorting(level);
            case WEIGHTED_ITEM_DISTRIBUTOR -> tickWeightedItems(level);
            case WEIGHTED_FLUID_DISTRIBUTOR -> tickWeightedFluids(level);
            case FLUID_BOTTLER -> tickBottler();
            case FLUID_DISTRIBUTOR -> tickFluidDistributor(level);
            case FLUID_REGULATOR -> tickRegulator(level);
            case CONDENSER -> tickCondenser();
            case SOLAR_DISTILLER -> tickSolarDistiller(level);
            default -> false;
        };
    }

    /** Tanque: o recipiente do slot 0 esvazia no tanque ou, se estiver vazio, enche com ele. */
    private boolean tickTank() {
        MachineBlockEntity.MachineTank tank = this.machine.mainTank();
        if (tank == null) return false;
        SimpleContainer inventory = this.machine.getInventory();
        return MachineFluids.drainIntoTank(inventory, 0, 1, tank) || MachineFluids.fillFromTank(inventory, 0, 1, tank);
    }

    /**
     * Bomba do IC2: a cada operação (20 ticks, 1 EU/t) tira um bloco-fonte de líquido encostado (ou
     * ligado a ele pelo mesmo líquido) e guarda 1.000 mB; enche recipientes do slot 0.
     */
    private boolean tickPump(Level level) {
        MachineBlockEntity.MachineTank tank = this.machine.mainTank();
        if (tank == null) return false;
        boolean changed = MachineFluids.fillFromTank(this.machine.getInventory(), 0, 1, tank);
        int ticks = Math.max(1, this.machine.getEnergyProfile().operationTicks());
        long power = this.machine.getEnergyProfile().power();

        if (tank.capacity() - tank.amount >= FluidConstants.BUCKET) {
            if (this.progress < ticks) {
                if (this.machine.useEnergy(power)) {
                    this.progress++;
                    this.working = true;
                    changed = true;
                }
            } else if (level.getGameTime() % 10 == 0 || this.progress == ticks) {
                if (pumpOnce(level, tank)) {
                    this.progress = 0;
                    this.working = true;
                } else {
                    this.progress = ticks + 1; // espera sem gastar energia
                }
                changed = true;
            }
        }
        this.machine.setProgressDisplay(Math.min(this.progress, ticks), ticks);
        return changed;
    }

    private boolean pumpOnce(Level level, MachineBlockEntity.MachineTank tank) {
        BlockPos origin = this.machine.getBlockPos();
        BlockPos start = null;
        for (Direction direction : new Direction[]{Direction.DOWN, Direction.NORTH, Direction.SOUTH, Direction.WEST, Direction.EAST, Direction.UP}) {
            if (!level.getFluidState(origin.relative(direction)).isEmpty()) {
                start = origin.relative(direction);
                break;
            }
        }
        if (start == null) return false;
        Fluid fluid = level.getFluidState(start).getType();
        Fluid still = fluid instanceof FlowingFluid flowing ? flowing.getSource() : fluid;
        FluidVariant variant = FluidVariant.of(still);
        if (!tank.canFill(variant, FluidConstants.BUCKET)) return false;

        BlockPos source = null;
        ArrayDeque<BlockPos> queue = new ArrayDeque<>();
        Set<BlockPos> seen = new HashSet<>();
        queue.add(start);
        seen.add(start);
        while (!queue.isEmpty() && seen.size() < PUMP_SEARCH_LIMIT) {
            BlockPos pos = queue.poll();
            FluidState state = level.getFluidState(pos);
            if (state.isEmpty() || !state.getType().isSame(still)) continue;
            if (state.isSource() && level.getBlockState(pos).getBlock() instanceof LiquidBlock) source = pos;
            for (Direction direction : Direction.values()) {
                BlockPos next = pos.relative(direction);
                if (Math.abs(next.getX() - origin.getX()) > 32 || Math.abs(next.getZ() - origin.getZ()) > 32) continue;
                if (seen.add(next)) queue.add(next);
            }
        }
        if (source == null) return false;
        level.setBlock(source, Blocks.AIR.defaultBlockState(), Block.UPDATE_ALL);
        tank.fill(variant, FluidConstants.BUCKET);
        return true;
    }

    /**
     * Máquina de triagem do IC2: cada item do buffer vai para a face cujo filtro tem esse item; sem
     * filtro, para as faces marcadas como rota padrão. Cada item custa 10 CWh.
     */
    private boolean tickSorting(Level level) {
        var own = ContainerStorage.of(this.machine.getInventory(), null);
        SimpleContainer inventory = this.machine.getInventory();
        boolean moved = false;
        for (int slot = SORTING_BUFFER_START; slot < SORTING_BUFFER_END; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            long affordable = this.machine.getStoredEnergy() / SORTING_COST_PER_ITEM;
            if (affordable <= 0) break;

            List<Direction> targets = new ArrayList<>();
            for (Direction direction : Direction.values()) {
                int first = SORTING_FILTER_START + direction.ordinal() * SORTING_FILTERS_PER_SIDE;
                for (int filter = first; filter < first + SORTING_FILTERS_PER_SIDE; filter++) {
                    if (ItemStack.isSameItem(inventory.getItem(filter), stack)) {
                        targets.add(direction);
                        break;
                    }
                }
            }
            if (targets.isEmpty()) {
                for (Direction direction : Direction.values()) {
                    if ((this.defaultRoutes & (1 << direction.ordinal())) != 0) targets.add(direction);
                }
            }
            for (Direction direction : targets) {
                long done = moveItems(level, own.getSlot(slot), direction, affordable);
                if (done > 0) {
                    this.machine.useEnergy(done * SORTING_COST_PER_ITEM);
                    moved = true;
                    break;
                }
            }
        }
        this.working = moved;
        return moved;
    }

    /** Distribuidor com prioridade: tenta as saídas na ordem escolhida; a primeira que aceitar leva. */
    private boolean tickWeightedItems(Level level) {
        if (this.priority.isEmpty()) return false;
        var own = ContainerStorage.of(this.machine.getInventory(), null);
        boolean moved = false;
        for (int slot = 0; slot < 9; slot++) {
            if (this.machine.getInventory().getItem(slot).isEmpty()) continue;
            for (Direction direction : this.priority) {
                if (moveItems(level, own.getSlot(slot), direction, 64) > 0) {
                    moved = true;
                    break;
                }
            }
        }
        this.working = moved;
        return moved;
    }

    private boolean tickWeightedFluids(Level level) {
        MachineBlockEntity.MachineTank tank = this.machine.mainTank();
        if (tank == null) return false;
        boolean changed = MachineFluids.fillFromTank(this.machine.getInventory(), 0, 1, tank);
        if (tank.amount <= 0) return changed;
        for (Direction direction : this.priority) {
            if (pushFluid(level, tank, direction, tank.amount) > 0) {
                this.working = true;
                return true;
            }
        }
        return changed;
    }

    /**
     * Envasadora do IC2 (2 EU/t, 100 ticks): esvazia o recipiente de cima no tanque ou enche o de
     * baixo com ele; o recipiente resultante vai para a saída.
     */
    private boolean tickBottler() {
        MachineBlockEntity.MachineTank tank = this.machine.mainTank();
        if (tank == null) return false;
        SimpleContainer inventory = this.machine.getInventory();
        int ticks = Math.max(1, this.machine.getEnergyProfile().operationTicks());
        this.machine.setProgressDisplay(this.progress, ticks);

        int slot = 1;
        boolean intoItem = false;
        MachineFluids.Transfer preview = MachineFluids.transfer(inventory.getItem(1), tank, false, false);
        if (preview == null || !fits(inventory.getItem(3), preview.leftover())) {
            slot = 2;
            intoItem = true;
            preview = MachineFluids.transfer(inventory.getItem(2), tank, true, false);
        }
        if (preview == null || !fits(inventory.getItem(3), preview.leftover())) {
            boolean had = this.progress > 0;
            this.progress = 0;
            return had;
        }
        if (!this.machine.useEnergy(this.machine.getEnergyProfile().power())) return false;
        this.working = true;
        if (++this.progress < ticks) return true;

        this.progress = 0;
        ItemStack stack = inventory.getItem(slot);
        MachineFluids.Transfer done = MachineFluids.transfer(stack, tank, intoItem, true);
        if (done == null) return true;
        stack.shrink(1);
        inventory.setItem(slot, stack);
        addToSlot(inventory, 3, done.leftover());
        return true;
    }

    /** Distribuidor de fluido: concentra na frente ou divide entre as outras cinco faces. */
    private boolean tickFluidDistributor(Level level) {
        MachineBlockEntity.MachineTank tank = this.machine.mainTank();
        if (tank == null) return false;
        boolean changed = MachineFluids.fillFromTank(this.machine.getInventory(), 0, 1, tank);
        if (tank.amount <= 0) return changed;
        Direction front = this.machine.facing();
        if (!this.distribute) {
            if (pushFluid(level, tank, front, tank.amount) > 0) {
                this.working = true;
                return true;
            }
            return changed;
        }
        List<Direction> sides = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (direction != front && FluidStorage.SIDED.find(level, this.machine.getBlockPos().relative(direction), direction.getOpposite()) != null) {
                sides.add(direction);
            }
        }
        if (sides.isEmpty()) return changed;
        long share = Math.max(MB, tank.amount / sides.size());
        for (Direction direction : sides) {
            if (tank.amount <= 0) break;
            this.working |= pushFluid(level, tank, direction, Math.min(share, tank.amount)) > 0;
        }
        return changed || this.working;
    }

    /** Regulador de fluido: manda a quantidade escolhida (0–1.000 mB) pela frente, por segundo ou por tick. */
    private boolean tickRegulator(Level level) {
        MachineBlockEntity.MachineTank tank = this.machine.mainTank();
        if (tank == null || this.regulatorAmount <= 0 || tank.amount <= 0) return false;
        if (!this.regulatorPerTick && level.getGameTime() % 20 != 0) return false;
        if (this.machine.getStoredEnergy() < REGULATOR_COST) return false;
        long moved = pushFluid(level, tank, this.machine.facing(), Math.min(tank.amount, this.regulatorAmount * MB));
        if (moved <= 0) return false;
        this.machine.useEnergy(REGULATOR_COST);
        this.working = true;
        return true;
    }

    /**
     * Condensador do IC2: vapor vira água destilada na razão 100:1. Sem ventoinhas condensa
     * 100 mB/t; cada ventoinha soma 100 mB/t e gasta 1.000 CW.
     */
    private boolean tickCondenser() {
        MachineBlockEntity.MachineTank steam = this.machine.mainTank();
        MachineBlockEntity.MachineTank water = this.machine.secondTank();
        if (steam == null || water == null) return false;
        boolean changed = MachineFluids.fillFromTank(this.machine.getInventory(), 1, 2, water);
        FluidVariant distilled = FluidVariant.of(IC2Fluids.DISTILLED_WATER.fluid());
        this.machine.setProgressDisplay(this.progress, 10_000);
        if (!water.canFill(distilled, 100 * MB)) return changed;

        if (this.progress >= 10_000) {
            water.fill(distilled, 100 * MB);
            this.progress -= 10_000;
            changed = true;
        }
        if (steam.amount <= 0) return changed;
        int vents = 0;
        for (int slot = 4; slot < 8; slot++) {
            if (!this.machine.getInventory().getItem(slot).isEmpty()) vents++;
        }
        if (vents > 0 && !this.machine.useEnergy(vents * CONDENSER_POWER_PER_VENT)) return changed;
        long drained = Math.min(steam.amount, (100 + vents * 100L) * MB);
        steam.consume(drained);
        this.progress += (int) (drained / MB);
        this.working = true;
        return true;
    }

    /** Destilador solar do IC2: sob o sol, 1 mB de água vira destilada a cada 36/72/144 ticks (quente/normal/frio). */
    private boolean tickSolarDistiller(Level level) {
        MachineBlockEntity.MachineTank water = this.machine.mainTank();
        MachineBlockEntity.MachineTank distilled = this.machine.secondTank();
        if (water == null || distilled == null) return false;
        boolean changed = MachineFluids.fillFromTank(this.machine.getInventory(), 1, 3, distilled);
        BlockPos pos = this.machine.getBlockPos();
        float temperature = level.getBiome(pos).value().getBaseTemperature();
        int rate = temperature > 1.0F ? 36 : temperature < 0.15F ? 144 : 72;
        if (MachineBlockEntity.sunlight(level, pos) <= 0.5) return changed;
        this.working = water.amount >= MB;
        if (level.getGameTime() % rate != 0 || water.amount < MB) return changed;
        FluidVariant out = FluidVariant.of(IC2Fluids.DISTILLED_WATER.fluid());
        if (!distilled.canFill(out, MB)) return changed;
        water.consume(MB);
        distilled.fill(out, MB);
        return true;
    }

    // ── ajudantes ─────────────────────────────────────────────────────────
    private long moveItems(Level level, Storage<ItemVariant> from, Direction direction, long max) {
        BlockPos target = this.machine.getBlockPos().relative(direction);
        Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, target, direction.getOpposite());
        if (storage == null) return 0;
        return StorageUtil.move(from, storage, variant -> true, max, null);
    }

    private long pushFluid(Level level, Storage<FluidVariant> from, Direction direction, long max) {
        BlockPos target = this.machine.getBlockPos().relative(direction);
        Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, target, direction.getOpposite());
        if (storage == null) return 0;
        return StorageUtil.move(from, storage, variant -> true, max, null);
    }

    private static boolean fits(ItemStack slot, ItemStack stack) {
        if (slot.isEmpty() || stack.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() + stack.getCount() <= slot.getMaxStackSize();
    }

    private static void addToSlot(SimpleContainer inventory, int slot, ItemStack stack) {
        if (stack.isEmpty()) return;
        ItemStack current = inventory.getItem(slot);
        if (current.isEmpty()) {
            inventory.setItem(slot, stack);
        } else {
            current.grow(stack.getCount());
            inventory.setItem(slot, current);
        }
    }

    // ── salvar/carregar ───────────────────────────────────────────────────
    void write(ValueOutput output) {
        output.putInt("LogisticsProgress", this.progress);
        output.putInt("DefaultRoutes", this.defaultRoutes);
        output.putInt("Priority", encodePriority());
        output.putInt("RegulatorAmount", this.regulatorAmount);
        output.putInt("RegulatorPerTick", this.regulatorPerTick ? 1 : 0);
        output.putInt("Distribute", this.distribute ? 1 : 0);
    }

    void read(ValueInput input) {
        this.progress = Math.max(0, input.getIntOr("LogisticsProgress", 0));
        this.defaultRoutes = input.getIntOr("DefaultRoutes", 0) & 0x3F;
        decodePriority(input.getIntOr("Priority", 0));
        this.regulatorAmount = Math.max(0, Math.min(1_000, input.getIntOr("RegulatorAmount", 0)));
        this.regulatorPerTick = input.getIntOr("RegulatorPerTick", 0) != 0;
        this.distribute = input.getIntOr("Distribute", 0) != 0;
    }
}
