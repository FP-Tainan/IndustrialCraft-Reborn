package net.ic2reborn.block.entity;

import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.ic2reborn.effect.RadiationHandler;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.menu.MachineGuiType;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.UUID;

/**
 * Máquinas utilitárias do IC2: placas de carga, bobina de Tesla, carregador de chunks, eletrolisador,
 * magnetizador, luminária, gerador de radioisótopos (RTG) e baú pessoal.
 */
final class UtilityLogic {
    private static final long MB = FluidConstants.BUCKET / 1_000;
    /** Tesla: 400 EU por ponto de dano (200.000 CW·tick); 1 EU/t ligada. */
    private static final long TESLA_PER_DAMAGE = 200_000;
    private static final long TESLA_IDLE = 500;
    /** Carregador de chunks: 1 EU/t por chunk. */
    private static final long CHUNK_POWER = 500;
    /** Eletrolisador do IC2: 40 mB de água → 26 mB de hidrogênio (embaixo) e 13 mB de oxigênio (em cima), 32 EU/t. */
    private static final long WATER_PER_RUN = 40 * MB, HYDROGEN_PER_RUN = 26 * MB, OXYGEN_PER_RUN = 13 * MB;
    /** Magnetizador: 2 EU por tick por jogador subindo. */
    private static final long MAGNET_BOOST = 1_000;
    static final int MAGNET_RANGE = 20;
    /** Luminária: 0,25 EU/t. */
    private static final long LUMINATOR_POWER = 125;
    /** RTG: 2^(n−1) EU/t com n pastilhas. */
    private static final long RTG_CW_PER_EU = 500;
    static final int RTG_SLOTS = 6;

    private final MachineBlockEntity machine;
    private final MachineGuiType type;
    private boolean working;
    private int ticker;
    private boolean chunkForced;
    private boolean enabled = true;
    private int progress;
    private @Nullable UUID owner;

    private UtilityLogic(MachineBlockEntity machine, MachineGuiType type) {
        this.machine = machine;
        this.type = type;
    }

    static @Nullable UtilityLogic create(MachineBlockEntity machine, MachineGuiType type) {
        return switch (type) {
            case CHARGEPAD, CHARGEPAD_CESU, CHARGEPAD_MFE, CHARGEPAD_MFSU, TESLA_COIL, CHUNK_LOADER, ELECTROLYZER, MAGNETIZER,
                 LUMINATOR, RT_GENERATOR, PERSONAL_CHEST -> new UtilityLogic(machine, type);
            default -> null;
        };
    }

    boolean working() {
        return this.working;
    }

    boolean canPlace(int slot, ItemStack stack) {
        if (this.type == MachineGuiType.RT_GENERATOR && slot < RTG_SLOTS) return stack.is(IC2AutoItems.RTG_PELLET.get());
        return true;
    }

    // ── dono do baú pessoal ───────────────────────────────────────────────
    void onPlacedBy(LivingEntity placer) {
        if (this.type == MachineGuiType.PERSONAL_CHEST && placer instanceof Player player) {
            this.owner = player.getUUID();
            this.machine.setChanged();
        }
    }

    boolean isOwner(Player player) {
        return this.type != MachineGuiType.PERSONAL_CHEST || this.owner == null || this.owner.equals(player.getUUID())
                || player.getAbilities().instabuild;
    }

    /** Clique no bloco: a luminária liga e desliga; o baú pessoal só abre para o dono. */
    boolean handleUse(Player player) {
        if (this.type == MachineGuiType.LUMINATOR) {
            if (!player.level().isClientSide()) {
                this.enabled = !this.enabled;
                this.machine.setChanged();
                player.sendOverlayMessage(this.enabled
                        ? Component.translatableWithFallback("message.ic2reborn.luminator.on", "Luminator on")
                        : Component.translatableWithFallback("message.ic2reborn.luminator.off", "Luminator off"));
            }
            return true;
        }
        if (this.type == MachineGuiType.PERSONAL_CHEST && !isOwner(player)) {
            if (!player.level().isClientSide()) {
                player.sendOverlayMessage(Component.translatableWithFallback("message.ic2reborn.personal_chest.locked",
                        "This chest belongs to someone else"));
            }
            return true;
        }
        return false;
    }

    // ── tick ──────────────────────────────────────────────────────────────
    boolean tick(Level level) {
        this.working = false;
        if (!(level instanceof ServerLevel server)) return false;
        return switch (this.type) {
            case CHARGEPAD, CHARGEPAD_CESU, CHARGEPAD_MFE, CHARGEPAD_MFSU -> tickChargepad(server);
            case TESLA_COIL -> tickTesla(server);
            case CHUNK_LOADER -> tickChunkLoader(server);
            case ELECTROLYZER -> tickElectrolyzer(server);
            case MAGNETIZER -> tickMagnetizer(server);
            case LUMINATOR -> {
                this.working = this.enabled && this.machine.useEnergy(LUMINATOR_POWER);
                yield this.working;
            }
            case RT_GENERATOR -> tickRtg();
            default -> false;
        };
    }

    /** Placa de carga do IC2: a cada 2 ticks carrega tudo o que o jogador em cima dela carrega (armadura e inventário). */
    private boolean tickChargepad(ServerLevel level) {
        if (++this.ticker % 2 != 0) return false;
        BlockPos pos = this.machine.getBlockPos();
        AABB above = new AABB(pos.getX(), pos.getY() + 1.0, pos.getZ(), pos.getX() + 1.0, pos.getY() + 1.3, pos.getZ() + 1.0);
        int voltage = this.machine.getEnergyProfile().voltage();
        boolean charged = false;
        for (Player player : level.getEntitiesOfClass(Player.class, above)) {
            long budget = Math.min(this.machine.getStoredEnergy(), this.machine.getEnergyProfile().power() * 2);
            var inventory = player.getInventory();
            for (int slot = 0; slot < inventory.getContainerSize() && budget > 0; slot++) {
                ItemStack stack = inventory.getItem(slot);
                if (stack.isEmpty()) continue;
                long moved = EnergyItems.charge(stack, budget, voltage, false);
                if (moved > 0) {
                    this.machine.useEnergy(moved);
                    budget -= moved;
                    charged = true;
                }
            }
            if (charged) inventory.setChanged();
        }
        this.working = charged;
        return charged;
    }

    /** Bobina de Tesla do IC2: com redstone, a cada 32 ticks dá um choque (1 de dano por 400 EU guardados) num ser vivo a até 4 blocos. */
    private boolean tickTesla(ServerLevel level) {
        if (!level.hasNeighborSignal(this.machine.getBlockPos()) || !this.machine.useEnergy(TESLA_IDLE)) return false;
        this.working = true;
        if (++this.ticker % 32 != 0) return true;
        int damage = (int) Math.min(100, this.machine.getStoredEnergy() / TESLA_PER_DAMAGE);
        if (damage <= 0) return true;
        BlockPos pos = this.machine.getBlockPos();
        AABB area = new AABB(pos).inflate(4.0);
        for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
            if (RadiationHandler.hasCompleteHazmat(entity)) continue;
            if (entity.hurtServer(level, level.damageSources().lightningBolt(), damage)) {
                level.sendParticles(ParticleTypes.ELECTRIC_SPARK, entity.getX(), entity.getY() + entity.getBbHeight() / 2, entity.getZ(),
                        damage * 2, 0.3, 0.5, 0.3, 0.1);
                this.machine.useEnergy(damage * TESLA_PER_DAMAGE);
                break;
            }
        }
        return true;
    }

    /** Carregador de chunks: com energia mantém o próprio chunk carregado. */
    private boolean tickChunkLoader(ServerLevel level) {
        boolean active = this.machine.useEnergy(CHUNK_POWER);
        this.working = active;
        if (active != this.chunkForced) {
            ChunkPos chunk = ChunkPos.containing(this.machine.getBlockPos());
            level.setChunkForced(chunk.x(), chunk.z(), active);
            this.chunkForced = active;
            return true;
        }
        return false;
    }

    void onRemoved(Level level) {
        if (this.type == MachineGuiType.CHUNK_LOADER && this.chunkForced && level instanceof ServerLevel server) {
            ChunkPos chunk = ChunkPos.containing(this.machine.getBlockPos());
            server.setChunkForced(chunk.x(), chunk.z(), false);
            this.chunkForced = false;
        }
    }

    /** Eletrolisador: separa a água; o hidrogênio vai para o tanque embaixo e o oxigênio para o de cima (os dois precisam caber). */
    private boolean tickElectrolyzer(ServerLevel level) {
        MachineBlockEntity.MachineTank water = this.machine.mainTank();
        int ticks = Math.max(1, this.machine.getEnergyProfile().operationTicks());
        this.machine.setProgressDisplay(this.progress, ticks);
        if (water == null || water.amount < WATER_PER_RUN || !outputsFit(level, false)) {
            boolean had = this.progress > 0;
            this.progress = 0;
            return had;
        }
        if (!this.machine.useEnergy(this.machine.getEnergyProfile().power())) return false;
        this.working = true;
        if (++this.progress >= ticks) {
            this.progress = 0;
            if (outputsFit(level, true)) water.consume(WATER_PER_RUN);
        }
        return true;
    }

    private boolean outputsFit(ServerLevel level, boolean commit) {
        BlockPos pos = this.machine.getBlockPos();
        Storage<FluidVariant> below = FluidStorage.SIDED.find(level, pos.below(), Direction.UP);
        Storage<FluidVariant> above = FluidStorage.SIDED.find(level, pos.above(), Direction.DOWN);
        if (below == null || above == null) return false;
        try (Transaction transaction = Transaction.openOuter()) {
            boolean fits = below.insert(FluidVariant.of(IC2Fluids.HYDROGEN.fluid()), HYDROGEN_PER_RUN, transaction) == HYDROGEN_PER_RUN
                    && above.insert(FluidVariant.of(IC2Fluids.OXYGEN.fluid()), OXYGEN_PER_RUN, transaction) == OXYGEN_PER_RUN;
            if (fits && commit) transaction.commit();
            return fits;
        }
    }

    /**
     * Magnetizador do IC2: magnetiza a coluna de cercas de ferro em cima dele (até 20 blocos). Quem
     * encosta nela sobe (o movimento é feito no cliente) e não toma dano de queda; cada jogador gasta 2 EU/t.
     */
    private boolean tickMagnetizer(ServerLevel level) {
        BlockPos pos = this.machine.getBlockPos();
        if (level.hasNeighborSignal(pos) || this.machine.getStoredEnergy() < MAGNET_BOOST) return false;
        this.working = true;
        int top = 0;
        while (top < MAGNET_RANGE && level.getBlockState(pos.above(top + 1)).is(IC2AutoBlocks.IRON_FENCE.get())) top++;
        if (top == 0) return false;
        AABB column = new AABB(pos.getX() - 0.8, pos.getY() + 1.0, pos.getZ() - 0.8, pos.getX() + 1.8, pos.getY() + top + 1.0, pos.getZ() + 1.8);
        boolean boosted = false;
        for (Player player : level.getEntitiesOfClass(Player.class, column)) {
            if (!this.machine.useEnergy(MAGNET_BOOST)) break;
            player.resetFallDistance();
            boosted = true;
        }
        return boosted;
    }

    /** RTG do IC2: 1, 2, 4... 32 EU/t conforme as pastilhas de radioisótopo. */
    private boolean tickRtg() {
        int pellets = 0;
        for (int slot = 0; slot < RTG_SLOTS; slot++) {
            if (this.machine.getInventory().getItem(slot).is(IC2AutoItems.RTG_PELLET.get())) pellets++;
        }
        if (pellets == 0) return false;
        this.machine.addGeneratedEnergy((1L << (pellets - 1)) * RTG_CW_PER_EU);
        this.working = true;
        return true;
    }

    // ── salvar/carregar ───────────────────────────────────────────────────
    void write(ValueOutput output) {
        output.putInt("UtilityEnabled", this.enabled ? 1 : 0);
        output.putInt("ChunkForced", this.chunkForced ? 1 : 0);
        output.putInt("UtilityProgress", this.progress);
        if (this.owner != null) output.putString("Owner", this.owner.toString());
    }

    void read(ValueInput input) {
        this.enabled = input.getIntOr("UtilityEnabled", 1) != 0;
        this.chunkForced = input.getIntOr("ChunkForced", 0) != 0;
        this.progress = Math.max(0, input.getIntOr("UtilityProgress", 0));
        this.owner = input.getString("Owner").map(value -> {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }).orElse(null);
    }
}
