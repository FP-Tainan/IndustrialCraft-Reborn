package net.ic2reborn.energy;

import net.craftenergy.api.EnergyUnits;
import net.ic2reborn.menu.MachineGuiType;

/**
 * Parâmetros elétricos de cada máquina, em Craft Energy (docs/energia-conversao-ic2.md).
 *
 * @param role           papel na rede
 * @param voltage        tensão nominal/de saída em MV (no transformador: o lado de baixa)
 * @param power          potência em CW (produção máxima do gerador, carga/descarga da bateria,
 *                       consumo da máquina, potência máxima do transformador)
 * @param capacity       energia interna em CW·tick
 * @param operationTicks duração de uma operação (máquinas de processamento)
 * @param highVoltage    lado de alta do transformador, em MV
 * @param efficiency     eficiência do transformador (0 a 1)
 */
public record MachineEnergyProfile(Role role, int voltage, long power, long capacity, int operationTicks,
                                   int highVoltage, double efficiency) {
    public enum Role { NONE, GENERATOR, STORAGE, PROCESSOR, TRANSFORMER, HEAT, KINETIC }

    public static final MachineEnergyProfile NONE = new MachineEnergyProfile(Role.NONE, 0, 0, 0, 0, 0, 0.0);

    /** Máquinas ainda não ligadas na rede ficam {@link #NONE}. */
    public static MachineEnergyProfile of(MachineGuiType type) {
        return switch (type) {
            case GENERATOR -> simple(Role.GENERATOR, 220, 5_000, EnergyUnits.fromCWh(2_000));
            // geradores sem combustível guardam só um segundo de produção
            case SOLAR_GENERATOR -> simple(Role.GENERATOR, 220, 500, 500L * 20);
            case WATER_GENERATOR -> simple(Role.GENERATOR, 220, 1_000, 1_000L * 20);
            case WIND_GENERATOR -> simple(Role.GENERATOR, 220, 5_000, 5_000L * 20);
            // IC2: 20 EU/t e 2.400 EU guardados
            case GEO_GENERATOR -> simple(Role.GENERATOR, 220, 10_000, EnergyUnits.fromCWh(1_200));
            // IC2: 8–32 EU/t conforme o combustível e 32.000 EU guardados
            case SEMIFLUID_GENERATOR -> simple(Role.GENERATOR, 220, 16_000, EnergyUnits.fromCWh(16_000));

            case BATBOX -> simple(Role.STORAGE, 220, 4_400, EnergyUnits.fromCWh(20_000));
            case CESU -> simple(Role.STORAGE, 1_000, 20_000, EnergyUnits.fromCWh(150_000));
            case MFE -> simple(Role.STORAGE, 2_400, 120_000, EnergyUnits.fromCWh(2_000_000));
            case MFSU -> simple(Role.STORAGE, 13_800, 1_000_000, EnergyUnits.fromCWh(20_000_000));

            case MACERATOR, COMPRESSOR, EXTRACTOR -> processor(220, 2_000, 300);
            case ELECTRIC_FURNACE -> processor(220, 3_000, 100);
            case RECYCLER -> processor(220, 1_000, 45);
            case SOLID_CANNER -> processor(220, 2_000, 200);
            case ORE_WASHING_PLANT -> processor(1_000, 16_000, 500);
            case CANNER -> processor(220, 4_000, 200);
            case METAL_FORMER -> processor(220, 4_000, 200);
            case BLOCK_CUTTER -> processor(220, 4_000, 450);
            case CENTRIFUGE -> processor(1_000, 48_000, 500);
            // IC2: 1.000 EU guardados; o consumo depende da broca (3.000 CW com a perfuradora)
            case MINER -> new MachineEnergyProfile(Role.PROCESSOR, 220, 3_000, EnergyUnits.fromCWh(500), 0, 0, 0.0);
            // calor (HU) sem eletricidade: fermentador e geradores de calor sólido, fluido e RT
            // energia cinética (KU) sem eletricidade: rotores e manivela
            case WIND_KINETIC_GENERATOR, WATER_KINETIC_GENERATOR, MANUAL_KINETIC_GENERATOR -> new MachineEnergyProfile(Role.KINETIC, 0, 0, 0, 0, 0, 0.0);
            // IC2: 4 KU = 1 EU → 125 CW por KU; saída a partir de MV
            case KINETIC_GENERATOR -> simple(Role.GENERATOR, 1_000, 250_000, 500_000);
            // IC2: 10 motores × 100 KU/t, 10.000 EU guardados
            case ELECTRIC_KINETIC_GENERATOR -> new MachineEnergyProfile(Role.PROCESSOR, 1_000, 125_000, EnergyUnits.fromCWh(5_000), 0, 0, 0.0);
            case FERMENTER, SOLID_HEAT_GENERATOR, FLUID_HEAT_GENERATOR, RT_HEAT_GENERATOR -> new MachineEnergyProfile(Role.HEAT, 0, 0, 0, 0, 0, 0.0);
            // IC2: 10 HU/t por bobina, 1 HU = 1 EU → 500 CW; 10.000 EU guardados
            case ELECTRIC_HEAT_GENERATOR -> new MachineEnergyProfile(Role.PROCESSOR, 1_000, 50_000, EnergyUnits.fromCWh(5_000), 0, 0, 0.0);
            // IC2: aquece com 1 EU/t, processa com mais 15 EU/t; a operação termina em 4.000 pontos de progresso
            case INDUCTION_FURNACE -> new MachineEnergyProfile(Role.PROCESSOR, 1_000, 16_000, EnergyUnits.fromCWh(5_000), 4_000, 0, 0.0);

            // IC2: 10.000 EU guardados; 1 CWh por posição olhada, 20 CWh por item colhido, 10 CWh por cuidado
            case CROP_HARVESTER, CROPMATRON -> new MachineEnergyProfile(Role.PROCESSOR, 220, 2_000, EnergyUnits.fromCWh(10_000), 0, 0, 0.0);
            case LV_TRANSFORMER -> transformer(220, 1_000, 20_000, 0.97);
            case MV_TRANSFORMER -> transformer(1_000, 2_400, 120_000, 0.975);
            case HV_TRANSFORMER -> transformer(2_400, 13_800, 1_000_000, 0.98);
            case EV_TRANSFORMER -> transformer(13_800, 69_000, 5_000_000, 0.985);

            default -> NONE;
        };
    }

    private static MachineEnergyProfile simple(Role role, int voltage, long power, long capacity) {
        return new MachineEnergyProfile(role, voltage, power, capacity, 0, 0, 0.0);
    }

    private static MachineEnergyProfile processor(int voltage, long power, int operationTicks) {
        return new MachineEnergyProfile(Role.PROCESSOR, voltage, power, power * operationTicks, operationTicks, 0, 0.0);
    }

    private static MachineEnergyProfile transformer(int lowVoltage, int highVoltage, long power, double efficiency) {
        return new MachineEnergyProfile(Role.TRANSFORMER, lowVoltage, power, 0, 0, highVoltage, efficiency);
    }

    /** Quanto uma máquina de processamento puxa da rede por tick: o dobro do consumo, para encher o buffer sem pico de corrente. */
    public long maxIntake() {
        return this.power * 2;
    }

    /** Se o block entity precisa de tick no servidor. */
    public boolean ticks() {
        return this.role != Role.NONE;
    }
}
