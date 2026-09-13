package net.craftenergy.content;

import com.mojang.serialization.Codec;
import net.craftenergy.fabric.CraftEnergyApi;
import net.craftenergy.registry.DeferredRegister;
import net.craftenergy.registry.RegistryObject;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.codec.ByteBufCodecs;

/** Componentes de dados de item do Craft Energy. */
public final class CEComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, CraftEnergyApi.MOD_ID);

    /** Energia guardada num item (baterias, ferramentas elétricas), em CW·tick. */
    public static final RegistryObject<DataComponentType<Long>> STORED_ENERGY = COMPONENTS.register("stored_energy",
            () -> DataComponentType.<Long>builder()
                    .persistent(Codec.LONG)
                    .networkSynchronized(ByteBufCodecs.VAR_LONG)
                    .build());

    private CEComponents() {}
}
