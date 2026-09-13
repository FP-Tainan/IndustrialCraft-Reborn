package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBlockTags;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Scanner do IC2 ({@code ItemScanner}): na mão, conta os minérios em volta; no minerador,
 * cada camada escaneada custa energia e define até onde ele procura minério.
 */
public class ScannerItem extends ElectricItem {
    /** Energia para escanear uma camada no minerador (IC2: 50 EU). */
    public static final long LAYER_SCAN_ENERGY = EnergyUnits.fromCWh(25);

    /**
     * @param range     alcance (IC2: 6 no OD, 12 no OV); no minerador vale a metade
     * @param useEnergy energia por uso na mão (IC2: 50 / 250 EU)
     */
    public enum Tier {
        OD(EnergyUnits.fromCWh(50_000), 64_000, 220, 6, EnergyUnits.fromCWh(25)),
        OV(EnergyUnits.fromCWh(500_000), 256_000, 1_000, 12, EnergyUnits.fromCWh(125));

        final long capacity;
        final long transferLimit;
        final int voltage;
        final int range;
        final long useEnergy;

        Tier(long capacity, long transferLimit, int voltage, int range, long useEnergy) {
            this.capacity = capacity;
            this.transferLimit = transferLimit;
            this.voltage = voltage;
            this.range = range;
            this.useEnergy = useEnergy;
        }
    }

    private final Tier tier;

    public ScannerItem(Properties properties, Tier tier) {
        super(properties, tier.capacity, tier.transferLimit, tier.voltage);
        this.tier = tier;
    }

    public int scanRange() {
        return this.tier.range;
    }

    /** Minerador: gasta a energia de uma camada e devolve o raio de busca, ou 0 sem energia. */
    public int startLayerScan(ItemStack stack) {
        return EnergyItems.use(stack, LAYER_SCAN_ENERGY) ? this.tier.range / 2 : 0;
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (level.isClientSide() || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        ItemStack stack = player.getItemInHand(hand);
        if (!EnergyItems.use(stack, this.tier.useEnergy)) {
            serverPlayer.sendSystemMessage(Component.translatableWithFallback("item.ic2reborn.scanner.empty",
                    "Scanner out of energy").withStyle(ChatFormatting.RED));
            return InteractionResult.FAIL;
        }

        Map<Block, Integer> ores = new LinkedHashMap<>();
        BlockPos center = player.blockPosition();
        int range = this.tier.range;
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-range, -range, -range), center.offset(range, range, range))) {
            BlockState state = level.getBlockState(pos);
            if (state.is(ConventionalBlockTags.ORES)) {
                ores.merge(state.getBlock(), 1, Integer::sum);
            }
        }

        serverPlayer.sendSystemMessage(Component.translatableWithFallback("item.ic2reborn.scanner.result",
                "Ores within %s blocks:", range).withStyle(ChatFormatting.GOLD));
        if (ores.isEmpty()) {
            serverPlayer.sendSystemMessage(Component.translatableWithFallback("item.ic2reborn.scanner.none",
                    "No ores found").withStyle(ChatFormatting.GRAY));
        }
        ores.entrySet().stream()
                .sorted(Map.Entry.<Block, Integer>comparingByValue().reversed())
                .forEach(entry -> serverPlayer.sendSystemMessage(entry.getKey().getName().copy()
                        .append(": " + entry.getValue()).withStyle(ChatFormatting.GRAY)));
        return InteractionResult.SUCCESS;
    }
}
