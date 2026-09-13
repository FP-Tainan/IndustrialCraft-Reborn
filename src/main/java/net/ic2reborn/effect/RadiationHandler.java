package net.ic2reborn.effect;

import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Itens radioativos do IC2 ({@code NuclearResourceType}): carregados no inventário dão radiação,
 * a não ser com a roupa hazmat completa. Dentro da caixa de contenção não contam.
 */
public final class RadiationHandler {
    /** Duração (s) e nível da radiação que o item dá. */
    public record Radiation(int seconds, int amplifier) {}

    private static @Nullable Map<Item, Radiation> table;

    private RadiationHandler() {}

    public static void init() {
        ServerTickEvents.END_SERVER_TICK.register(server -> {
            if (server.getTickCount() % 20 != 0) return;
            for (ServerPlayer player : server.getPlayerList().getPlayers()) {
                tickPlayer(player);
            }
        });
    }

    private static Map<Item, Radiation> table() {
        if (table == null) {
            Map<Item, Radiation> map = new HashMap<>();
            map.put(IC2AutoItems.URANIUM.get(), new Radiation(60, 100));
            map.put(IC2AutoItems.URANIUM_235.get(), new Radiation(150, 100));
            map.put(IC2AutoItems.URANIUM_238.get(), new Radiation(10, 90));
            map.put(IC2AutoItems.PLUTONIUM.get(), new Radiation(150, 100));
            map.put(IC2AutoItems.MOX.get(), new Radiation(300, 100));
            map.put(IC2AutoItems.SMALL_URANIUM_235.get(), new Radiation(150, 100));
            map.put(IC2AutoItems.SMALL_PLUTONIUM.get(), new Radiation(150, 100));
            map.put(IC2AutoItems.RTG_PELLET.get(), new Radiation(2, 90));
            // barras de combustível: como o urânio empobrecido do IC2
            for (Item rod : new Item[]{IC2AutoItems.URANIUM_FUEL_ROD.get(), IC2AutoItems.DUAL_URANIUM_FUEL_ROD.get(),
                    IC2AutoItems.QUAD_URANIUM_FUEL_ROD.get(), IC2AutoItems.MOX_FUEL_ROD.get(),
                    IC2AutoItems.DUAL_MOX_FUEL_ROD.get(), IC2AutoItems.QUAD_MOX_FUEL_ROD.get()}) {
                map.put(rod, new Radiation(10, 100));
            }
            table = map;
        }
        return table;
    }

    public static @Nullable Radiation radiationOf(ItemStack stack) {
        return stack.isEmpty() ? null : table().get(stack.getItem());
    }

    public static boolean isRadioactive(ItemStack stack) {
        return radiationOf(stack) != null;
    }

    /** Uma checagem por segundo do inventário do jogador (IC2: a cada tick do item). */
    public static void tickPlayer(ServerPlayer player) {
        if (player.getAbilities().invulnerable || hasCompleteHazmat(player)) return;
        Radiation worst = null;
        for (ItemStack stack : player.getInventory()) {
            Radiation radiation = radiationOf(stack);
            if (radiation != null && (worst == null || radiation.seconds() > worst.seconds())) worst = radiation;
        }
        if (worst == null) return;
        MobEffectInstance current = player.getEffect(IC2Effects.radiation());
        if (current == null || current.getDuration() < worst.seconds() * 20) {
            player.addEffect(new MobEffectInstance(IC2Effects.radiation(), worst.seconds() * 20, worst.amplifier()));
        }
    }

    /**
     * Roupa hazmat completa: capacete, peitoral e calças hazmat e botas de borracha. Peças quânticas
     * com carga valem como hazmat (IC2: IHazmatLike).
     */
    public static boolean hasCompleteHazmat(LivingEntity entity) {
        return protects(entity, EquipmentSlot.HEAD, IC2AutoItems.HAZMAT_HELMET.get())
                && protects(entity, EquipmentSlot.CHEST, IC2AutoItems.HAZMAT_CHESTPLATE.get())
                && protects(entity, EquipmentSlot.LEGS, IC2AutoItems.HAZMAT_LEGGINGS.get())
                && protects(entity, EquipmentSlot.FEET, IC2AutoItems.RUBBER_BOOTS.get());
    }

    private static boolean protects(LivingEntity entity, EquipmentSlot slot, Item hazmat) {
        ItemStack stack = entity.getItemBySlot(slot);
        if (stack.is(hazmat)) return true;
        return stack.getItem() instanceof net.ic2reborn.item.ElectricArmorItem armor
                && armor.kind() == net.ic2reborn.item.ElectricArmorItem.Kind.QUANTUM
                && net.craftenergy.content.item.EnergyItems.getStored(stack) > 0;
    }
}
