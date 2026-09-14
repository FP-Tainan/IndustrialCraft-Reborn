package net.ic2reborn.item;

import net.craftenergy.content.block.CableBlock;
import net.craftenergy.fabric.EnergyNetworkManager;
import net.craftenergy.grid.EnergyNetwork;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.network.MultimeterReadingPayload;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Multímetro: segurado na mão ou no slot do escudo, mede o cabo ou a máquina que o jogador está
 * olhando (até 8 blocos) e mostra tensão (MV), corrente (RA) e potência (CW) flutuando sobre o bloco.
 * Máquinas de calor mostram CCº, as de torque CKGF·M e o reator o calor do núcleo em MMEV.
 */
public class MultimeterItem extends Item {
    private static final double RANGE = 8.0;
    private static final int UPDATE_TICKS = 5;
    /** Temperatura do cabo mostrada no multímetro: frio a 20 CCº, queima a 220 CCº. */
    public static final double CABLE_AMBIENT = 20.0;
    public static final double CABLE_BURN = 220.0;
    public static final String CABLE_TEMPERATURE = "CCº";

    public MultimeterItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(MultimeterItem::tick);
    }

    private static boolean holds(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof MultimeterItem || player.getOffhandItem().getItem() instanceof MultimeterItem;
    }

    private static void tick(MinecraftServer server) {
        if (server.getTickCount() % UPDATE_TICKS != 0) return;
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (!holds(player)) continue;
            HitResult hit = player.pick(RANGE, 1.0F, false);
            MultimeterReadingPayload reading = hit instanceof BlockHitResult blockHit && hit.getType() == HitResult.Type.BLOCK
                    ? measure(player.level(), blockHit.getBlockPos()) : MultimeterReadingPayload.empty();
            ServerPlayNetworking.send(player, reading);
        }
    }

    /** Lê o bloco: cabo (rede do Craft Energy) ou máquina do IC2 Reborn. */
    public static MultimeterReadingPayload measure(ServerLevel level, BlockPos pos) {
        List<Double> values = new ArrayList<>();
        List<String> units = new ArrayList<>();
        if (level.getBlockState(pos).getBlock() instanceof CableBlock) {
            double voltage = 0;
            double current = 0;
            for (EnergyNetwork<Long> network : EnergyNetworkManager.get(level).networksAt(pos)) {
                voltage = Math.max(voltage, network.voltage());
                current += network.conductorCurrent(pos.asLong());
            }
            electric(values, units, voltage, current * voltage);
            // aquecimento do cabo: 0 (frio, 20 CCº) até 1 (queima, 220 CCº)
            values.add(CABLE_AMBIENT + EnergyNetworkManager.get(level).heatAt(pos) * (CABLE_BURN - CABLE_AMBIENT));
            units.add(CABLE_TEMPERATURE);
        } else if (level.getBlockEntity(pos) instanceof MachineBlockEntity machine) {
            machine.multimeterReading(values, units);
        }
        return values.isEmpty() ? MultimeterReadingPayload.empty() : new MultimeterReadingPayload(pos.immutable(), values, units);
    }

    /** Tensão, corrente e potência, como no multímetro de verdade. */
    public static void electric(List<Double> values, List<String> units, double voltage, double power) {
        values.add(voltage);
        units.add("MV");
        values.add(voltage <= 0 ? 0.0 : power / voltage);
        units.add("RA");
        values.add(power);
        units.add("CW");
    }

    @Override
    @SuppressWarnings("deprecation")
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.multimeter",
                "Hold it (or put it in the shield slot) and look at cables and machines").withStyle(ChatFormatting.GRAY));
    }
}
