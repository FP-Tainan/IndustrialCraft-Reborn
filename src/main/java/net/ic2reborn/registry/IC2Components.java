package net.ic2reborn.registry;

import net.craftenergy.registry.DeferredRegister;
import net.craftenergy.registry.RegistryObject;
import net.ic2reborn.IC2Reborn;
import net.ic2reborn.item.CropSeedItem;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;

/** Componentes de item do IC2 Reborn. */
public final class IC2Components {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, IC2Reborn.MODID);

    /** Planta e atributos do saco de sementes. */
    public static final RegistryObject<DataComponentType<CropSeedItem.CropSeed>> CROP_SEED = COMPONENTS.register("crop_seed",
            () -> DataComponentType.<CropSeedItem.CropSeed>builder()
                    .persistent(CropSeedItem.CropSeed.CODEC)
                    .networkSynchronized(CropSeedItem.CropSeed.STREAM_CODEC)
                    .build());

    private IC2Components() {}
}
