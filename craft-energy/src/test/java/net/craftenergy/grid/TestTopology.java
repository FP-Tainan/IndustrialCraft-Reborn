package net.craftenergy.grid;

import net.craftenergy.api.EnergyConductor;
import net.craftenergy.api.EnergyNode;
import net.craftenergy.api.EnergySink;
import net.craftenergy.api.EnergySource;
import net.craftenergy.api.Side;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mundo de mentira para testar redes sem Minecraft. */
final class TestTopology implements GridTopology<TestTopology.Pos> {
    record Pos(int x, int y, int z) {}

    private final Map<Pos, EnergyNode[]> faces = new HashMap<>();

    /** Coloca um nó exposto nas 6 faces. */
    TestTopology place(int x, int y, int z, EnergyNode node) {
        EnergyNode[] array = this.faces.computeIfAbsent(new Pos(x, y, z), pos -> new EnergyNode[6]);
        for (Side side : Side.values()) array[side.ordinal()] = node;
        return this;
    }

    /** Coloca um nó exposto só numa face. */
    TestTopology placeFace(int x, int y, int z, Side side, EnergyNode node) {
        this.faces.computeIfAbsent(new Pos(x, y, z), pos -> new EnergyNode[6])[side.ordinal()] = node;
        return this;
    }

    List<Pos> positions() {
        return new ArrayList<>(this.faces.keySet());
    }

    List<EnergyNetwork<Pos>> build() {
        return NetworkBuilder.build(this, positions());
    }

    @Override
    public EnergyNode nodeAt(Pos pos, Side side) {
        EnergyNode[] array = this.faces.get(pos);
        return array == null ? null : array[side.ordinal()];
    }

    @Override
    public Pos offset(Pos pos, Side side) {
        return new Pos(pos.x() + side.dx, pos.y() + side.dy, pos.z() + side.dz);
    }

    static EnergyNetwork<Pos> networkAt(List<EnergyNetwork<Pos>> networks, int x, int y, int z) {
        Pos pos = new Pos(x, y, z);
        return networks.stream().filter(network -> network.positions().contains(pos)).findFirst().orElseThrow();
    }

    // ── aparelhos de teste ───────────────────────────────────────────────
    static final class Source implements EnergySource {
        final int voltage;
        long available;
        long lastDrawn;

        Source(int voltage, long available) {
            this.voltage = voltage;
            this.available = available;
        }

        @Override public int outputVoltage() { return this.voltage; }
        @Override public long availablePower() { return this.available; }
        @Override public void drawPower(long power) { this.lastDrawn = power; }
    }

    static final class Sink implements EnergySink {
        final int nominal;
        long demand;
        long lastReceived;
        int overvoltages;

        Sink(int nominal, long demand) {
            this.nominal = nominal;
            this.demand = demand;
        }

        @Override public int nominalVoltage() { return this.nominal; }
        @Override public long powerDemand() { return this.demand; }
        @Override public void receivePower(long power, int voltage) { this.lastReceived = power; }
        @Override public void onOvervoltage(int voltage) { this.overvoltages++; }
    }

    record Cable(int maxVoltage, double maxCurrent, double resistance) implements EnergyConductor {}

    static final class RecordingListener implements GridListener<Pos> {
        final List<Pos> overcurrent = new ArrayList<>();
        final List<Pos> cableOvervoltage = new ArrayList<>();
        final List<Pos> sinkOvervoltage = new ArrayList<>();

        @Override public void conductorOvercurrent(Pos pos, EnergyConductor conductor, double current) { this.overcurrent.add(pos); }
        @Override public void conductorOvervoltage(Pos pos, EnergyConductor conductor, int voltage) { this.cableOvervoltage.add(pos); }
        @Override public void sinkOvervoltage(Pos pos, EnergySink sink, int voltage) { this.sinkOvervoltage.add(pos); }
    }
}
