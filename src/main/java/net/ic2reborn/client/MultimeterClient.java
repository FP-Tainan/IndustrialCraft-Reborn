package net.ic2reborn.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderContext;
import net.fabricmc.fabric.api.client.rendering.v1.level.LevelRenderEvents;
import net.ic2reborn.item.MultimeterItem;
import net.ic2reborn.network.MultimeterReadingPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

/** Mostra a leitura do multímetro flutuando sobre o bloco medido (como um visor de multímetro). */
public final class MultimeterClient {
    private static final int FULL_BRIGHT = 0xF000F0;
    private static final int LINE_HEIGHT = 10;
    private static MultimeterReadingPayload reading = MultimeterReadingPayload.empty();
    private static long receivedAt;

    private MultimeterClient() {}

    public static void init() {
        ClientPlayNetworking.registerGlobalReceiver(MultimeterReadingPayload.TYPE, (payload, context) -> {
            reading = payload;
            receivedAt = System.currentTimeMillis();
        });
        LevelRenderEvents.COLLECT_SUBMITS.register(MultimeterClient::render);
    }

    private static void render(LevelRenderContext context) {
        Minecraft minecraft = Minecraft.getInstance();
        LocalPlayer player = minecraft.player;
        MultimeterReadingPayload current = reading;
        if (player == null || current.values().isEmpty() || System.currentTimeMillis() - receivedAt > 1_000) return;
        if (!(player.getMainHandItem().getItem() instanceof MultimeterItem) && !(player.getOffhandItem().getItem() instanceof MultimeterItem)) return;

        CameraRenderState camera = context.levelState().cameraRenderState;
        BlockPos pos = current.pos();
        PoseStack pose = context.poseStack();
        pose.pushPose();
        // logo acima do bloco, para dar para ler de perto
        pose.translate(pos.getX() + 0.5 - camera.pos.x, pos.getY() + 0.7 - camera.pos.y, pos.getZ() + 0.5 - camera.pos.z);
        int lines = current.values().size();
        for (int i = 0; i < lines; i++) {
            double value = current.values().get(i);
            String unit = current.units().get(i);
            Component text = Component.literal(format(value) + " " + unit);
            if (unit.equals(MultimeterItem.CABLE_TEMPERATURE)) text = text.copy().withStyle(temperatureColor(value));
            int offset = (lines - 1 - i) * -LINE_HEIGHT;
            context.submitNodeCollector().submitNameTag(pose, Vec3.ZERO, offset, text, true, FULL_BRIGHT, camera);
        }
        pose.popPose();
    }

    /** Verde frio, amarelo morno, laranja quente, vermelho perto de queimar. */
    private static net.minecraft.ChatFormatting temperatureColor(double temperature) {
        double heat = (temperature - MultimeterItem.CABLE_AMBIENT) / (MultimeterItem.CABLE_BURN - MultimeterItem.CABLE_AMBIENT);
        if (heat < 0.1) return net.minecraft.ChatFormatting.GREEN;
        if (heat < 0.4) return net.minecraft.ChatFormatting.YELLOW;
        if (heat < 0.7) return net.minecraft.ChatFormatting.GOLD;
        return net.minecraft.ChatFormatting.RED;
    }

    /** Duas casas decimais, com vírgula em português, e prefixo k/M para valores grandes. */
    private static String format(double value) {
        String prefix = "";
        double shown = value;
        if (Math.abs(shown) >= 1_000_000) {
            shown /= 1_000_000;
            prefix = "M";
        } else if (Math.abs(shown) >= 10_000) {
            shown /= 1_000;
            prefix = "k";
        }
        String language = Minecraft.getInstance().getLanguageManager().getSelected();
        Locale locale = language.startsWith("pt") ? Locale.forLanguageTag("pt-BR") : Locale.ROOT;
        return String.format(locale, "%.2f", shown) + prefix;
    }
}
