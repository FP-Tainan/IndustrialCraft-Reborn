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

    /** Biogás (mB) no jetpack a combustível. */
    public static final RegistryObject<DataComponentType<Integer>> FUEL = COMPONENTS.register("fuel",
            () -> DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                    .build());


    /** Modo do laser de mineração. */
    public static final RegistryObject<DataComponentType<Integer>> LASER_MODE = COMPONENTS.register("laser_mode",
            () -> DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                    .build());

    /** Dinamites vinculadas ao controle remoto. */
    public static final RegistryObject<DataComponentType<java.util.List<net.minecraft.core.BlockPos>>> REMOTE_LINKS = COMPONENTS.register("remote_links",
            () -> DataComponentType.<java.util.List<net.minecraft.core.BlockPos>>builder()
                    .persistent(net.minecraft.core.BlockPos.CODEC.listOf())
                    .networkSynchronized(net.minecraft.core.BlockPos.STREAM_CODEC.apply(net.minecraft.network.codec.ByteBufCodecs.list()))
                    .build());

    /** Teletransportador guardado no transmissor de frequência. */
    public static final RegistryObject<DataComponentType<net.minecraft.core.BlockPos>> TELEPORT_TARGET = COMPONENTS.register("teleport_target",
            () -> DataComponentType.<net.minecraft.core.BlockPos>builder()
                    .persistent(net.minecraft.core.BlockPos.CODEC)
                    .networkSynchronized(net.minecraft.core.BlockPos.STREAM_CODEC)
                    .build());

    /** Jetpack no modo estável (mantém a altura). */
    public static final RegistryObject<DataComponentType<net.minecraft.util.Unit>> JETPACK_HOVER = COMPONENTS.register("jetpack_hover",
            () -> DataComponentType.<net.minecraft.util.Unit>builder()
                    .persistent(net.minecraft.util.Unit.CODEC)
                    .networkSynchronized(net.minecraft.network.codec.StreamCodec.unit(net.minecraft.util.Unit.INSTANCE))
                    .build());

    private IC2Components() {}
}
