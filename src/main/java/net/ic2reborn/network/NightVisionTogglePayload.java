package net.ic2reborn.network;

import net.ic2reborn.IC2Reborn;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.Identifier;

/** Tecla de visão noturna apertada no cliente: liga/desliga a do capacete vestido. */
public record NightVisionTogglePayload() implements CustomPacketPayload {
    public static final NightVisionTogglePayload INSTANCE = new NightVisionTogglePayload();
    public static final Type<NightVisionTogglePayload> TYPE =
            new Type<>(Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "nightvision_toggle"));
    public static final StreamCodec<RegistryFriendlyByteBuf, NightVisionTogglePayload> CODEC = StreamCodec.unit(INSTANCE);

    @Override
    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }
}
