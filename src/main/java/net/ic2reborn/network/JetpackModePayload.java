package net.ic2reborn.network;

import net.ic2reborn.IC2Reborn;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Tecla do jetpack apertada no cliente: alterna voo livre e modo estável no peitoral vestido. */
public record JetpackModePayload() implements CustomPacketPayload {
    public static final JetpackModePayload INSTANCE = new JetpackModePayload();
    public static final Type<JetpackModePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "jetpack_mode"));
    public static final StreamCodec<RegistryFriendlyByteBuf, JetpackModePayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
