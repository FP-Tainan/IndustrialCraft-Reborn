package net.ic2reborn.registry;

import net.craftenergy.registry.DeferredRegister;
import net.craftenergy.registry.RegistryObject;
import net.ic2reborn.IC2Reborn;
import net.ic2reborn.entity.IC2Boat;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;

/** Entidades do IC2 Reborn. */
public final class IC2Entities {
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(Registries.ENTITY_TYPE, IC2Reborn.MODID);

    public static final RegistryObject<EntityType<IC2Boat>> RUBBER_BOAT = boat("rubber_boat", IC2Boat.Kind.RUBBER, false);
    public static final RegistryObject<EntityType<IC2Boat>> CARBON_BOAT = boat("carbon_boat", IC2Boat.Kind.CARBON, false);
    public static final RegistryObject<EntityType<IC2Boat>> ELECTRIC_BOAT = boat("electric_boat", IC2Boat.Kind.ELECTRIC, true);

    public static final RegistryObject<EntityType<net.ic2reborn.entity.DynamiteEntity>> DYNAMITE = ENTITY_TYPES.register("dynamite",
            () -> EntityType.Builder.<net.ic2reborn.entity.DynamiteEntity>of(net.ic2reborn.entity.DynamiteEntity::new, MobCategory.MISC)
                    .noLootTable()
                    .sized(0.25F, 0.25F)
                    .clientTrackingRange(4)
                    .build(ENTITY_TYPES.key("dynamite")));
    private IC2Entities() {}

    private static RegistryObject<EntityType<IC2Boat>> boat(String name, IC2Boat.Kind kind, boolean fireImmune) {
        return ENTITY_TYPES.register(name, () -> {
            EntityType.Builder<IC2Boat> builder = EntityType.Builder.<IC2Boat>of(
                            (type, level) -> new IC2Boat(type, level, kind, () -> net.minecraft.core.registries.BuiltInRegistries.ITEM
                                    .getValue(net.minecraft.resources.Identifier.fromNamespaceAndPath(IC2Reborn.MODID, name))),
                            MobCategory.MISC)
                    .noLootTable()
                    .sized(1.375F, 0.5625F)
                    .eyeHeight(0.5625F)
                    .clientTrackingRange(10);
            if (fireImmune) builder = builder.fireImmune();
            return builder.build(ENTITY_TYPES.key(name));
        });
    }
}
