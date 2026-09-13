package net.ic2reborn.energy;

import net.craftenergy.api.EnergyUnits;
import net.ic2reborn.menu.MachineGuiType;

/**
 * Parâmetros elétricos de cada máquina, em Craft Energy (docs/energia-conversao-ic2.md).
 *
 * @param role           papel na rede
 * @param voltage        tensão nominal/de saída em MV
 * @param power          potência em CW (produção do gerador, carga/descarga da bateria, consumo da máquina)
 * @param capacity       energia interna em CW·tick
 * @param operationTicks duração de uma operação (máquinas de processamento)
 */
public record MachineEnergyProfile(Role role, int voltage, long power, long capacity, int operationTicks) {
    public enum Role { NONE, GENERATOR, STORAGE, PROCESSOR }

    public static final MachineEnergyProfile NONE = new MachineEnergyProfile(Role.NONE, 0, 0, 0, 0);

    /** Máquinas ainda não ligadas na rede ficam {@link #NONE}. */
    public static MachineEnergyProfile of(MachineGuiType type) {
        return switch (type) {
            case GENERATOR -> new MachineEnergyProfile(Role.GENERATOR, 220, 5_000, EnergyUnits.fromCWh(2_000), 0);
            case BATBOX -> new MachineEnergyProfile(Role.STORAGE, 220, 4_400, EnergyUnits.fromCWh(20_000), 0);
            case CESU -> new MachineEnergyProfile(Role.STORAGE, 1_000, 20_000, EnergyUnits.fromCWh(150_000), 0);
            case MFE -> new MachineEnergyProfile(Role.STORAGE, 2_400, 120_000, EnergyUnits.fromCWh(2_000_000), 0);
            case MFSU -> new MachineEnergyProfile(Role.STORAGE, 13_800, 1_000_000, EnergyUnits.fromCWh(20_000_000), 0);
            case MACERATOR -> processor(220, 2_000, 300);
            default -> NONE;
        };
    }

    private static MachineEnergyProfile processor(int voltage, long power, int operationTicks) {
        return new MachineEnergyProfile(Role.PROCESSOR, voltage, power, power * operationTicks, operationTicks);
    }

    /** Quanto uma máquina de processamento puxa da rede por tick: o dobro do consumo, para encher o buffer sem pico de corrente. */
    public long maxIntake() {
        return this.power * 2;
    }
}
