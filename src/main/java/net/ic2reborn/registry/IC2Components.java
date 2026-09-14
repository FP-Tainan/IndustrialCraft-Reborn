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

    /** Face escolhida para o ejetor/puxador (sem o componente: todos os lados). */
    public static final RegistryObject<DataComponentType<net.minecraft.core.Direction>> UPGRADE_DIRECTION = COMPONENTS.register("upgrade_direction",
            () -> DataComponentType.<net.minecraft.core.Direction>builder()
                    .persistent(net.minecraft.core.Direction.CODEC)
                    .networkSynchronized(net.minecraft.core.Direction.STREAM_CODEC)
                    .build());

    /** Fluido guardado no item do tanque quebrado. */
    public static final RegistryObject<DataComponentType<net.ic2reborn.fluid.StoredFluid>> STORED_FLUID = COMPONENTS.register("stored_fluid",
            () -> DataComponentType.<net.ic2reborn.fluid.StoredFluid>builder()
                    .persistent(net.ic2reborn.fluid.StoredFluid.CODEC)
                    .networkSynchronized(net.ic2reborn.fluid.StoredFluid.STREAM_CODEC)
                    .build());
    /** Desgaste (ou calor guardado) de um componente do reator. */
    public static final RegistryObject<DataComponentType<Integer>> REACTOR_DAMAGE = COMPONENTS.register("reactor_damage",
            () -> DataComponentType.<Integer>builder()
                    .persistent(com.mojang.serialization.Codec.INT)
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.VAR_INT)
                    .build());
    /** Molde gravado na memória de cristal. */
    public static final RegistryObject<DataComponentType<net.minecraft.world.item.Item>> PATTERN = COMPONENTS.register("pattern",
            () -> DataComponentType.<net.minecraft.world.item.Item>builder()
                    .persistent(net.minecraft.core.registries.BuiltInRegistries.ITEM.byNameCodec())
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.registry(net.minecraft.core.registries.Registries.ITEM))
                    .build());

    /** Moldes do armazenamento de moldes quebrado. */
    public static final RegistryObject<DataComponentType<java.util.List<net.minecraft.world.item.Item>>> PATTERNS = COMPONENTS.register("patterns",
            () -> DataComponentType.<java.util.List<net.minecraft.world.item.Item>>builder()
                    .persistent(net.minecraft.core.registries.BuiltInRegistries.ITEM.byNameCodec().listOf())
                    .networkSynchronized(net.minecraft.network.codec.ByteBufCodecs.registry(net.minecraft.core.registries.Registries.ITEM)
                            .apply(net.minecraft.network.codec.ByteBufCodecs.list()))
                    .build());
    private IC2Components() {}
}
