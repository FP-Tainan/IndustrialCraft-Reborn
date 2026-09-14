package net.ic2reborn.block.entity;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.fluid.MachineFluids;
import net.ic2reborn.item.CrystalMemoryItem;
import net.ic2reborn.menu.MachineGuiType;
import net.ic2reborn.recipe.UuValues;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.OptionalDouble;

/**
 * UU-matter do IC2: fabricador de massa (energia → UU, sucata amplifica), scanner (lê o molde de um
 * item para a memória de cristal ou o armazenamento de moldes), armazenamento de moldes e replicador
 * (gasta UU e energia para recriar o molde escolhido).
 */
final class UuLogic {
    private static final long MB = FluidConstants.BUCKET / 1_000;

    // fabricador: amplificador 0, saída 1, célula 2
    static final int FAB_AMPLIFIER = 0, FAB_OUTPUT = 1, FAB_CONTAINER = 2;
    /** Sucata: 5.000 EU de amplificação; caixa de sucata: 45.000 EU (em CW·tick, 1 EU = 500). */
    private static final long SCRAP_VALUE = 2_500_000, SCRAP_BOX_VALUE = 22_500_000;
    private static final long MAX_SCRAP = 5_000_000;

    // scanner: descarga 0, entrada 1, memória 2
    static final int SCAN_INPUT = 1, SCAN_DISK = 2;
    static final int STATE_IDLE = 0, STATE_SCANNING = 1, STATE_COMPLETED = 2, STATE_FAILED = 3, STATE_NO_STORAGE = 4,
            STATE_NO_ENERGY = 5, STATE_TRANSFER_ERROR = 6, STATE_ALREADY_RECORDED = 7;

    // replicador: descarga 0, saída 1, UU 2 → célula 3
    static final int REP_OUTPUT = 1;
    static final int MODE_STOPPED = 0, MODE_SINGLE = 1, MODE_REPEAT = 2;
    /** Replicador do IC2: 0,1 mB de UU por tick (unidades de custo: 10 por tick). */
    private static final double UNITS_PER_TICK = 10.0;
    private static final int REPLICATOR_BASE_TICKS = 1_000;

    private final MachineBlockEntity machine;
    private final MachineGuiType type;
    private boolean working;

    private long scrap;
    private int state;
    private int progress;
    private @Nullable Item pattern;
    private double patternCost;
    private int mode;
    private int index;
    private double processed;
    private final List<Item> patterns = new ArrayList<>();

    private UuLogic(MachineBlockEntity machine, MachineGuiType type) {
        this.machine = machine;
        this.type = type;
    }

    static @Nullable UuLogic create(MachineBlockEntity machine, MachineGuiType type) {
        return switch (type) {
            case MASS_FABRICATOR, SCANNER, REPLICATOR, PATTERN_STORAGE -> new UuLogic(machine, type);
            default -> null;
        };
    }

    boolean working() {
        return this.working;
    }

    boolean canPlace(int slot, ItemStack stack) {
        return switch (this.type) {
            case MASS_FABRICATOR -> slot != FAB_AMPLIFIER || stack.is(IC2AutoItems.SCRAP.get()) || stack.is(IC2AutoItems.SCRAP_BOX.get());
            case SCANNER -> slot != SCAN_DISK || stack.getItem() instanceof CrystalMemoryItem;
            case PATTERN_STORAGE -> stack.getItem() instanceof CrystalMemoryItem;
            default -> true;
        };
    }

    // ── GUI ───────────────────────────────────────────────────────────────
    @Nullable Integer dataValue(int field) {
        Item shown = this.type == MachineGuiType.PATTERN_STORAGE ? selectedPattern() : this.pattern;
        int costUnits = (int) Math.min(Integer.MAX_VALUE, Math.ceil(this.patternCost));
        return switch (this.type) {
            case MASS_FABRICATOR -> field == MachineBlockEntity.DATA_HEAT ? (int) (this.scrap / 500) : null;
            case SCANNER -> switch (field) {
                case MachineBlockEntity.DATA_MODE -> this.state;
                case MachineBlockEntity.DATA_PROGRESS -> this.progress;
                case MachineBlockEntity.DATA_MAX_PROGRESS -> Math.max(1, this.machine.getEnergyProfile().operationTicks());
                case MachineBlockEntity.DATA_HEAT -> shown == null ? 0 : BuiltInRegistries.ITEM.getId(shown) + 1;
                case MachineBlockEntity.DATA_MAX_HEAT -> costUnits;
                default -> null;
            };
            case REPLICATOR -> switch (field) {
                case MachineBlockEntity.DATA_MODE -> this.mode | (this.index << 2) | (this.patternCount() << 16);
                case MachineBlockEntity.DATA_PROGRESS -> (int) this.processed;
                case MachineBlockEntity.DATA_MAX_PROGRESS -> costUnits;
                case MachineBlockEntity.DATA_HEAT -> shown == null ? 0 : BuiltInRegistries.ITEM.getId(shown) + 1;
                case MachineBlockEntity.DATA_MAX_HEAT -> costUnits;
                default -> null;
            };
            case PATTERN_STORAGE -> switch (field) {
                case MachineBlockEntity.DATA_MODE -> this.index | (this.patterns.size() << 16);
                case MachineBlockEntity.DATA_HEAT -> shown == null ? 0 : BuiltInRegistries.ITEM.getId(shown) + 1;
                case MachineBlockEntity.DATA_MAX_HEAT -> shown == null ? 0 : (int) Math.ceil(costOf(shown));
                default -> null;
            };
            default -> null;
        };
    }

    private int patternCount() {
        MachineBlockEntity storage = findStorage();
        return storage == null || storage.uuLogic() == null ? 0 : storage.uuLogic().patterns.size();
    }

    boolean handleButton(int id) {
        int button = id - MachineBlockEntity.BUTTON_UU;
        boolean handled = switch (this.type) {
            case SCANNER -> switch (button) {
                case 0 -> {
                    resetScan();
                    yield true;
                }
                case 1 -> {
                    record();
                    yield true;
                }
                default -> false;
            };
            case REPLICATOR -> switch (button) {
                case 10, 11 -> {
                    int count = patternCount();
                    if (count > 0) this.index = Math.floorMod(this.index + (button == 10 ? -1 : 1), count);
                    this.mode = MODE_STOPPED;
                    this.processed = 0;
                    yield true;
                }
                case 13 -> {
                    this.mode = MODE_STOPPED;
                    yield true;
                }
                case 14 -> {
                    this.mode = MODE_SINGLE;
                    yield true;
                }
                case 15 -> {
                    this.mode = MODE_REPEAT;
                    yield true;
                }
                default -> false;
            };
            case PATTERN_STORAGE -> switch (button) {
                case 20, 21 -> {
                    if (!this.patterns.isEmpty()) this.index = Math.floorMod(this.index + (button == 20 ? -1 : 1), this.patterns.size());
                    yield true;
                }
                case 22 -> {
                    ItemStack disk = this.machine.getInventory().getItem(0);
                    Item selected = selectedPattern();
                    if (disk.getItem() instanceof CrystalMemoryItem && selected != null) {
                        CrystalMemoryItem.setPattern(disk, selected);
                        this.machine.getInventory().setItem(0, disk);
                    }
                    yield true;
                }
                case 23 -> {
                    ItemStack disk = this.machine.getInventory().getItem(0);
                    Item recorded = CrystalMemoryItem.pattern(disk);
                    if (recorded != null) addPattern(recorded);
                    yield true;
                }
                default -> false;
            };
            default -> false;
        };
        if (handled) this.machine.setChanged();
        return handled;
    }

    // ── moldes ────────────────────────────────────────────────────────────
    List<Item> patterns() {
        return this.patterns;
    }

    boolean addPattern(Item item) {
        if (this.patterns.contains(item)) return false;
        this.patterns.add(item);
        this.machine.setChanged();
        return true;
    }

    private @Nullable Item selectedPattern() {
        if (this.patterns.isEmpty()) return null;
        if (this.index < 0 || this.index >= this.patterns.size()) this.index = 0;
        return this.patterns.get(this.index);
    }

    private double costOf(Item item) {
        if (!(this.machine.getLevel() instanceof ServerLevel server)) return 0;
        OptionalDouble cost = UuValues.cost(server.getServer(), item);
        return cost.isPresent() ? cost.getAsDouble() : 0;
    }

    private @Nullable MachineBlockEntity findStorage() {
        Level level = this.machine.getLevel();
        if (level == null) return null;
        BlockPos pos = this.machine.getBlockPos();
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof MachineBlockEntity other
                    && other.getGuiType() == MachineGuiType.PATTERN_STORAGE) {
                return other;
            }
        }
        return null;
    }

    // ── tick ──────────────────────────────────────────────────────────────
    boolean tick(Level level) {
        this.working = false;
        if (!(level instanceof ServerLevel server)) return false;
        return switch (this.type) {
            case MASS_FABRICATOR -> tickFabricator(server);
            case SCANNER -> tickScanner(server);
            case REPLICATOR -> tickReplicator(server);
            default -> false;
        };
    }

    /**
     * Fabricador de massa do IC2: sem redstone, com o buffer cheio, gasta toda a energia e faz 1 mB de
     * UU-matter. A sucata guardada soma cinco vezes a energia recebida em cada tick.
     */
    private boolean tickFabricator(ServerLevel level) {
        MachineBlockEntity.MachineTank tank = this.machine.mainTank();
        if (tank == null) return false;
        SimpleContainer inventory = this.machine.getInventory();
        boolean changed = MachineFluids.fillFromTank(inventory, FAB_CONTAINER, FAB_OUTPUT, tank);
        if (level.hasNeighborSignal(this.machine.getBlockPos())) return changed;

        if (this.scrap < MAX_SCRAP) {
            ItemStack amplifier = inventory.getItem(FAB_AMPLIFIER);
            long value = amplifier.is(IC2AutoItems.SCRAP.get()) ? SCRAP_VALUE : amplifier.is(IC2AutoItems.SCRAP_BOX.get()) ? SCRAP_BOX_VALUE : 0;
            if (value > 0) {
                amplifier.shrink(1);
                inventory.setItem(FAB_AMPLIFIER, amplifier);
                this.scrap += value;
                changed = true;
            }
        }
        long gained = this.machine.getStoredEnergy() - this.machine.lastEnergySnapshot();
        if (this.scrap > 0 && gained > 0) {
            long bonus = Math.min(this.scrap, gained);
            this.machine.setStoredEnergy(this.machine.getStoredEnergy() + 5 * bonus);
            this.scrap -= bonus;
            changed = true;
        }
        long capacity = this.machine.getEnergyProfile().capacity();
        this.machine.setProgressDisplay((int) (1000 * this.machine.getStoredEnergy() / Math.max(1, capacity)), 1000);
        FluidVariant uu = FluidVariant.of(IC2Fluids.UU_MATTER.fluid());
        if (this.machine.getStoredEnergy() >= capacity && tank.canFill(uu, MB)) {
            tank.fill(uu, MB);
            this.machine.setStoredEnergy(0);
            changed = true;
        }
        this.working = this.machine.getStoredEnergy() > 0 || gained > 0;
        this.machine.snapshotEnergy();
        return changed;
    }

    /** Scanner do IC2: com 256 EU/t por 3.300 ticks lê o molde do item; o botão de gravar salva na memória ou no armazenamento. */
    private boolean tickScanner(ServerLevel level) {
        SimpleContainer inventory = this.machine.getInventory();
        int ticks = Math.max(1, this.machine.getEnergyProfile().operationTicks());
        this.machine.setProgressDisplay(this.progress, ticks);
        if (this.state == STATE_COMPLETED || this.state == STATE_TRANSFER_ERROR) return false;
        ItemStack input = inventory.getItem(SCAN_INPUT);
        if (input.isEmpty() || (this.pattern != null && !input.is(this.pattern))) {
            boolean had = this.state != STATE_IDLE || this.progress > 0;
            this.state = STATE_IDLE;
            resetScan();
            return had;
        }
        if (findStorage() == null && !(inventory.getItem(SCAN_DISK).getItem() instanceof CrystalMemoryItem)) {
            this.state = STATE_NO_STORAGE;
            this.progress = 0;
            return false;
        }
        OptionalDouble cost = UuValues.cost(level.getServer(), input.getItem());
        if (cost.isEmpty()) {
            this.state = STATE_FAILED;
            return false;
        }
        if (isRecorded(input.getItem())) {
            this.state = STATE_ALREADY_RECORDED;
            this.progress = 0;
            return false;
        }
        if (!this.machine.useEnergy(this.machine.getEnergyProfile().power())) {
            this.state = STATE_NO_ENERGY;
            return false;
        }
        this.pattern = input.getItem();
        this.patternCost = cost.getAsDouble();
        this.state = STATE_SCANNING;
        this.working = true;
        if (++this.progress >= ticks) {
            this.state = STATE_COMPLETED;
            input.shrink(1);
            inventory.setItem(SCAN_INPUT, input);
        }
        return true;
    }

    private boolean isRecorded(Item item) {
        if (CrystalMemoryItem.pattern(this.machine.getInventory().getItem(SCAN_DISK)) == item) return true;
        MachineBlockEntity storage = findStorage();
        return storage != null && storage.uuLogic() != null && storage.uuLogic().patterns.contains(item);
    }

    private void record() {
        if (this.state != STATE_COMPLETED && this.state != STATE_TRANSFER_ERROR || this.pattern == null) {
            resetScan();
            return;
        }
        ItemStack disk = this.machine.getInventory().getItem(SCAN_DISK);
        if (disk.getItem() instanceof CrystalMemoryItem) {
            CrystalMemoryItem.setPattern(disk, this.pattern);
            this.machine.getInventory().setItem(SCAN_DISK, disk);
        } else {
            MachineBlockEntity storage = findStorage();
            if (storage == null || storage.uuLogic() == null) {
                this.state = STATE_TRANSFER_ERROR;
                return;
            }
            storage.uuLogic().addPattern(this.pattern);
        }
        resetScan();
    }

    private void resetScan() {
        this.state = STATE_IDLE;
        this.progress = 0;
        this.pattern = null;
        this.patternCost = 0;
    }

    /** Replicador do IC2: com o molde escolhido no armazenamento vizinho, gasta 0,1 mB de UU e 512 EU por tick até completar o custo. */
    private boolean tickReplicator(ServerLevel level) {
        MachineBlockEntity.MachineTank tank = this.machine.mainTank();
        if (tank == null) return false;
        MachineBlockEntity storage = findStorage();
        List<Item> available = storage == null || storage.uuLogic() == null ? List.of() : storage.uuLogic().patterns;
        Item selected = null;
        if (!available.isEmpty()) {
            if (this.index < 0 || this.index >= available.size()) this.index = 0;
            selected = available.get(this.index);
        }
        if (selected != this.pattern) {
            this.pattern = selected;
            this.processed = 0;
            this.mode = MODE_STOPPED;
            OptionalDouble cost = selected == null ? OptionalDouble.empty() : UuValues.cost(level.getServer(), selected);
            this.patternCost = cost.isPresent() ? cost.getAsDouble() : 0;
        }
        this.machine.setProgressDisplay((int) this.processed, (int) Math.ceil(this.patternCost));
        if (this.mode == MODE_STOPPED || this.pattern == null || this.patternCost <= 0) return false;

        SimpleContainer inventory = this.machine.getInventory();
        ItemStack result = new ItemStack(this.pattern);
        ItemStack output = inventory.getItem(REP_OUTPUT);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, result) || output.getCount() >= output.getMaxStackSize())) return false;

        double speed = (double) REPLICATOR_BASE_TICKS / Math.max(1, this.machine.getEnergyProfile().operationTicks());
        double step = Math.min(UNITS_PER_TICK * speed, this.patternCost - this.processed);
        long drops = (long) Math.ceil(step * MB / 100.0);
        if (tank.amount < drops || this.machine.getStoredEnergy() < this.machine.getEnergyProfile().power()) return false;
        tank.consume(drops);
        this.machine.useEnergy(this.machine.getEnergyProfile().power());
        this.processed += step;
        this.working = true;
        if (this.processed >= this.patternCost - 1.0E-9) {
            this.processed = 0;
            if (output.isEmpty()) {
                inventory.setItem(REP_OUTPUT, result);
            } else {
                output.grow(1);
                inventory.setItem(REP_OUTPUT, output);
            }
            if (this.mode == MODE_SINGLE) this.mode = MODE_STOPPED;
        }
        return true;
    }

    // ── salvar/carregar ───────────────────────────────────────────────────
    void write(ValueOutput output) {
        output.putLong("Scrap", this.scrap);
        output.putInt("UuState", this.state);
        output.putInt("UuProgress", this.progress);
        output.putInt("UuMode", this.mode);
        output.putInt("UuIndex", this.index);
        output.putDouble("UuProcessed", this.processed);
        if (this.pattern != null && this.type == MachineGuiType.SCANNER) {
            output.putString("UuPattern", BuiltInRegistries.ITEM.getKey(this.pattern).toString());
            output.putDouble("UuPatternCost", this.patternCost);
        }
        if (this.type == MachineGuiType.PATTERN_STORAGE) {
            output.store("Patterns", BuiltInRegistries.ITEM.byNameCodec().listOf(), List.copyOf(this.patterns));
        }
    }

    void read(ValueInput input) {
        this.scrap = Math.max(0, input.getLongOr("Scrap", 0));
        this.state = input.getIntOr("UuState", STATE_IDLE);
        this.progress = Math.max(0, input.getIntOr("UuProgress", 0));
        this.mode = input.getIntOr("UuMode", MODE_STOPPED);
        this.index = Math.max(0, input.getIntOr("UuIndex", 0));
        this.processed = input.getDoubleOr("UuProcessed", 0);
        if (this.type == MachineGuiType.SCANNER) {
            this.pattern = input.getString("UuPattern")
                    .flatMap(id -> BuiltInRegistries.ITEM.getOptional(net.minecraft.resources.Identifier.parse(id))).orElse(null);
            this.patternCost = input.getDoubleOr("UuPatternCost", 0);
        }
        if (this.type == MachineGuiType.PATTERN_STORAGE) {
            this.patterns.clear();
            input.read("Patterns", BuiltInRegistries.ITEM.byNameCodec().listOf()).ifPresent(this.patterns::addAll);
        }
    }
}
