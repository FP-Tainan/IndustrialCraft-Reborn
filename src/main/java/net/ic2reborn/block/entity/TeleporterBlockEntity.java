package net.ic2reborn.block.entity;

import net.craftenergy.api.EnergyUnits;
import net.ic2reborn.menu.MachineGuiType;
import net.ic2reborn.registry.IC2BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.VehicleEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Teletransportador do IC2 ({@code TileEntityTeleporter}): com sinal de redstone e destino
 * vinculado, leva a entidade mais próxima de cima dele até o outro teletransportador. A energia
 * sai dos armazenamentos encostados: peso × (distância + 10)^0,7 × 5 EU.
 */
public class TeleporterBlockEntity extends BlockEntity {
    private @Nullable BlockPos target;
    private int cooldown;

    public TeleporterBlockEntity(BlockPos pos, BlockState state) {
        super(IC2BlockEntities.TELEPORTER.get(), pos, state);
    }

    public @Nullable BlockPos getTarget() {
        return this.target;
    }

    public void setTarget(@Nullable BlockPos target) {
        this.target = target == null ? null : target.immutable();
        setChanged();
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, TeleporterBlockEntity teleporter) {
        if (teleporter.cooldown > 0) {
            teleporter.cooldown--;
            return;
        }
        if (teleporter.target == null || !level.hasNeighborSignal(pos) || !(level instanceof ServerLevel server)) return;
        if (!(level.getBlockEntity(teleporter.target) instanceof TeleporterBlockEntity destination)) {
            teleporter.setTarget(null);
            return;
        }
        AABB area = new AABB(pos.getX() - 1, pos.getY(), pos.getZ() - 1, pos.getX() + 2, pos.getY() + 3, pos.getZ() + 2);
        Entity closest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : level.getEntities((Entity) null, area, entity -> !entity.isPassenger() && entity.isAlive())) {
            double distance = entity.distanceToSqr(pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5);
            if (distance < best) {
                best = distance;
                closest = entity;
            }
        }
        if (closest != null) teleporter.teleport(server, closest, destination);
    }

    private void teleport(ServerLevel level, Entity entity, TeleporterBlockEntity destination) {
        int weight = weightOf(entity);
        if (weight <= 0 || this.target == null) return;
        double distance = Math.sqrt(this.worldPosition.distSqr(this.target));
        // IC2: EU; aqui 1 EU = 0,5 CWh
        long cost = EnergyUnits.fromCWh(weight * Math.pow(distance + 10.0, 0.7) * 5.0 * 0.5);
        List<MachineBlockEntity> storages = storages(level);
        long available = storages.stream().mapToLong(MachineBlockEntity::getStoredEnergy).sum();
        if (cost > available) return;
        drain(storages, cost);

        entity.teleportTo(this.target.getX() + 0.5, this.target.getY() + 1.5, this.target.getZ() + 0.5);
        entity.resetFallDistance();
        destination.cooldown = 20;
        this.cooldown = 20;
        level.playSound(null, this.worldPosition, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.playSound(null, this.target, SoundEvents.ENDERMAN_TELEPORT, SoundSource.BLOCKS, 1.0F, 1.0F);
        level.sendParticles(ParticleTypes.PORTAL, this.target.getX() + 0.5, this.target.getY() + 1.5, this.target.getZ() + 0.5,
                30, 0.4, 0.8, 0.4, 0.1);
    }

    /** Armazenamentos (BatBox, CESU, MFE, MFSU) encostados no teletransportador. */
    private List<MachineBlockEntity> storages(Level level) {
        List<MachineBlockEntity> found = new ArrayList<>();
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(this.worldPosition.relative(direction)) instanceof MachineBlockEntity machine
                    && isStorage(machine.getGuiType()) && machine.getStoredEnergy() > 0) {
                found.add(machine);
            }
        }
        return found;
    }

    private static boolean isStorage(MachineGuiType type) {
        return type == MachineGuiType.BATBOX || type == MachineGuiType.CESU || type == MachineGuiType.MFE || type == MachineGuiType.MFSU;
    }

    private static void drain(List<MachineBlockEntity> storages, long amount) {
        List<MachineBlockEntity> left = new ArrayList<>(storages);
        while (amount > 0 && !left.isEmpty()) {
            long share = (amount + left.size() - 1) / left.size();
            for (var iterator = left.iterator(); iterator.hasNext() && amount > 0; ) {
                MachineBlockEntity storage = iterator.next();
                long take = Math.min(Math.min(share, amount), storage.getStoredEnergy());
                storage.setStoredEnergy(storage.getStoredEnergy() - take);
                amount -= take;
                if (storage.getStoredEnergy() <= 0) iterator.remove();
            }
        }
    }

    /** IC2: item 100 × pilha cheia, animal/veículo 100, jogador 1.000, outros seres 500. */
    private static int weightOf(Entity entity) {
        if (entity instanceof ItemEntity item) {
            return Math.max(1, 100 * item.getItem().getCount() / item.getItem().getMaxStackSize());
        }
        if (entity instanceof Animal || entity instanceof VehicleEntity) return 100;
        if (entity instanceof Player) return 1_000;
        return entity instanceof net.minecraft.world.entity.LivingEntity ? 500 : 0;
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        if (this.target != null) output.store("Target", BlockPos.CODEC, this.target);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.target = input.read("Target", BlockPos.CODEC).orElse(null);
    }
}
