package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItem;
import net.craftenergy.content.item.EnergyItems;
import net.ic2reborn.registry.IC2Components;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.ItemAttributeModifiers;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.ArmorMaterial;
import net.minecraft.world.item.equipment.ArmorType;
import net.minecraft.world.item.equipment.Equippable;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Armaduras e mochilas elétricas do IC2 ({@code ItemArmorElectric}): nano, quântica, batpacks,
 * pack de energia, jetpack elétrico e óculos de visão noturna. Recarregam como bateria; as de
 * proteção só protegem com carga (a proteção some e volta conforme a energia).
 *
 * <p>Valores do IC2 convertidos como nas ferramentas: EU × 0,5 = CWh; EU/t × 500 = CW.
 */
public class ElectricArmorItem extends ElectricItem {
    public enum Kind {
        NANO(EquipmentSlot.HEAD, 500_000, 800_000, 2_400, 2_500, 0.9, false),
        QUANTUM(EquipmentSlot.HEAD, 5_000_000, 6_000_000, 13_800, 10_000, 1.0, false),
        BATPACK(EquipmentSlot.CHEST, 30_000, 50_000, 220, 0, 0.0, true),
        ADVANCED_BATPACK(EquipmentSlot.CHEST, 300_000, 500_000, 1_000, 0, 0.0, true),
        ENERGY_PACK(EquipmentSlot.CHEST, 1_000_000, 500_000, 2_400, 0, 0.0, true),
        ELECTRIC_JETPACK(EquipmentSlot.CHEST, 15_000, 30_000, 220, 0, 0.0, false),
        NIGHTVISION_GOGGLES(EquipmentSlot.HEAD, 100_000, 100_000, 220, 0, 0.0, false),
        // capacetes solares do Advanced Solar Panels (IC2: 1M/10M EU, 3.000/10.000 EU/t, 800/2.000 EU por dano)
        ADVANCED_SOLAR(EquipmentSlot.HEAD, 500_000, 1_500_000, 2_400, 400, 0.9, false),
        HYBRID_SOLAR(EquipmentSlot.HEAD, 5_000_000, 5_000_000, 13_800, 1_000, 1.0, false),
        ULTIMATE_SOLAR(EquipmentSlot.HEAD, 5_000_000, 5_000_000, 13_800, 1_000, 1.0, false);

        final EquipmentSlot defaultSlot;
        final long capacityCWh;
        final long transferLimit;
        final int voltage;
        /** CWh gastos por ponto de dano absorvido (IC2: energyPerDamage). */
        final long energyPerDamageCWh;
        final double absorption;
        /** Mochilas que alimentam os itens elétricos da hotbar. */
        final boolean providesEnergy;

        Kind(EquipmentSlot defaultSlot, long capacityCWh, long transferLimit, int voltage, long energyPerDamageCWh,
             double absorption, boolean providesEnergy) {
            this.defaultSlot = defaultSlot;
            this.capacityCWh = capacityCWh;
            this.transferLimit = transferLimit;
            this.voltage = voltage;
            this.energyPerDamageCWh = energyPerDamageCWh;
            this.absorption = absorption;
            this.providesEnergy = providesEnergy;
        }
    }

    private final Kind kind;
    private final EquipmentSlot slot;
    private final @Nullable ItemAttributeModifiers protection;

    /**
     * @param material material de proteção e textura (nano, quântica) ou null para mochilas/óculos
     * @param asset    textura no corpo quando não há material
     */
    public ElectricArmorItem(Properties properties, Kind kind, EquipmentSlot slot, @Nullable ArmorMaterial material, String asset) {
        this(properties, kind, slot, material, material != null ? material.assetId() : IC2ArmorMaterials.asset(asset));
    }

    /** Proteção de um material com a textura de outra (capacetes solares). */
    protected ElectricArmorItem(Properties properties, Kind kind, EquipmentSlot slot, @Nullable ArmorMaterial material,
                                net.minecraft.resources.ResourceKey<net.minecraft.world.item.equipment.EquipmentAsset> asset) {
        super(properties.component(DataComponents.EQUIPPABLE, Equippable.builder(slot)
                        .setEquipSound(SoundEvents.ARMOR_EQUIP_IRON)
                        .setAsset(asset)
                        .setDamageOnHurt(false)
                        .build()),
                EnergyUnits.fromCWh(kind.capacityCWh), kind.transferLimit, kind.voltage);
        this.kind = kind;
        this.slot = slot;
        this.protection = material == null ? null : material.createAttributes(armorType(slot));
    }

    private static ArmorType armorType(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> ArmorType.HELMET;
            case CHEST -> ArmorType.CHESTPLATE;
            case LEGS -> ArmorType.LEGGINGS;
            default -> ArmorType.BOOTS;
        };
    }

    public Kind kind() {
        return this.kind;
    }

    public EquipmentSlot slot() {
        return this.slot;
    }

    /** Energia (CW·tick) gasta por ponto de dano absorvido. */
    public long energyPerDamage() {
        return EnergyUnits.fromCWh(this.kind.energyPerDamageCWh);
    }

    /** Fração do dano que esta peça absorve com carga (IC2: base da peça × razão do conjunto). */
    public double absorptionRatio() {
        double base = switch (this.slot) {
            case HEAD, FEET -> 0.15;
            case CHEST -> 0.4;
            case LEGS -> 0.3;
            default -> 0.0;
        };
        double ratio = this.kind == Kind.QUANTUM && this.slot == EquipmentSlot.CHEST ? 1.2 : this.kind.absorption;
        return base * ratio;
    }

    public boolean isCharged(ItemStack stack) {
        return EnergyItems.getStored(stack) >= Math.max(1, energyPerDamage());
    }

    @Override
    public boolean canDischarge(ItemStack stack) {
        return this.kind.providesEnergy;
    }

    /** Visão noturna ligada (capacetes nano/quântico e óculos). */
    public static boolean nightVisionOn(ItemStack stack) {
        return stack.has(IC2Components.ACTIVE.get());
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot equipped) {
        // a proteção comum só existe com carga
        if (this.protection != null) {
            boolean charged = isCharged(stack);
            boolean has = !stack.getOrDefault(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY).modifiers().isEmpty();
            if (charged && !has) {
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, this.protection);
            } else if (!charged && has) {
                stack.set(DataComponents.ATTRIBUTE_MODIFIERS, ItemAttributeModifiers.EMPTY);
            }
        }
        if (equipped != this.slot || !(entity instanceof ServerPlayer player)) return;
        if (this.kind.providesEnergy) chargeHotbar(stack, player);
        ArmorEffects.tickWorn(this, stack, level, player);
    }

    /** Batpack: alimenta os itens elétricos da hotbar e da mão secundária (IC2: canProvideEnergy). */
    private void chargeHotbar(ItemStack pack, ServerPlayer player) {
        for (int i = 0; i <= 9; i++) {
            ItemStack target = i == 9 ? player.getOffhandItem() : player.getInventory().getItem(i);
            if (target.isEmpty() || target == pack || !(target.getItem() instanceof EnergyItem item)
                    || target.getItem() instanceof ElectricArmorItem armor && armor.kind.providesEnergy) {
                continue;
            }
            long room = EnergyItems.charge(target, item.transferLimit(target), this.kind.voltage, true);
            if (room <= 0) continue;
            long moved = EnergyItems.discharge(pack, room, Integer.MAX_VALUE, false);
            if (moved > 0) EnergyItems.charge(target, moved, this.kind.voltage, false);
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        if (ArmorEffects.isJetpack(stack)) {
            tooltip.accept(ArmorEffects.jetpackModeText(ArmorEffects.hoverMode(stack)).withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.jetpack_mode_key", "Switch mode: %s",
                    Component.keybind("key.ic2reborn.jetpack_mode")).withStyle(ChatFormatting.DARK_GRAY));
        }
        if (this.slot == EquipmentSlot.HEAD && (this.kind == Kind.NANO || this.kind == Kind.QUANTUM || this.kind == Kind.NIGHTVISION_GOGGLES)) {
            tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.nightvision_key",
                    "Night vision: %s", Component.keybind("key.ic2reborn.nightvision")).withStyle(ChatFormatting.DARK_GRAY));
        }
    }
}
