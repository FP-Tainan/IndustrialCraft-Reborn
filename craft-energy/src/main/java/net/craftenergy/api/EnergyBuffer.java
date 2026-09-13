package net.craftenergy.api;

/**
 * Bateria: guarda energia em CWh (internamente em CW·tick, ver {@link EnergyUnits}).
 * Carrega com o excedente dos geradores e descarrega quando falta potência.
 */
public interface EnergyBuffer extends EnergyNode {
    /** Tensão de operação em MV. */
    int voltage();

    /** Potência máxima de carga em CW. */
    long maxChargePower();

    /** Potência máxima de descarga em CW. */
    long maxDischargePower();

    /** Energia guardada, em CW·tick. */
    long storedEnergy();

    /** Capacidade, em CW·tick. */
    long energyCapacity();

    /** Adiciona energia (potência deste tick × 1 tick). */
    void charge(long power);

    /** Remove energia (potência deste tick × 1 tick). */
    void discharge(long power);
}
