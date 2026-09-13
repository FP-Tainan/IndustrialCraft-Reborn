package net.craftenergy.grid;

import net.craftenergy.api.EnergyNode;

/** Vértice da rede: uma posição + a instância do nó (comparada por identidade). */
public final class GridVertex<P> {
    private final P pos;
    private final EnergyNode node;

    public GridVertex(P pos, EnergyNode node) {
        this.pos = pos;
        this.node = node;
    }

    public P pos() {
        return this.pos;
    }

    public EnergyNode node() {
        return this.node;
    }

    @Override
    public boolean equals(Object other) {
        return other instanceof GridVertex<?> vertex && vertex.node == this.node && vertex.pos.equals(this.pos);
    }

    @Override
    public int hashCode() {
        return 31 * this.pos.hashCode() + System.identityHashCode(this.node);
    }

    @Override
    public String toString() {
        return this.node.getClass().getSimpleName() + "@" + this.pos;
    }
}
