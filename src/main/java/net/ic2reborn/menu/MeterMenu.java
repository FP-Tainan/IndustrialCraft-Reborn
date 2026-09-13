package net.ic2reborn.menu;

import net.craftenergy.fabric.EnergyNetworkManager;
import net.craftenergy.grid.EnergyNetwork;
import net.craftenergy.grid.NetworkTickReport;
import net.ic2reborn.registry.IC2Menus;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Tela do medidor ({@code ContainerMeter}): a cada tick lê a rede do bloco medido e guarda média,
 * mínimo e máximo desde o último "zerar". Modos: potência entregue, potência gerada, corrente e tensão.
 */
public class MeterMenu extends AbstractContainerMenu {
    public static final int MODE_DELIVERED = 0;
    public static final int MODE_GENERATED = 1;
    public static final int MODE_CURRENT = 2;
    public static final int MODE_VOLTAGE = 3;
    public static final int BUTTON_RESET = 4;

    private static final int FIELD_MODE = 0;
    private static final int FIELD_COUNT = 1;
    private static final int FIELD_AVG = 2;
    private static final int FIELD_MIN = 3;
    private static final int FIELD_MAX = 4;
    private static final int FIELDS = 5;
    /** Corrente vai com duas casas decimais. */
    private static final double CURRENT_SCALE = 100.0;

    private final BlockPos pos;
    private final Player player;
    private final ContainerData data;

    private int mode = MODE_DELIVERED;
    private int count;
    private double average;
    private double minimum;
    private double maximum;

    public MeterMenu(int containerId, Inventory inventory, BlockPos pos) {
        super(IC2Menus.METER.get(), containerId);
        this.pos = pos;
        this.player = inventory.player;
        this.data = this.player.level().isClientSide() ? new SimpleContainerData(FIELDS * 2) : new ContainerData() {
            @Override
            public int get(int index) {
                int value = field(index / 2);
                return (index & 1) == 0 ? value & 0xFFFF : (value >>> 16) & 0xFFFF;
            }

            @Override
            public void set(int index, int value) {
            }

            @Override
            public int getCount() {
                return FIELDS * 2;
            }
        };
        addDataSlots(this.data);

        // IC2: ContainerFullInv com 218 de altura
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) {
                addSlot(new Slot(inventory, col + row * 9 + 9, 8 + col * 18, 136 + row * 18));
            }
        }
        for (int col = 0; col < 9; col++) {
            addSlot(new Slot(inventory, col, 8 + col * 18, 194));
        }
    }

    private int field(int field) {
        double scale = this.mode == MODE_CURRENT ? CURRENT_SCALE : 1.0;
        return switch (field) {
            case FIELD_MODE -> this.mode;
            case FIELD_COUNT -> this.count;
            case FIELD_AVG -> clamp(this.average * scale);
            case FIELD_MIN -> clamp(this.minimum * scale);
            case FIELD_MAX -> clamp(this.maximum * scale);
            default -> 0;
        };
    }

    private static int clamp(double value) {
        return (int) Math.max(Integer.MIN_VALUE, Math.min(Integer.MAX_VALUE, Math.round(value)));
    }

    private int synced(int field) {
        return (this.data.get(field * 2) & 0xFFFF) | ((this.data.get(field * 2 + 1) & 0xFFFF) << 16);
    }

    // ── cliente ───────────────────────────────────────────────────────────
    public int getMode() {
        return synced(FIELD_MODE);
    }

    public int getCount() {
        return synced(FIELD_COUNT);
    }

    public double getAverage() {
        return scaled(synced(FIELD_AVG));
    }

    public double getMinimum() {
        return scaled(synced(FIELD_MIN));
    }

    public double getMaximum() {
        return scaled(synced(FIELD_MAX));
    }

    private double scaled(int value) {
        return getMode() == MODE_CURRENT ? value / CURRENT_SCALE : value;
    }

    // ── servidor ──────────────────────────────────────────────────────────
    @Override
    public void broadcastChanges() {
        if (this.player.level() instanceof ServerLevel level) sample(level);
        super.broadcastChanges();
    }

    /** Uma leitura da rede (IC2: detectAndSendChanges). */
    public void sample(ServerLevel level) {
        double value = read(level);
        if (this.count == 0) {
            this.average = this.minimum = this.maximum = value;
        } else {
            this.minimum = Math.min(this.minimum, value);
            this.maximum = Math.max(this.maximum, value);
            this.average = (this.average * this.count + value) / (this.count + 1);
        }
        this.count++;
    }

    private double read(ServerLevel level) {
        List<EnergyNetwork<Long>> networks = EnergyNetworkManager.get(level).networksAt(this.pos);
        if (networks.isEmpty()) return 0.0;
        EnergyNetwork<Long> network = networks.getFirst();
        NetworkTickReport report = network.lastReport() == null ? NetworkTickReport.EMPTY : network.lastReport();
        return switch (this.mode) {
            case MODE_DELIVERED -> report.delivered();
            case MODE_GENERATED -> report.generated() + report.fromStorage();
            case MODE_CURRENT -> {
                double current = 0.0;
                for (EnergyNetwork<Long> each : networks) {
                    current = Math.max(current, each.conductorCurrent(this.pos.asLong()));
                }
                // fora de um cabo: a corrente que a rede entregou
                yield current > 0 || report.voltage() <= 0 ? current : (double) report.delivered() / report.voltage();
            }
            default -> network.voltage();
        };
    }

    public void reset() {
        this.count = 0;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (id >= MODE_DELIVERED && id <= MODE_VOLTAGE) {
            this.mode = id;
            reset();
            return true;
        }
        if (id == BUTTON_RESET) {
            reset();
            return true;
        }
        return false;
    }

    @Override
    public boolean stillValid(Player player) {
        return player.distanceToSqr(this.pos.getX() + 0.5, this.pos.getY() + 0.5, this.pos.getZ() + 0.5) <= 64.0;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }
}
