package net.ic2reborn.fluid;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/** Fluido guardado no item de um tanque quebrado (em gotas do Fabric). */
public record StoredFluid(FluidVariant variant, long amount) {
    public static final Codec<StoredFluid> CODEC = RecordCodecBuilder.create(instance -> instance.group(
            FluidVariant.CODEC.fieldOf("variant").forGetter(StoredFluid::variant),
            Codec.LONG.fieldOf("amount").forGetter(StoredFluid::amount)
    ).apply(instance, StoredFluid::new));

    public static final StreamCodec<RegistryFriendlyByteBuf, StoredFluid> STREAM_CODEC = StreamCodec.composite(
            FluidVariant.PACKET_CODEC, StoredFluid::variant,
            ByteBufCodecs.VAR_LONG, StoredFluid::amount,
            StoredFluid::new);
}
