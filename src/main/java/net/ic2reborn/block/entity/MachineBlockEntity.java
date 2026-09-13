package net.ic2reborn.block.entity;

import net.craftenergy.api.BufferedTransformer;
import net.craftenergy.api.EnergyNode;
import net.craftenergy.api.EnergySink;
import net.craftenergy.api.EnergySource;
import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.EnergyItems;
import net.craftenergy.fabric.CraftEnergyApi;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.SingleFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.FilteringStorage;
import net.ic2reborn.IC2Reborn;
import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.energy.MachineEnergyProfile;
import net.ic2reborn.energy.WindSim;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.fluid.MachineFluids;
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
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;

/**
 * Block entity genérico das máquinas do IC2 Reborn.
 *
 * <p>O inventário vem do layout da GUI; a parte elétrica vem do {@link MachineEnergyProfile}:
 * <ul>
 *   <li>geradores são {@link EnergySource} (combustão, geotérmico, semifluido, solar, água, vento);</li>
 *   <li>armazenamentos (BatBox, CESU, MFE, MFSU) têm entrada nas faces laterais — um carregador
 *   que aceita qualquer tensão até a sua, com corrente limitada à nominal — e saída na face da
 *   frente, na tensão do bloco (como no IC2);</li>
 *   <li>máquinas de processamento são {@link EnergySink} com um buffer interno;</li>
 *   <li>transformadores expõem um {@link BufferedTransformer} (alta na frente, baixa nas outras
 *   faces), com os modos do IC2: redstone, abaixa fixo, eleva fixo.</li>
 * </ul>
 * Slots de carga e descarga movem energia de/para itens do Craft Energy (baterias), respeitando
 * o nível de tensão. Máquinas com fluido têm tanques expostos pelo Transfer API do Fabric e um
 * slot que esvazia baldes e células neles. Enquanto trabalha, a máquina fica {@link MachineBlock#ACTIVE}.
 */
public class MachineBlockEntity extends BlockEntity implements ExtendedMenuProvider<MachineGuiType> {
    /** Campos lógicos sincronizados com a GUI; cada um viaja como dois valores de 16 bits. */
    public static final int DATA_ENERGY = 0;          // CWh guardados
    public static final int DATA_CAPACITY = 1;        // CWh de capacidade
    public static final int DATA_PROGRESS = 2;        // progresso, combustível ou produção
    public static final int DATA_MAX_PROGRESS = 3;
    public static final int DATA_POWER = 4;           // CW que passaram pelo bloco no último tick
    public static final int DATA_VOLTAGE = 5;         // MV
    public static final int DATA_MODE = 6;            // modo do transformador ou da enlatadora
    public static final int DATA_FLUID = 7;           // id do fluido no registro + 1 (0 = vazio)
    public static final int DATA_FLUID_AMOUNT = 8;    // mB
    public static final int DATA_FLUID_CAPACITY = 9;  // mB
    /** Campos por tanque; o tanque de saída vem logo depois (10, 11, 12). */
    public static final int DATA_PER_TANK = 3;
    public static final int DATA_COUNT = 13;

    /** Botões da GUI: 0–2 modos do transformador, 10–13 modos da enlatadora, 14 troca os tanques. */
    public static final int BUTTON_CANNER_MODE = 10;
    public static final int BUTTON_SWAP_TANKS = 14;

    /** Modos do transformador do IC2. */
    public enum TransformerMode { REDSTONE, STEP_DOWN, STEP_UP }

    /** Modos da enlatadora do IC2. */
    public enum CannerMode { BOTTLE_SOLID, EMPTY_LIQUID, BOTTLE_LIQUID, ENRICH_LIQUID }

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
    /** Geotérmico do IC2: 2 mB de lava por tick de produção (1 balde = 500 ticks). */
    private static final long GEO_LAVA_PER_TICK = FluidConstants.BUCKET / 500;
    /** Tanques do IC2: 8 baldes (semifluido: 10). */
    private static final long TANK_CAPACITY = 8 * FluidConstants.BUCKET;
    /** A textura ativa continua alguns ticks depois de parar, para não piscar. */
    private static final int ACTIVE_HOLD_TICKS = 10;

    private static final int GENERATOR_FUEL_SLOT = 1;
    private static final int WATER_FUEL_SLOT = 0;

    /**
     * Combustível do gerador semifluido (IC2: {@code SemiFluidFuelManager}).
     *
     * @param power          CW produzidos por tick
     * @param dropletsPerTick fluido gasto por tick
     */
    private record SemifluidFuel(long power, long dropletsPerTick) {}

    private static @Nullable SemifluidFuel semifluidFuel(Fluid fluid) {
        if (fluid == IC2Fluids.BIOMASS.fluid()) return new SemifluidFuel(8_000, FluidConstants.BUCKET / 1_000);
        if (fluid == IC2Fluids.BIOGAS.fluid()) return new SemifluidFuel(16_000, FluidConstants.BUCKET / 2_000);
        if (fluid == IC2Fluids.CREOSOTE.fluid()) return new SemifluidFuel(8_000, FluidConstants.BUCKET / 375);
        return null;
    }

    /** Índices dos slots de cada máquina, na ordem do layout; -1 quando não existe. */
    private record Slots(int input, int secondary, int[] outputs, int discharge, int charge, int fluidIn, int fluidOut) {
        private static final int[] NO_OUTPUTS = new int[0];

        static Slots charge(int slot) {
            return new Slots(-1, -1, NO_OUTPUTS, -1, slot, -1, -1);
        }

        static Slots of(MachineGuiType type, MachineEnergyProfile.Role role) {
            return switch (type) {
                case GENERATOR, SOLAR_GENERATOR, WIND_GENERATOR -> charge(0);
                case WATER_GENERATOR -> charge(1);
                case GEO_GENERATOR, SEMIFLUID_GENERATOR -> new Slots(-1, -1, NO_OUTPUTS, -1, 2, 0, 1);
                case BATBOX, CESU, MFE, MFSU -> new Slots(-1, -1, NO_OUTPUTS, 1, 0, -1, -1);
                case ORE_WASHING_PLANT -> new Slots(0, -1, new int[]{1, 2, 3}, 10, -1, 8, 9);
                case SOLID_CANNER -> new Slots(0, 1, new int[]{2}, 3, -1, -1, -1);
                case CANNER -> new Slots(0, 7, new int[]{1}, 2, -1, -1, -1);
                default -> role == MachineEnergyProfile.Role.PROCESSOR
                        ? new Slots(0, -1, new int[]{1}, 2, -1, -1, -1)
                        : new Slots(-1, -1, NO_OUTPUTS, -1, -1, -1, -1);
            };
        }
    }

    private final MachineGuiType guiType;
    private final MachineEnergyProfile profile;
    private final Slots slots;
    private final String recipeKey;
    private final SimpleContainer inventory;
    /** Tanque principal (entrada) e, na enlatadora, o de saída. */
    private final @Nullable MachineTank tank;
    private final @Nullable MachineTank outputTank;
    /** Como o tanque principal aceita fluido de fora (slot de fluido, canos). */
    private final @Nullable Storage<FluidVariant> tankInput;
    /** O que canos e outros mods enxergam. */
    private final @Nullable Storage<FluidVariant> exposedFluids;
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
    private int activeHold;
    private TransformerMode transformerMode = TransformerMode.REDSTONE;
    private CannerMode cannerMode = CannerMode.BOTTLE_SOLID;
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
        this.slots = Slots.of(this.guiType, this.profile.role());
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

        MachineTank mainTank = null;
        MachineTank secondTank = null;
        Storage<FluidVariant> input = null;
        Storage<FluidVariant> exposed = null;
        switch (this.guiType) {
            case GEO_GENERATOR -> {
                mainTank = new MachineTank(TANK_CAPACITY);
                input = exposed = insertOnly(mainTank, fluid -> fluid == Fluids.LAVA);
            }
            case ORE_WASHING_PLANT -> {
                mainTank = new MachineTank(TANK_CAPACITY);
                input = exposed = insertOnly(mainTank, fluid -> fluid == Fluids.WATER);
            }
            case SEMIFLUID_GENERATOR -> {
                mainTank = new MachineTank(10 * FluidConstants.BUCKET);
                input = exposed = insertOnly(mainTank, fluid -> semifluidFuel(fluid) != null);
            }
            case CANNER -> {
                mainTank = new MachineTank(TANK_CAPACITY);
                secondTank = new MachineTank(TANK_CAPACITY);
                input = insertOnly(mainTank, fluid -> true);
                exposed = new CombinedStorage<>(List.of(input, FilteringStorage.extractOnlyOf(secondTank)));
            }
            default -> {
            }
        }
        this.tank = mainTank;
        this.outputTank = secondTank;
        this.tankInput = input;
        this.exposedFluids = exposed;

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

    /** Visão do tanque que só aceita entrada dos fluidos permitidos. */
    private static Storage<FluidVariant> insertOnly(Storage<FluidVariant> tank, Predicate<Fluid> accepts) {
        return new FilteringStorage<>(tank) {
            @Override
            protected boolean canInsert(FluidVariant resource) {
                return accepts.test(resource.getFluid());
            }

            @Override
            protected boolean canExtract(FluidVariant resource) {
                return false;
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

    /** Tanques para canos e outros mods (entrada filtrada; na enlatadora também a saída), ou null. */
    public @Nullable Storage<FluidVariant> getFluidStorage(@Nullable Direction side) {
        return this.exposedFluids;
    }

    /** Tanque 0 (principal) ou 1 (saída da enlatadora), ou null se a máquina não tem. */
    public @Nullable SingleFluidStorage getTank(int index) {
        return index == 0 ? this.tank : index == 1 ? this.outputTank : null;
    }

    public TransformerMode getTransformerMode() {
        return this.transformerMode;
    }

    public CannerMode getCannerMode() {
        return this.cannerMode;
    }

    /** Botão da GUI: 0 = redstone, 1 = abaixa fixo, 2 = eleva fixo. Só vale para transformadores. */
    public boolean setTransformerMode(int mode) {
        if (this.transformer == null || mode < 0 || mode >= TransformerMode.values().length) return false;
        this.transformerMode = TransformerMode.values()[mode];
        setChanged();
        return true;
    }

    /** Botões da GUI: modos do transformador, modos da enlatadora e troca de tanques. */
    public boolean handleMenuButton(int id) {
        if (this.transformer != null) return setTransformerMode(id);
        if (this.guiType != MachineGuiType.CANNER) return false;

        if (id >= BUTTON_CANNER_MODE && id < BUTTON_CANNER_MODE + CannerMode.values().length) {
            this.cannerMode = CannerMode.values()[id - BUTTON_CANNER_MODE];
            this.progress = 0;
            setChanged();
            return true;
        }
        if (id == BUTTON_SWAP_TANKS && this.tank != null && this.outputTank != null) {
            FluidVariant variant = this.tank.variant;
            long amount = this.tank.amount;
            this.tank.variant = this.outputTank.variant;
            this.tank.amount = this.outputTank.amount;
            this.outputTank.variant = variant;
            this.outputTank.amount = amount;
            this.progress = 0;
            setChanged();
            return true;
        }
        return false;
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
        if (this.tankInput != null && this.slots.fluidIn() >= 0) {
            changed |= MachineFluids.drainIntoTank(this.inventory, this.slots.fluidIn(), this.slots.fluidOut(), this.tankInput);
        }

        long energyBefore = this.energy;
        changed |= switch (this.profile.role()) {
            case GENERATOR -> switch (this.guiType) {
                case SOLAR_GENERATOR -> tickSolar(level);
                case WATER_GENERATOR -> tickWater(level);
                case WIND_GENERATOR -> tickWind(level);
                case GEO_GENERATOR -> tickGeo();
                case SEMIFLUID_GENERATOR -> tickSemifluid();
                default -> tickGenerator(level);
            };
            case PROCESSOR -> tickProcessor(level);
            case TRANSFORMER -> tickTransformer(level);
            case STORAGE, NONE -> false;
        };
        if (changed) setChanged();

        boolean working = switch (this.profile.role()) {
            case GENERATOR -> this.energy > energyBefore;
            case PROCESSOR -> this.energy < energyBefore;
            case TRANSFORMER -> this.transformer != null && this.transformer.mode() == BufferedTransformer.Mode.STEP_UP;
            case STORAGE, NONE -> false;
        };
        updateActive(level, working);
    }

    /** Troca a textura para ativa/inativa (IC2: {@code setActive}). */
    private void updateActive(Level level, boolean working) {
        if (working) {
            this.activeHold = ACTIVE_HOLD_TICKS;
        } else if (this.activeHold > 0) {
            this.activeHold--;
        }
        boolean active = this.activeHold > 0;
        BlockState state = this.getBlockState();
        if (state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE) != active) {
            level.setBlock(this.worldPosition, state.setValue(MachineBlock.ACTIVE, active), Block.UPDATE_CLIENTS);
        }
    }

    // ── slots de carga e descarga ─────────────────────────────────────────
    /** Carrega a bateria do slot de carga e puxa energia do slot de descarga (bateria ou redstone). */
    private boolean handleEnergyItems() {
        boolean changed = false;

        int chargeSlot = this.slots.charge();
        if (chargeSlot >= 0 && chargeSlot < this.inventory.getContainerSize() && this.energy > 0) {
            ItemStack stack = this.inventory.getItem(chargeSlot);
            long moved = EnergyItems.charge(stack, this.energy, this.profile.voltage(), false);
            if (moved > 0) {
                this.energy -= moved;
                this.inventory.setItem(chargeSlot, stack);
                changed = true;
            }
        }

        int dischargeSlot = this.slots.discharge();
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

    /** Geotérmico do IC2: 2 mB de lava por tick viram a produção cheia, se houver espaço no buffer. */
    private boolean tickGeo() {
        if (this.tank == null) return false;
        long production = this.profile.power();
        if (this.profile.capacity() - this.energy < production || this.tank.amount < GEO_LAVA_PER_TICK) return false;
        this.tank.consume(GEO_LAVA_PER_TICK);
        this.energy += production;
        return true;
    }

    /** Semifluido do IC2: biomassa e creosoto dão 8.000 CW, biogás 16.000 CW; cada um dura um tanto por balde. */
    private boolean tickSemifluid() {
        if (this.tank == null || this.tank.isResourceBlank()) return false;
        SemifluidFuel fuel = semifluidFuel(this.tank.variant.getFluid());
        if (fuel == null || this.tank.amount < fuel.dropletsPerTick()
                || this.profile.capacity() - this.energy < fuel.power()) return false;
        this.tank.consume(fuel.dropletsPerTick());
        this.energy += fuel.power();
        this.progress = (int) fuel.power();
        this.maxProgress = (int) this.profile.power();
        return true;
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
        Operation operation = findOperation(level);

        if (operation == null || !insertResults(operation.preview(), true) || !hasFluid(operation.fluid())
                || !canFillOutput(operation)) {
            if (this.progress == 0) return false;
            this.progress = 0;
            return true;
        }
        if (this.energy < this.profile.power()) return false;

        this.energy -= this.profile.power();
        if (++this.progress >= this.profile.operationTicks()) {
            this.progress = 0;
            List<ItemStack> results = operation.result().apply(level.getRandom());
            shrinkSlot(this.slots.input(), operation.inputCount());
            shrinkSlot(this.slots.secondary(), operation.secondaryCount());
            if (this.tank != null && operation.fluid() > 0) {
                this.tank.consume(operation.fluid());
            }
            if (this.outputTank != null && operation.resultFluid() != null) {
                this.outputTank.fill(operation.resultFluid(), operation.resultFluidAmount());
            }
            insertResults(results, false);
        }
        return true;
    }

    private void shrinkSlot(int slot, int count) {
        if (slot < 0 || count <= 0) return;
        ItemStack stack = this.inventory.getItem(slot);
        stack.shrink(count);
        this.inventory.setItem(slot, stack);
    }

    /**
     * Operação possível agora.
     *
     * @param inputCount        quantos itens consome da entrada
     * @param secondaryCount    quantos itens consome do segundo slot (latas, recipientes)
     * @param preview           maiores resultados possíveis, para conferir se cabem na saída
     * @param result            resultados reais (o reciclador depende de sorte; a enlatadora move fluido aqui)
     * @param fluid             fluido gasto do tanque principal, em gotas do Fabric
     * @param resultFluid       fluido produzido no tanque de saída (ou null)
     * @param resultFluidAmount gotas produzidas
     */
    private record Operation(int inputCount, int secondaryCount, List<ItemStack> preview,
                             Function<RandomSource, List<ItemStack>> result, long fluid,
                             @Nullable FluidVariant resultFluid, long resultFluidAmount) {
        static Operation items(int inputCount, int secondaryCount, List<ItemStack> preview,
                               Function<RandomSource, List<ItemStack>> result) {
            return new Operation(inputCount, secondaryCount, preview, result, 0, null, 0);
        }
    }

    private @Nullable Operation findOperation(Level level) {
        ItemStack input = this.slots.input() >= 0 ? this.inventory.getItem(this.slots.input()) : ItemStack.EMPTY;
        ItemStack secondary = this.slots.secondary() >= 0 ? this.inventory.getItem(this.slots.secondary()) : ItemStack.EMPTY;
        if (this.guiType == MachineGuiType.CANNER) return cannerOperation(input, secondary);
        if (input.isEmpty()) return null;
        return switch (this.guiType) {
            case ELECTRIC_FURNACE -> smeltingOperation(level, input);
            case RECYCLER -> recyclingOperation(input);
            default -> recipeOperation(this.recipeKey, input, secondary);
        };
    }

    private @Nullable Operation recipeOperation(String machine, ItemStack input, ItemStack secondary) {
        if (input.isEmpty()) return null;
        Fluid tankFluid = this.tank == null || this.tank.isResourceBlank() ? null : this.tank.variant.getFluid();
        long tankMb = this.tank == null ? 0 : this.tank.amount * 1000 / FluidConstants.BUCKET;
        return MachineRecipes.INSTANCE.find(machine, input, secondary, tankFluid, tankMb)
                .map(recipe -> new Operation(recipe.inputCount(), recipe.secondary() == null ? 0 : recipe.secondaryCount(),
                        recipe.createResults(), random -> recipe.createResults(),
                        recipe.fluidAmount() * FluidConstants.BUCKET / 1000,
                        recipe.resultFluid() == null ? null : FluidVariant.of(recipe.resultFluid()),
                        recipe.resultFluidAmount() * FluidConstants.BUCKET / 1000))
                .orElse(null);
    }

    /**
     * Enlatadora do IC2 ({@code TileEntityCanner}):
     * sólidos = receitas do enlatador de sólidos; esvaziar = recipiente → tanque de saída;
     * encher = tanque de entrada → recipiente; enriquecer = fluido de entrada + item → fluido de saída.
     */
    private @Nullable Operation cannerOperation(ItemStack input, ItemStack container) {
        return switch (this.cannerMode) {
            case BOTTLE_SOLID -> recipeOperation("solid_canner", input, container);
            case ENRICH_LIQUID -> recipeOperation("canner_enrich", input, ItemStack.EMPTY);
            case EMPTY_LIQUID -> containerOperation(container, this.outputTank, false);
            case BOTTLE_LIQUID -> containerOperation(container, this.tank, true);
        };
    }

    private @Nullable Operation containerOperation(ItemStack container, @Nullable MachineTank target, boolean fill) {
        if (target == null || container.isEmpty()) return null;
        MachineFluids.Transfer preview = MachineFluids.transfer(container, target, fill, false);
        if (preview == null) return null;

        ItemStack single = container.copyWithCount(1);
        return Operation.items(0, 1, preview.leftover().isEmpty() ? List.of() : List.of(preview.leftover()), random -> {
            MachineFluids.Transfer done = MachineFluids.transfer(single, target, fill, true);
            if (done == null) return List.of(single); // não deveria acontecer: devolve o recipiente
            return done.leftover().isEmpty() ? List.of() : List.of(done.leftover());
        });
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
        return Operation.items(1, 0, List.of(result), random -> List.of(result.copy()));
    }

    /** Reciclador: consome qualquer item; 1 em 8 vira sucata (exceto a lista negra). */
    private Operation recyclingOperation(ItemStack input) {
        Item scrap = IC2AutoItems.SCRAP.get();
        boolean blacklisted = BuiltInRegistries.ITEM.wrapAsHolder(input.getItem()).is(RECYCLER_BLACKLIST);
        return Operation.items(1, 0, List.of(new ItemStack(scrap)),
                random -> !blacklisted && random.nextInt(RECYCLE_CHANCE) == 0 ? List.of(new ItemStack(scrap)) : List.of());
    }

    private boolean hasFluid(long amount) {
        return amount <= 0 || (this.tank != null && this.tank.amount >= amount);
    }

    private boolean canFillOutput(Operation operation) {
        return operation.resultFluid() == null
                || (this.outputTank != null && this.outputTank.canFill(operation.resultFluid(), operation.resultFluidAmount()));
    }

    /** Distribui os resultados nos slots de saída; com {@code simulate} só confere se cabem. */
    private boolean insertResults(List<ItemStack> results, boolean simulate) {
        int[] outputs = this.slots.outputs();
        ItemStack[] contents = new ItemStack[outputs.length];
        for (int i = 0; i < outputs.length; i++) {
            contents[i] = this.inventory.getItem(outputs[i]).copy();
        }

        for (ItemStack result : results) {
            int remaining = result.getCount();
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                ItemStack slot = contents[i];
                if (!slot.isEmpty() && ItemStack.isSameItemSameComponents(slot, result)) {
                    int moved = Math.min(remaining, slot.getMaxStackSize() - slot.getCount());
                    slot.grow(moved);
                    remaining -= moved;
                }
            }
            for (int i = 0; i < contents.length && remaining > 0; i++) {
                if (contents[i].isEmpty()) {
                    int moved = Math.min(remaining, result.getMaxStackSize());
                    contents[i] = result.copyWithCount(moved);
                    remaining -= moved;
                }
            }
            if (remaining > 0) return false;
        }

        if (!simulate) {
            for (int i = 0; i < outputs.length; i++) {
                this.inventory.setItem(outputs[i], contents[i]);
            }
        }
        return true;
    }

    private void explode() {
        if (this.level != null) {
            int voltage = this.transformer != null ? this.profile.highVoltage() : this.profile.voltage();
            CraftEnergyApi.explodeFromOvervoltage(this.level, this.worldPosition, voltage);
        }
    }

    // ── tanque ────────────────────────────────────────────────────────────
    /** Tanque de um fluido por vez. A máquina mexe direto; de fora só pelas visões filtradas. */
    private final class MachineTank extends SingleFluidStorage {
        private final long capacity;

        MachineTank(long capacity) {
            this.capacity = capacity;
        }

        @Override
        protected long getCapacity(FluidVariant variant) {
            return this.capacity;
        }

        @Override
        protected void onFinalCommit() {
            MachineBlockEntity.this.setChanged();
        }

        void consume(long droplets) {
            this.amount = Math.max(0, this.amount - droplets);
            if (this.amount == 0) this.variant = FluidVariant.blank();
        }

        boolean canFill(FluidVariant fluid, long droplets) {
            return (this.isResourceBlank() || this.variant.equals(fluid)) && this.capacity - this.amount >= droplets;
        }

        void fill(FluidVariant fluid, long droplets) {
            if (this.isResourceBlank()) this.variant = fluid;
            this.amount = Math.min(this.capacity, this.amount + droplets);
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
        if (field >= DATA_FLUID && field < DATA_FLUID + 2 * DATA_PER_TANK) {
            MachineTank fluidTank = (field - DATA_FLUID) / DATA_PER_TANK == 0 ? this.tank : this.outputTank;
            if (fluidTank == null) return 0;
            return switch ((field - DATA_FLUID) % DATA_PER_TANK) {
                case 0 -> fluidTank.isResourceBlank() ? 0 : BuiltInRegistries.FLUID.getId(fluidTank.variant.getFluid()) + 1;
                case 1 -> clampToInt(fluidTank.amount * 1000 / FluidConstants.BUCKET);
                default -> clampToInt(fluidTank.capacity * 1000 / FluidConstants.BUCKET);
            };
        }
        return switch (field) {
            case DATA_ENERGY -> clampToInt(this.energy / EnergyUnits.TICKS_PER_HOUR);
            case DATA_CAPACITY -> clampToInt(this.profile.capacity() / EnergyUnits.TICKS_PER_HOUR);
            case DATA_PROGRESS -> this.progress;
            case DATA_MAX_PROGRESS -> this.maxProgress;
            case DATA_POWER -> clampToInt(this.lastFlow);
            case DATA_VOLTAGE -> this.profile.voltage();
            case DATA_MODE -> this.transformer != null ? this.transformerMode.ordinal() : this.cannerMode.ordinal();
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
        if (this.tank != null) {
            this.tank.writeValue(output);
        }
        if (this.outputTank != null) {
            output.store("OutputFluid", FluidVariant.CODEC, this.outputTank.variant);
            output.putLong("OutputFluidAmount", this.outputTank.amount);
            output.putInt("CannerMode", this.cannerMode.ordinal());
        }
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
        if (this.guiType == MachineGuiType.GENERATOR) {
            this.maxProgress = this.totalFuel;
        }
        if (this.tank != null) {
            this.tank.readValue(input);
        }
        if (this.outputTank != null) {
            this.outputTank.variant = input.read("OutputFluid", FluidVariant.CODEC).orElse(FluidVariant.blank());
            this.outputTank.amount = this.outputTank.variant.isBlank() ? 0 : input.getLongOr("OutputFluidAmount", 0);
            int mode = input.getIntOr("CannerMode", 0);
            this.cannerMode = CannerMode.values()[Math.max(0, Math.min(CannerMode.values().length - 1, mode))];
        }
        if (this.transformer != null) {
            this.transformer.setBufferedPower(input.getLongOr("TransformerBuffer", 0));
            int mode = input.getIntOr("TransformerMode", TransformerMode.REDSTONE.ordinal());
            this.transformerMode = TransformerMode.values()[Math.max(0, Math.min(TransformerMode.values().length - 1, mode))];
        }
    }
}
