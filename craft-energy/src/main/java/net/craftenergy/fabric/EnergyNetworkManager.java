package net.craftenergy.fabric;

import it.unimi.dsi.fastutil.longs.Long2DoubleMap;
import it.unimi.dsi.fastutil.longs.Long2DoubleOpenHashMap;
import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import it.unimi.dsi.fastutil.objects.ObjectIterator;
import net.craftenergy.api.EnergyConductor;
import net.craftenergy.api.Side;
import net.craftenergy.grid.EnergyNetwork;
import net.craftenergy.grid.GridListener;
import net.craftenergy.grid.GridVertex;
import net.craftenergy.grid.NetworkBuilder;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.SectionPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.entity.BlockEntity;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Redes elétricas de uma dimensão.
 *
 * <p>Mudanças marcam posições como "sujas"; no começo do tick seguinte só as redes
 * afetadas são reconstruídas. Depois cada rede roda seu tick, e os cabos sobrecarregados
 * esquentam: soltam fumaça e, se passarem do limite, queimam.
 */
public final class EnergyNetworkManager {
    /** Calor por tick a cada 100% de corrente acima do limite do cabo (1.0 = queima). */
    public static double overcurrentHeatPerTick = 0.05;
    /** Calor por tick quando a tensão da rede passa do limite do cabo. */
    public static double overvoltageHeatPerTick = 0.25;
    /** Quanto do calor sobra por tick quando o cabo volta ao normal. */
    public static double coolingFactor = 0.9;

    private static final Map<ServerLevel, EnergyNetworkManager> MANAGERS = new HashMap<>();

    private final ServerLevel level;
    private final LevelTopology topology;
    private final List<EnergyNetwork<Long>> networks = new ArrayList<>();
    private final Long2ObjectOpenHashMap<List<EnergyNetwork<Long>>> byPos = new Long2ObjectOpenHashMap<>();
    private final LongOpenHashSet dirty = new LongOpenHashSet();
    private final Long2DoubleOpenHashMap heat = new Long2DoubleOpenHashMap();
    private final LongOpenHashSet heatedThisTick = new LongOpenHashSet();

    private final GridListener<Long> listener = new GridListener<>() {
        @Override
        public void conductorOvercurrent(Long pos, EnergyConductor conductor, double current) {
            double ratio = conductor.maxCurrent() <= 0 ? 2.0 : current / conductor.maxCurrent();
            addHeat(pos, (ratio - 1.0) * overcurrentHeatPerTick);
        }

        @Override
        public void conductorOvervoltage(Long pos, EnergyConductor conductor, int voltage) {
            addHeat(pos, overvoltageHeatPerTick);
        }
    };

    private EnergyNetworkManager(ServerLevel level) {
        this.level = level;
        this.topology = new LevelTopology(level);
    }

    public static EnergyNetworkManager get(ServerLevel level) {
        return MANAGERS.computeIfAbsent(level, EnergyNetworkManager::new);
    }

    // ── consultas ─────────────────────────────────────────────────────────
    /** Redes que passam por {@code pos} (um transformador fica em duas). */
    public List<EnergyNetwork<Long>> networksAt(BlockPos pos) {
        List<EnergyNetwork<Long>> found = this.byPos.get(pos.asLong());
        return found == null ? List.of() : Collections.unmodifiableList(found);
    }

    public List<EnergyNetwork<Long>> networks() {
        return Collections.unmodifiableList(this.networks);
    }

    /** Calor do cabo em {@code pos}, de 0 (frio) a 1 (queima). */
    public double heatAt(BlockPos pos) {
        return this.heat.get(pos.asLong());
    }

    // ── mudanças ──────────────────────────────────────────────────────────
    public void invalidate(BlockPos pos) {
        invalidate(pos.asLong());
    }

    public void invalidate(long pos) {
        markDirty(pos);
        for (Side side : Side.values()) {
            markDirty(LevelTopology.offset(pos, side));
        }
    }

    private void markDirty(long pos) {
        this.dirty.add(pos);
        List<EnergyNetwork<Long>> found = this.byPos.get(pos);
        if (found != null) {
            for (EnergyNetwork<Long> network : List.copyOf(found)) removeNetwork(network);
        }
    }

    private void removeNetwork(EnergyNetwork<Long> network) {
        if (!this.networks.remove(network)) return;
        for (Long pos : network.positions()) {
            List<EnergyNetwork<Long>> found = this.byPos.get(pos.longValue());
            if (found != null) {
                found.remove(network);
                if (found.isEmpty()) this.byPos.remove(pos.longValue());
            }
            this.dirty.add(pos.longValue());
        }
    }

    private void rebuild() {
        LongArrayList seeds = new LongArrayList(this.dirty);
        this.dirty.clear();

        for (EnergyNetwork<Long> network : NetworkBuilder.build(this.topology, seeds)) {
            // uma rede antiga que não foi invalidada mas faz parte do mesmo componente é substituída
            Set<GridVertex<Long>> vertices = new HashSet<>(network.vertices());
            for (Long pos : network.positions()) {
                List<EnergyNetwork<Long>> found = this.byPos.get(pos.longValue());
                if (found == null) continue;
                for (EnergyNetwork<Long> old : List.copyOf(found)) {
                    if (old.vertices().stream().anyMatch(vertices::contains)) removeNetwork(old);
                }
            }

            this.networks.add(network);
            for (Long pos : network.positions()) {
                this.byPos.computeIfAbsent(pos.longValue(), key -> new ArrayList<>(1)).add(network);
            }
        }
        // removeNetwork acima marca posições que já estão na rede nova; não precisa refazer
        for (EnergyNetwork<Long> network : this.networks) {
            for (Long pos : network.positions()) this.dirty.remove(pos.longValue());
        }
    }

    // ── tick ──────────────────────────────────────────────────────────────
    /** Ações que mexem no mundo (explosões) e precisam esperar o tick das redes terminar; uma por posição. */
    private final Long2ObjectOpenHashMap<Runnable> afterTick = new Long2ObjectOpenHashMap<>();

    /** Agenda uma ação para depois do tick das redes desta dimensão. */
    public void runAfterTick(BlockPos pos, Runnable action) {
        this.afterTick.putIfAbsent(pos.asLong(), action);
    }

    private void tick() {
        if (!this.dirty.isEmpty()) rebuild();

        if (!this.networks.isEmpty() || !this.heat.isEmpty()) {
            this.heatedThisTick.clear();
            for (EnergyNetwork<Long> network : this.networks) {
                network.tick(this.listener);
            }
            updateHeat();
        }

        if (!this.afterTick.isEmpty()) {
            List<Runnable> actions = new ArrayList<>(this.afterTick.values());
            this.afterTick.clear();
            actions.forEach(Runnable::run);
        }
    }

    private void addHeat(long pos, double amount) {
        this.heatedThisTick.add(pos);
        this.heat.addTo(pos, Math.max(0.0, amount));
    }

    private void updateHeat() {
        LongArrayList burned = new LongArrayList();
        boolean smokeTick = this.level.getGameTime() % 10 == 0;

        ObjectIterator<Long2DoubleMap.Entry> iterator = this.heat.long2DoubleEntrySet().fastIterator();
        while (iterator.hasNext()) {
            Long2DoubleMap.Entry entry = iterator.next();
            long pos = entry.getLongKey();
            double value = entry.getDoubleValue();

            if (value >= 1.0) {
                burned.add(pos);
                iterator.remove();
                continue;
            }
            if (!this.heatedThisTick.contains(pos)) {
                value *= coolingFactor;
                if (value < 0.01) {
                    iterator.remove();
                    continue;
                }
                entry.setValue(value);
            }
            if (value >= 0.5 && smokeTick) {
                BlockPos blockPos = BlockPos.of(pos);
                if (this.level.isLoaded(blockPos)) {
                    this.level.sendParticles(ParticleTypes.SMOKE, blockPos.getX() + 0.5, blockPos.getY() + 0.6,
                            blockPos.getZ() + 0.5, 2, 0.15, 0.05, 0.15, 0.0);
                }
            }
        }

        for (int i = 0; i < burned.size(); i++) {
            burn(burned.getLong(i));
        }
    }

    private void burn(long packed) {
        BlockPos pos = BlockPos.of(packed);
        if (!this.level.isLoaded(pos)) return;

        this.level.destroyBlock(pos, false);
        this.level.sendParticles(ParticleTypes.LARGE_SMOKE, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5,
                8, 0.2, 0.2, 0.2, 0.01);
        this.level.playSound(null, pos, SoundEvents.FIRE_EXTINGUISH, SoundSource.BLOCKS, 0.6F, 1.2F);
        invalidate(packed);
    }

    private void chunkUnloaded(ChunkPos chunk) {
        for (EnergyNetwork<Long> network : List.copyOf(this.networks)) {
            for (Long pos : network.positions()) {
                if (SectionPos.blockToSectionCoord(BlockPos.getX(pos)) == chunk.x()
                        && SectionPos.blockToSectionCoord(BlockPos.getZ(pos)) == chunk.z()) {
                    removeNetwork(network);
                    break;
                }
            }
        }
    }

    // ── ganchos de eventos (CraftEnergyMod) ───────────────────────────────
    static void tickLevel(ServerLevel level) {
        EnergyNetworkManager manager = MANAGERS.get(level);
        if (manager != null) manager.tick();
    }

    static void onChunkUnload(ServerLevel level, ChunkPos chunk) {
        EnergyNetworkManager manager = MANAGERS.get(level);
        if (manager != null) manager.chunkUnloaded(chunk);
    }

    static void onBlockEntityLoad(ServerLevel level, BlockEntity blockEntity) {
        BlockPos pos = blockEntity.getBlockPos();
        for (Direction direction : Direction.values()) {
            if (CraftEnergyApi.NODE.find(level, pos, blockEntity.getBlockState(), blockEntity, direction) != null) {
                get(level).invalidate(pos);
                return;
            }
        }
    }

    static void onBlockEntityUnload(ServerLevel level, BlockEntity blockEntity) {
        EnergyNetworkManager manager = MANAGERS.get(level);
        if (manager != null && manager.byPos.containsKey(blockEntity.getBlockPos().asLong())) {
            manager.invalidate(blockEntity.getBlockPos());
        }
    }

    static void remove(ServerLevel level) {
        MANAGERS.remove(level);
    }

    static void clear() {
        MANAGERS.clear();
    }
}
