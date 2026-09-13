package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.menu.v1.ExtendedMenuProvider;
import net.ic2reborn.crop.CropBlockEntity;
import net.ic2reborn.crop.CropCard;
import net.ic2reborn.menu.CropnalyzerMenu;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;

/**
 * Cropnalyzer do IC2 ({@code ItemCropnalyzer}): na mão abre a análise de sacos de sementes; clicado
 * numa plantação mostra no chat o estado dela.
 */
public class CropnalyzerItem extends ElectricItem {
    /** IC2: 100.000 EU, 128 EU/t, nível 2. */
    public static final long CAPACITY = EnergyUnits.fromCWh(100_000);

    public CropnalyzerItem(Properties properties) {
        super(properties, CAPACITY, 128_000, 1_000);
    }

    /** Energia para analisar um saco que está no nível {@code level} (0 a 3). */
    public static long energyForLevel(int level) {
        return EnergyUnits.fromCWh(switch (level) {
            case 1 -> 90;
            case 2 -> 900;
            case 3 -> 9_000;
            default -> 10;
        });
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (player instanceof ServerPlayer serverPlayer) {
            ItemStack stack = player.getItemInHand(hand);
            serverPlayer.openMenu(new ExtendedMenuProvider<InteractionHand>() {
                @Override
                public InteractionHand getScreenOpeningData(ServerPlayer opener) {
                    return hand;
                }

                @Override
                public Component getDisplayName() {
                    return stack.getHoverName();
                }

                @Override
                public AbstractContainerMenu createMenu(int containerId, Inventory inventory, Player opener) {
                    return new CropnalyzerMenu(containerId, inventory, hand);
                }
            });
        }
        return InteractionResult.SUCCESS;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        if (player == null || player.isShiftKeyDown()
                || !(context.getLevel().getBlockEntity(context.getClickedPos()) instanceof CropBlockEntity crop)
                || crop.getCrop() == null) {
            return InteractionResult.PASS;
        }
        if (context.getLevel().isClientSide()) return InteractionResult.SUCCESS;
        if (!EnergyItems.use(context.getItemInHand(), energyForLevel(2))) return InteractionResult.PASS;

        CropCard card = crop.getCrop();
        message(player, "name", "Crop name: %s (by %s)", card.name(), card.discoveredBy());
        message(player, "size", "Crop size: %s/%s", crop.getSize(), card.maxSize());
        message(player, "nutrients", "Nutrient storage: %s/100", crop.getStorageNutrients());
        message(player, "water", "Water storage: %s/200", crop.getStorageWater());
        message(player, "herbicide", "Herbicide storage: %s/100", crop.getStorageWeedEx());
        message(player, "growth", "Growth points: %s/%s", crop.getGrowthPoints(), card.growthDuration(crop));
        return InteractionResult.SUCCESS;
    }

    private static void message(Player player, String key, String fallback, Object... args) {
        if (player instanceof ServerPlayer serverPlayer) {
            serverPlayer.sendSystemMessage(Component.translatableWithFallback("message.ic2reborn.cropnalyzer." + key, fallback, args));
        }
    }
}
