package net.craftenergy.grid;

import net.craftenergy.api.EnergyConductor;
import net.craftenergy.api.EnergySink;

/** Recebe os eventos de proteção de uma rede durante o tick. */
public interface GridListener<P> {
    GridListener<?> NONE = new GridListener<>() {};

    @SuppressWarnings("unchecked")
    static <P> GridListener<P> none() {
        return (GridListener<P>) NONE;
    }

    /** Corrente acima do máximo do cabo: ele aquece, perde energia e pode queimar. */
    default void conductorOvercurrent(P pos, EnergyConductor conductor, double current) {
    }

    /** Tensão da rede acima do que o cabo suporta. */
    default void conductorOvervoltage(P pos, EnergyConductor conductor, int voltage) {
    }

    /** Tensão da rede acima do que a máquina suporta. */
    default void sinkOvervoltage(P pos, EnergySink sink, int voltage) {
    }

    /** Tensão da rede acima do que a bateria suporta. */
    default void bufferOvervoltage(P pos, net.craftenergy.api.EnergyBuffer buffer, int voltage) {
    }
}
