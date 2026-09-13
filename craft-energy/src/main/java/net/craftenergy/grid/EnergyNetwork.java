package net.craftenergy.grid;

import net.craftenergy.api.EnergyBuffer;
import net.craftenergy.api.EnergyConductor;
import net.craftenergy.api.EnergyNode;
import net.craftenergy.api.EnergySink;
import net.craftenergy.api.EnergySource;
import net.craftenergy.api.EnergyUnits;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.PriorityQueue;
import java.util.Set;

/**
 * Uma rede elétrica conectada, com uma única tensão.
 *
 * <p>A cada tick:
 * <ol>
 *   <li>a tensão da rede é a maior entre geradores e baterias; quem está fora da
 *   tolerância dessa tensão não participa;</li>
 *   <li>cada máquina pede {@code CW}; a corrente é {@code RA = CW / MV} e a perda no
 *   caminho até o gerador mais próximo é {@code RA² × R};</li>
 *   <li>os geradores cobrem a demanda + perdas; o que faltar sai das baterias; se ainda
 *   faltar, todas as máquinas recebem a mesma fração;</li>
 *   <li>sobra dos geradores carrega as baterias;</li>
 *   <li>a corrente de cada máquina é somada nos cabos do seu caminho e os limites de
 *   MV/RA dos cabos são verificados.</li>
 * </ol>
 * O caminho de cada consumidor é o de menor resistência até um gerador ou bateria,
 * calculado quando a rede é montada.
 */
public final class EnergyNetwork<P> {
    private static final double EPSILON = 1e-9;

    private final List<GridVertex<P>> vertices;
    private final int[] sources;
    private final int[] sinks;
    private final int[] buffers;
    private final int[] conductors;
    private final int[][] sinkPaths;
    private final int[][] bufferPaths;
    private final double[] sinkResistance;
    private final double[] bufferResistance;
    private final double[] conductorCurrent;
    private int voltage;
    private NetworkTickReport lastReport = NetworkTickReport.EMPTY;

    EnergyNetwork(List<GridVertex<P>> vertices, int[][] adjacency) {
        this.vertices = List.copyOf(vertices);
        int count = vertices.size();

        List<Integer> sourceList = new ArrayList<>();
        List<Integer> sinkList = new ArrayList<>();
        List<Integer> bufferList = new ArrayList<>();
        List<Integer> conductorList = new ArrayList<>();
        int[] conductorIndex = new int[count];
        Arrays.fill(conductorIndex, -1);

        for (int i = 0; i < count; i++) {
            EnergyNode node = vertices.get(i).node();
            int roles = 0;
            if (node instanceof EnergySource) { sourceList.add(i); roles++; }
            if (node instanceof EnergySink) { sinkList.add(i); roles++; }
            if (node instanceof EnergyBuffer) { bufferList.add(i); roles++; }
            if (node instanceof EnergyConductor) { conductorIndex[i] = conductorList.size(); conductorList.add(i); roles++; }
            if (roles != 1) {
                throw new IllegalArgumentException("EnergyNode must have exactly one role (source, sink, buffer or conductor): "
                        + node.getClass().getName());
            }
        }

        this.sources = toArray(sourceList);
        this.sinks = toArray(sinkList);
        this.buffers = toArray(bufferList);
        this.conductors = toArray(conductorList);
        this.conductorCurrent = new double[this.conductors.length];

        // Dijkstra a partir de todos os geradores e baterias; entrar num cabo custa a resistência dele
        double[] distance = new double[count];
        int[] previous = new int[count];
        Arrays.fill(distance, Double.POSITIVE_INFINITY);
        Arrays.fill(previous, -1);
        PriorityQueue<double[]> queue = new PriorityQueue<>(Comparator.comparingDouble(entry -> entry[0]));
        for (int supplier : concat(this.sources, this.buffers)) {
            distance[supplier] = 0.0;
            queue.add(new double[]{0.0, supplier});
        }
        while (!queue.isEmpty()) {
            double[] entry = queue.poll();
            int vertex = (int) entry[1];
            if (entry[0] > distance[vertex]) continue;
            for (int neighbour : adjacency[vertex]) {
                double weight = conductorIndex[neighbour] >= 0
                        ? Math.max(0.0, ((EnergyConductor) vertices.get(neighbour).node()).resistance())
                        : 0.0;
                double candidate = distance[vertex] + weight;
                if (candidate < distance[neighbour]) {
                    distance[neighbour] = candidate;
                    previous[neighbour] = vertex;
                    queue.add(new double[]{candidate, neighbour});
                }
            }
        }

        this.sinkPaths = new int[this.sinks.length][];
        this.sinkResistance = new double[this.sinks.length];
        for (int i = 0; i < this.sinks.length; i++) {
            this.sinkPaths[i] = path(this.sinks[i], previous, conductorIndex);
            this.sinkResistance[i] = Double.isInfinite(distance[this.sinks[i]]) ? 0.0 : distance[this.sinks[i]];
        }
        this.bufferPaths = new int[this.buffers.length][];
        this.bufferResistance = new double[this.buffers.length];
        for (int i = 0; i < this.buffers.length; i++) {
            this.bufferPaths[i] = path(this.buffers[i], previous, conductorIndex);
            this.bufferResistance[i] = 0.0;
        }
    }

    /** Executa um tick da rede e devolve o resumo. */
    public NetworkTickReport tick(GridListener<P> listener) {
        Arrays.fill(this.conductorCurrent, 0.0);

        int networkVoltage = 0;
        for (int vertex : this.sources) networkVoltage = Math.max(networkVoltage, source(vertex).outputVoltage());
        for (int vertex : this.buffers) networkVoltage = Math.max(networkVoltage, buffer(vertex).voltage());
        this.voltage = networkVoltage;
        double tolerance = EnergyUnits.DEFAULT_VOLTAGE_TOLERANCE;

        // ── oferta ───────────────────────────────────────────────────────
        long[] sourceAvailable = new long[this.sources.length];
        long totalSource = 0;
        for (int i = 0; i < this.sources.length; i++) {
            EnergySource source = source(this.sources[i]);
            if (networkVoltage > 0 && EnergyUnits.withinTolerance(source.outputVoltage(), networkVoltage, tolerance)) {
                sourceAvailable[i] = Math.max(0, source.availablePower());
                totalSource += sourceAvailable[i];
            }
        }

        long[] dischargeCap = new long[this.buffers.length];
        long[] chargeCap = new long[this.buffers.length];
        long totalDischarge = 0;
        for (int i = 0; i < this.buffers.length; i++) {
            EnergyBuffer buffer = buffer(this.buffers[i]);
            if (networkVoltage > 0 && EnergyUnits.withinTolerance(buffer.voltage(), networkVoltage, tolerance)) {
                long stored = Math.max(0, buffer.storedEnergy());
                dischargeCap[i] = Math.max(0, Math.min(buffer.maxDischargePower(), stored));
                chargeCap[i] = Math.max(0, Math.min(buffer.maxChargePower(), buffer.energyCapacity() - stored));
                totalDischarge += dischargeCap[i];
            }
        }

        // ── demanda + perdas ─────────────────────────────────────────────
        long[] demand = new long[this.sinks.length];
        double[] need = new double[this.sinks.length];
        long totalDemand = 0;
        double totalNeed = 0.0;
        for (int i = 0; i < this.sinks.length; i++) {
            EnergySink sink = sink(this.sinks[i]);
            long wanted = Math.max(0, sink.powerDemand());
            if (networkVoltage <= 0) {
                wanted = 0;
            } else if (networkVoltage > sink.nominalVoltage() * (1.0 + sink.voltageTolerance())) {
                wanted = 0;
                sink.onOvervoltage(networkVoltage);
                listener.sinkOvervoltage(this.vertices.get(this.sinks[i]).pos(), sink, networkVoltage);
            } else if (networkVoltage < sink.nominalVoltage() * (1.0 - sink.voltageTolerance())) {
                wanted = 0;
            }
            demand[i] = wanted;
            double current = EnergyUnits.current(wanted, networkVoltage);
            need[i] = wanted + EnergyUnits.resistiveLoss(current, this.sinkResistance[i]);
            totalDemand += wanted;
            totalNeed += need[i];
        }

        // ── de onde vem a potência ───────────────────────────────────────
        double fromSources;
        double fromBuffers;
        double fraction;
        if (totalNeed <= totalSource + EPSILON) {
            fraction = totalNeed > 0 ? 1.0 : 0.0;
            fromSources = totalNeed;
            fromBuffers = 0.0;
        } else {
            fromSources = totalSource;
            fromBuffers = Math.min(totalDischarge, totalNeed - totalSource);
            fraction = (fromSources + fromBuffers) / totalNeed;
        }

        // ── entrega às máquinas ──────────────────────────────────────────
        long delivered = 0;
        double losses = 0.0;
        for (int i = 0; i < this.sinks.length; i++) {
            long power = (long) Math.floor(demand[i] * fraction + EPSILON);
            sink(this.sinks[i]).receivePower(power, networkVoltage);
            delivered += power;

            double flow = need[i] * fraction;
            losses += flow - demand[i] * fraction;
            addCurrent(this.sinkPaths[i], EnergyUnits.current(flow, networkVoltage));
        }

        // ── sobra dos geradores carrega as baterias ──────────────────────
        long charged = 0;
        double surplus = totalSource - fromSources;
        if (surplus > EPSILON) {
            double[] bufferNeed = new double[this.buffers.length];
            double totalBufferNeed = 0.0;
            for (int i = 0; i < this.buffers.length; i++) {
                double current = EnergyUnits.current(chargeCap[i], networkVoltage);
                bufferNeed[i] = chargeCap[i] + EnergyUnits.resistiveLoss(current, this.bufferResistance[i]);
                totalBufferNeed += bufferNeed[i];
            }
            double share = totalBufferNeed > 0 ? Math.min(1.0, surplus / totalBufferNeed) : 0.0;
            for (int i = 0; i < this.buffers.length; i++) {
                long power = (long) Math.floor(chargeCap[i] * share + EPSILON);
                if (power > 0) {
                    buffer(this.buffers[i]).charge(power);
                    charged += power;
                }
                double flow = bufferNeed[i] * share;
                losses += flow - chargeCap[i] * share;
                addCurrent(this.bufferPaths[i], EnergyUnits.current(flow, networkVoltage));
                fromSources += flow;
            }
        }

        // ── baterias e geradores pagam a conta ───────────────────────────
        long discharged = 0;
        if (fromBuffers > EPSILON) {
            long[] parts = Apportion.distribute((long) Math.ceil(fromBuffers - EPSILON), dischargeCap);
            for (int i = 0; i < this.buffers.length; i++) {
                if (parts[i] > 0) {
                    buffer(this.buffers[i]).discharge(parts[i]);
                    discharged += parts[i];
                }
            }
        }

        long generated = 0;
        long[] draws = Apportion.distribute(Math.min(totalSource, (long) Math.ceil(fromSources - EPSILON)), sourceAvailable);
        for (int i = 0; i < this.sources.length; i++) {
            source(this.sources[i]).drawPower(draws[i]);
            generated += draws[i];
        }

        // ── proteção dos cabos ───────────────────────────────────────────
        for (int i = 0; i < this.conductors.length; i++) {
            GridVertex<P> vertex = this.vertices.get(this.conductors[i]);
            EnergyConductor conductor = (EnergyConductor) vertex.node();
            if (networkVoltage > conductor.maxVoltage()) {
                listener.conductorOvervoltage(vertex.pos(), conductor, networkVoltage);
            }
            if (this.conductorCurrent[i] > conductor.maxCurrent() + EPSILON) {
                listener.conductorOvercurrent(vertex.pos(), conductor, this.conductorCurrent[i]);
            }
        }

        this.lastReport = new NetworkTickReport(networkVoltage, totalDemand, delivered, Math.round(losses),
                generated, discharged, charged);
        return this.lastReport;
    }

    /** Tensão calculada no último tick, em MV. */
    public int voltage() {
        return this.voltage;
    }

    public NetworkTickReport lastReport() {
        return this.lastReport;
    }

    public List<GridVertex<P>> vertices() {
        return this.vertices;
    }

    /** Posições distintas que fazem parte desta rede. */
    public Set<P> positions() {
        Set<P> positions = new LinkedHashSet<>();
        for (GridVertex<P> vertex : this.vertices) positions.add(vertex.pos());
        return Collections.unmodifiableSet(positions);
    }

    /** Corrente (RA) que passou pelo cabo em {@code pos} no último tick, ou 0. */
    public double conductorCurrent(P pos) {
        for (int i = 0; i < this.conductors.length; i++) {
            if (this.vertices.get(this.conductors[i]).pos().equals(pos)) return this.conductorCurrent[i];
        }
        return 0.0;
    }

    private void addCurrent(int[] path, double current) {
        if (current <= 0) return;
        for (int conductor : path) this.conductorCurrent[conductor] += current;
    }

    private EnergySource source(int vertex) {
        return (EnergySource) this.vertices.get(vertex).node();
    }

    private EnergySink sink(int vertex) {
        return (EnergySink) this.vertices.get(vertex).node();
    }

    private EnergyBuffer buffer(int vertex) {
        return (EnergyBuffer) this.vertices.get(vertex).node();
    }

    private static int[] path(int vertex, int[] previous, int[] conductorIndex) {
        List<Integer> path = new ArrayList<>();
        for (int current = vertex; current >= 0; current = previous[current]) {
            if (conductorIndex[current] >= 0) path.add(conductorIndex[current]);
        }
        return toArray(path);
    }

    private static int[] toArray(List<Integer> values) {
        return values.stream().mapToInt(Integer::intValue).toArray();
    }

    private static int[] concat(int[] a, int[] b) {
        int[] result = Arrays.copyOf(a, a.length + b.length);
        System.arraycopy(b, 0, result, a.length, b.length);
        return result;
    }
}
