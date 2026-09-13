package net.ic2reborn.entity;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.EnergyItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.vehicle.boat.Boat;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.function.Supplier;

/**
 * Barcos do IC2 ({@code EntityIC2Boat}): de borracha, de carbono e elétrico. O elétrico não pega
 * fogo e, com o piloto indo para a frente, acelera gastando 4 EU/t da armadura/mochila vestida.
 */
public class IC2Boat extends Boat {
    public enum Kind { RUBBER, CARBON, ELECTRIC }

    /** IC2: 4 EU por tick de aceleração. */
    private static final long BOOST_ENERGY = EnergyUnits.fromCWh(2);
    private static final double TOP_SPEED = 0.7;

    private final Kind kind;

    public IC2Boat(EntityType<? extends Boat> type, Level level, Kind kind, Supplier<Item> drop) {
        super(type, level, drop);
        this.kind = kind;
    }

    public Kind kind() {
        return this.kind;
    }

    @Override
    public void tick() {
        if (this.kind == Kind.ELECTRIC) {
            clearFire();
            for (Entity passenger : getPassengers()) {
                passenger.clearFire();
            }
            if (getControllingPassenger() instanceof Player driver && driver.zza > 0 && boost(driver, !level().isClientSide())) {
                Vec3 motion = getDeltaMovement();
                Vec3 forward = Vec3.directionFromRotation(0.0F, getYRot());
                Vec3 boosted = motion.add(forward.x * 0.04, 0.0, forward.z * 0.04);
                double horizontal = Math.hypot(boosted.x, boosted.z);
                if (horizontal > TOP_SPEED) {
                    boosted = new Vec3(boosted.x / horizontal * TOP_SPEED, boosted.y, boosted.z / horizontal * TOP_SPEED);
                }
                setDeltaMovement(boosted);
            }
        }
        super.tick();
    }

    /** Tira a energia da primeira peça vestida que tenha carga (no cliente só confere). */
    private static boolean boost(Player driver, boolean drain) {
        for (EquipmentSlot slot : new EquipmentSlot[]{EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET, EquipmentSlot.HEAD}) {
            ItemStack stack = driver.getItemBySlot(slot);
            if (EnergyItems.getStored(stack) >= BOOST_ENERGY) {
                if (drain) EnergyItems.use(stack, BOOST_ENERGY);
                return true;
            }
        }
        return false;
    }
}
