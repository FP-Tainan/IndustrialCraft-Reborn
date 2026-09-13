package net.craftenergy.api;

/**
 * Cabo: liga os aparelhos da rede.
 * A capacidade aproximada numa tensão é {@code MV × RA máximos}.
 */
public interface EnergyConductor extends EnergyNode {
    /** Tensão máxima suportada em MV. */
    int maxVoltage();

    /** Corrente máxima suportada em RA. */
    double maxCurrent();

    /** Resistência elétrica de um bloco de cabo. Perda = RA² × resistência. */
    default double resistance() {
        return 0.0;
    }
}
