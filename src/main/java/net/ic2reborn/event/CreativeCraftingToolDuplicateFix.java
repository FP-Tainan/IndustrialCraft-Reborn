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
 * Corrige um efeito colateral do modo Criativo com ferramentas de crafting reutilizaveis.
 *
 * Em Survival, o crafting remainder volta para o mesmo slot da grade com dano.
 * Em Creative, o jogo nao consome o item da grade, mas ainda cria o remainder danificado,
 * o que fazia martelo/alicate duplicarem no inventario. Aqui removemos esse remainder
 * extra depois do craft, mantendo apenas a ferramenta da grade.
 */
@Mod.EventBusSubscriber(modid = IC2Reborn.MODID)
public final class CreativeCraftingToolDuplicateFix {
    private CreativeCraftingToolDuplicateFix() {}

    @SubscribeEvent
    public static void onItemCrafted(PlayerEvent.ItemCraftedEvent event) {
        Player player = event.getEntity();
        if (!player.isCreative()) return;

        ItemStack crafted = event.getCrafting();
        if (crafted.isEmpty()) return;

        Container grid = event.getContainer();

        int hammerDamage = -1;
        int cutterDamage = -1;

        for (int slot = 0; slot < grid.getContainerSize(); slot++) {
            ItemStack input = grid.getItem(slot);
            if (input.isEmpty()) continue;

            if (hammerDamage < 0 && input.is(IC2AutoItems.HAMMER.get()) && isHammerOutput(crafted)) {
                hammerDamage = nextDamage(input);
            }

            if (cutterDamage < 0 && input.is(IC2AutoItems.CUTTER.get()) && isCutterOutput(crafted)) {
                cutterDamage = nextDamage(input);
            }
        }

        if (hammerDamage < 0 && cutterDamage < 0) return;

        final int expectedHammerDamage = hammerDamage;
        final int expectedCutterDamage = cutterDamage;

        if (expectedHammerDamage >= 0) {
            removeOneDuplicateTool(player, IC2AutoItems.HAMMER.get(), expectedHammerDamage);
        }
        if (expectedCutterDamage >= 0) {
            removeOneDuplicateTool(player, IC2AutoItems.CUTTER.get(), expectedCutterDamage);
        }
    }

    private static int nextDamage(ItemStack stack) {
        if (!stack.isDamageableItem()) return -1;
        return stack.getDamageValue() + 1;
    }

    private static void removeOneDuplicateTool(Player player, Item tool, int expectedDamage) {
        // Primeiro tenta remover exatamente o remainder esperado.
        if (removeOneMatchingTool(player, tool, expectedDamage)) return;

        // Fallback: remove qualquer copia danificada da ferramenta no inventario.
        // Isso evita acumulo caso o dano visto pelo evento ja seja o dano apos o remainder.
        removeOneMatchingTool(player, tool, -1);
    }

    private static boolean removeOneMatchingTool(Player player, Item tool, int expectedDamage) {
        var inventory = player.getInventory();

        for (int slot = 0; slot < inventory.getContainerSize(); slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!stack.is(tool)) continue;
            if (stack.getCount() != 1) continue;
            if (!stack.isDamageableItem()) continue;
            if (stack.getDamageValue() <= 0) continue;
            if (expectedDamage >= 0 && stack.getDamageValue() != expectedDamage) continue;

            inventory.setItem(slot, ItemStack.EMPTY);
            return true;
        }

        return false;
    }

    private static boolean isHammerOutput(ItemStack stack) {
        String path = itemPath(stack);
        return path.startsWith("plate_")
            || path.startsWith("dense_plate_")
            || path.startsWith("casing_");
    }

    private static boolean isCutterOutput(ItemStack stack) {
        String path = itemPath(stack);
        return path.startsWith("cable_") || path.equals("glass_fibre_cable");
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
