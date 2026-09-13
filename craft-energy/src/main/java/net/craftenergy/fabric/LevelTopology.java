package net.craftenergy.fabric;

import net.craftenergy.api.EnergyNode;
import net.craftenergy.api.Side;
import net.craftenergy.grid.GridTopology;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;

/** O mundo do jogo visto pela rede: posições são {@link BlockPos#asLong()}. */
final class LevelTopology implements GridTopology<Long> {
    private final ServerLevel level;

    LevelTopology(ServerLevel level) {
        this.level = level;
    }

    @Override
    public EnergyNode nodeAt(Long pos, Side side) {
        BlockPos blockPos = BlockPos.of(pos);
        // nunca carrega chunk para montar a rede
        if (!this.level.isLoaded(blockPos)) return null;
        return CraftEnergyApi.NODE.find(this.level, blockPos, CraftEnergyApi.direction(side));
    }

    @Override
    public Long offset(Long pos, Side side) {
        return offset((long) pos, side);
    }

    static long offset(long pos, Side side) {
        return BlockPos.asLong(BlockPos.getX(pos) + side.dx, BlockPos.getY(pos) + side.dy, BlockPos.getZ(pos) + side.dz);
    }
}
