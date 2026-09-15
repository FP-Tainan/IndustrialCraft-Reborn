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
    /** LOGISTICS: sem eletricidade, mas com tick (tanques, buffers, distribuidores). */
    public enum Role { NONE, GENERATOR, STORAGE, PROCESSOR, TRANSFORMER, HEAT, KINETIC, LOGISTICS }

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
            // Advanced Machines: fim de jogo, só em extra-alta tensão. IC2: 15/24/48 EU/t → 2.000 CW por EU/t;
            // a operação pede 120.000 pontos de progresso (o calor de cada tick), o reciclador 10.000
            case ROTARY_MACERATOR, SINGULARITY_COMPRESSOR, CENTRIFUGE_EXTRACTOR -> heating(30_000, ADVANCED_PROGRESS);
            case COMPACTING_RECYCLER -> heating(30_000, ADVANCED_PROGRESS / 12);
            case LIQUESCENT_EXTRUDER, IMPELLERIZED_ROLLER, WATER_JET_CUTTER, VACUUM_CANNER -> heating(48_000, ADVANCED_PROGRESS);
            case THERMAL_WASHER -> heating(96_000, ADVANCED_PROGRESS);
            // Advanced Solar Panels: produção de dia (a noturna fica no block entity), em tensões altas.
            // IC2: 8/64/512/4.096 EU/t e 32k/100k/1M/10M EU → EU/t × 500 = CW, EU × 0,5 = CWh
            case ADVANCED_SOLAR_PANEL -> simple(Role.GENERATOR, 1_000, 4_000, EnergyUnits.fromCWh(16_000));
            case HYBRID_SOLAR_PANEL -> simple(Role.GENERATOR, 2_400, 32_000, EnergyUnits.fromCWh(50_000));
            case ULTIMATE_SOLAR_PANEL -> simple(Role.GENERATOR, 13_800, 256_000, EnergyUnits.fromCWh(500_000));
            case QUANTUM_SOLAR_PANEL -> simple(Role.GENERATOR, 69_000, 2_048_000, EnergyUnits.fromCWh(5_000_000));
            // gerador quântico (criativo): produção e tensão escolhidas na GUI; o block entity monta o perfil real
            case QUANTUM_GENERATOR -> simple(Role.GENERATOR, 2_400, 256_000, 256_000);
            // transformador molecular: qualquer tensão, sem buffer; puxa da rede só o que falta da receita
            case MOLECULAR_TRANSFORMER -> new MachineEnergyProfile(Role.PROCESSOR, 69_000, 100_000_000, 100_000_000, 0, 0, 0.0);
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
            // metalurgia sem eletricidade: combustível (fornalha de ferro), calor (alto-forno) e o forno de coque
            case IRON_FURNACE, BLAST_FURNACE, COKE_KILN, COKE_KILN_HATCH, COKE_KILN_GRATE -> new MachineEnergyProfile(Role.HEAT, 0, 0, 0, 0, 0, 0.0);
            // armazenamento e logística (LogisticsLogic)
            case TANK, ITEM_BUFFER, WEIGHTED_ITEM_DISTRIBUTOR, WEIGHTED_FLUID_DISTRIBUTOR, FLUID_DISTRIBUTOR, SOLAR_DISTILLER -> new MachineEnergyProfile(Role.LOGISTICS, 0, 0, 0, 0, 0, 0.0);
            // IC2: bomba 1 EU/t por 20 ticks; envasadora 2 EU/t por 100 ticks
            case PUMP -> processor(220, 500, 20);
            case FLUID_BOTTLER -> processor(220, 1_000, 100);
            // IC2: triagem nível 2 (20 EU por item), regulador nível 4 (10 EU por operação), condensador nível 3 (2 EU/t por ventoinha)
            case SORTING_MACHINE -> new MachineEnergyProfile(Role.PROCESSOR, 1_000, 20_000, 1_000_000, 0, 0, 0.0);
            case FLUID_REGULATOR -> new MachineEnergyProfile(Role.PROCESSOR, 13_800, 5_000, 200_000, 0, 0, 0.0);
            case CONDENSER -> new MachineEnergyProfile(Role.PROCESSOR, 2_400, 4_000, 400_000, 0, 0, 0.0);
            // vapor e Stirling: calor e KU sem eletricidade; o gerador Stirling rende 0,5 EU por HU
            case STEAM_GENERATOR, STEAM_REPRESSURIZER, LIQUID_HEAT_EXCHANGER -> new MachineEnergyProfile(Role.HEAT, 0, 0, 0, 0, 0, 0.0);
            case STEAM_KINETIC_GENERATOR, STIRLING_KINETIC_GENERATOR -> new MachineEnergyProfile(Role.KINETIC, 0, 0, 0, 0, 0, 0.0);
            case STIRLING_GENERATOR -> simple(Role.GENERATOR, 1_000, 50_000, 500_000);
            // reator nuclear: gerador de 13.800 MV (1 de produção = 5 EU/t); porta de fluidos com tick; injetores (IC2: 48.000 EU, nível 2)
            case NUCLEAR_REACTOR -> simple(Role.GENERATOR, 13_800, 50_000_000, 100_000_000);
            case REACTOR_FLUID_PORT -> new MachineEnergyProfile(Role.LOGISTICS, 0, 0, 0, 0, 0, 0.0);
            case REACTOR_COOLANT_INJECTOR -> new MachineEnergyProfile(Role.PROCESSOR, 1_000, 20_000, 24_000_000, 0, 0, 0.0);
            // UU-matter (IC2): fabricador nível 3 com 1.000.000 EU por mB; scanner 256 EU/t por 3.300 ticks; replicador 512 EU/t
            case MASS_FABRICATOR -> new MachineEnergyProfile(Role.PROCESSOR, 2_400, 256_000, 500_000_000, 0, 0, 0.0);
            case SCANNER -> new MachineEnergyProfile(Role.PROCESSOR, 13_800, 128_000, 256_000_000, 3_300, 0, 0.0);
            case REPLICATOR -> new MachineEnergyProfile(Role.PROCESSOR, 13_800, 256_000, 1_000_000_000, 1_000, 0, 0.0);
            // placas de carga: os mesmos armazenamentos do BatBox ao MFSU
            case CHARGEPAD -> simple(Role.STORAGE, 220, 4_400, EnergyUnits.fromCWh(20_000));
            case CHARGEPAD_CESU -> simple(Role.STORAGE, 1_000, 20_000, EnergyUnits.fromCWh(150_000));
            case CHARGEPAD_MFE -> simple(Role.STORAGE, 2_400, 120_000, EnergyUnits.fromCWh(2_000_000));
            case CHARGEPAD_MFSU -> simple(Role.STORAGE, 13_800, 1_000_000, EnergyUnits.fromCWh(20_000_000));
            // utilidades (IC2): Tesla 10.000 EU nível 2; carregador de chunks 2.500 EU; eletrolisador 32 EU/t; magnetizador; luminária; RTG até 32 EU/t
            case TESLA_COIL -> new MachineEnergyProfile(Role.PROCESSOR, 1_000, 20_000, 5_000_000, 0, 0, 0.0);
            case CHUNK_LOADER -> new MachineEnergyProfile(Role.PROCESSOR, 220, 1_000, 1_250_000, 0, 0, 0.0);
            case ELECTROLYZER -> new MachineEnergyProfile(Role.PROCESSOR, 1_000, 16_000, 16_000_000, 20, 0, 0.0);
            case MAGNETIZER -> new MachineEnergyProfile(Role.PROCESSOR, 220, 2_000, 50_000, 0, 0, 0.0);
            case LUMINATOR -> new MachineEnergyProfile(Role.PROCESSOR, 220, 250, 5_000, 0, 0, 0.0);
            case RT_GENERATOR -> simple(Role.GENERATOR, 220, 16_000, 10_000_000);
            // automação (IC2): terraformador nível 4; minerador avançado 4.000.000 EU; fabricador em lote 2 EU/t por 40 ticks; O-Mats
            case TERRAFORMER -> new MachineEnergyProfile(Role.PROCESSOR, 13_800, 1_000_000, 50_000_000, 0, 0, 0.0);
            case ADVANCED_MINER -> new MachineEnergyProfile(Role.PROCESSOR, 2_400, 512_000, 2_000_000_000L, 0, 0, 0.0);
            case BATCH_CRAFTER -> new MachineEnergyProfile(Role.PROCESSOR, 220, 1_000, 10_000_000, 40, 0, 0.0);
            case ENERGY_O_MAT -> new MachineEnergyProfile(Role.PROCESSOR, 13_800, 1_000_000, 10_000_000, 0, 0, 0.0);
            case TRADE_O_MAT -> new MachineEnergyProfile(Role.LOGISTICS, 0, 0, 0, 0, 0, 0.0);
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

    /** Pontos de progresso de uma operação das Advanced Machines (12 ticks com o calor no máximo). */
    public static final int ADVANCED_PROGRESS = 120_000;

    /** Advanced Machines: 13.800 MV, buffer para 640 ticks trabalhando; operationTicks guarda os pontos de progresso. */
    private static MachineEnergyProfile heating(long power, int progressPoints) {
        return new MachineEnergyProfile(Role.PROCESSOR, 13_800, power, power * 640, progressPoints, 0, 0.0);
    }

    /** Advanced Machines com upgrades: overclocker não faz nada; transformador e armazenamento valem. */
    public MachineEnergyProfile heatingUpgraded(int transformers, int storageUpgrades) {
        if (transformers <= 0 && storageUpgrades <= 0) return this;
        int newVoltage = this.voltage;
        for (int i = 0; i < transformers; i++) newVoltage = nextVoltage(newVoltage);
        return new MachineEnergyProfile(this.role, newVoltage, this.power,
                this.capacity + EnergyUnits.fromCWh(5_000L * storageUpgrades), this.operationTicks, this.highVoltage, this.efficiency);
    }

    private static MachineEnergyProfile transformer(int lowVoltage, int highVoltage, long power, double efficiency) {
        return new MachineEnergyProfile(Role.TRANSFORMER, lowVoltage, power, 0, 0, highVoltage, efficiency);
    }

    /**
     * Perfil com upgrades do IC2: cada overclocker deixa a operação 30% mais curta e o consumo 60%
     * maior; cada transformador sobe um nível de tensão; cada armazenamento soma 10.000 EU (5.000 CWh).
     */
    public MachineEnergyProfile upgraded(int overclockers, int transformers, int storageUpgrades) {
        if (overclockers <= 0 && transformers <= 0 && storageUpgrades <= 0) return this;
        int ticks = this.operationTicks <= 0 ? this.operationTicks
                : Math.max(1, (int) Math.round(this.operationTicks * Math.pow(0.7, overclockers)));
        long newPower = Math.round(this.power * Math.pow(1.6, overclockers));
        int newVoltage = this.voltage;
        for (int i = 0; i < transformers; i++) newVoltage = nextVoltage(newVoltage);
        long newCapacity = Math.max(this.capacity, newPower * Math.max(1, ticks)) + EnergyUnits.fromCWh(5_000L * storageUpgrades);
        return new MachineEnergyProfile(this.role, newVoltage, newPower, newCapacity, ticks, this.highVoltage, this.efficiency);
    }

    private static int nextVoltage(int voltage) {
        if (voltage < 1_000) return 1_000;
        if (voltage < 2_400) return 2_400;
        if (voltage < 13_800) return 13_800;
        return 69_000;
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
