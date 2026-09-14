package net.ic2reborn.block.entity;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.fluid.MachineFluids;
import net.ic2reborn.menu.MachineGuiType;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.function.Predicate;

/**
 * Cadeia de vapor do IC2: gerador de vapor (caldeira), repressurizador, gerador cinético a vapor
 * e gerador cinético Stirling. Os geradores de calor vizinhos entregam HU pela face virada para a
 * máquina, como no resto do mod.
 */
final class SteamLogic {
    private static final long MB = FluidConstants.BUCKET / 1_000;

    // caldeira (IC2: TileEntitySteamGenerator)
    private static final float MAX_HEAT = 500.0F;
    private static final float HEAT_PER_HU = 5.0E-4F;
    private static final float COOLING_PER_MB = 0.1F;
    private static final int MAX_HU_INPUT = 1_200;
    static final int MAX_CALCIFICATION = 100_000;
    private static final int STEAM_EXPANSION = 100;
    private static final float EPSILON = 1.0E-4F;

    /** Saída mostrada na GUI da caldeira. */
    static final int OUTPUT_NONE = 0, OUTPUT_STEAM = 3, OUTPUT_SUPERHEATED = 4;

    // repressurizador: 10 mB de vapor + 1 HU → 16 mB de vapor (32 com vapor superaquecido)
    private static final int REPRESSURIZER_INPUT = 10;

    // Stirling cinético: a cada 4 HU, 12 KU e 1 HU aquecem o líquido
    private static final int STIRLING_MAX_HEAT = 1_000;
    private static final int STIRLING_MAX_KU = 2_000;

    /** Status do gerador cinético a vapor (bits). */
    static final int STATUS_NO_TURBINE = 1, STATUS_VENTING = 2, STATUS_THROTTLED = 4, STATUS_BLOCKED = 8;

    private final MachineBlockEntity machine;
    private final MachineGuiType type;
    private boolean working;

    // caldeira
    private int inputMb;
    private int pressure;
    private float systemHeat = -1;
    private int calcification;
    private int heatInput;
    private int outputMb;
    private int outputFluid;

    // repressurizador e Stirling cinético
    private int heatStored;
    private int kuBuffer;
    private int liquidHeat;

    // cinético a vapor
    private int kuOutput;
    private int condensation;
    private boolean blockedByWater;
    private int status;

    private SteamLogic(MachineBlockEntity machine, MachineGuiType type) {
        this.machine = machine;
        this.type = type;
    }

    static @Nullable SteamLogic create(MachineBlockEntity machine, MachineGuiType type) {
        return switch (type) {
            case STEAM_GENERATOR, STEAM_REPRESSURIZER, STEAM_KINETIC_GENERATOR, STIRLING_KINETIC_GENERATOR -> new SteamLogic(machine, type);
            default -> null;
        };
    }

    boolean working() {
        return this.working;
    }

    boolean canPlace(int slot, ItemStack stack) {
        if (this.type == MachineGuiType.STEAM_KINETIC_GENERATOR && slot == 1) return stack.is(IC2AutoItems.STEAM_TURBINE.get());
        return true;
    }

    // ── GUI ───────────────────────────────────────────────────────────────
    /** Valores sincronizados que substituem os campos padrão do block entity. */
    @Nullable Integer dataValue(int field) {
        if (this.type == MachineGuiType.STEAM_GENERATOR) {
            return switch (field) {
                case MachineBlockEntity.DATA_MODE -> this.inputMb | (this.pressure << 11) | (this.outputFluid << 20);
                case MachineBlockEntity.DATA_POWER -> this.heatInput;
                case MachineBlockEntity.DATA_ENERGY -> this.outputMb;
                case MachineBlockEntity.DATA_PROGRESS -> this.calcification;
                case MachineBlockEntity.DATA_MAX_PROGRESS -> MAX_CALCIFICATION;
                case MachineBlockEntity.DATA_HEAT -> Math.round(Math.max(0, this.systemHeat) * 10);
                case MachineBlockEntity.DATA_MAX_HEAT -> Math.round(MAX_HEAT * 10);
                default -> null;
            };
        }
        if (this.type == MachineGuiType.STEAM_KINETIC_GENERATOR) {
            return switch (field) {
                case MachineBlockEntity.DATA_MODE -> this.status;
                case MachineBlockEntity.DATA_PROGRESS -> this.kuOutput;
                default -> null;
            };
        }
        if (this.type == MachineGuiType.STIRLING_KINETIC_GENERATOR) {
            return switch (field) {
                case MachineBlockEntity.DATA_PROGRESS -> this.kuBuffer;
                case MachineBlockEntity.DATA_MAX_PROGRESS -> STIRLING_MAX_KU;
                default -> null;
            };
        }
        return null;
    }

    boolean handleButton(int id) {
        if (this.type != MachineGuiType.STEAM_GENERATOR) return false;
        int[] water = {1_000, 100, 10, 1, -1_000, -100, -10, -1};
        int[] valve = {100, 10, 1, -100, -10, -1};
        if (id >= MachineBlockEntity.BUTTON_STEAM_WATER && id < MachineBlockEntity.BUTTON_STEAM_WATER + water.length) {
            this.inputMb = Math.max(0, Math.min(1_000, this.inputMb + water[id - MachineBlockEntity.BUTTON_STEAM_WATER]));
        } else if (id >= MachineBlockEntity.BUTTON_STEAM_PRESSURE && id < MachineBlockEntity.BUTTON_STEAM_PRESSURE + valve.length) {
            this.pressure = Math.max(0, Math.min(300, this.pressure + valve[id - MachineBlockEntity.BUTTON_STEAM_PRESSURE]));
        } else {
            return false;
        }
        this.machine.setChanged();
        return true;
    }

    // ── tick ──────────────────────────────────────────────────────────────
    boolean tick(Level level) {
        this.working = false;
        return switch (this.type) {
            case STEAM_GENERATOR -> tickSteamGenerator(level);
            case STEAM_REPRESSURIZER -> tickRepressurizer(level);
            case STEAM_KINETIC_GENERATOR -> tickSteamKinetic(level);
            case STIRLING_KINETIC_GENERATOR -> tickStirlingKinetic(level);
            default -> false;
        };
    }

    private float biomeTemperature(Level level) {
        return level.getBiome(this.machine.getBlockPos()).value().getBaseTemperature() * 25.0F;
    }

    /**
     * Caldeira do IC2: aquece com até 1.200 HU/t dos vizinhos; acima de 100 °C (mais com a válvula de
     * pressão) cada mB de água vira 100 mB de vapor, e acima de 374 °C sai vapor superaquecido. Água
     * comum calcifica a caldeira; com 100.000 mB ela para. O vapor vai para os vizinhos que aceitarem.
     */
    private boolean tickSteamGenerator(Level level) {
        float ambient = biomeTemperature(level);
        if (this.systemHeat < ambient) this.systemHeat = ambient;
        boolean worked = this.calcification < MAX_CALCIFICATION && workBoiler(level, ambient);
        if (!worked) {
            this.heatInput = 0;
            this.outputMb = 0;
            this.outputFluid = OUTPUT_NONE;
            cooldown(0.01F, ambient);
        }
        this.working = worked && this.outputMb > 0;
        return true;
    }

    private boolean workBoiler(Level level, float ambient) {
        MachineBlockEntity.MachineTank water = this.machine.mainTank();
        this.heatInput = drawHeat(level, MAX_HU_INPUT, null);
        if (this.heatInput <= 0 || water == null) return false;
        this.outputMb = 0;
        this.outputFluid = OUTPUT_NONE;

        if (water.amount < MB || this.inputMb <= 0) {
            heatup(this.heatInput);
            return true;
        }
        Fluid inputFluid = water.variant.getFluid();
        boolean distilled = inputFluid == IC2Fluids.DISTILLED_WATER.fluid();
        int maxAmount = (int) Math.min(this.inputMb, water.amount / MB);
        float huNeeded = 100.0F + this.pressure / 220.0F * 100.0F;
        float targetTemp = 100.0F + this.pressure / 220.0F * 100.0F * 2.74F;
        float requiredHeat = targetTemp - this.systemHeat;
        float remainingHu = this.heatInput;
        if (requiredHeat > EPSILON) {
            int heatRequired = (int) Math.ceil(requiredHeat / HEAT_PER_HU);
            if (this.heatInput <= heatRequired) {
                heatup(this.heatInput);
                return true;
            }
            heatup(heatRequired);
            remainingHu -= heatRequired;
            requiredHeat = targetTemp - this.systemHeat;
        }

        float availableSystemHu = Math.min(-requiredHeat / HEAT_PER_HU, (float) (MAX_HU_INPUT - this.heatInput));
        int activeAmount = Math.max(0, Math.min(maxAmount, (int) ((remainingHu + availableSystemHu) / huNeeded)));
        int totalAmount = activeAmount;
        remainingHu -= activeAmount * huNeeded;
        if (remainingHu < 0.0F) {
            cooldown(-remainingHu * HEAT_PER_HU, ambient);
            requiredHeat = targetTemp - this.systemHeat;
        }
        if (requiredHeat <= -0.1001F) {
            int coolingAmount = Math.min(Math.min(maxAmount, (int) (-requiredHeat / COOLING_PER_MB)), 20);
            cooldown(coolingAmount * COOLING_PER_MB, ambient);
            totalAmount = Math.max(activeAmount, coolingAmount);
        }
        if (remainingHu > 0.0F) heatup(remainingHu);
        if (totalAmount <= 0) return true;

        if (!distilled) this.calcification += totalAmount;
        water.consume(totalAmount * MB);
        if (activeAmount <= 0) return true;

        this.outputMb = activeAmount * STEAM_EXPANSION;
        boolean superheated = this.systemHeat >= 373.9999F;
        this.outputFluid = superheated ? OUTPUT_SUPERHEATED : OUTPUT_STEAM;
        FluidVariant steam = FluidVariant.of(superheated ? IC2Fluids.SUPERHEATED_STEAM.fluid() : IC2Fluids.STEAM.fluid());
        long sent = distribute(level, steam, this.outputMb * MB, null);
        long leftoverWater = (this.outputMb * MB - sent) / STEAM_EXPANSION;
        if (leftoverWater > 0 && water.canFill(FluidVariant.of(inputFluid), leftoverWater)) {
            water.fill(FluidVariant.of(inputFluid), leftoverWater);
        }
        return true;
    }

    private void heatup(float hu) {
        this.systemHeat = Math.min(MAX_HEAT, this.systemHeat + hu * HEAT_PER_HU);
    }

    private void cooldown(float amount, float ambient) {
        this.systemHeat = Math.max(this.systemHeat - amount, ambient);
    }

    /** Repressurizador do IC2: cada HU transforma 10 mB de vapor em 16 mB de vapor (32 se superaquecido). */
    private boolean tickRepressurizer(Level level) {
        MachineBlockEntity.MachineTank input = this.machine.mainTank();
        MachineBlockEntity.MachineTank output = this.machine.secondTank();
        if (input == null || output == null) return false;
        boolean changed = false;
        if (output.amount > 0) {
            changed = distribute(level, output.variant, output.amount, target -> !(target instanceof MachineBlockEntity other
                    && other.getGuiType() == MachineGuiType.STEAM_REPRESSURIZER), output) > 0;
        }
        if (input.amount < REPRESSURIZER_INPUT * MB) return changed;
        int aim = (int) (input.amount / (REPRESSURIZER_INPUT * MB));
        if (this.heatStored < aim) this.heatStored += drawHeat(level, aim - this.heatStored, null);
        int produced = input.variant.getFluid() == IC2Fluids.SUPERHEATED_STEAM.fluid() ? 32 : 16;
        FluidVariant steam = FluidVariant.of(IC2Fluids.STEAM.fluid());
        while (this.heatStored > 0 && input.amount >= REPRESSURIZER_INPUT * MB && output.canFill(steam, produced * MB)) {
            this.heatStored--;
            input.consume(REPRESSURIZER_INPUT * MB);
            output.fill(steam, produced * MB);
            this.working = true;
        }
        return changed || this.working;
    }

    /**
     * Gerador cinético a vapor do IC2: com a turbina, gasta todo o vapor do tanque e entrega 2 KU por mB
     * (4 com vapor superaquecido) pela frente. Vapor comum condensa 10% em água destilada, que freia a
     * turbina conforme enche o tanque; o resto do vapor vai para condensadores vizinhos (ou é perdido).
     */
    private boolean tickSteamKinetic(Level level) {
        MachineBlockEntity.MachineTank steam = this.machine.mainTank();
        MachineBlockEntity.MachineTank water = this.machine.secondTank();
        if (steam == null || water == null) return false;
        FluidVariant distilled = FluidVariant.of(IC2Fluids.DISTILLED_WATER.fluid());
        if (this.blockedByWater && water.canFill(distilled, MB)) this.blockedByWater = false;
        boolean hasTurbine = this.machine.getInventory().getItem(1).is(IC2AutoItems.STEAM_TURBINE.get());
        this.status = (hasTurbine ? 0 : STATUS_NO_TURBINE) | (this.blockedByWater ? STATUS_BLOCKED : 0);

        if (steam.amount < MB || !hasTurbine || this.blockedByWater) {
            boolean had = this.kuOutput > 0;
            this.kuOutput = 0;
            return had;
        }
        boolean hot = steam.variant.getFluid() == IC2Fluids.SUPERHEATED_STEAM.fluid();
        int amount = (int) (steam.amount / MB);
        steam.consume(amount * MB);
        int rawKu = amount * 2 * (hot ? 2 : 1);
        int vented;
        if (hot) {
            vented = outputSteam(level, amount, true);
        } else {
            int condensed = amount / 10;
            this.condensation += condensed;
            vented = outputSteam(level, amount - condensed, false);
        }
        if (vented > 0) this.status |= STATUS_VENTING;

        float throttle = 1.0F;
        if (water.amount > 0) {
            throttle = 1.0F - (float) water.amount / water.capacity();
            this.status |= STATUS_THROTTLED;
        }
        this.kuOutput = (int) (rawKu * throttle);
        if (this.condensation >= 100) {
            if (water.canFill(distilled, MB)) {
                this.condensation -= 100;
                water.fill(distilled, MB);
            } else {
                this.blockedByWater = true;
            }
        }
        this.working = this.kuOutput > 0;
        return true;
    }

    /** Manda o vapor que sai da turbina para condensadores (e, se superaquecido, outras turbinas); devolve o que foi perdido. */
    private int outputSteam(Level level, int amount, boolean hot) {
        long sent = distribute(level, FluidVariant.of(IC2Fluids.STEAM.fluid()), amount * MB, target -> target instanceof MachineBlockEntity other
                && (other.getGuiType() == MachineGuiType.CONDENSER || hot && other.getGuiType() == MachineGuiType.STEAM_KINETIC_GENERATOR));
        return (int) (amount - sent / MB);
    }

    /**
     * Gerador cinético Stirling do IC2: puxa até 1.000 HU das faces que não são a frente; a cada 4 HU
     * guarda 12 KU (máx. 2.000) e aquece o líquido do tanque (água → água quente, refrigerante → quente).
     */
    private boolean tickStirlingKinetic(Level level) {
        MachineBlockEntity.MachineTank input = this.machine.mainTank();
        MachineBlockEntity.MachineTank output = this.machine.secondTank();
        if (input == null || output == null) return false;
        boolean changed = MachineFluids.fillFromTank(this.machine.getInventory(), 2, 3, output);
        if (this.heatStored < STIRLING_MAX_HEAT) {
            this.heatStored += drawHeat(level, STIRLING_MAX_HEAT - this.heatStored, this.machine.facing());
        }
        if (input.amount < MB || this.kuBuffer >= STIRLING_MAX_KU) return changed;
        Heating heating = heating(input.variant.getFluid());
        if (heating == null) return changed;
        FluidVariant hot = FluidVariant.of(heating.hot());
        if (!output.isResourceBlank() && !output.variant.equals(hot)) return changed;

        long room = (output.capacity() - output.amount) / MB;
        int use = this.heatStored / 4;
        use = (int) Math.min(use, Math.min(room, input.amount / MB) * heating.huPerMb() - this.liquidHeat);
        use = Math.min(use, (STIRLING_MAX_KU - this.kuBuffer) / 3);
        if (use > 0) {
            this.kuBuffer = Math.min(STIRLING_MAX_KU, this.kuBuffer + use * 12);
            this.liquidHeat += use;
            this.heatStored -= use * 4;
            this.working = true;
            changed = true;
        }
        if (this.liquidHeat >= heating.huPerMb()) {
            long mb = Math.min(this.liquidHeat / heating.huPerMb(), Math.min(room, input.amount / MB));
            if (mb > 0) {
                this.liquidHeat -= (int) (mb * heating.huPerMb());
                input.consume(mb * MB);
                output.fill(hot, mb * MB);
                changed = true;
            }
        }
        return changed;
    }

    record Heating(Fluid hot, int huPerMb) {}

    static @Nullable Heating heating(Fluid cold) {
        if (cold == Fluids.WATER) return new Heating(IC2Fluids.HOT_WATER.fluid(), 1);
        if (cold == IC2Fluids.COOLANT.fluid()) return new Heating(IC2Fluids.HOT_COOLANT.fluid(), 20);
        return null;
    }

    static boolean heatable(Fluid fluid) {
        return heating(fluid) != null;
    }

    // ── energia cinética ──────────────────────────────────────────────────
    int drawKinetic(Direction side, int request, boolean simulate) {
        if (side != this.machine.facing()) return 0;
        if (this.type == MachineGuiType.STEAM_KINETIC_GENERATOR) return Math.max(0, Math.min(request, this.kuOutput));
        int drawn = Math.max(0, Math.min(request, this.kuBuffer));
        if (!simulate && drawn > 0) {
            this.kuBuffer -= drawn;
            this.machine.setChanged();
        }
        return drawn;
    }

    int kineticBandwidth(Direction side) {
        if (side != this.machine.facing()) return 0;
        return this.type == MachineGuiType.STEAM_KINETIC_GENERATOR ? this.kuOutput : STIRLING_MAX_KU;
    }

    // ── ajudantes ─────────────────────────────────────────────────────────
    /** Puxa calor dos geradores vizinhos (menos pela face {@code except}). */
    private int drawHeat(Level level, int amount, @Nullable Direction except) {
        int remaining = amount;
        BlockPos pos = this.machine.getBlockPos();
        for (Direction direction : Direction.values()) {
            if (remaining <= 0) break;
            if (direction == except) continue;
            if (level.getBlockEntity(pos.relative(direction)) instanceof MachineBlockEntity source && source.isHeatSource()) {
                remaining -= source.drawHeat(direction.getOpposite(), remaining, false);
            }
        }
        return amount - remaining;
    }

    private long distribute(Level level, FluidVariant fluid, long droplets, @Nullable Predicate<BlockEntity> accepts) {
        return distribute(level, fluid, droplets, accepts, null);
    }

    /** Coloca fluido nos vizinhos que aceitarem; com {@code from}, tira de lá o que foi entregue. */
    private long distribute(Level level, FluidVariant fluid, long droplets, @Nullable Predicate<BlockEntity> accepts,
                            MachineBlockEntity.MachineTank from) {
        long left = droplets;
        BlockPos pos = this.machine.getBlockPos();
        for (Direction direction : Direction.values()) {
            if (left <= 0) break;
            BlockPos target = pos.relative(direction);
            if (accepts != null && !accepts.test(level.getBlockEntity(target))) continue;
            Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, target, direction.getOpposite());
            if (storage == null) continue;
            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = storage.insert(fluid, left, transaction);
                transaction.commit();
                left -= inserted;
            }
        }
        long sent = droplets - left;
        if (from != null && sent > 0) from.consume(sent);
        return sent;
    }

    // ── salvar/carregar ───────────────────────────────────────────────────
    void write(ValueOutput output) {
        output.putInt("SteamInputMb", this.inputMb);
        output.putInt("SteamPressure", this.pressure);
        output.putFloat("SystemHeat", this.systemHeat);
        output.putInt("Calcification", this.calcification);
        output.putInt("SteamHeatStored", this.heatStored);
        output.putInt("KuBuffer", this.kuBuffer);
        output.putInt("LiquidHeat", this.liquidHeat);
        output.putInt("Condensation", this.condensation);
    }

    void read(ValueInput input) {
        this.inputMb = Math.max(0, Math.min(1_000, input.getIntOr("SteamInputMb", 0)));
        this.pressure = Math.max(0, Math.min(300, input.getIntOr("SteamPressure", 0)));
        this.systemHeat = input.getFloatOr("SystemHeat", -1);
        this.calcification = Math.max(0, input.getIntOr("Calcification", 0));
        this.heatStored = Math.max(0, input.getIntOr("SteamHeatStored", 0));
        this.kuBuffer = Math.max(0, input.getIntOr("KuBuffer", 0));
        this.liquidHeat = Math.max(0, input.getIntOr("LiquidHeat", 0));
        this.condensation = Math.max(0, input.getIntOr("Condensation", 0));
    }
}
