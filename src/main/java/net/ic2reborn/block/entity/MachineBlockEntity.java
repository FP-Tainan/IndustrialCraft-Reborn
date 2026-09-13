package net.ic2reborn.block.entity;

import net.craftenergy.api.BufferedTransformer;
import net.craftenergy.api.EnergyNode;
import net.craftenergy.api.EnergySink;
import net.craftenergy.api.EnergySource;
import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.EnergyItems;
import net.craftenergy.fabric.CraftEnergyApi;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.ic2reborn.IC2Reborn;
import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.energy.MachineEnergyProfile;
import net.ic2reborn.energy.WindSim;
import net.ic2reborn.menu.MachineGuiType;
import net.ic2reborn.menu.MachineMenu;
import net.ic2reborn.menu.layout.MachineLayout;
import net.ic2reborn.recipe.MachineRecipes;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.FluidTags;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.SmeltingRecipe;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;

/**
 * Block entity genérico das máquinas do IC2 Reborn.
 *
 * <p>O inventário vem do layout da GUI; a parte elétrica vem do {@link MachineEnergyProfile}:
 * <ul>
 *   <li>geradores são {@link EnergySource} (combustão, solar, água, vento);</li>
 *   <li>armazenamentos (BatBox, CESU, MFE, MFSU) têm entrada nas faces laterais — um carregador
 *   que aceita qualquer tensão até a sua, com corrente limitada à nominal — e saída na face da
 *   frente, na tensão do bloco (como no IC2);</li>
 *   <li>máquinas de processamento são {@link EnergySink} com um buffer interno;</li>
 *   <li>transformadores expõem um {@link BufferedTransformer} (alta na frente, baixa nas outras
 *   faces), com os modos do IC2: redstone, abaixa fixo, eleva fixo.</li>
 * </ul>
 * Slots de carga e descarga movem energia de/para itens do Craft Energy (baterias), respeitando
 * o nível de tensão. Os nós são expostos ao Craft Energy pelo lookup registrado em {@code IC2Reborn}.
 */
public class MachineBlockEntity extends BlockEntity implements ExtendedMenuProvider<MachineGuiType> {
    /** Campos lógicos sincronizados com a GUI; cada um viaja como dois valores de 16 bits. */
    public static final int DATA_ENERGY = 0;        // CWh guardados
    public static final int DATA_CAPACITY = 1;      // CWh de capacidade
    public static final int DATA_PROGRESS = 2;      // progresso, combustível ou produção
    public static final int DATA_MAX_PROGRESS = 3;
    public static final int DATA_POWER = 4;         // CW que passaram pelo bloco no último tick
    public static final int DATA_VOLTAGE = 5;       // MV
    public static final int DATA_MODE = 6;          // modo do transformador
    public static final int DATA_COUNT = 7;

    /** Modos do transformador do IC2. */
    public enum TransformerMode { REDSTONE, STEP_DOWN, STEP_UP }

    /** Itens que o reciclador consome sem nunca dar sucata. */
    public static final TagKey<Item> RECYCLER_BLACKLIST = TagKey.create(Registries.ITEM,
            Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "recycler_blacklist"));
    /** Reciclador do IC2: 1 sucata a cada 8 itens, em média. */
    private static final int RECYCLE_CHANCE = 8;
    /** Pó de redstone no slot de descarga, como no IC2 (800 EU → 400 CWh). */
    public static final long REDSTONE_ENERGY = EnergyUnits.fromCWh(400);
    /** Gerador de água do IC2: um balde/célula rende 500 ticks; no máximo 2.000 ticks guardados. */
    public static final int WATER_FUEL_PER_ITEM = 500;
    private static final int MAX_WATER_FUEL = 2_000;
    /** Vento do IC2: 0,1 EU/t por unidade de vento → 50 CW. */
    private static final double WIND_CW_PER_UNIT = 50.0;

    private static final int GENERATOR_FUEL_SLOT = 1;
    private static final int WATER_FUEL_SLOT = 0;
    private static final int PROCESSOR_INPUT_SLOT = 0;
    private static final int PROCESSOR_OUTPUT_SLOT = 1;

    private final MachineGuiType guiType;
    private final MachineEnergyProfile profile;
    private final String recipeKey;
    private final SimpleContainer inventory;
    private final @Nullable EnergyNode energyNode;
    private final @Nullable EnergySource storageOutput;
    private final @Nullable BufferedTransformer transformer;

    private long energy;
    private int progress;
    private int maxProgress;
    private int fuel;
    private int totalFuel;
    private long fuelPower;
    private int lastInputVoltage;
    private long flowThisTick;
    private long lastFlow;
    private TransformerMode transformerMode = TransformerMode.REDSTONE;
    private @Nullable RecipeHolder<SmeltingRecipe> lastSmelting;
    // geradores sem combustível
    private double sunlight;
    private int waterBlocks = -1;
    private int waterMicro;
    private int windObstructions = -1;
    private long windPower = -1;

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
            case STORAGE -> new StorageInput();
            case PROCESSOR -> new ProcessorNode();
            case TRANSFORMER, NONE -> null;
        };
        this.storageOutput = this.profile.role() == MachineEnergyProfile.Role.STORAGE ? new StorageOutput() : null;

        this.transformer = this.profile.role() != MachineEnergyProfile.Role.TRANSFORMER ? null
                : new BufferedTransformer(this.profile.highVoltage(), this.profile.voltage(),
                        this.profile.power(), this.profile.efficiency()) {
                    @Override
                    protected void onChanged() {
                        MachineBlockEntity.this.setChanged();
                    }

                    @Override
                    protected void onOvervoltage(int voltage) {
                        explode();
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

    /** Energia guardada, em CW·tick. */
    public long getStoredEnergy() {
        return this.energy;
    }

    /** Define a energia guardada (limitada à capacidade), em CW·tick. */
    public void setStoredEnergy(long energy) {
        this.energy = Math.max(0, Math.min(this.profile.capacity(), energy));
        setChanged();
    }

    public TransformerMode getTransformerMode() {
        return this.transformerMode;
    }

    /** Botão da GUI: 0 = redstone, 1 = abaixa fixo, 2 = eleva fixo. Só vale para transformadores. */
    public boolean setTransformerMode(int mode) {
        if (this.transformer == null || mode < 0 || mode >= TransformerMode.values().length) return false;
        this.transformerMode = TransformerMode.values()[mode];
        setChanged();
        return true;
    }

    /** Nó principal (armazenamentos: a entrada). Para nós por face, use {@link #getEnergyNode(Direction)}. */
    public @Nullable EnergyNode getEnergyNode() {
        return this.energyNode;
    }

    /**
     * Nó exposto numa face. Transformador: frente = alta, demais = baixa.
     * Armazenamento: frente = saída, demais = entrada.
     */
    public @Nullable EnergyNode getEnergyNode(@Nullable Direction face) {
        if (this.transformer != null) {
            if (face == null) return null;
            return face == front() ? this.transformer.highSide() : this.transformer.lowSide();
        }
        if (this.storageOutput != null && face != null && face == front()) {
            return this.storageOutput;
        }
        return this.energyNode;
    }

    private Direction front() {
        return this.getBlockState().getValue(MachineBlock.FACING);
    }

    // ── tick ──────────────────────────────────────────────────────────────
    public static void serverTick(Level level, BlockPos pos, BlockState state, MachineBlockEntity machine) {
        machine.tickServer(level);
    }

    private void tickServer(Level level) {
        // a rede roda depois dos block entities; o fluxo do tick anterior vira o valor mostrado
        this.lastFlow = this.flowThisTick;
        this.flowThisTick = 0;

        boolean changed = handleEnergyItems();
        changed |= switch (this.profile.role()) {
            case GENERATOR -> switch (this.guiType) {
                case SOLAR_GENERATOR -> tickSolar(level);
                case WATER_GENERATOR -> tickWater(level);
                case WIND_GENERATOR -> tickWind(level);
                default -> tickGenerator(level);
            };
            case PROCESSOR -> tickProcessor(level);
            case TRANSFORMER -> tickTransformer(level);
            case STORAGE, NONE -> false;
        };
        if (changed) setChanged();
    }

    // ── slots de carga e descarga ─────────────────────────────────────────
    private int chargeSlot() {
        return switch (this.guiType) {
            case GENERATOR, SOLAR_GENERATOR, WIND_GENERATOR, BATBOX, CESU, MFE, MFSU -> 0;
            case WATER_GENERATOR -> 1;
            default -> -1;
        };
    }

    private int dischargeSlot() {
        return switch (this.profile.role()) {
            case STORAGE -> 1;
            case PROCESSOR -> 2;
            default -> -1;
        };
    }

    /** Carrega a bateria do slot de carga e puxa energia do slot de descarga (bateria ou redstone). */
    private boolean handleEnergyItems() {
        boolean changed = false;

        int chargeSlot = chargeSlot();
        if (chargeSlot >= 0 && chargeSlot < this.inventory.getContainerSize() && this.energy > 0) {
            ItemStack stack = this.inventory.getItem(chargeSlot);
            long moved = EnergyItems.charge(stack, this.energy, this.profile.voltage(), false);
            if (moved > 0) {
                this.energy -= moved;
                this.inventory.setItem(chargeSlot, stack);
                changed = true;
            }
        }

        int dischargeSlot = dischargeSlot();
        if (dischargeSlot >= 0 && dischargeSlot < this.inventory.getContainerSize()) {
            ItemStack stack = this.inventory.getItem(dischargeSlot);
            long room = this.profile.capacity() - this.energy;
            if (stack.getItem() == Items.REDSTONE) {
                // máquinas com buffer menor que uma redstone ainda aceitam quando estão vazias
                if (room >= REDSTONE_ENERGY || this.energy == 0) {
                    stack.shrink(1);
                    this.inventory.setItem(dischargeSlot, stack);
                    this.energy = Math.min(this.profile.capacity(), this.energy + REDSTONE_ENERGY);
                    changed = true;
                }
            } else if (room > 0) {
                long moved = EnergyItems.discharge(stack, room, this.profile.voltage(), false);
                if (moved > 0) {
                    this.energy += moved;
                    this.inventory.setItem(dischargeSlot, stack);
                    changed = true;
                }
            }
        }
        return changed;
    }

    // ── geradores ─────────────────────────────────────────────────────────
    /** Soma produção ao buffer, sem passar da capacidade. */
    private boolean produce(long power) {
        long added = Math.min(power, this.profile.capacity() - this.energy);
        if (added <= 0) return false;
        this.energy += added;
        return true;
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

    /** Solar do IC2: luz do céu ÷ 15, só de dia, reduzida pela chuva e pela tempestade. */
    private boolean tickSolar(Level level) {
        if (level.getGameTime() % 20 == 0) {
            this.sunlight = sunlight(level, this.worldPosition);
        }
        long production = Math.round(this.profile.power() * this.sunlight);
        this.progress = (int) production;
        this.maxProgress = (int) this.profile.power();
        return produce(production);
    }

    public static double sunlight(Level level, BlockPos pos) {
        if (!level.isBrightOutside()) return 0.0;
        double sky = level.getBrightness(LightLayer.SKY, pos.above()) / 15.0;
        double weather = (1.0 - level.getRainLevel(1.0F) * 5.0 / 16.0) * (1.0 - level.getThunderLevel(1.0F) * 5.0 / 16.0);
        return Math.max(0.0, Math.min(1.0, sky * weather));
    }

    /**
     * Gerador de água do IC2: balde de água rende 500 CW e célula de água 1.000 CW, por 500 ticks.
     * Sem combustível, cada bloco de água no cubo 3×3×3 em volta soma 5 CW.
     */
    private boolean tickWater(Level level) {
        boolean changed = false;

        if (this.fuel + WATER_FUEL_PER_ITEM <= MAX_WATER_FUEL) {
            ItemStack stack = this.inventory.getItem(WATER_FUEL_SLOT);
            ItemStack container = ItemStack.EMPTY;
            long power = 0;
            if (stack.getItem() == Items.WATER_BUCKET) {
                container = new ItemStack(Items.BUCKET);
                power = 500;
            } else if (stack.getItem() == IC2AutoItems.WATER_CELL.get()) {
                container = new ItemStack(IC2AutoItems.FLUID_CELL.get());
                power = 1_000;
            }
            if (power > 0) {
                if (stack.getCount() == 1) {
                    this.inventory.setItem(WATER_FUEL_SLOT, container);
                } else {
                    stack.shrink(1);
                    this.inventory.setItem(WATER_FUEL_SLOT, stack);
                    Containers.dropItemStack(level, this.worldPosition.getX() + 0.5, this.worldPosition.getY() + 1.0,
                            this.worldPosition.getZ() + 0.5, container);
                }
                this.fuel += WATER_FUEL_PER_ITEM;
                this.fuelPower = power;
                changed = true;
            }
        }

        if (this.fuel > 0) {
            if (produce(this.fuelPower)) {
                this.fuel--;
                changed = true;
            }
        } else {
            if (this.waterBlocks < 0 || level.getGameTime() % 128 == 0) {
                this.waterBlocks = countWater(level);
            }
            this.waterMicro += this.waterBlocks;
            long power = (this.waterMicro / 100) * 500L;
            this.waterMicro %= 100;
            if (power > 0) changed |= produce(power);
        }

        this.progress = this.fuel;
        this.maxProgress = MAX_WATER_FUEL;
        return changed;
    }

    private int countWater(Level level) {
        int count = 0;
        for (BlockPos pos : BlockPos.betweenClosed(this.worldPosition.offset(-1, -1, -1), this.worldPosition.offset(1, 1, 1))) {
            if (level.getFluidState(pos).is(FluidTags.WATER)) count++;
        }
        return count;
    }

    /** Eólico do IC2: vento da dimensão na altura do bloco, menos os blocos em volta (9×7×9). */
    private boolean tickWind(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) return false;
        long time = level.getGameTime();
        if (this.windObstructions < 0 || time % 1024 == 0) {
            this.windObstructions = countObstructions(level);
        }
        if (this.windPower < 0 || time % 128 == 0) {
            double wind = WindSim.get(serverLevel).windAt(serverLevel, this.worldPosition.getY())
                    * (1.0 - this.windObstructions / 567.0);
            this.windPower = Math.max(0, Math.min(this.profile.power(), Math.round(wind * WIND_CW_PER_UNIT)));
        }
        this.progress = (int) this.windPower;
        this.maxProgress = (int) this.profile.power();
        return produce(this.windPower);
    }

    private int countObstructions(Level level) {
        int count = -1; // o próprio gerador não conta
        for (BlockPos pos : BlockPos.betweenClosed(this.worldPosition.offset(-4, -2, -4), this.worldPosition.offset(4, 4, 4))) {
            if (!level.getBlockState(pos).isAir()) count++;
        }
        return Math.max(0, count);
    }

    // ── transformador ─────────────────────────────────────────────────────
    /** Modo redstone: eleva a tensão com sinal de redstone, abaixa sem sinal. */
    private boolean tickTransformer(Level level) {
        if (this.transformer == null) return false;
        BufferedTransformer.Mode wanted = switch (this.transformerMode) {
            case STEP_UP -> BufferedTransformer.Mode.STEP_UP;
            case STEP_DOWN -> BufferedTransformer.Mode.STEP_DOWN;
            case REDSTONE -> level.hasNeighborSignal(this.worldPosition)
                    ? BufferedTransformer.Mode.STEP_UP : BufferedTransformer.Mode.STEP_DOWN;
        };
        if (this.transformer.mode() == wanted) return false;

        this.transformer.setMode(wanted);
        // os nós de alta e baixa trocaram de papel (entrada/saída)
        CraftEnergyApi.markChanged(level, this.worldPosition);
        return true;
    }

    // ── máquinas de processamento ─────────────────────────────────────────
    /** Máquina padrão do IC2: gasta a potência por tick e completa a operação no fim da duração. */
    private boolean tickProcessor(Level level) {
        ItemStack input = this.inventory.getItem(PROCESSOR_INPUT_SLOT);
        Operation operation = findOperation(level, input);

        if (operation == null || !canOutput(operation.preview())) {
            if (this.progress == 0) return false;
            this.progress = 0;
            return true;
        }
        if (this.energy < this.profile.power()) return false;

        this.energy -= this.profile.power();
        if (++this.progress >= this.profile.operationTicks()) {
            this.progress = 0;
            ItemStack result = operation.result().apply(level.getRandom());
            input.shrink(operation.inputCount());
            this.inventory.setItem(PROCESSOR_INPUT_SLOT, input);

            if (!result.isEmpty()) {
                ItemStack output = this.inventory.getItem(PROCESSOR_OUTPUT_SLOT);
                if (output.isEmpty()) {
                    this.inventory.setItem(PROCESSOR_OUTPUT_SLOT, result);
                } else {
                    output.grow(result.getCount());
                    this.inventory.setItem(PROCESSOR_OUTPUT_SLOT, output);
                }
            }
        }
        return true;
    }

    /**
     * Operação possível para a entrada atual.
     *
     * @param inputCount quantos itens a operação consome
     * @param preview    maior resultado possível, para conferir se cabe na saída
     * @param result     resultado real (o reciclador depende de sorte)
     */
    private record Operation(int inputCount, ItemStack preview, Function<RandomSource, ItemStack> result) {}

    private @Nullable Operation findOperation(Level level, ItemStack input) {
        if (input.isEmpty()) return null;
        return switch (this.guiType) {
            case ELECTRIC_FURNACE -> smeltingOperation(level, input);
            case RECYCLER -> recyclingOperation(input);
            default -> MachineRecipes.INSTANCE.find(this.recipeKey, input)
                    .map(recipe -> new Operation(recipe.inputCount(), recipe.createResult(), random -> recipe.createResult()))
                    .orElse(null);
        };
    }

    /** Fornalha elétrica: as mesmas receitas da fornalha do vanilla. */
    private @Nullable Operation smeltingOperation(Level level, ItemStack input) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        Optional<RecipeHolder<SmeltingRecipe>> recipe = this.lastSmelting == null
                ? serverLevel.recipeAccess().getRecipeFor(RecipeType.SMELTING, recipeInput, serverLevel)
                : serverLevel.recipeAccess().getRecipeFor(RecipeType.SMELTING, recipeInput, serverLevel, this.lastSmelting);
        if (recipe.isEmpty()) return null;

        this.lastSmelting = recipe.get();
        ItemStack result = recipe.get().value().assemble(recipeInput);
        if (result.isEmpty()) return null;
        return new Operation(1, result, random -> result.copy());
    }

    /** Reciclador: consome qualquer item; 1 em 8 vira sucata (exceto a lista negra). */
    private Operation recyclingOperation(ItemStack input) {
        Item scrap = IC2AutoItems.SCRAP.get();
        boolean blacklisted = BuiltInRegistries.ITEM.wrapAsHolder(input.getItem()).is(RECYCLER_BLACKLIST);
        return new Operation(1, new ItemStack(scrap),
                random -> !blacklisted && random.nextInt(RECYCLE_CHANCE) == 0 ? new ItemStack(scrap) : ItemStack.EMPTY);
    }

    private boolean canOutput(ItemStack result) {
        ItemStack output = this.inventory.getItem(PROCESSOR_OUTPUT_SLOT);
        if (output.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(output, result)
                && output.getCount() + result.getCount() <= output.getMaxStackSize();
    }

    private void explode() {
        if (this.level != null) {
            int voltage = this.transformer != null ? this.profile.highVoltage() : this.profile.voltage();
            CraftEnergyApi.explodeFromOvervoltage(this.level, this.worldPosition, voltage);
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

    /**
     * Entrada do armazenamento: aceita qualquer tensão até a nominal (+10%), com corrente
     * limitada à nominal. CESU: 20 RA → 20.000 CW a 1.000 MV, ou 4.400 CW vindo de 220 MV.
     */
    private final class StorageInput implements EnergySink {
        @Override
        public int nominalVoltage() {
            return profile.voltage();
        }

        @Override
        public int minimumVoltage() {
            return 1;
        }

        @Override
        public long powerDemand() {
            long room = profile.capacity() - energy;
            long byCurrent = (long) (lastInputVoltage * ((double) profile.power() / profile.voltage()));
            return Math.max(0, Math.min(profile.power(), Math.min(byCurrent, room)));
        }

        @Override
        public void receivePower(long power, int voltage) {
            lastInputVoltage = voltage;
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

    /** Saída do armazenamento, na face da frente, na tensão do bloco. */
    private final class StorageOutput implements EnergySource {
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
            flowThisTick -= power;
            setChanged();
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
            case DATA_MODE -> this.transformerMode.ordinal();
            default -> 0;
        };
    }

    private static int clampToInt(long value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, value));
    }

    /** Quebrar, desmontar ou explodir a máquina solta o inventário no chão. */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        if (this.level != null) {
            Containers.dropContents(this.level, pos, this.inventory);
        }
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
        output.putLong("FuelPower", this.fuelPower);
        if (this.transformer != null) {
            output.putLong("TransformerBuffer", this.transformer.bufferedPower());
            output.putInt("TransformerMode", this.transformerMode.ordinal());
        }
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
        this.fuelPower = input.getLongOr("FuelPower", 0);
        if (this.profile.role() == MachineEnergyProfile.Role.GENERATOR && this.guiType == MachineGuiType.GENERATOR) {
            this.maxProgress = this.totalFuel;
        }
        if (this.transformer != null) {
            this.transformer.setBufferedPower(input.getLongOr("TransformerBuffer", 0));
            int mode = input.getIntOr("TransformerMode", TransformerMode.REDSTONE.ordinal());
            this.transformerMode = TransformerMode.values()[Math.max(0, Math.min(TransformerMode.values().length - 1, mode))];
        }
    }
}
