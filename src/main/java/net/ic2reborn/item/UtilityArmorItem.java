package net.ic2reborn.item;

import net.craftenergy.content.item.EnergyItems;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Components;
import net.minecraft.ChatFormatting;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.equipment.Equippable;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.Locale;
import java.util.function.Consumer;

/**
 * Armaduras utilitárias do IC2 ({@code ItemArmorUtility} e derivadas): hazmat, botas de borracha,
 * botas estáticas, capacete solar, jetpack a biogás e CF pack (espuma de construção). Os efeitos
 * ficam em {@link ArmorEffects}.
 */
public class UtilityArmorItem extends Item {
    public enum Kind { HAZMAT, RUBBER_BOOTS, STATIC_BOOTS, SOLAR_HELMET, FUEL_JETPACK, CF_PACK }

    /** Jetpack a biogás: 30.000 mB (IC2: ItemArmorJetpack). */
    public static final int JETPACK_CAPACITY = 30_000;
    /** CF pack: 80.000 mB de espuma (IC2: ItemArmorCFPack). */
    public static final int CF_PACK_CAPACITY = 80_000;

    private final Kind kind;
    private final EquipmentSlot slot;

    public UtilityArmorItem(Properties properties, Kind kind, EquipmentSlot slot) {
        super(properties);
        this.kind = kind;
        this.slot = slot;
    }

    public Kind kind() {
        return this.kind;
    }

    /** Propriedades de uma peça vestível sem durabilidade nem proteção, com a textura {@code asset}. */
    public static Item.Properties equippable(Item.Properties properties, EquipmentSlot slot, String asset) {
        return properties.stacksTo(1).component(DataComponents.EQUIPPABLE, Equippable.builder(slot)
                .setEquipSound(SoundEvents.ARMOR_EQUIP_IRON)
                .setAsset(IC2ArmorMaterials.asset(asset))
                .build());
    }

    private boolean holdsFluid() {
        return this.kind == Kind.FUEL_JETPACK || this.kind == Kind.CF_PACK;
    }

    public static int capacity(ItemStack stack) {
        return stack.getItem() instanceof UtilityArmorItem item && item.kind == Kind.CF_PACK ? CF_PACK_CAPACITY : JETPACK_CAPACITY;
    }

    public static int fuel(ItemStack stack) {
        return stack.getOrDefault(IC2Components.FUEL.get(), 0);
    }

    public static void setFuel(ItemStack stack, int millibuckets) {
        stack.set(IC2Components.FUEL.get(), Math.max(0, Math.min(capacity(stack), millibuckets)));
    }

    static boolean isFoamCell(ItemStack stack) {
        return IC2Fluids.CONSTRUCTION_FOAM.cell() != null && stack.is(IC2Fluids.CONSTRUCTION_FOAM.cell().get());
    }

    /** Gasta a célula cheia e devolve a vazia. */
    static void useUpCell(Player player, ItemStack cell) {
        if (player.getAbilities().instabuild) return;
        cell.shrink(1);
        ItemStack empty = new ItemStack(IC2AutoItems.FLUID_CELL.get());
        if (!player.getInventory().add(empty)) player.drop(empty, false);
    }

    /** Jetpack: célula de biogás na outra mão; CF pack: célula de espuma. Cada célula = 1.000 mB. */
    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (holdsFluid()) {
            ItemStack pack = player.getItemInHand(hand);
            ItemStack cell = player.getItemInHand(hand == InteractionHand.MAIN_HAND ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND);
            boolean rightCell = this.kind == Kind.FUEL_JETPACK ? cell.is(IC2Fluids.BIOGAS.cell().get()) : isFoamCell(cell);
            if (rightCell && fuel(pack) + 1_000 <= capacity(pack)) {
                if (!level.isClientSide()) {
                    setFuel(pack, fuel(pack) + 1_000);
                    useUpCell(player, cell);
                }
                return InteractionResult.SUCCESS;
            }
        }
        return super.use(level, player, hand);
    }

    @Override
    public void inventoryTick(ItemStack stack, ServerLevel level, Entity entity, @Nullable EquipmentSlot equipped) {
        if (equipped == this.slot && entity instanceof ServerPlayer player) {
            ArmorEffects.tickUtility(this, stack, level, player);
        }
    }

    @Override
    public boolean isBarVisible(ItemStack stack) {
        return holdsFluid() || super.isBarVisible(stack);
    }

    @Override
    public int getBarWidth(ItemStack stack) {
        return holdsFluid() ? Math.round(13.0F * fuel(stack) / capacity(stack)) : super.getBarWidth(stack);
    }

    @Override
    public int getBarColor(ItemStack stack) {
        if (this.kind == Kind.FUEL_JETPACK) return 0x8FD14F;
        return this.kind == Kind.CF_PACK ? 0xC8C8C8 : super.getBarColor(stack);
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        if (this.kind == Kind.FUEL_JETPACK) {
            tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.jetpack_fuel", "Biogas: %s / %s mB",
                    fuel(stack), JETPACK_CAPACITY).withStyle(ChatFormatting.GRAY));
            tooltip.accept(ArmorEffects.jetpackModeText(ArmorEffects.hoverMode(stack)).withStyle(ChatFormatting.GRAY));
            tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.jetpack_mode_key", "Switch mode: %s",
                    Component.keybind("key.ic2reborn.jetpack_mode")).withStyle(ChatFormatting.DARK_GRAY));
        } else if (this.kind == Kind.CF_PACK) {
            tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.foam", "Construction foam: %s / %s mB",
                    fuel(stack), CF_PACK_CAPACITY).withStyle(ChatFormatting.GRAY));
        } else if (this.kind == Kind.SOLAR_HELMET || this.kind == Kind.STATIC_BOOTS) {
            tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn." + this.kind.name().toLowerCase(Locale.ROOT),
                    this.kind == Kind.SOLAR_HELMET ? "Charges the item worn on the chest in sunlight" : "Charges the item worn on the chest while walking")
                    .withStyle(ChatFormatting.GRAY));
        }
    }

    /** Usado pelo capacete solar e pelas botas estáticas: carrega o que está vestido no peito. */
    static void chargeChest(ServerPlayer player, long amount) {
        ItemStack chest = player.getItemBySlot(EquipmentSlot.CHEST);
        if (!chest.isEmpty()) EnergyItems.charge(chest, amount, Integer.MAX_VALUE, false);
    }
}
