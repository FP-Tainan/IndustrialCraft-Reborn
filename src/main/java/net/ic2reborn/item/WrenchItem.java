package net.ic2reborn.item;

import net.craftenergy.fabric.CraftEnergyApi;
import net.ic2reborn.block.MachineBlock;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

/**
 * Chave inglesa do IC2.
 *
 * <p><b>Chave clássica</b> ({@code wrench}): clicar numa face vira a frente da máquina para ela;
 * agachado, vira para o lado oposto. Se a máquina já está virada para lá (ou não pode virar
 * para essa direção), a chave desmonta a máquina. Girar gasta 1 de durabilidade, desmontar 10.
 *
 * <p><b>Chave nova</b> ({@code wrench_new}): a direção depende de onde a face é clicada —
 * centro = a própria face, faixa de uma borda = a direção dessa borda, canto = o lado oposto.
 * Ela só gira, nunca desmonta.
 *
 * <p>Máquinas do IC2 Reborn giram só na horizontal por enquanto.
 */
public class WrenchItem extends Item {
    public static final int ROTATE_DAMAGE = 1;
    public static final int REMOVE_DAMAGE = 10;

    private final boolean rotateByHit;

    public WrenchItem(Properties properties, boolean rotateByHit) {
        super(properties);
        this.rotateByHit = rotateByHit;
    }

    @Override
    public InteractionResult useOn(UseOnContext context) {
        Level level = context.getLevel();
        BlockPos pos = context.getClickedPos();
        BlockState state = level.getBlockState(pos);
        if (!(state.getBlock() instanceof MachineBlock)) return InteractionResult.PASS;

        ItemStack stack = context.getItemInHand();
        Player player = context.getPlayer();
        Direction face = context.getClickedFace();
        Direction current = state.getValue(MachineBlock.FACING);

        Direction target;
        if (this.rotateByHit) {
            Vec3 hit = context.getClickLocation().subtract(pos.getX(), pos.getY(), pos.getZ());
            target = rotateByHit(face, hit.x, hit.y, hit.z);
        } else {
            target = context.isSecondaryUseActive() ? face.getOpposite() : face;
        }
        boolean canRotate = target.getAxis().isHorizontal() && target != current;
        boolean canRemove = !this.rotateByHit && remainingDurability(stack) >= REMOVE_DAMAGE;

        if (!canRotate && !canRemove) return InteractionResult.FAIL;
        if (level.isClientSide()) return InteractionResult.SUCCESS;

        if (canRotate) {
            level.setBlock(pos, state.setValue(MachineBlock.FACING, target), Block.UPDATE_ALL);
            // a saída do armazenamento e o lado de alta do transformador mudaram de face
            CraftEnergyApi.markChanged(level, pos);
            damage(stack, player, context, ROTATE_DAMAGE);
        } else {
            level.destroyBlock(pos, true, player);
            damage(stack, player, context, REMOVE_DAMAGE);
        }
        level.playSound(null, pos, SoundEvents.IRON_TRAPDOOR_CLOSE, SoundSource.BLOCKS, 0.6F, 1.6F);
        return InteractionResult.SUCCESS;
    }

    private static int remainingDurability(ItemStack stack) {
        return stack.isDamageableItem() ? stack.getMaxDamage() - stack.getDamageValue() : Integer.MAX_VALUE;
    }

    private static void damage(ItemStack stack, Player player, UseOnContext context, int amount) {
        if (player != null) {
            stack.hurtAndBreak(amount, player, context.getHand());
        }
    }

    /**
     * Grade de 3×3 da chave nova ({@code RotationUtil.rotateByHit} do IC2). {@code x/y/z} é o ponto
     * clicado relativo ao bloco (0 a 1).
     */
    public static Direction rotateByHit(Direction face, double x, double y, double z) {
        double u;
        double v;
        Direction uLowDir;
        Direction uHighDir;
        Direction vLowDir;
        Direction vHighDir;
        switch (face.getAxis()) {
            case Y -> {
                u = x; v = z;
                uLowDir = Direction.WEST; uHighDir = Direction.EAST;
                vLowDir = Direction.NORTH; vHighDir = Direction.SOUTH;
            }
            case Z -> {
                u = x; v = y;
                uLowDir = Direction.WEST; uHighDir = Direction.EAST;
                vLowDir = Direction.DOWN; vHighDir = Direction.UP;
            }
            default -> {
                u = z; v = y;
                uLowDir = Direction.NORTH; uHighDir = Direction.SOUTH;
                vLowDir = Direction.DOWN; vHighDir = Direction.UP;
            }
        }

        boolean uLow = u <= 0.25;
        boolean uHigh = u >= 0.75;
        boolean vLow = v <= 0.25;
        boolean vHigh = v >= 0.75;
        boolean uEdge = uLow || uHigh;
        boolean vEdge = vLow || vHigh;

        if (!uEdge && !vEdge) return face;
        if (uEdge && vEdge) return face.getOpposite();
        if (uLow) return uLowDir;
        if (uHigh) return uHighDir;
        return vLow ? vLowDir : vHighDir;
    }
}
