package net.ic2reborn.client;

import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.ic2reborn.block.MachineBlock;
import net.ic2reborn.item.ElectricArmorItem;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Cerca de ferro magnetizada do IC2 ({@code BlockIC2Fence}): encostado numa coluna de cercas em cima de
 * um magnetizador ligado, o jogador sobe (até 1,5 blocos/tick com botas de metal, 0,5 sem); agachado
 * desce devagar.
 */
public final class MagnetizerClient {
    private static final int RANGE = 20;

    private MagnetizerClient() {}

    public static void init() {
        ClientTickEvents.END_CLIENT_TICK.register(MagnetizerClient::tick);
    }

    private static void tick(Minecraft minecraft) {
        LocalPlayer player = minecraft.player;
        if (player == null || minecraft.level == null || player.getAbilities().flying) return;
        if (!touchesPoweredFence(minecraft.level, player.blockPosition())) return;

        Vec3 motion = player.getDeltaMovement();
        double y = motion.y;
        if (player.isShiftKeyDown()) {
            y = Math.max(y * 0.8, -0.15);
        } else {
            y += 0.075;
            if (y > 0) y *= 1.03;
            y = Math.min(y, hasMetalShoes(player) ? 1.5 : 0.5);
        }
        player.setDeltaMovement(motion.x, y, motion.z);
        player.resetFallDistance();
    }

    private static boolean touchesPoweredFence(Level level, BlockPos feet) {
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            for (int dy = 0; dy <= 1; dy++) {
                BlockPos start = feet.relative(direction).above(dy);
                if (isFence(level, start) && poweredColumn(level, start)) return true;
            }
        }
        return false;
    }

    private static boolean poweredColumn(Level level, BlockPos fence) {
        BlockPos pos = fence;
        for (int i = 0; i < RANGE && isFence(level, pos); i++) pos = pos.below();
        BlockState state = level.getBlockState(pos);
        return state.is(IC2AutoBlocks.MAGNETIZER.get()) && state.hasProperty(MachineBlock.ACTIVE) && state.getValue(MachineBlock.ACTIVE);
    }

    private static boolean isFence(Level level, BlockPos pos) {
        return level.getBlockState(pos).is(IC2AutoBlocks.IRON_FENCE.get());
    }

    private static boolean hasMetalShoes(LocalPlayer player) {
        ItemStack boots = player.getItemBySlot(EquipmentSlot.FEET);
        return boots.is(Items.IRON_BOOTS) || boots.is(Items.GOLDEN_BOOTS) || boots.is(Items.CHAINMAIL_BOOTS)
                || boots.is(Items.NETHERITE_BOOTS) || boots.getItem() instanceof ElectricArmorItem
                || net.minecraft.core.registries.BuiltInRegistries.ITEM.getKey(boots.getItem()).getPath().equals("bronze_boots");
    }
}
