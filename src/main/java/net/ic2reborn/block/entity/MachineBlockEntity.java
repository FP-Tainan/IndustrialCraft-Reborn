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
    public static final int DATA_MODE = 6;            // modo (transformador, enlatadora, conformador) ou aviso da lâmina
    public static final int DATA_FLUID = 7;           // id do fluido no registro + 1 (0 = vazio)
    public static final int DATA_FLUID_AMOUNT = 8;    // mB
    public static final int DATA_FLUID_CAPACITY = 9;  // mB
    /** Campos por tanque; o tanque de saída vem logo depois (10, 11, 12). */
    public static final int DATA_PER_TANK = 3;
    public static final int DATA_HEAT = 13;
    public static final int DATA_MAX_HEAT = 14;
    public static final int DATA_COUNT = 15;

    /** Botões da GUI: 0–2 modos do transformador, 10–13 modos da enlatadora, 14 troca os tanques, 20 modo do conformador. */
    public static final int BUTTON_CANNER_MODE = 10;
    public static final int BUTTON_SWAP_TANKS = 14;
    public static final int BUTTON_METAL_FORMER_MODE = 20;

    /** Modos do transformador do IC2. */
    public enum TransformerMode { REDSTONE, STEP_DOWN, STEP_UP }

    /** Modos da enlatadora do IC2. */
    public enum CannerMode { BOTTLE_SOLID, EMPTY_LIQUID, BOTTLE_LIQUID, ENRICH_LIQUID }

    /** Receitas do conformador de metal, na ordem dos modos do IC2: extrudar, laminar, cortar. */
    private static final String[] METAL_FORMER_RECIPES = {"metal_former_extruding", "metal_former_rolling", "metal_former_cutting"};

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
    /** Aquecer (indução, centrífuga) gasta 1 EU/t no IC2 → 1.000 CW a 1.000 MV. */
    private static final long HEATING_POWER = 1_000;
    /** Forno de indução: calor máximo 10.000, operação em 4.000 pontos, processar gasta 15 EU/t a mais. */
    private static final int INDUCTION_MAX_HEAT = 10_000;
    private static final int INDUCTION_OPERATION = 4_000;
    private static final long INDUCTION_PROCESS_POWER = 15_000;
    /** Centrífuga térmica: calor máximo 5.000. */
    private static final int CENTRIFUGE_MAX_HEAT = 5_000;

    private static final int GENERATOR_FUEL_SLOT = 1;
    private static final int WATER_FUEL_SLOT = 0;
    private static final int BLADE_SLOT = 3;

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
                // descarga 0, scanner 1, tubos 2, broca 3, upgrade 4, buffer 5–19 (usados pelo MinerLogic)
                case MINER -> new Slots(-1, -1, NO_OUTPUTS, 0, -1, -1, -1);
                // fermentador: célula de biomassa 0→1, célula de biogás 2→3, fertilizante 4
                case FERMENTER -> new Slots(-1, -1, new int[]{4}, -1, -1, 0, 1);
                case SOLID_HEAT_GENERATOR -> new Slots(0, -1, new int[]{1}, -1, -1, -1, -1);
                case FLUID_HEAT_GENERATOR -> new Slots(-1, -1, NO_OUTPUTS, -1, -1, 0, 1);
                // 10 bobinas e a descarga
                case ELECTRIC_HEAT_GENERATOR -> new Slots(-1, -1, NO_OUTPUTS, 10, -1, -1, -1);
                // 10 motores e a descarga
                case ELECTRIC_KINETIC_GENERATOR -> new Slots(-1, -1, NO_OUTPUTS, 10, -1, -1, -1);
                // duas entradas (A, B) e duas saídas
                case INDUCTION_FURNACE -> new Slots(0, 1, new int[]{2, 3}, 4, -1, -1, -1);
                case CENTRIFUGE -> new Slots(0, -1, new int[]{2, 3, 4}, 1, -1, -1, -1);
                // colheitadeira: descarga 0, colheita 1–15, upgrades 16–19
                case CROP_HARVESTER -> new Slots(-1, -1, java.util.stream.IntStream.rangeClosed(1, 15).toArray(), 0, -1, -1, -1);
                // cropmatron: descarga 0, fertilizante 1–7, herbicida 8→9, água 10→11, upgrades 12–15
                case CROPMATRON -> new Slots(-1, -1, new int[]{9, 11}, 0, -1, 10, 11);
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

    /** Minerador: tubos, broca e scanner (IC2: TileEntityMiner). */
    private final @Nullable MinerLogic miner;
    private final @Nullable CropMachineLogic cropLogic;

    // calor (fermentador e geradores de calor)
    private int heatBuffer;
    private int transmitHeat;
    private long heatStore;
    private boolean heatWorking;

    // energia cinética (KU)
    private long kineticStore;
    private int kuOutput;
    private double windStrength;
    private int rotorCrossSection;
    private int rotorObstructed;
    private boolean rotorActive;
    private int kineticTicker;
    private int waterBiome = -1;
    private @Nullable Direction waterFacing;
    private int distanceToNormalBiome;
    private int manualClicks;
    private boolean kineticWorking;

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
    private int heat;
    private int maxHeat;
    private int workHeat = CENTRIFUGE_MAX_HEAT;
    private boolean bladeTooWeak;
    private int metalFormerMode;
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
        this.maxHeat = this.guiType == MachineGuiType.INDUCTION_FURNACE ? INDUCTION_MAX_HEAT : CENTRIFUGE_MAX_HEAT;

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
            case FERMENTER -> {
                mainTank = new MachineTank(10 * FluidConstants.BUCKET);
                secondTank = new MachineTank(2 * FluidConstants.BUCKET);
                input = insertOnly(mainTank, fluid -> fluid == IC2Fluids.BIOMASS.fluid());
                exposed = new CombinedStorage<>(List.of(input, FilteringStorage.extractOnlyOf(secondTank)));
            }
            case FLUID_HEAT_GENERATOR -> {
                mainTank = new MachineTank(10 * FluidConstants.BUCKET);
                input = exposed = insertOnly(mainTank, fluid -> heatFuel(fluid) != null);
            }
            case CROPMATRON -> {
                mainTank = new MachineTank(2 * FluidConstants.BUCKET);
                secondTank = new MachineTank(2 * FluidConstants.BUCKET);
                input = insertOnly(mainTank, fluid -> fluid == Fluids.WATER);
                exposed = new CombinedStorage<>(List.of(input, insertOnly(secondTank, fluid -> fluid == IC2Fluids.WEED_EX.fluid())));
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

        this.miner = this.guiType == MachineGuiType.MINER ? new MinerLogic(this) : null;
        this.cropLogic = this.guiType == MachineGuiType.CROP_HARVESTER || this.guiType == MachineGuiType.CROPMATRON
                ? new CropMachineLogic(this, this.guiType == MachineGuiType.CROP_HARVESTER) : null;

        this.energyNode = switch (this.profile.role()) {
            case GENERATOR -> new GeneratorNode();
            case STORAGE -> new StorageInput();
            case PROCESSOR -> new ProcessorNode();
            case TRANSFORMER, HEAT, KINETIC, NONE -> null;
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

    /** Calor atual (forno de indução, centrífuga térmica). */
    public int getHeat() {
        return this.heat;
    }

    /** Cortador de blocos: sem lâmina, ou lâmina mais mole que o bloco. */
    public boolean isBladeTooWeak() {
        return this.bladeTooWeak;
    }

    /** Botão da GUI: 0 = redstone, 1 = abaixa fixo, 2 = eleva fixo. Só vale para transformadores. */
    public boolean setTransformerMode(int mode) {
        if (this.transformer == null || mode < 0 || mode >= TransformerMode.values().length) return false;
        this.transformerMode = TransformerMode.values()[mode];
        setChanged();
        return true;
    }

    /** Botões da GUI: modos do transformador, da enlatadora e do conformador, e troca de tanques. */
    public boolean handleMenuButton(int id) {
        if (this.transformer != null) return setTransformerMode(id);

        if (this.guiType == MachineGuiType.METAL_FORMER && id == BUTTON_METAL_FORMER_MODE) {
            this.metalFormerMode = (this.metalFormerMode + 1) % METAL_FORMER_RECIPES.length;
            this.progress = 0;
            setChanged();
            return true;
        }
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

    /** Tanques do Cropmatron: 0 = água, 1 = herbicida. */
    @Nullable SingleFluidStorage cropTank(int index) {
        return index == 0 ? this.tank : this.outputTank;
    }

    /** Colheitadeira/Cropmatron: olha agora a próxima posição da área (testes). */
    public void performCropScan() {
        if (this.cropLogic != null && this.level != null) this.cropLogic.scan(this.level);
    }
    /** Leitura do anemômetro nos geradores eólicos; null nas outras máquinas. */
    public @Nullable Component windMeterReading() {
        if (!(this.level instanceof ServerLevel serverLevel)) return null;
        if (this.guiType == MachineGuiType.WIND_GENERATOR) {
            int obstructions = countObstructions(serverLevel);
            double wind = WindSim.get(serverLevel).windAt(serverLevel, this.worldPosition.getY()) * (1.0 - obstructions / 567.0);
            return windText(wind, obstructions);
        }
        if (this.guiType == MachineGuiType.WIND_KINETIC_GENERATOR) {
            if (rotorItem() == null) {
                return Component.translatableWithFallback("message.ic2reborn.wind_meter.rotor_none", "No rotor to catch wind");
            }
            if (!this.rotorActive) {
                return Component.translatableWithFallback("message.ic2reborn.wind_meter.rotor_blocked", "Rotor blocked from catching wind");
            }
            return windText(this.windStrength, Math.max(0, this.rotorObstructed));
        }
        return null;
    }

    private static Component windText(double wind, int obstructions) {
        return wind <= 0.0
                ? Component.translatableWithFallback("message.ic2reborn.wind_meter.obstructed", "No wind due to %s obstruction(s)", obstructions)
                : Component.translatableWithFallback("message.ic2reborn.wind_meter.effective", "Effective wind strength: %s",
                        net.ic2reborn.item.WindMeterItem.format(wind));
    }
    /** Gasta energia do buffer (minerador); false, sem gastar, se não houver o bastante. */
    boolean useEnergy(long amount) {
        if (amount < 0 || this.energy < amount) return false;
        this.energy -= amount;
        setChanged();
        return true;
    }

    int machineVoltage() {
        return this.profile.voltage();
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
        // IC2: a face cinética (frente) do gerador cinético e do cinético elétrico não troca eletricidade
        if ((this.guiType == MachineGuiType.KINETIC_GENERATOR || this.guiType == MachineGuiType.ELECTRIC_KINETIC_GENERATOR)
                && face != null && face == front()) {
            return null;
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
                case KINETIC_GENERATOR -> tickKineticGenerator(level);
                case SEMIFLUID_GENERATOR -> tickSemifluid();
                default -> tickGenerator(level);
            };
            case PROCESSOR -> switch (this.guiType) {
                case ELECTRIC_KINETIC_GENERATOR -> tickElectricKinetic();
                case ELECTRIC_HEAT_GENERATOR -> tickElectricHeat();
                case MINER -> this.miner != null && this.miner.tick(level);
                case CROP_HARVESTER, CROPMATRON -> this.cropLogic != null && this.cropLogic.tick(level);
                case INDUCTION_FURNACE -> tickInduction(level);
                case CENTRIFUGE -> tickCentrifugeHeat(level) | tickProcessor(level);
                default -> tickProcessor(level);
            };
            case KINETIC -> tickKineticSource(level);
            case HEAT -> tickHeatMachine(level);
            case TRANSFORMER -> tickTransformer(level);
            case STORAGE, NONE -> false;
        };
        if (changed) setChanged();

        boolean working = switch (this.profile.role()) {
            case GENERATOR -> this.energy > energyBefore;
            case PROCESSOR -> this.energy < energyBefore;
            case KINETIC -> this.kineticWorking;
            case HEAT -> this.heatWorking;
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

    // ── máquinas com calor ────────────────────────────────────────────────
    /**
     * Forno de indução do IC2: com algo para fundir (ou redstone) aquece 1 ponto por tick até 10.000;
     * sem isso esfria 4 por tick. Processando, o progresso sobe calor ÷ 30 por tick e, a cada
     * 4.000 pontos, funde as duas entradas de uma vez.
     */
    private boolean tickInduction(Level level) {
        boolean changed = false;
        int inputA = this.slots.input();
        int inputB = this.slots.secondary();
        int outputA = this.slots.outputs()[0];
        int outputB = this.slots.outputs()[1];

        if (this.progress >= INDUCTION_OPERATION) {
            smeltInto(level, inputA, outputA);
            smeltInto(level, inputB, outputB);
            this.progress = 0;
            changed = true;
        }

        boolean canOperate = smeltResult(level, inputA, outputA) != null || smeltResult(level, inputB, outputB) != null;
        if ((canOperate || level.hasNeighborSignal(this.worldPosition)) && this.energy >= HEATING_POWER) {
            this.energy -= HEATING_POWER;
            if (this.heat < INDUCTION_MAX_HEAT) this.heat++;
            changed = true;
        } else if (this.heat > 0) {
            this.heat -= Math.min(this.heat, 4);
            changed = true;
        }

        if (canOperate && this.energy >= INDUCTION_PROCESS_POWER) {
            this.energy -= INDUCTION_PROCESS_POWER;
            this.progress += this.heat / 30;
            changed = true;
        } else if (!canOperate && this.progress != 0) {
            this.progress = 0;
            changed = true;
        }
        this.maxProgress = INDUCTION_OPERATION;
        this.maxHeat = INDUCTION_MAX_HEAT;
        return changed;
    }

    /** Resultado da fornalha para o slot, se couber na saída. */
    private @Nullable ItemStack smeltResult(Level level, int inputSlot, int outputSlot) {
        if (!(level instanceof ServerLevel serverLevel)) return null;
        ItemStack input = this.inventory.getItem(inputSlot);
        if (input.isEmpty()) return null;
        SingleRecipeInput recipeInput = new SingleRecipeInput(input);
        Optional<RecipeHolder<SmeltingRecipe>> recipe = serverLevel.recipeAccess().getRecipeFor(RecipeType.SMELTING, recipeInput, serverLevel);
        if (recipe.isEmpty()) return null;
        ItemStack result = recipe.get().value().assemble(recipeInput);
        if (result.isEmpty()) return null;
        ItemStack output = this.inventory.getItem(outputSlot);
        if (!output.isEmpty() && (!ItemStack.isSameItemSameComponents(output, result)
                || output.getCount() + result.getCount() > output.getMaxStackSize())) return null;
        return result;
    }

    private void smeltInto(Level level, int inputSlot, int outputSlot) {
        ItemStack result = smeltResult(level, inputSlot, outputSlot);
        if (result == null) return;
        shrinkSlot(inputSlot, 1);
        ItemStack output = this.inventory.getItem(outputSlot);
        if (output.isEmpty()) {
            this.inventory.setItem(outputSlot, result);
        } else {
            output.grow(result.getCount());
            this.inventory.setItem(outputSlot, output);
        }
    }

    /**
     * Centrífuga térmica do IC2: aquece 1 ponto por tick até o calor que a receita pede (máx. 5.000),
     * ou até 5.000 com redstone; sem receita esfria 1 por tick. A operação só anda com calor suficiente.
     */
    private boolean tickCentrifugeHeat(Level level) {
        ItemStack input = this.inventory.getItem(this.slots.input());
        boolean redstone = level.hasNeighborSignal(this.worldPosition);
        Optional<MachineRecipes.Compiled> recipe = MachineRecipes.INSTANCE.find("thermal_centrifuge", input);

        int requested = Integer.MIN_VALUE;
        if (recipe.isPresent() && !redstone) {
            requested = Math.min(CENTRIFUGE_MAX_HEAT, recipe.get().minHeat());
            this.workHeat = requested;
            if (this.heat > requested) this.heat = requested;
        } else if (this.heat <= CENTRIFUGE_MAX_HEAT && redstone) {
            requested = CENTRIFUGE_MAX_HEAT;
            this.workHeat = requested;
        }

        int before = this.heat;
        if (this.energy >= HEATING_POWER && this.heat - 1 < requested) {
            this.energy -= HEATING_POWER;
            this.heat++;
        } else {
            this.heat -= Math.min(this.heat, 1);
        }
        this.maxHeat = this.workHeat;
        return this.heat != before;
    }

    // ── energia cinética (IC2: IKineticSource) ────────────────────────────
    /** Gerador cinético do IC2: 4 KU = 1 EU → 125 CW por KU. */
    private static final long CW_PER_KU = 125;
    /** Buffer dos geradores cinéticos elétrico e manual. */
    private static final int KINETIC_BUFFER = 1_000;
    private static final int MOTOR_SLOTS = 10;
    private static final int MOTOR_KU = 100;
    private static final int MANUAL_CLICK_KU = 400;
    private static final int MANUAL_CLICKS_PER_TICK = 10;
    private static final int WIND_TICK_RATE = 32;
    private static final int WATER_TICK_RATE = 20;
    private static final float WIND_OUTPUT = 10.0F;
    private static final float WATER_OUTPUT = 0.2F;
    private static final int BIOME_INVALID = 0;
    private static final int BIOME_OCEAN = 1;
    private static final int BIOME_RIVER = 2;

    public boolean isKineticSource() {
        return switch (this.guiType) {
            case WIND_KINETIC_GENERATOR, WATER_KINETIC_GENERATOR, MANUAL_KINETIC_GENERATOR, ELECTRIC_KINETIC_GENERATOR -> true;
            default -> false;
        };
    }

    /**
     * Entrega KU a quem pede pela face {@code side} desta máquina. Como no IC2: eólico e água entregam
     * pelas costas (o rotor fica na frente), o elétrico pela frente e o manual por qualquer lado.
     */
    public int drawKinetic(Direction side, int request, boolean simulate) {
        return switch (this.guiType) {
            case WIND_KINETIC_GENERATOR, WATER_KINETIC_GENERATOR -> side.getOpposite() == front() ? Math.min(request, this.kuOutput) : 0;
            case MANUAL_KINETIC_GENERATOR -> takeKinetic(Math.min(request, this.kineticStore), simulate);
            case ELECTRIC_KINETIC_GENERATOR -> side == front() ? takeKinetic(Math.min(request, Math.min(maxMotorKu(), this.kineticStore)), simulate) : 0;
            default -> 0;
        };
    }

    private int kineticBandwidth(Direction side) {
        return switch (this.guiType) {
            case WIND_KINETIC_GENERATOR, WATER_KINETIC_GENERATOR -> side.getOpposite() == front() ? this.kuOutput : 0;
            case MANUAL_KINETIC_GENERATOR -> KINETIC_BUFFER;
            case ELECTRIC_KINETIC_GENERATOR -> side == front() ? maxMotorKu() : 0;
            default -> 0;
        };
    }

    private int takeKinetic(long amount, boolean simulate) {
        int drawn = (int) Math.max(0, amount);
        if (!simulate && drawn > 0) {
            this.kineticStore -= drawn;
            setChanged();
        }
        return drawn;
    }

    private int maxMotorKu() {
        return countItems(MOTOR_SLOTS, IC2AutoItems.ELECTRIC_MOTOR.get()) * MOTOR_KU;
    }

    /** Manivela do gerador manual: 400 KU por clique (até 1.000), gasta fome; precisa ter mais de 6 de fome. */
    public void crank(Player player) {
        if (this.guiType != MachineGuiType.MANUAL_KINETIC_GENERATOR) return;
        if (player.getFoodData().getFoodLevel() <= 6 || this.manualClicks >= MANUAL_CLICKS_PER_TICK) return;
        this.kineticStore = Math.min(this.kineticStore + MANUAL_CLICK_KU, KINETIC_BUFFER);
        player.causeFoodExhaustion(0.25F);
        this.manualClicks++;
        setChanged();
    }

    /**
     * Estado para a GUI. Eólico: 0 sem rotor, 1 sem espaço, 2 vento fraco, 3 produzindo.
     * Água: 0 bioma errado, 1 sem rotor, 2 sem espaço/água, 3 produzindo.
     */
    public int kineticStatus() {
        boolean water = this.guiType == MachineGuiType.WATER_KINETIC_GENERATOR;
        if (water && this.waterBiome == BIOME_INVALID) return 0;
        int offset = water ? 1 : 0;
        if (rotorItem() == null) return offset;
        if (!this.rotorActive) return offset + 1;
        if (this.kuOutput <= 0) return water ? 3 : 2;
        return 3;
    }

    private boolean tickKineticSource(Level level) {
        return switch (this.guiType) {
            case WIND_KINETIC_GENERATOR -> tickWindKinetic(level);
            case WATER_KINETIC_GENERATOR -> tickWaterKinetic(level);
            case MANUAL_KINETIC_GENERATOR -> {
                this.manualClicks = 0;
                this.kineticWorking = this.kineticStore > 0;
                yield false;
            }
            default -> false;
        };
    }

    private @Nullable net.ic2reborn.item.RotorItem rotorItem() {
        if (!(this.inventory.getItem(0).getItem() instanceof net.ic2reborn.item.RotorItem rotor)) return null;
        return this.guiType == MachineGuiType.WATER_KINETIC_GENERATOR && !rotor.acceptsWater() ? null : rotor;
    }

    private int rotorDiameter() {
        net.ic2reborn.item.RotorItem rotor = rotorItem();
        if (rotor == null) return 0;
        return this.guiType == MachineGuiType.WATER_KINETIC_GENERATOR && this.waterBiome == BIOME_RIVER
                ? (rotor.diameter() + 1) * 2 / 3 : rotor.diameter();
    }

    private void damageRotor(int amount) {
        ItemStack stack = this.inventory.getItem(0);
        if (stack.isEmpty() || !stack.isDamageableItem()) return;
        int damage = stack.getDamageValue() + amount;
        if (damage >= stack.getMaxDamage()) {
            this.inventory.setItem(0, ItemStack.EMPTY);
        } else {
            stack.setDamageValue(damage);
            this.inventory.setItem(0, stack);
        }
    }

    private int rotorHealth() {
        ItemStack stack = this.inventory.getItem(0);
        if (stack.isEmpty() || stack.getMaxDamage() <= 0) return 0;
        return 100 - stack.getDamageValue() * 100 / stack.getMaxDamage();
    }

    /**
     * Espaço do rotor (IC2: checkSpace). Com {@code onlyRotor}, olha só o plano logo na frente; senão,
     * um volume o dobro do rotor e {@code length} para frente e para trás. Conta as colunas ocupadas
     * (não ar no eólico, não água no de água); -1 se houver outro gerador igual no volume.
     */
    private int checkRotorSpace(Level level, int length, boolean onlyRotor) {
        boolean water = this.guiType == MachineGuiType.WATER_KINETIC_GENERATOR;
        int box = rotorDiameter() / 2;
        int start = 0;
        if (onlyRotor) {
            length = 1;
            start = length + 1;
        } else {
            box *= 2;
        }
        Direction forward = front();
        Direction right = forward.getClockWise(Direction.Axis.Y);
        int occupiedColumns = 0;
        for (int up = -box; up <= box; up++) {
            for (int side = -box; side <= box; side++) {
                boolean occupied = false;
                for (int fwd = start - length; fwd <= length; fwd++) {
                    BlockPos pos = this.worldPosition.offset(fwd * forward.getStepX() + side * right.getStepX(), up,
                            fwd * forward.getStepZ() + side * right.getStepZ());
                    BlockState state = level.getBlockState(pos);
                    boolean blocked = water ? !state.is(net.minecraft.world.level.block.Blocks.WATER) : !state.isAir();
                    if (blocked) {
                        occupied = true;
                        if ((up != 0 || side != 0 || fwd != 0) && !onlyRotor
                                && level.getBlockEntity(pos) instanceof MachineBlockEntity other && other.guiType == this.guiType) {
                            return -1;
                        }
                    }
                }
                if (occupied) occupiedColumns++;
            }
        }
        return occupiedColumns;
    }

    /** Eólico do IC2: a cada 32 ticks mede o vento na altura do bloco, descontando obstruções. */
    private boolean tickWindKinetic(Level level) {
        if (this.kineticTicker++ % WIND_TICK_RATE != 0 || !(level instanceof ServerLevel serverLevel)) return false;
        net.ic2reborn.item.RotorItem rotor = rotorItem();
        this.maxHeat = rotorDiameter() / 2;
        this.rotorActive = rotor != null && checkRotorSpace(level, 1, true) == 0;
        boolean changed = false;
        this.windStrength = 0;
        if (this.rotorActive) {
            int diameter = rotorDiameter();
            this.rotorCrossSection = (diameter / 2 * 2 * 2 + 1) * (diameter / 2 * 2 * 2 + 1);
            this.rotorObstructed = checkRotorSpace(level, diameter * 3, false);
            if (this.rotorObstructed > 0 && this.rotorObstructed <= (diameter + 1) / 2) this.rotorObstructed = 0;
            if (this.rotorObstructed >= 0) {
                double wind = WindSim.get(serverLevel).windAt(serverLevel, this.worldPosition.getY());
                wind *= 1.0 - Math.pow((double) this.rotorObstructed / this.rotorCrossSection, 2.0);
                this.windStrength = Math.max(0.0, wind);
                if (this.windStrength >= rotor.minWind()) {
                    damageRotor(this.windStrength <= rotor.maxWind() ? 1 : 4);
                    changed = true;
                }
            }
        }
        rotor = rotorItem();
        this.kuOutput = this.rotorActive && rotor != null && this.windStrength >= rotor.minWind()
                ? (int) (this.windStrength * WIND_OUTPUT * rotor.efficiency()) : 0;
        this.kineticWorking = this.kuOutput > 0;
        this.progress = this.kuOutput;
        this.maxProgress = rotorHealth();
        return changed;
    }

    /**
     * Água do IC2: só em oceano (maré, segue o dia) ou rio (correnteza); a força depende de quão
     * longe está a margem na direção do rotor. O rotor de madeira não serve.
     */
    private boolean tickWaterKinetic(Level level) {
        if (this.kineticTicker++ % WATER_TICK_RATE != 0) return false;
        Direction facing = front();
        if (this.waterBiome < 0 || this.waterFacing != facing) {
            net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(this.worldPosition);
            this.waterBiome = biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) ? BIOME_OCEAN
                    : biome.is(net.minecraft.tags.BiomeTags.IS_RIVER) ? BIOME_RIVER : BIOME_INVALID;
            this.waterFacing = facing;
            this.distanceToNormalBiome = 200;
            for (int distance = 1; distance < 200; distance++) {
                if (!isWaterBiome(level, this.worldPosition.relative(facing, distance))
                        || !isWaterBiome(level, this.worldPosition.relative(facing, -distance))) {
                    this.distanceToNormalBiome = distance;
                    break;
                }
            }
        }

        this.kuOutput = 0;
        this.rotorActive = false;
        boolean changed = false;
        net.ic2reborn.item.RotorItem rotor = rotorItem();
        this.maxHeat = rotorDiameter() / 2;
        if (this.waterBiome != BIOME_INVALID && rotor != null && checkRotorSpace(level, 1, true) == 0) {
            this.rotorActive = true;
            int diameter = rotorDiameter();
            this.rotorCrossSection = (diameter / 2 * 2 * 2 + 1) * (diameter / 2 * 2 * 2 + 1);
            this.rotorObstructed = checkRotorSpace(level, diameter * 3, false);
            if (this.rotorObstructed > 0 && this.rotorObstructed <= (diameter + 1) / 2) this.rotorObstructed = 0;
            if (this.rotorObstructed >= 0) {
                double obstruction = (double) this.rotorObstructed / this.rotorCrossSection;
                int waterFlow;
                if (this.waterBiome == BIOME_OCEAN) {
                    double tide = Math.sin(level.getOverworldClockTime() * Math.PI / 6000.0);
                    tide *= Math.abs(tide);
                    double speed = tide * this.distanceToNormalBiome / 100.0 * (1.0 - obstruction * obstruction);
                    waterFlow = (int) ((int) (speed * 3000.0) * rotor.efficiency());
                    damageRotor(2);
                } else {
                    double speed = Math.max(20, Math.min(50, this.distanceToNormalBiome)) / 50.0;
                    waterFlow = (int) (speed * 1000.0 * (rotor.efficiency() * (1.0 - 0.3 * level.getRandom().nextFloat() - 0.1 * obstruction)));
                    damageRotor(1);
                }
                this.kuOutput = (int) Math.abs(waterFlow * WATER_OUTPUT);
                changed = true;
            }
        }
        this.kineticWorking = this.kuOutput > 0;
        this.progress = this.kuOutput;
        this.maxProgress = rotorHealth();
        return changed;
    }

    private static boolean isWaterBiome(Level level, BlockPos pos) {
        net.minecraft.core.Holder<net.minecraft.world.level.biome.Biome> biome = level.getBiome(pos);
        return biome.is(net.minecraft.tags.BiomeTags.IS_OCEAN) || biome.is(net.minecraft.tags.BiomeTags.IS_RIVER);
    }

    /** Gerador cinético elétrico: enche o buffer de KU com energia (125 CW por KU); os motores limitam a entrega. */
    private boolean tickElectricKinetic() {
        long ku = Math.max(0, Math.min(KINETIC_BUFFER - this.kineticStore, this.energy / CW_PER_KU));
        this.energy -= ku * CW_PER_KU;
        this.kineticStore += ku;
        this.progress = (int) this.kineticStore;
        this.maxProgress = maxMotorKu();
        return ku > 0;
    }

    /** Gerador cinético: puxa KU da máquina na frente dele e converte em energia (125 CW por KU). */
    private boolean tickKineticGenerator(Level level) {
        Direction facing = front();
        if (!(level.getBlockEntity(this.worldPosition.relative(facing)) instanceof MachineBlockEntity source)
                || !source.isKineticSource()) {
            this.progress = 0;
            this.maxProgress = 0;
            return false;
        }
        Direction side = facing.getOpposite();
        int available = source.drawKinetic(side, source.kineticBandwidth(side), true);
        int request = (int) Math.min(available, (this.profile.capacity() - this.energy) / CW_PER_KU);
        int drawn = request > 0 ? source.drawKinetic(side, request, false) : 0;
        this.energy += drawn * CW_PER_KU;
        this.progress = clampToInt(drawn * CW_PER_KU);
        this.maxProgress = clampToInt(available * CW_PER_KU);
        return drawn > 0;
    }

    // ── calor (IC2: IHeatSource) ──────────────────────────────────────────
    /** Fermentador (general.ini do IC2): 4.000 HU por ciclo, 20 mB de biomassa → 400 mB de biogás, 500 mB de biomassa por fertilizante. */
    private static final int FERMENTER_HEAT_PER_RUN = 4_000;
    private static final long FERMENTER_BIOMASS_PER_RUN = FluidConstants.BUCKET * 20 / 1_000;
    private static final long FERMENTER_BIOGAS_PER_RUN = FluidConstants.BUCKET * 400 / 1_000;
    private static final int FERMENTER_BIOMASS_PER_FERTILIZER = 500;
    /** O fermentador puxa até 100 HU por tick da fonte de calor na frente dele. */
    private static final int FERMENTER_HEAT_DRAW = 100;
    private static final int FERMENTER_CELL_IN = 2;
    private static final int FERMENTER_CELL_OUT = 3;
    /** Geradores de calor: sólido 20 HU/t; elétrico 10 HU/t por bobina, 1 HU = 500 CW; RT 2^(n−1) × 2 HU/t. */
    private static final int SOLID_HEAT_PER_TICK = 20;
    private static final int COIL_HEAT = 10;
    private static final int COIL_SLOTS = 10;
    private static final long ENERGY_PER_HU = 500;
    private static final int RT_HEAT_BASE = 2;
    private static final int RT_SLOTS = 6;

    /** Combustível do gerador de calor fluido (IC2: FluidHeatManager). */
    private record HeatFuel(long dropletsPerTick, int heat) {}

    private static @Nullable HeatFuel heatFuel(Fluid fluid) {
        // biomassa: 20 mB a cada 20 ticks → 16 HU/t; biogás: 10 mB a cada 20 ticks → 32 HU/t
        if (fluid == IC2Fluids.BIOMASS.fluid()) return new HeatFuel(FluidConstants.BUCKET / 1_000, 16);
        if (fluid == IC2Fluids.BIOGAS.fluid()) return new HeatFuel(FluidConstants.BUCKET / 2_000, 32);
        return null;
    }

    public boolean isHeatSource() {
        return switch (this.guiType) {
            case SOLID_HEAT_GENERATOR, FLUID_HEAT_GENERATOR, ELECTRIC_HEAT_GENERATOR, RT_HEAT_GENERATOR -> true;
            default -> false;
        };
    }

    /** HU por tick que o gerador entrega no máximo agora. */
    public int maxHeatEmitted() {
        return switch (this.guiType) {
            case SOLID_HEAT_GENERATOR -> SOLID_HEAT_PER_TICK;
            case FLUID_HEAT_GENERATOR -> {
                HeatFuel fuel = this.tank == null || this.tank.isResourceBlank() ? null : heatFuel(this.tank.variant.getFluid());
                yield fuel == null ? 0 : fuel.heat();
            }
            case ELECTRIC_HEAT_GENERATOR -> countItems(COIL_SLOTS, IC2AutoItems.COIL.get()) * COIL_HEAT;
            case RT_HEAT_GENERATOR -> {
                int pellets = countItems(RT_SLOTS, IC2AutoItems.RTG_PELLET.get());
                yield pellets == 0 ? 0 : (1 << (pellets - 1)) * RT_HEAT_BASE;
            }
            default -> 0;
        };
    }

    private int countItems(int slots, Item item) {
        int count = 0;
        for (int slot = 0; slot < slots && slot < this.inventory.getContainerSize(); slot++) {
            if (this.inventory.getItem(slot).getItem() == item) count++;
        }
        return count;
    }

    /**
     * Entrega calor a quem está na frente do gerador; {@code side} é a face do gerador pedida,
     * que precisa ser a frente dele (gerador e consumidor frente a frente, como no IC2).
     */
    public int drawHeat(Direction side, int request, boolean simulate) {
        if (!isHeatSource() || side != front()) return 0;
        int drawn = Math.min(request, this.heatBuffer);
        if (!simulate) {
            this.heatBuffer -= drawn;
            this.transmitHeat = drawn;
            setChanged();
        }
        return drawn;
    }

    private boolean tickHeatMachine(Level level) {
        return this.guiType == MachineGuiType.FERMENTER ? tickFermenter(level) : tickHeatSource(level);
    }

    /** Geradores de calor sem eletricidade: completam o buffer até o máximo por tick. */
    private boolean tickHeatSource(Level level) {
        boolean changed = false;
        boolean solid = this.guiType == MachineGuiType.SOLID_HEAT_GENERATOR;
        if (solid && this.fuel <= 0 && this.heatBuffer == 0) {
            changed = gainSolidFuel(level);
        }

        int wanted = maxHeatEmitted() - this.heatBuffer;
        int produced = wanted > 0 ? fillHeatBuffer(wanted) : 0;
        this.heatBuffer += produced;

        if (solid && this.fuel > 0) {
            this.heatStore += SOLID_HEAT_PER_TICK;
            this.fuel--;
            if (this.fuel == 0 && level.getRandom().nextBoolean()) {
                insertResults(List.of(new ItemStack(net.ic2reborn.registry.IC2Items.ASHES.get())), false);
            }
            changed = true;
        }
        this.heatWorking = produced > 0 || (solid && this.fuel > 0);
        if (solid) {
            this.progress = this.fuel;
            this.maxProgress = this.totalFuel;
        }
        return changed || produced > 0;
    }

    /** Gerador de calor elétrico: transforma energia em calor, 500 CW por HU. */
    private boolean tickElectricHeat() {
        int wanted = maxHeatEmitted() - this.heatBuffer;
        int produced = wanted > 0 ? fillHeatBuffer(wanted) : 0;
        this.heatBuffer += produced;
        return produced > 0;
    }

    private int fillHeatBuffer(int max) {
        return switch (this.guiType) {
            case SOLID_HEAT_GENERATOR -> {
                int taken = (int) Math.min(max, this.heatStore);
                this.heatStore -= taken;
                yield taken;
            }
            case FLUID_HEAT_GENERATOR -> {
                if (this.tank == null || this.tank.isResourceBlank()) yield 0;
                HeatFuel fuel = heatFuel(this.tank.variant.getFluid());
                if (fuel == null || this.tank.amount < fuel.dropletsPerTick()) yield 0;
                this.tank.consume(fuel.dropletsPerTick());
                yield fuel.heat();
            }
            case RT_HEAT_GENERATOR -> Math.min(max, maxHeatEmitted());
            case ELECTRIC_HEAT_GENERATOR -> {
                int amount = (int) Math.min(max, this.energy / ENERGY_PER_HU);
                this.energy -= amount * ENERGY_PER_HU;
                yield amount;
            }
            default -> 0;
        };
    }

    /** Gerador de calor sólido: queima como o gerador (tempo de queima ÷ 4), se houver lugar para as cinzas. */
    private boolean gainSolidFuel(Level level) {
        ItemStack stack = this.inventory.getItem(0);
        if (stack.isEmpty()) return false;
        if (!insertResults(List.of(new ItemStack(net.ic2reborn.registry.IC2Items.ASHES.get())), true)) return false;
        int value = level.fuelValues().burnDuration(stack) / 4;
        if (value <= 0) return false;
        if (stack.getItem() == Items.LAVA_BUCKET) {
            this.inventory.setItem(0, new ItemStack(Items.BUCKET));
        } else {
            stack.shrink(1);
            this.inventory.setItem(0, stack);
        }
        this.fuel += value;
        this.totalFuel = value;
        return true;
    }

    /**
     * Fermentador do IC2: com biomassa e uma fonte de calor na frente (frente a frente), junta calor
     * até 4.000 HU e transforma 20 mB de biomassa em 400 mB de biogás. Enche células vazias com biogás.
     */
    private boolean tickFermenter(Level level) {
        boolean changed = false;
        if (this.outputTank != null) {
            changed = MachineFluids.fillFromTank(this.inventory, FERMENTER_CELL_IN, FERMENTER_CELL_OUT, this.outputTank);
        }
        this.maxProgress = FERMENTER_BIOMASS_PER_FERTILIZER;
        if (this.progress >= FERMENTER_BIOMASS_PER_FERTILIZER
                && insertResults(List.of(new ItemStack(IC2AutoItems.FERTILIZER.get())), false)) {
            this.progress = 0;
            changed = true;
        }

        this.heatWorking = false;
        if (this.tank == null || this.outputTank == null || this.tank.isResourceBlank()) return changed;
        Direction facing = front();
        if (!(level.getBlockEntity(this.worldPosition.relative(facing)) instanceof MachineBlockEntity source)
                || !source.isHeatSource()) return changed;

        FluidVariant biogas = FluidVariant.of(IC2Fluids.BIOGAS.fluid());
        if (this.tank.variant.getFluid() != IC2Fluids.BIOMASS.fluid() || this.tank.amount < FERMENTER_BIOMASS_PER_RUN
                || !this.outputTank.canFill(biogas, FERMENTER_BIOGAS_PER_RUN)) return changed;

        this.heatBuffer += source.drawHeat(facing.getOpposite(), FERMENTER_HEAT_DRAW, false);
        if (this.heatBuffer >= FERMENTER_HEAT_PER_RUN) {
            this.heatBuffer -= FERMENTER_HEAT_PER_RUN;
            this.tank.consume(FERMENTER_BIOMASS_PER_RUN);
            this.outputTank.fill(biogas, FERMENTER_BIOGAS_PER_RUN);
            this.progress += 20;
        }
        this.heatWorking = true;
        return true;
    }

    // ── máquinas de processamento ─────────────────────────────────────────
    /** Máquina padrão do IC2: gasta a potência por tick e completa a operação no fim da duração. */
    private boolean tickProcessor(Level level) {
        Operation operation = findOperation(level);

        if (operation == null || !insertResults(operation.preview(), true) || !hasFluid(operation.fluid())
                || !canFillOutput(operation) || this.heat < operation.minHeat()) {
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
     * @param minHeat           calor mínimo para trabalhar
     * @param hardness          dureza mínima da lâmina
     */
    private record Operation(int inputCount, int secondaryCount, List<ItemStack> preview,
                             Function<RandomSource, List<ItemStack>> result, long fluid,
                             @Nullable FluidVariant resultFluid, long resultFluidAmount, int minHeat, int hardness) {
        static Operation items(int inputCount, int secondaryCount, List<ItemStack> preview,
                               Function<RandomSource, List<ItemStack>> result) {
            return new Operation(inputCount, secondaryCount, preview, result, 0, null, 0, 0, 0);
        }
    }

    private @Nullable Operation findOperation(Level level) {
        ItemStack input = this.slots.input() >= 0 ? this.inventory.getItem(this.slots.input()) : ItemStack.EMPTY;
        ItemStack secondary = this.slots.secondary() >= 0 ? this.inventory.getItem(this.slots.secondary()) : ItemStack.EMPTY;
        return switch (this.guiType) {
            case CANNER -> cannerOperation(input, secondary);
            case BLOCK_CUTTER -> cutterOperation(input);
            case ELECTRIC_FURNACE -> input.isEmpty() ? null : smeltingOperation(level, input);
            case RECYCLER -> input.isEmpty() ? null : recyclingOperation(input);
            case METAL_FORMER -> recipeOperation(METAL_FORMER_RECIPES[this.metalFormerMode], input, secondary);
            case CENTRIFUGE -> recipeOperation("thermal_centrifuge", input, secondary);
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
                        recipe.resultFluidAmount() * FluidConstants.BUCKET / 1000,
                        recipe.minHeat(), recipe.hardness()))
                .orElse(null);
    }

    /** Cortador de blocos do IC2: precisa de lâmina tão dura quanto o bloco (ferro 3, aço 6, diamante 9); a lâmina não gasta. */
    private @Nullable Operation cutterOperation(ItemStack input) {
        ItemStack blade = this.inventory.getItem(BLADE_SLOT);
        if (blade.isEmpty()) {
            this.bladeTooWeak = true;
            return null;
        }
        Operation operation = recipeOperation("block_cutter", input, ItemStack.EMPTY);
        this.bladeTooWeak = operation != null && operation.hardness() > bladeHardness(blade);
        return this.bladeTooWeak ? null : operation;
    }

    private static int bladeHardness(ItemStack blade) {
        Item item = blade.getItem();
        if (item == IC2AutoItems.BLOCK_CUTTING_BLADE_IRON.get()) return 3;
        if (item == IC2AutoItems.BLOCK_CUTTING_BLADE_STEEL.get()) return 6;
        if (item == IC2AutoItems.BLOCK_CUTTING_BLADE_DIAMOND.get()) return 9;
        return 0;
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
            long intake = guiType == MachineGuiType.MINER ? MinerLogic.MAX_INTAKE : profile.maxIntake();
            return Math.max(0, Math.min(intake, profile.capacity() - energy));
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
            case DATA_MODE -> machineMode();
            case DATA_HEAT -> isHeatSource() ? this.transmitHeat : this.guiType == MachineGuiType.FERMENTER ? this.heatBuffer : this.heat;
            case DATA_MAX_HEAT -> isHeatSource() ? maxHeatEmitted() : this.guiType == MachineGuiType.FERMENTER ? FERMENTER_HEAT_PER_RUN : this.maxHeat;
            default -> 0;
        };
    }

    private int machineMode() {
        if (this.transformer != null) return this.transformerMode.ordinal();
        return switch (this.guiType) {
            case METAL_FORMER -> this.metalFormerMode;
            case WIND_KINETIC_GENERATOR, WATER_KINETIC_GENERATOR -> kineticStatus();
            case BLOCK_CUTTER -> this.bladeTooWeak ? 1 : 0;
            default -> this.cannerMode.ordinal();
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

    // ── energia no item ───────────────────────────────────────────────────
    /** IC2 (energyRetainedInStorageBlockDrops): armazenamento quebrado ou desmontado guarda 80% da energia no item. */
    private static final double STORAGE_ENERGY_RETAINED = 0.8;

    @Override
    protected void collectImplicitComponents(net.minecraft.core.component.DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (this.profile.role() == MachineEnergyProfile.Role.STORAGE && this.energy > 0) {
            components.set(net.craftenergy.content.CEComponents.STORED_ENERGY.get(), (long) (this.energy * STORAGE_ENERGY_RETAINED));
        }
    }

    @Override
    protected void applyImplicitComponents(net.minecraft.core.component.DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Long stored = components.get(net.craftenergy.content.CEComponents.STORED_ENERGY.get());
        if (stored != null && this.profile.role() == MachineEnergyProfile.Role.STORAGE) {
            this.energy = Math.max(0, Math.min(this.profile.capacity(), stored));
        }
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
        output.putInt("Heat", this.heat);
        if (this.miner != null) {
            this.miner.write(output);
        }
        output.putInt("HeatBuffer", this.heatBuffer);
        output.putLong("HeatStore", this.heatStore);
        output.putLong("KineticStore", this.kineticStore);
        if (this.guiType == MachineGuiType.METAL_FORMER) {
            output.putInt("MetalFormerMode", this.metalFormerMode);
        }
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
        this.heat = Math.max(0, input.getIntOr("Heat", 0));
        if (this.miner != null) {
            this.miner.read(input);
        }
        this.heatBuffer = Math.max(0, input.getIntOr("HeatBuffer", 0));
        this.heatStore = Math.max(0, input.getLongOr("HeatStore", 0));
        this.kineticStore = Math.max(0, input.getLongOr("KineticStore", 0));
        this.metalFormerMode = Math.floorMod(input.getIntOr("MetalFormerMode", 0), METAL_FORMER_RECIPES.length);
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
