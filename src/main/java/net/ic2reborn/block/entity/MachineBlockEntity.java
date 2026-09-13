package net.ic2reborn.block.entity;

import net.craftenergy.api.EnergyBuffer;
import net.craftenergy.api.EnergyNode;
import net.craftenergy.api.EnergySink;
import net.craftenergy.api.EnergySource;
import net.craftenergy.api.EnergyUnits;
import net.craftenergy.fabric.CraftEnergyApi;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.ic2reborn.energy.MachineEnergyProfile;
import net.ic2reborn.menu.MachineGuiType;
import net.ic2reborn.menu.MachineMenu;
import net.ic2reborn.menu.layout.MachineLayout;
import net.ic2reborn.recipe.MachineRecipes;
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
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;

/**
 * Block entity genérico das máquinas do IC2 Reborn.
 *
 * <p>O inventário vem do layout da GUI; a parte elétrica vem do {@link MachineEnergyProfile}:
 * geradores são {@link EnergySource}, armazenamentos são {@link EnergyBuffer} e máquinas de
 * processamento são {@link EnergySink} com um buffer interno. O nó é exposto ao Craft Energy
 * pelo lookup registrado em {@code IC2Reborn}.
 */
public class MachineBlockEntity extends BlockEntity implements ExtendedMenuProvider<MachineGuiType> {
    /** Campos lógicos sincronizados com a GUI; cada um viaja como dois valores de 16 bits. */
    public static final int DATA_ENERGY = 0;        // CWh guardados
    public static final int DATA_CAPACITY = 1;      // CWh de capacidade
    public static final int DATA_PROGRESS = 2;      // progresso (ou combustível restante no gerador)
    public static final int DATA_MAX_PROGRESS = 3;
    public static final int DATA_POWER = 4;         // CW que passaram pelo nó no último tick
    public static final int DATA_VOLTAGE = 5;       // MV
    public static final int DATA_COUNT = 6;

    private static final int GENERATOR_FUEL_SLOT = 1;
    private static final int PROCESSOR_INPUT_SLOT = 0;
    private static final int PROCESSOR_OUTPUT_SLOT = 1;

    private final MachineGuiType guiType;
    private final MachineEnergyProfile profile;
    private final String recipeKey;
    private final SimpleContainer inventory;
    private final @Nullable EnergyNode energyNode;

    private long energy;
    private int progress;
    private int maxProgress;
    private int fuel;
    private int totalFuel;
    private long flowThisTick;
    private long lastFlow;

    private final ContainerData data = new ContainerData() {
        @Override
        public int get(int index) {
            int value = logicalValue(index / 2);
            return (index & 1) == 0 ? value & 0xFFFF : (value >>> 16) & 0xFFFF;
        }

        @Override
        public void set(int index, int value) {
        }

        @Override
        public int getCount() {
            return DATA_COUNT * 2;
        }
    };

    public MachineBlockEntity(BlockPos pos, BlockState state) {
        super(IC2BlockEntities.MACHINE.get(), pos, state);
        this.guiType = MachineGuiType.fromBlockId(BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath());
        this.profile = MachineEnergyProfile.of(this.guiType);
        this.recipeKey = this.guiType.name().toLowerCase(Locale.ROOT);
        this.maxProgress = this.profile.operationTicks();

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

        this.energyNode = switch (this.profile.role()) {
            case GENERATOR -> new GeneratorNode();
            case STORAGE -> new StorageNode();
            case PROCESSOR -> new ProcessorNode();
            case NONE -> null;
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

    /** Nó de energia desta máquina, ou {@code null} se ela ainda não participa da rede. */
    public @Nullable EnergyNode getEnergyNode() {
        return this.energyNode;
    }

    // ── tick ──────────────────────────────────────────────────────────────
    public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
        machine.tickServer(level);
    }

    private void tickServer(Level level) {
        // a rede roda depois dos block entities; o fluxo do tick anterior vira o valor mostrado
        this.lastFlow = this.flowThisTick;
        this.flowThisTick = 0;

        boolean changed = switch (this.profile.role()) {
            case GENERATOR -> tickGenerator(level);
            case PROCESSOR -> tickProcessor();
            case STORAGE, NONE -> false;
        };
        if (changed) setChanged();
    }

    /** Gerador do IC2: queima combustível (tempo de queima ÷ 4) e produz enquanto houver espaço. */
    private boolean tickGenerator(Level level) {
        long production = this.profile.power();
        boolean hasRoom = this.profile.capacity() - this.energy >= production;
        boolean changed = false;

        if (this.fuel <= 0 && hasRoom) {
            ItemStack stack = this.inventory.getItem(GENERATOR_FUEL_SLOT);
            int burn = stack.isEmpty() ? 0 : level.fuelValues().burnDuration(stack) / 4;
            if (burn > 0) {
                this.fuel = burn;
                this.totalFuel = burn;
                if (stack.getItem() == Items.LAVA_BUCKET) {
                    this.inventory.setItem(GENERATOR_FUEL_SLOT, new ItemStack(Items.BUCKET));
                } else {
                    stack.shrink(1);
                    this.inventory.setItem(GENERATOR_FUEL_SLOT, stack);
                }
                changed = true;
            }
        }

        if (this.fuel > 0 && hasRoom) {
            this.energy += production;
            this.fuel--;
            changed = true;
        }

        this.progress = this.fuel;
        this.maxProgress = this.totalFuel;
        return changed;
    }

    /** Máquina padrão do IC2: gasta a potência por tick e completa a receita no fim da duração. */
    private boolean tickProcessor() {
        ItemStack input = this.inventory.getItem(PROCESSOR_INPUT_SLOT);
        Optional<MachineRecipes.Compiled> recipe = MachineRecipes.INSTANCE.find(this.recipeKey, input);

        if (recipe.isEmpty() || !canOutput(recipe.get())) {
            if (this.progress == 0) return false;
            this.progress = 0;
            return true;
        }
        if (this.energy < this.profile.power()) return false;

        this.energy -= this.profile.power();
        if (++this.progress >= this.profile.operationTicks()) {
            this.progress = 0;
            ItemStack result = recipe.get().createResult();
            input.shrink(recipe.get().inputCount());
            this.inventory.setItem(PROCESSOR_INPUT_SLOT, input);

            ItemStack output = this.inventory.getItem(PROCESSOR_OUTPUT_SLOT);
            if (output.isEmpty()) {
                this.inventory.setItem(PROCESSOR_OUTPUT_SLOT, result);
            } else {
                output.grow(result.getCount());
                this.inventory.setItem(PROCESSOR_OUTPUT_SLOT, output);
            }
        }
        return true;
    }

    private boolean canOutput(MachineRecipes.Compiled recipe) {
        ItemStack output = this.inventory.getItem(PROCESSOR_OUTPUT_SLOT);
        if (output.isEmpty()) return true;
        ItemStack result = recipe.createResult();
        return ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void explode() {
        if (this.level != null) {
            CraftEnergyApi.explodeFromOvervoltage(this.level, this.worldPosition, this.profile.voltage());
        }
    }

    // ── nós de energia ────────────────────────────────────────────────────
    private final class GeneratorNode implements EnergySource {
        @Override
        public int outputVoltage() {
            return profile.voltage();
        }

        @Override
        public long availablePower() {
            return Math.min(profile.power(), energy);
        }

        @Override
        public void drawPower(long power) {
            if (power <= 0) return;
            energy = Math.max(0, energy - power);
            flowThisTick += power;
            setChanged();
        }
    }

    private final class StorageNode implements EnergyBuffer {
        @Override
        public int voltage() {
            return profile.voltage();
        }

        @Override
        public long maxChargePower() {
            return profile.power();
        }

        @Override
        public long maxDischargePower() {
            return profile.power();
        }

        @Override
        public long storedEnergy() {
            return energy;
        }

        @Override
        public long energyCapacity() {
            return profile.capacity();
        }

        @Override
        public void charge(long power) {
            if (power <= 0) return;
            energy = Math.min(profile.capacity(), energy + power);
            flowThisTick += power;
            setChanged();
        }

        @Override
        public void discharge(long power) {
            if (power <= 0) return;
            energy = Math.max(0, energy - power);
            flowThisTick -= power;
            setChanged();
        }

        @Override
        public void onOvervoltage(int voltage) {
            explode();
        }
    }

    private final class ProcessorNode implements EnergySink {
        @Override
        public int nominalVoltage() {
            return profile.voltage();
        }

        @Override
        public long powerDemand() {
            return Math.max(0, Math.min(profile.maxIntake(), profile.capacity() - energy));
        }

        @Override
        public void receivePower(long power, int voltage) {
            if (power <= 0) return;
            energy = Math.min(profile.capacity(), energy + power);
            flowThisTick += power;
            setChanged();
        }

        @Override
        public void onOvervoltage(int voltage) {
            explode();
        }
    }

    // ── GUI ───────────────────────────────────────────────────────────────
    private int logicalValue(int field) {
        return switch (field) {
            case DATA_ENERGY -> clampToInt(this.energy / EnergyUnits.TICKS_PER_HOUR);
            case DATA_CAPACITY -> clampToInt(this.profile.capacity() / EnergyUnits.TICKS_PER_HOUR);
            case DATA_PROGRESS -> this.progress;
            case DATA_MAX_PROGRESS -> this.maxProgress;
            case DATA_POWER -> clampToInt(this.lastFlow);
            case DATA_VOLTAGE -> this.profile.voltage();
            default -> 0;
        };
    }

    private static int clampToInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
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

    // ── salvar/carregar ───────────────────────────────────────────────────
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

        output.putLong("Energy", this.energy);
        output.putInt("Progress", this.progress);
        output.putInt("Fuel", this.fuel);
        output.putInt("TotalFuel", this.totalFuel);
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

        this.energy = Math.max(0, Math.min(this.profile.capacity(), input.getLongOr("Energy", 0)));
        this.progress = input.getIntOr("Progress", 0);
        this.fuel = input.getIntOr("Fuel", 0);
        this.totalFuel = input.getIntOr("TotalFuel", 0);
        if (this.profile.role() == MachineEnergyProfile.Role.GENERATOR) {
            this.maxProgress = this.totalFuel;
        }
    }
}
