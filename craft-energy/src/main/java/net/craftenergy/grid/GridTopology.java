package net.craftenergy.grid;

import net.craftenergy.api.EnergyNode;
import net.craftenergy.api.Side;

/**
 * Como o construtor de redes enxerga o mundo. No jogo, {@code P} é a posição do bloco;
 * nos testes, qualquer tipo de coordenada.
 */
public interface GridTopology<P> {
    /** Nó exposto pela face {@code side} do bloco em {@code pos}, ou {@code null} se não conecta. */
    EnergyNode nodeAt(P pos, Side side);

    /** Posição vizinha na direção {@code side}. */
    P offset(P pos, Side side);
}
