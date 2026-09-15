package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.EnergyItem;
import net.craftenergy.content.item.EnergyItems;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.equipment.ArmorMaterial;
import org.jetbrains.annotations.Nullable;

/**
 * Capacetes solares do Advanced Solar Panels ({@code ItemArmourSolarHelmet}): geram como o painel do
 * mesmo nível (dia limpo rende a produção de dia; noite ou chuva, a noturna), carregam as outras peças
 * vestidas, a mão secundária e o inventário, e guardam o que sobra em si mesmos. O híbrido e o supremo
 * repõem o ar debaixo d'água gastando energia.
 */
public class SolarHelmetItem extends ElectricArmorItem {
    /** IC2: 1.000 EU por reposição de 200 de ar. */
    private static final long AIR_ENERGY = EnergyUnits.fromCWh(500);
    private static final EquipmentSlot[] WORN = {EquipmentSlot.FEET, EquipmentSlot.LEGS, EquipmentSlot.CHEST, EquipmentSlot.OFFHAND};

    private final long dayPower;
    private final long nightPower;
    private final boolean refillsAir;

    /**
     * @param dayPower   CW gerados de dia
     * @param nightPower CW gerados à noite ou na chuva
     */
    public SolarHelmetItem(Properties properties, Kind kind, ArmorMaterial material, String asset,
                           long dayPower, long nightPower, boolean refillsAir) {
        super(properties, kind, EquipmentSlot.HEAD, material, IC2ArmorMaterials.asset(asset));
        this.dayPower = dayPower;
        this.nightPower = nightPower;
        this.refillsAir = refillsAir;
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot equipped) {
        super.inventoryTick(stack, level, entity, equipped);
        if (equipped != EquipmentSlot.HEAD || !(entity instanceof ServerPlayer player)) return;

        if (this.refillsAir && player.getAirSupply() < 100 && EnergyItems.getStored(stack) >= AIR_ENERGY) {
            player.setAirSupply(player.getAirSupply() + 200);
            EnergyItems.use(stack, AIR_ENERGY);
        }

        long power = switch (MachineBlockEntity.solarState(level, player.blockPosition())) {
            case MachineBlockEntity.SOLAR_DAY -> this.dayPower;
            case MachineBlockEntity.SOLAR_NIGHT -> this.nightPower;
            default -> 0L;
        };
        for (EquipmentSlot slot : WORN) {
            if (power <= 0) return;
            power -= charge(player.getItemBySlot(slot), power);
        }
        for (int i = 0; i < 36 && power > 0; i++) {
            power -= charge(player.getInventory().getItem(i), power);
        }
        if (power > 0) EnergyItems.charge(stack, power, Integer.MAX_VALUE, false);
    }

    private long charge(ItemStack target, long power) {
        if (target.isEmpty() || !(target.getItem() instanceof EnergyItem)) return 0;
        return EnergyItems.charge(target, power, kind().voltage, false);
    }
}
