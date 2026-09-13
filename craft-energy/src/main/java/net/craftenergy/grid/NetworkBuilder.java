package net.craftenergy.grid;

import net.craftenergy.api.EnergyNode;
import net.craftenergy.api.Side;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Monta as redes a partir de posições iniciais, seguindo as faces conectadas.
 *
 * <p>Dois nós vizinhos se ligam quando cada um expõe um nó na face voltada para o outro.
 * Transformadores expõem nós diferentes em cada lado, então separam redes.
 */
public final class NetworkBuilder {
    private NetworkBuilder() {}

    public static <P> List<EnergyNetwork<P>> build(GridTopology<P> topology, Iterable<P> seeds) {
        Set<GridVertex<P>> visited = new HashSet<>();
        List<EnergyNetwork<P>> networks = new ArrayList<>();

        for (P seed : seeds) {
            for (Side side : Side.values()) {
                EnergyNode node = topology.nodeAt(seed, side);
                if (node == null) continue;

                GridVertex<P> start = new GridVertex<>(seed, node);
                if (visited.contains(start)) continue;

                networks.add(explore(topology, start, visited));
            }
        }
        return networks;
    }

    private static <P> EnergyNetwork<P> explore(GridTopology<P> topology, GridVertex<P> start, Set<GridVertex<P>> visited) {
        List<GridVertex<P>> vertices = new ArrayList<>();
        List<List<Integer>> adjacency = new ArrayList<>();
        Map<GridVertex<P>, Integer> index = new HashMap<>();
        ArrayDeque<GridVertex<P>> queue = new ArrayDeque<>();

        addVertex(start, vertices, adjacency, index, queue, visited);

        while (!queue.isEmpty()) {
            GridVertex<P> vertex = queue.poll();
            int vertexIndex = index.get(vertex);

            for (Side side : Side.values()) {
                if (topology.nodeAt(vertex.pos(), side) != vertex.node()) continue;

                P neighbourPos = topology.offset(vertex.pos(), side);
                EnergyNode neighbourNode = topology.nodeAt(neighbourPos, side.opposite());
                if (neighbourNode == null) continue;

                GridVertex<P> neighbour = new GridVertex<>(neighbourPos, neighbourNode);
                Integer neighbourIndex = index.get(neighbour);
                if (neighbourIndex == null) {
                    neighbourIndex = addVertex(neighbour, vertices, adjacency, index, queue, visited);
                }

                List<Integer> edges = adjacency.get(vertexIndex);
                if (!edges.contains(neighbourIndex)) edges.add(neighbourIndex);
                List<Integer> back = adjacency.get(neighbourIndex);
                if (!back.contains(vertexIndex)) back.add(vertexIndex);
            }
        }

        int[][] edges = new int[vertices.size()][];
        for (int i = 0; i < edges.length; i++) {
            edges[i] = adjacency.get(i).stream().mapToInt(Integer::intValue).toArray();
        }
        return new EnergyNetwork<>(vertices, edges);
    }

    private static <P> int addVertex(GridVertex<P> vertex, List<GridVertex<P>> vertices, List<List<Integer>> adjacency,
                                     Map<GridVertex<P>, Integer> index, ArrayDeque<GridVertex<P>> queue,
                                     Set<GridVertex<P>> visited) {
        int vertexIndex = vertices.size();
        vertices.add(vertex);
        adjacency.add(new ArrayList<>());
        index.put(vertex, vertexIndex);
        visited.add(vertex);
        queue.add(vertex);
        return vertexIndex;
    }
}
