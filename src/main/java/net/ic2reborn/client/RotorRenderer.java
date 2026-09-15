package net.ic2reborn.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.ic2reborn.IC2Reborn;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.item.RotorItem;
import net.fabricmc.fabric.api.transfer.v1.client.fluid.FluidVariantRendering;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.block.FluidModel;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Rotor dos geradores cinéticos eólico e de água (IC2: {@code KineticGeneratorRenderer}): quatro pás
 * na frente do gerador, do tamanho do diâmetro do rotor, girando conforme o torque produzido.
 */
public class RotorRenderer implements BlockEntityRenderer<MachineBlockEntity, RotorRenderer.State> {
    private static final float BLADE_WIDTH = 0.5F;
    private static final float BLADE_THICKNESS = 1.0F / 16.0F;
    private static final float HUB = 0.1F;

    public static final class State extends BlockEntityRenderState {
        @Nullable Identifier texture;
        float bladeLength;
        Direction facing = Direction.NORTH;
        float angle;
        /** Transformador molecular: desenhado pelo {@link MolecularTransformerModel}. */
        boolean molecular;
        /** Tanques: fluido e nível (0 a 1). */
        FluidVariant tankFluid = FluidVariant.blank();
        float tankLevel;
    }

    private static final float TANK_MIN = 2.05F / 16.0F;
    private static final float TANK_MAX = 13.95F / 16.0F;

    public RotorRenderer(BlockEntityRendererProvider.Context context) {
    }

    @Override
    public State createRenderState() {
        return new State();
    }

    @Override
    public void extractRenderState(MachineBlockEntity machine, State state, float partialTick, Vec3 cameraPos,
                                   @Nullable ModelFeatureRenderer.CrumblingOverlay crumbling) {
        BlockEntityRenderState.extractBase(machine, state, crumbling);
        state.molecular = machine.getGuiType() == net.ic2reborn.menu.MachineGuiType.MOLECULAR_TRANSFORMER;
        if (state.molecular) {
            state.facing = machine.facingDirection();
            return;
        }
        state.tankFluid = machine.displayedTankFluid();
        state.tankLevel = machine.displayedTankLevel();
        RotorItem rotor = machine.displayedRotor();
        Level level = machine.getLevel();
        if (rotor == null || level == null) {
            state.texture = null;
            return;
        }
        String path = BuiltInRegistries.ITEM.getKey(rotor).getPath();
        state.texture = Identifier.fromNamespaceAndPath(IC2Reborn.MODID, "textures/entity/rotor/" + path.replace("rotor_", "") + ".png");
        state.bladeLength = rotor.diameter() / 2.0F;
        state.facing = machine.facingDirection();
        int output = machine.displayedKineticOutput();
        float degreesPerTick = output <= 0 ? 0.0F : Math.min(24.0F, 2.0F + output / 8.0F);
        state.angle = ((level.getGameTime() % 36_000) + partialTick) * degreesPerTick % 360.0F;
        BlockPos front = machine.getBlockPos().relative(state.facing);
        state.lightCoords = level.getBrightness(LightLayer.BLOCK, front) << 4 | level.getBrightness(LightLayer.SKY, front) << 20;
    }

    @Override
    public void submit(State state, PoseStack pose, SubmitNodeCollector collector, CameraRenderState camera) {
        if (state.molecular) {
            MolecularTransformerModel.submit(pose, collector, state.facing, state.lightCoords);
            return;
        }
        if (!state.tankFluid.isBlank() && state.tankLevel > 0.001F) {
            submitTankFluid(state, collector, pose);
            return;
        }
        if (state.texture == null) return;
        pose.pushPose();
        pose.translate(0.5F, 0.5F, 0.5F);
        pose.mulPose(Axis.YP.rotationDegrees(-state.facing.toYRot()));
        pose.translate(0.0F, 0.0F, 0.5F + 0.02F);
        pose.mulPose(Axis.ZP.rotationDegrees(state.angle));
        for (int blade = 0; blade < 4; blade++) {
            pose.pushPose();
            pose.mulPose(Axis.ZP.rotationDegrees(blade * 90.0F));
            pose.mulPose(Axis.YP.rotationDegrees(25.0F));
            collector.submitCustomGeometry(pose, RenderTypes.entityCutout(state.texture),
                    (matrix, consumer) -> blade(matrix, consumer, state.bladeLength, state.lightCoords));
            pose.popPose();
        }
        pose.popPose();
    }

    /** Fluido dentro dos tanques, como no tanque do BuildCraft. */
    private static void submitTankFluid(State state, SubmitNodeCollector collector, PoseStack pose) {
        FluidModel model = Minecraft.getInstance().getModelManager().getFluidStateModelSet().get(state.tankFluid.getFluid().defaultFluidState());
        TextureAtlasSprite still = model.stillMaterial().sprite();
        int color = 0xFF000000 | FluidVariantRendering.getColor(state.tankFluid);
        float top = Math.max(0.02F, state.tankLevel) * (15.9F / 16.0F);
        int light = state.lightCoords;
        collector.submitCustomGeometry(pose, RenderTypes.entityTranslucent(TextureAtlas.LOCATION_BLOCKS),
                (matrix, consumer) -> fluidBox(matrix, consumer, still, color, light, top));
    }

    private static void fluidBox(PoseStack.Pose m, VertexConsumer c, TextureAtlasSprite sprite, int color, int light, float y1) {
        float x0 = TANK_MIN, z0 = TANK_MIN, x1 = TANK_MAX, z1 = TANK_MAX, y0 = 0.001F;
        float u0 = sprite.getU0(), u1 = sprite.getU1(), v0 = sprite.getV0(), v1 = sprite.getV1();
        float vSide = v0 + (v1 - v0) * (y1 - y0);
        fluidQuad(m, c, color, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, u0, v0, u1, v1);
        fluidQuad(m, c, color, light, 0, -1, 0, x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1, u0, v0, u1, v1);
        fluidQuad(m, c, color, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, u0, vSide, u1, v0);
        fluidQuad(m, c, color, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, u0, vSide, u1, v0);
        fluidQuad(m, c, color, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, u0, vSide, u1, v0);
        fluidQuad(m, c, color, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, u0, vSide, u1, v0);
    }

    /** a→b→c→d; UV: a = (u0,v0), c = (u1,v1). */
    private static void fluidQuad(PoseStack.Pose m, VertexConsumer c, int color, int light, float nx, float ny, float nz,
                                  float ax, float ay, float az, float bx, float by, float bz,
                                  float cx, float cy, float cz, float dx, float dy, float dz,
                                  float u0, float v0, float u1, float v1) {
        fluidVertex(m, c, color, light, nx, ny, nz, ax, ay, az, u0, v0);
        fluidVertex(m, c, color, light, nx, ny, nz, bx, by, bz, u1, v0);
        fluidVertex(m, c, color, light, nx, ny, nz, cx, cy, cz, u1, v1);
        fluidVertex(m, c, color, light, nx, ny, nz, dx, dy, dz, u0, v1);
    }

    private static void fluidVertex(PoseStack.Pose m, VertexConsumer c, int color, int light, float nx, float ny, float nz,
                                    float x, float y, float z, float u, float v) {
        c.addVertex(m, x, y, z).setColor(color).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light).setNormal(m, nx, ny, nz);
    }

    /** Uma pá: caixa fina do cubo até a ponta, com a textura do modelo do rotor do IC2 (32×256). */
    private static void blade(PoseStack.Pose matrix, VertexConsumer consumer, float length, int light) {
        float x0 = -BLADE_WIDTH / 2, x1 = BLADE_WIDTH / 2;
        float y0 = HUB, y1 = HUB + length;
        float z0 = 0.0F, z1 = BLADE_THICKNESS;
        float u1 = 8.0F / 32.0F;
        float v1 = Math.min(1.0F, length * 16.0F / 256.0F);
        // frente e costas
        quad(matrix, consumer, light, 0, 0, 1, x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1, 0, v1, u1, v1, u1, 0, 0, 0);
        quad(matrix, consumer, light, 0, 0, -1, x1, y0, z0, x0, y0, z0, x0, y1, z0, x1, y1, z0, 0, v1, u1, v1, u1, 0, 0, 0);
        // bordas
        float edge = 1.0F / 32.0F;
        quad(matrix, consumer, light, 1, 0, 0, x1, y0, z1, x1, y0, z0, x1, y1, z0, x1, y1, z1, 0, v1, edge, v1, edge, 0, 0, 0);
        quad(matrix, consumer, light, -1, 0, 0, x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0, 0, v1, edge, v1, edge, 0, 0, 0);
        quad(matrix, consumer, light, 0, 1, 0, x0, y1, z1, x1, y1, z1, x1, y1, z0, x0, y1, z0, 0, edge, u1, edge, u1, 0, 0, 0);
    }

    private static void quad(PoseStack.Pose matrix, VertexConsumer consumer, int light, float nx, float ny, float nz,
                             float ax, float ay, float az, float bx, float by, float bz,
                             float cx, float cy, float cz, float dx, float dy, float dz,
                             float au, float av, float bu, float bv, float cu, float cv, float du, float dv) {
        vertex(matrix, consumer, light, nx, ny, nz, ax, ay, az, au, av);
        vertex(matrix, consumer, light, nx, ny, nz, bx, by, bz, bu, bv);
        vertex(matrix, consumer, light, nx, ny, nz, cx, cy, cz, cu, cv);
        vertex(matrix, consumer, light, nx, ny, nz, dx, dy, dz, du, dv);
    }

    private static void vertex(PoseStack.Pose matrix, VertexConsumer consumer, int light, float nx, float ny, float nz,
                               float x, float y, float z, float u, float v) {
        consumer.addVertex(matrix, x, y, z).setColor(-1).setUv(u, v).setOverlay(OverlayTexture.NO_OVERLAY).setLight(light)
                .setNormal(matrix, nx, ny, nz);
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    @Override
    public int getViewDistance() {
        return 96;
    }
}
