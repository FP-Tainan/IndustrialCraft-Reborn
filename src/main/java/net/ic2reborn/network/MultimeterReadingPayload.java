package net.ic2reborn.network;

import net.ic2reborn.IC2Reborn;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

import java.util.List;

/**
 * Leitura do multímetro enviada ao cliente: o bloco medido e as grandezas (valor + unidade), na
 * ordem em que aparecem. Sem linhas = nada para mostrar.
 */
public record MultimeterReadingPayload(BlockPos pos, List<Double> values, List<String> units) implements CustomPacketPayload {
    public static final Type<MultimeterReadingPayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "multimeter_reading"));
    public static final StreamCodec<RegistryFriendlyByteBuf, MultimeterReadingPayload> CODEC = StreamCodec.composite(
            BlockPos.STREAM_CODEC, MultimeterReadingPayload::pos,
            ByteBufCodecs.DOUBLE.apply(ByteBufCodecs.list(8)), MultimeterReadingPayload::values,
            ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list(8)), MultimeterReadingPayload::units,
            MultimeterReadingPayload::new);

    public static MultimeterReadingPayload empty() {
        return new MultimeterReadingPayload(BlockPos.ZERO, List.of(), List.of());
    }

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
