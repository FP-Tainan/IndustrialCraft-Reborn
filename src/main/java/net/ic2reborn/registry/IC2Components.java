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

    /** Ferramenta ligada (nanossabre); a presença do componente muda o modelo do item. */
    public static final RegistryObject<DataComponentType<net.minecraft.util.Unit>> ACTIVE = COMPONENTS.register("active",
            () -> DataComponentType.<net.minecraft.util.Unit>builder()
                    .persistent(net.minecraft.util.Unit.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE))
                    .build());

    /** Motosserra com a tosquia desligada. */
    public static final RegistryObject<DataComponentType<net.minecraft.util.Unit>> NO_SHEAR = COMPONENTS.register("no_shear",
            () -> DataComponentType.<net.minecraft.util.Unit>builder()
                    .persistent(net.minecraft.util.Unit.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE))
                    .build());

    private IC2Components() {}
}
