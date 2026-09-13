package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.energy.WindSim;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Anemômetro do IC2 ({@code ItemWindmeter}): no ar mostra o vento na sua altura; num gerador eólico
 * ou turbina eólica, o vento que chega nela descontando obstruções.
 */
public class WindMeterItem extends ElectricItem {
    /** IC2: 10.000 EU, 100 EU/t, nível 1, 50 EU por medição. */
    private static final long CAPACITY = EnergyUnits.fromCWh(5_000);
    public static final long OPERATION_ENERGY = EnergyUnits.fromCWh(25);

    public WindMeterItem(Properties properties) {
        super(properties, CAPACITY, 50_000, 220);
    }

    public static String format(double wind) {
        return String.format(Locale.ROOT, "%.2f", Math.max(0.0, wind));
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (EnergyItems.getStored(stack) < OPERATION_ENERGY) return InteractionResult.PASS;
        if (player instanceof ServerPlayer serverPlayer && level instanceof ServerLevel serverLevel) {
            EnergyItems.use(stack, OPERATION_ENERGY);
            double wind = WindSim.get(serverLevel).windAt(serverLevel, player.getY());
            serverPlayer.sendSystemMessage(Component.translatableWithFallback("message.ic2reborn.wind_meter.info",
                    "Wind strength: %s", format(wind)));
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()
                || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof MachineBlockEntity machine)) {
            return InteractionResult.PASS;
        }
        if (!(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        Component reading = machine.windMeterReading();
        if (reading == null) return InteractionResult.PASS;
        ItemStack stack = context.getItemInHand();
        if (!EnergyItems.use(stack, OPERATION_ENERGY)) return InteractionResult.PASS;
        serverPlayer.sendSystemMessage(reading);
        return InteractionResult.SUCCESS;
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.wind_meter",
                "Right-click: wind here; on a wind generator: effective wind").withStyle(ChatFormatting.GRAY));
    }
}
