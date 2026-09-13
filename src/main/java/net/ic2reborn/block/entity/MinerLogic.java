package net.ic2reborn.block.entity;

import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.ic2reborn.block.MiningPipeBlock;
import net.ic2reborn.item.DrillItem;
import net.ic2reborn.item.ScannerItem;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.FluidState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

/**
 * Minerador do IC2 ({@code TileEntityMiner}).
 *
 * <p>Com broca e tubos, desce abrindo um furo e deixa a ponta do tubo no fundo. Com scanner, a
 * cada camada procura minério (tag {@code c:ores}) num quadrado de raio igual à metade do alcance
 * do scanner e "anda" até ele pela camada, quebrando o que estiver no caminho. O que cai vai para
 * o buffer (ou para o chão, se estiver cheio). Sem broca, recolhe os tubos de volta. Fontes de
 * líquido travam o furo, como no IC2 sem bomba.
 */
final class MinerLogic {
    /** Quanto o minerador puxa da rede por tick: a broca de irídio gasta 100.000 CW. */
    static final long MAX_INTAKE = 200_000;

    static final int SCANNER_SLOT = 1;
    static final int PIPE_SLOT = 2;
    static final int DRILL_SLOT = 3;
    static final int BUFFER_START = 5;
    static final int BUFFER_END = 20;

    /** IC2: andar pelo ar e recolher tubo gastam 3 EU/t por 20 ticks. */
    private static final long AIR_POWER = 1_500;
    private static final int AIR_TICKS = 20;
    /** IC2: colher um bloco custa 2 EU por bloco de profundidade. */
    private static final long HARVEST_COST_PER_DEPTH = 1_000;

    private enum Mode { NONE, WITHDRAW, MINE_AIR, MINE_DRILL }

    private enum Result { WORKING, DONE, FAILED_TEMP, FAILED_PERM }

    private final MachineBlockEntity machine;
    private Mode lastMode = Mode.NONE;
    private @Nullable Item lastDrill;
    private int progress;
    private int scannedLevel = Integer.MIN_VALUE;
    private int scanRange;
    private int lastX = Integer.MIN_VALUE;
    private int lastZ = Integer.MIN_VALUE;

    MinerLogic(MachineBlockEntity machine) {
        this.machine = machine;
    }

    /** Um tick do minerador; true se trabalhou. */
    boolean tick(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        if (this.lastX == Integer.MIN_VALUE) {
            this.lastX = pos().getX();
            this.lastZ = pos().getZ();
        }
        chargeTool(SCANNER_SLOT);
        chargeTool(DRILL_SLOT);
        return work(serverLevel);
    }

    private BlockPos pos() {
        return this.machine.getBlockPos();
    }

    private SimpleContainer inventory() {
        return this.machine.getInventory();
    }

    /** O minerador carrega a broca e o scanner com a própria energia. */
    private void chargeTool(int slot) {
        ItemStack stack = inventory().getItem(slot);
        long moved = EnergyItems.charge(stack, this.machine.getStoredEnergy(), this.machine.machineVoltage(), false);
        if (moved > 0 && this.machine.useEnergy(moved)) {
            inventory().setItem(slot, stack);
        }
    }

    private boolean work(ServerLevel level) {
        BlockPos operating = operatingPos(level);
        if (!(inventory().getItem(DRILL_SLOT).getItem() instanceof DrillItem)) {
            return withdrawPipe(level, operating);
        }
        if (operating.getY() < level.getMinY()) return false;

        BlockState state = level.getBlockState(operating);
        if (!isTip(state)) {
            return operating.getY() > level.getMinY() && digDown(level, operating, state, false);
        }
        Result result = mineLevel(level, operating.getY());
        if (result == Result.DONE) {
            BlockPos below = operating.below();
            return digDown(level, below, level.getBlockState(below), true);
        }
        return result == Result.WORKING;
    }

    /** Primeiro bloco abaixo do minerador que não é tubo (a ponta, ou onde o furo termina). */
    private BlockPos operatingPos(Level level) {
        BlockPos pos = pos().below();
        while (pos.getY() >= level.getMinY() && isPipe(level.getBlockState(pos))) {
            pos = pos.below();
        }
        return pos;
    }

    private static boolean isPipe(BlockState state) {
        return state.is(IC2AutoBlocks.MINING_PIPE.get()) && !state.getValue(MiningPipeBlock.TIP);
    }

    private static boolean isTip(BlockState state) {
        return state.is(IC2AutoBlocks.MINING_PIPE.get()) && state.getValue(MiningPipeBlock.TIP);
    }

    private static BlockState pipeState(boolean tip) {
        return IC2AutoBlocks.MINING_PIPE.get().defaultBlockState().setValue(MiningPipeBlock.TIP, tip);
    }

    // ── recolher tubos ────────────────────────────────────────────────────
    private boolean withdrawPipe(ServerLevel level, BlockPos operating) {
        if (this.lastMode != Mode.WITHDRAW) {
            this.lastMode = Mode.WITHDRAW;
            this.progress = 0;
        }
        if (operating.getY() < level.getMinY() || !isTip(level.getBlockState(operating))) {
            operating = operating.above();
        }
        if (operating.getY() == pos().getY() || this.machine.getStoredEnergy() < AIR_POWER) return false;

        if (this.progress < AIR_TICKS) {
            this.machine.useEnergy(AIR_POWER);
            this.progress++;
        } else {
            this.progress = 0;
            removePipe(level, operating);
        }
        return true;
    }

    /** Tira o tubo e, se houver um bloco no slot de tubos, tapa o buraco com ele. */
    private void removePipe(ServerLevel level, BlockPos pos) {
        level.removeBlock(pos, false);
        storeDrop(level, new ItemStack(IC2AutoItems.MINING_PIPE.get()));

        ItemStack filler = inventory().getItem(PIPE_SLOT);
        if (!filler.isEmpty() && filler.getItem() != IC2AutoItems.MINING_PIPE.get() && filler.getItem() instanceof BlockItem blockItem) {
            level.setBlock(pos, blockItem.getBlock().defaultBlockState(), Block.UPDATE_ALL);
            filler.shrink(1);
            inventory().setItem(PIPE_SLOT, filler);
        }
    }

    // ── descer ────────────────────────────────────────────────────────────
    private boolean digDown(ServerLevel level, BlockPos pos, BlockState state, boolean removeTipAbove) {
        ItemStack pipes = inventory().getItem(PIPE_SLOT);
        if (pipes.getItem() != IC2AutoItems.MINING_PIPE.get()) return false;

        if (pos.getY() < level.getMinY()) {
            if (removeTipAbove) level.setBlock(pos.above(), pipeState(false), Block.UPDATE_ALL);
            return false;
        }

        Result result = mineBlock(level, pos, state);
        if (result == Result.FAILED_TEMP || result == Result.FAILED_PERM) {
            if (removeTipAbove) level.setBlock(pos.above(), pipeState(false), Block.UPDATE_ALL);
            return false;
        }
        if (result == Result.DONE) {
            if (removeTipAbove) level.setBlock(pos.above(), pipeState(false), Block.UPDATE_ALL);
            pipes.shrink(1);
            inventory().setItem(PIPE_SLOT, pipes);
            level.setBlock(pos, pipeState(true), Block.UPDATE_ALL);
        }
        return true;
    }

    // ── minerar a camada ──────────────────────────────────────────────────
    private Result mineLevel(ServerLevel level, int y) {
        ItemStack scanner = inventory().getItem(SCANNER_SLOT);
        if (!(scanner.getItem() instanceof ScannerItem scannerItem)) return Result.DONE;

        if (this.scannedLevel != y) {
            this.scanRange = scannerItem.startLayerScan(scanner);
            inventory().setItem(SCANNER_SLOT, scanner);
        }
        if (this.scanRange <= 0) return Result.FAILED_TEMP;
        this.scannedLevel = y;

        BlockPos pos = pos();
        for (int x = pos.getX() - this.scanRange; x <= pos.getX() + this.scanRange; x++) {
            for (int z = pos.getZ() - this.scanRange; z <= pos.getZ() + this.scanRange; z++) {
                BlockPos target = new BlockPos(x, y, z);
                BlockState state = level.getBlockState(target);
                if (state.is(ConventionalBlockTags.ORES) && canMine(level, target, state)) {
                    Result result = mineTowards(level, target);
                    if (result == Result.DONE) return Result.WORKING;
                    if (result != Result.FAILED_PERM) return result;
                }
            }
        }
        return Result.DONE;
    }

    /** Anda da coluna do minerador até o minério (linha de Bresenham), quebrando o que bloqueia. */
    private Result mineTowards(ServerLevel level, BlockPos destination) {
        BlockPos pos = pos();
        int dx = Math.abs(destination.getX() - pos.getX());
        int sx = pos.getX() < destination.getX() ? 1 : -1;
        int dz = -Math.abs(destination.getZ() - pos.getZ());
        int sz = pos.getZ() < destination.getZ() ? 1 : -1;
        int error = dx + dz;
        int cx = pos.getX();
        int cz = pos.getZ();

        while (cx != destination.getX() || cz != destination.getZ()) {
            boolean fromCurrent = cx == this.lastX && cz == this.lastZ;
            int doubled = 2 * error;
            if (doubled > dz) {
                error += dz;
                cx += sx;
            } else if (doubled < dx) {
                error += dx;
                cz += sz;
            }

            BlockPos target = new BlockPos(cx, destination.getY(), cz);
            BlockState state = level.getBlockState(target);
            boolean blocking;
            if (fromCurrent) {
                blocking = true;
            } else if (!state.isAir()) {
                FluidState fluid = state.getFluidState();
                blocking = fluid.isEmpty() || fluid.isSource();
            } else {
                blocking = false;
            }

            if (blocking) {
                Result result = mineBlock(level, target, state);
                if (result == Result.DONE) {
                    this.lastX = cx;
                    this.lastZ = cz;
                }
                return result;
            }
        }
        this.lastX = pos.getX();
        this.lastZ = pos.getZ();
        return Result.DONE;
    }

    private Result mineBlock(ServerLevel level, BlockPos target, BlockState state) {
        boolean air = state.isAir();
        if (!air) {
            FluidState fluid = state.getFluidState();
            if (!fluid.isEmpty() && fluid.isSource()) return Result.FAILED_PERM; // IC2 sem bomba: fonte de líquido trava
            if (fluid.isEmpty() && !canMine(level, target, state)) return Result.FAILED_PERM;
        }

        ItemStack drillStack = inventory().getItem(DRILL_SLOT);
        Mode mode;
        long power;
        int duration;
        if (air) {
            mode = Mode.MINE_AIR;
            power = AIR_POWER;
            duration = AIR_TICKS;
        } else if (drillStack.getItem() instanceof DrillItem drill) {
            mode = Mode.MINE_DRILL;
            power = drill.minerPower();
            duration = drill.minerTicks();
        } else {
            return Result.FAILED_TEMP;
        }

        if (this.lastMode != mode || (mode == Mode.MINE_DRILL && this.lastDrill != drillStack.getItem())) {
            this.lastMode = mode;
            this.lastDrill = drillStack.getItem();
            this.progress = 0;
        }

        if (this.progress < duration) {
            if (this.machine.useEnergy(power)) {
                this.progress++;
                return Result.WORKING;
            }
            return Result.FAILED_TEMP;
        }
        if (air || harvest(level, target, state, drillStack)) {
            this.progress = 0;
            return Result.DONE;
        }
        return Result.FAILED_TEMP;
    }

    private boolean harvest(ServerLevel level, BlockPos target, BlockState state, ItemStack drillStack) {
        long cost = HARVEST_COST_PER_DEPTH * Math.max(0, pos().getY() - target.getY());
        if (this.machine.getStoredEnergy() < cost) return false;
        if (!(drillStack.getItem() instanceof DrillItem drill) || !EnergyItems.use(drillStack, drill.energyPerBlock())) return false;
        inventory().setItem(DRILL_SLOT, drillStack);
        this.machine.useEnergy(cost);

        for (ItemStack drop : Block.getDrops(state, level, target, level.getBlockEntity(target))) {
            storeDrop(level, drop);
        }
        level.removeBlock(target, false);
        return true;
    }

    /** Pode quebrar: ar, blocos que não pedem ferramenta, ou que a broca colhe. Nunca tubos nem blocos com inventário. */
    private boolean canMine(Level level, BlockPos target, BlockState state) {
        if (state.isAir()) return true;
        if (state.is(IC2AutoBlocks.MINING_PIPE.get()) || level.getBlockEntity(target) != null) return false;
        if (state.getDestroySpeed(level, target) < 0.0F) return false;
        if (!state.requiresCorrectToolForDrops()) return true;
        return inventory().getItem(DRILL_SLOT).getItem() instanceof DrillItem drill && drill.canHarvest(state);
    }

    /** Guarda no buffer (juntando pilhas); o que não couber cai em cima do minerador. */
    private void storeDrop(Level level, ItemStack stack) {
        SimpleContainer inventory = inventory();
        for (int slot = BUFFER_START; slot < BUFFER_END && !stack.isEmpty(); slot++) {
            ItemStack current = inventory.getItem(slot);
            if (!current.isEmpty() && ItemStack.isSameItemSameComponents(current, stack)) {
                int moved = Math.min(stack.getCount(), current.getMaxStackSize() - current.getCount());
                if (moved > 0) {
                    current.grow(moved);
                    stack.shrink(moved);
                    inventory.setItem(slot, current);
                }
            }
        }
        for (int slot = BUFFER_START; slot < BUFFER_END && !stack.isEmpty(); slot++) {
            if (inventory.getItem(slot).isEmpty()) {
                inventory.setItem(slot, stack.copy());
                stack.setCount(0);
            }
        }
        if (!stack.isEmpty()) {
            BlockPos pos = pos();
            Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
        }
    }

    void write(ValueOutput output) {
        output.putInt("MinerMode", this.lastMode.ordinal());
        output.putInt("MinerProgress", this.progress);
    }

    void read(ValueInput input) {
        int mode = input.getIntOr("MinerMode", 0);
        this.lastMode = Mode.values()[Math.max(0, Math.min(Mode.values().length - 1, mode))];
        this.progress = input.getIntOr("MinerProgress", 0);
    }
}
