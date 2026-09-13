package net.craftenergy.api;

/** Gerador: entrega potência (CW) numa tensão de saída (MV). */
public interface EnergySource extends EnergyNode {
    /** Tensão de saída em MV. */
    int outputVoltage();

    /** Potência que pode ser entregue neste tick, em CW (limitada pela potência máxima). */
    long availablePower();

    /** Chamado todo tick com a potência realmente consumida da fonte (pode ser 0). */
    void drawPower(long power);
}
