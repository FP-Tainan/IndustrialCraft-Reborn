package net.ic2reborn.event;

import net.ic2reborn.IC2Reborn;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.listener.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Martelo e alicate são consumidos pelo recipe JSON vanilla, então este evento
 * devolve a ferramenta para o jogador com +1 de dano de durabilidade.
 */
@Mod.EventBusSubscriber(modid = IC2Reborn.MODID)
public final class CraftingToolEvents {
    private CraftingToolEvents() {}

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;

        Container grid = event.getContainer();
        Player player = event.getEntity();

        boolean returnedHammer = false;
        boolean returnedCutter = false;

        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack input = grid.getItem(slot);
            if (input.isEmpty()) continue;

            if (!returnedHammer && input.is(IC2AutoItems.HAMMER.get()) && isHammerOutput(crafted)) {
                giveDamagedBack(player, input, IC2AutoItems.HAMMER.get());
                returnedHammer = true;
            }

            if (!returnedCutter && input.is(IC2AutoItems.CUTTER.get()) && isCutterOutput(crafted)) {
                giveDamagedBack(player, input, IC2AutoItems.CUTTER.get());
                returnedCutter = true;
            }
        }
    }

    private static void giveDamagedBack(Player player, ItemStack original, Item tool) {
        ItemStack damaged = original.copy();
        damaged.setCount(1);
        if (!damaged.isDamageableItem()) damaged = new ItemStack(tool);
        damaged.setDamageValue(damaged.getDamageValue() + 1);
        if (damaged.getDamageValue() >= damaged.getMaxDamage()) return;
        if (!player.getInventory().add(damaged)) player.drop(damaged, false);
    }

    private static boolean isHammerOutput(ItemStack stack) {
        String path = itemPath(stack);
        return path.startsWith("plate_")
            || path.startsWith("dense_plate_")
            || path.startsWith("casing_");
    }

    private static boolean isCutterOutput(ItemStack stack) {
        return itemPath(stack).startsWith("cable_") || itemPath(stack).equals("glass_fibre_cable");
    }

    private static String itemPath(ItemStack stack) {
        Item item = stack.getItem();

        for (var entry : IC2AutoItems.ITEMS.getEntries()) {
            if (item == entry.get()) {
                return pathFromRegistryId(String.valueOf(entry.getId()));
            }
        }

        for (var entry : IC2Items.ITEMS.getEntries()) {
            if (item == entry.get()) {
                return pathFromRegistryId(String.valueOf(entry.getId()));
            }
        }

        return "";
    }

    private static String pathFromRegistryId(String registryId) {
        int colon = registryId.indexOf(':');
        return colon >= 0 ? registryId.substring(colon + 1) : registryId;
    }
}
