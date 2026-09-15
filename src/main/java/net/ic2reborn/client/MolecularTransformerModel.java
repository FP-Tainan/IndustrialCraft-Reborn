package net.ic2reborn.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.ic2reborn.IC2Reborn;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;

/**
 * Transformador molecular do Advanced Solar Panels ({@code PrettyMolecularTransformerModel}): base, núcleo,
 * tampa e três braços a 120°, com a textura translúcida de 128×64 no layout de caixas dos modelos antigos.
 */
final class MolecularTransformerModel {
    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "textures/block/molecular_transformer.png");
    private static final float TEXTURE_WIDTH = 128.0F;
    private static final float TEXTURE_HEIGHT = 64.0F;
    private static final float ARM = (float) (2.0 * Math.PI / 3.0);

    /** Caixa do ModelRenderer: textura (u, v), canto (x, y, z), tamanho (w, h, d) e giro em Y no pivô (0, 16, 0). */
    private record Box(int u, int v, float x, float y, float z, int w, int h, int d, float rotY) {}

    private static final Box[] BOXES = {
            new Box(0, 0, -5, 4, -5, 10, 3, 10, 0),              // coreBottom
            new Box(0, 44, -3, -4, -3, 6, 9, 6, 0),              // coreWorkZone
            new Box(25, 44, -2, -8, -1.466667F, 3, 2, 3, 0),     // coreTopElectr
            new Box(0, 30, -5, -7, -4.5F, 9, 3, 9, 0),           // coreTopPlate
            new Box(20, 16, 3, -8, -5, 4, 3, 10, 0),             // firstElTop
            new Box(49, 16, 4, 3, -3, 3, 5, 6, 0),               // firstElBottom
            new Box(20, 16, 3, -8, -5, 4, 3, 10, -ARM),          // secondElTop
            new Box(49, 16, 4, 3, -3, 3, 5, 6, -ARM),            // secondElBottom
            new Box(20, 16, 3, -8, -5, 4, 3, 10, ARM),           // thirdElTop
            new Box(49, 16, 4, 3, -3, 3, 5, 6, ARM),             // thirdElBottom
    };

    private MolecularTransformerModel() {}

    static void submit(PoseStack pose, SubmitNodeCollector collector, Direction facing, int light) {
        pose.pushPose();
        // TESR do addon: translate(0.5, 1.5, 0.5), gira 180° e escala 1/16
        pose.translate(0.5F, 1.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-facing.toYRot()));
        pose.mulPose(Axis.ZP.rotationDegrees(180.0F));
        pose.scale(1.0F / 16.0F, 1.0F / 16.0F, 1.0F / 16.0F);
        pose.translate(0.0F, 16.0F, 0.0F);
        for (Box box : BOXES) {
            pose.pushPose();
            if (box.rotY() != 0.0F) pose.mulPose(Axis.YP.rotation(box.rotY()));
            collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TEXTURE), (matrix, consumer) -> box(matrix, consumer, box, light));
            pose.popPose();
        }
        pose.popPose();
    }

    /** Seis faces no layout do ModelBox (espelhado, como no addon). */
    private static void box(PoseStack.Pose matrix, VertexConsumer consumer, Box box, int light) {
        float x0 = box.x(), y0 = box.y(), z0 = box.z();
        float x1 = x0 + box.w(), y1 = y0 + box.h(), z1 = z0 + box.d();
        int u = box.u(), v = box.v(), w = box.w(), h = box.h(), d = box.d();
        // laterais: a borda de cima da textura fica em y0 (o y do modelo cresce para baixo)
        face(matrix, consumer, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u + d, v + d, u + d + w, v + d + h);
        face(matrix, consumer, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u + 2 * d + w, v + d, u + 2 * d + 2 * w, v + d + h);
        face(matrix, consumer, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u + d + w, v + d, u + 2 * d + w, v + d + h);
        face(matrix, consumer, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u, v + d, u + d, v + d + h);
        face(matrix, consumer, light, 0, -1, 0, x1, y0, z1, x0, y0, z1, x0, y0, z0, x1, y0, z0, u + d, v, u + d + w, v + d);
        face(matrix, consumer, light, 0, 1, 0, x1, y1, z0, x0, y1, z0, x0, y1, z1, x1, y1, z1, u + d + w, v, u + d + 2 * w, v + d);
    }

    /** Quad com a textura de (u0, v0) a (u1, v1): a→b é a borda de cima, d→c a de baixo. */
    private static void face(PoseStack.Pose matrix, VertexConsumer consumer, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float u0, float v0, float u1, float v1) {
        u0 /= TEXTURE_WIDTH;
        u1 /= TEXTURE_WIDTH;
        v0 /= TEXTURE_HEIGHT;
        v1 /= TEXTURE_HEIGHT;
        vertex(matrix, consumer, light, nx, ny, nz, ax, ay, az, u1, v0);
        vertex(matrix, consumer, light, nx, ny, nz, bx, by, bz, u0, v0);
        vertex(matrix, consumer, light, nx, ny, nz, cx, cy, cz, u0, v1);
        vertex(matrix, consumer, light, nx, ny, nz, dx, dy, dz, u1, v1);
    }

    private static void vertex(PoseStack.Pose matrix, VertexConsumer consumer, int light, float nx, float ny, float nz,
                               float x, float y, float z, float u, float v) {
        consumer.addVertex(matrix, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(matrix, nx, ny, nz);
    }
}
