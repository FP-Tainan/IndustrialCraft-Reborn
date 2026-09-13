package net.craftenergy.api;

/**
 * Máquina: consome potência (CW) numa tensão nominal (MV).
 * A corrente necessária é {@code RA = CW / MV}.
 */
public interface EnergySink extends EnergyNode {
    /** Tensão nominal em MV. */
    int nominalVoltage();

    /**
     * Faixa aceita em torno da tensão nominal. Acima dela a máquina sofre sobretensão;
     * abaixo, não recebe energia (não liga).
     */
    default double voltageTolerance() {
        return EnergyUnits.DEFAULT_VOLTAGE_TOLERANCE;
    }

    /**
     * Menor tensão em que o aparelho funciona. Por padrão é a nominal menos a tolerância;
     * carregadores (entrada de baterias) podem aceitar qualquer tensão menor que a nominal.
     */
    default int minimumVoltage() {
        return (int) Math.ceil(nominalVoltage() * (1.0 - voltageTolerance()));
    }

    /** Potência desejada neste tick, em CW. */
    long powerDemand();

    /** Chamado todo tick com a potência entregue (pode ser 0) e a tensão da rede. */
    void receivePower(long power, int voltage);

    /**
     * Chamado quando a rede está acima da tensão suportada. A máquina decide o que
     * acontece (queimar, explodir, desligar...). Nesse tick ela não recebe energia.
     */
    default void onOvervoltage(int voltage) {
    }
}
