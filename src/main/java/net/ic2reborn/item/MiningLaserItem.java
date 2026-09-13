package net.ic2reborn.item;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.ElectricItem;
import net.craftenergy.content.item.EnergyItems;
import net.ic2reborn.registry.IC2Components;
import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

/**
 * Laser de mineração do IC2 ({@code ItemToolMiningLaser} e {@code EntityMiningLaser}). Agachado +
 * botão direito troca o modo; botão direito atira. O feixe anda bloco a bloco: cada bloco quebrado
 * gasta força conforme a dureza, o ar gasta 0,5, vidro deixa passar e água apaga o feixe.
 */
public class MiningLaserItem extends ElectricItem {
    /** IC2: 300.000 EU, 512 EU/t, nível 3. */
    private static final long CAPACITY = EnergyUnits.fromCWh(150_000);

    public enum Mode {
        MINING(625, 5.0F, Integer.MAX_VALUE, false),
        LOW_FOCUS(50, 5.0F, 1, false),
        LONG_RANGE(2_500, 20.0F, Integer.MAX_VALUE, false),
        HORIZONTAL(1_500, 5.0F, Integer.MAX_VALUE, false),
        SUPER_HEAT(1_250, 8.0F, Integer.MAX_VALUE, false),
        SCATTER(5_000, 12.0F, Integer.MAX_VALUE, false),
        EXPLOSIVE(2_500, 12.0F, Integer.MAX_VALUE, true),
        THREE_BY_THREE(1_500, 5.0F, Integer.MAX_VALUE, false);

        /** CWh por disparo (IC2: 1250/100/5000/3000/2500/10000/5000/3000 EU). */
        final long energyCWh;
        final float power;
        final int blockBreaks;
        final boolean explosive;

        Mode(long energyCWh, float power, int blockBreaks, boolean explosive) {
            this.energyCWh = energyCWh;
            this.power = power;
            this.blockBreaks = blockBreaks;
            this.explosive = explosive;
        }

        String key() {
            return name().toLowerCase(Locale.ROOT);
        }
    }

    public MiningLaserItem(Properties properties) {
        super(properties, CAPACITY, 256_000, 2_400);
    }

    public static Mode mode(ItemStack stack) {
        int index = stack.getOrDefault(IC2Components.LASER_MODE.get(), 0);
        return Mode.values()[Math.floorMod(index, Mode.values().length)];
    }

    private static Component modeName(Mode mode) {
        return Component.translatableWithFallback("message.ic2reborn.laser.mode." + mode.key(), mode.name());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (!(level instanceof ServerLevel serverLevel) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        if (player.isShiftKeyDown()) {
            Mode next = Mode.values()[(mode(stack).ordinal() + 1) % Mode.values().length];
            stack.set(IC2Components.LASER_MODE.get(), next.ordinal());
            serverPlayer.sendSystemMessage(Component.translatableWithFallback("message.ic2reborn.tool.mode", "Mode: %s", modeName(next)));
            return InteractionResult.SUCCESS;
        }
        Mode mode = mode(stack);
        // horizontal e 3x3 atiram mirando um bloco (useOn)
        if (mode == Mode.HORIZONTAL || mode == Mode.THREE_BY_THREE) return InteractionResult.PASS;
        if (!EnergyItems.use(stack, EnergyUnits.fromCWh(mode.energyCWh))) return InteractionResult.FAIL;

        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        switch (mode) {
            case LOW_FOCUS -> shoot(serverLevel, player, eye, look, 4.0F, mode.power, 1, false, false);
            case LONG_RANGE -> shoot(serverLevel, player, eye, look, Float.POSITIVE_INFINITY, mode.power, mode.blockBreaks, false, false);
            case SUPER_HEAT -> shoot(serverLevel, player, eye, look, Float.POSITIVE_INFINITY, mode.power, mode.blockBreaks, false, true);
            case EXPLOSIVE -> shoot(serverLevel, player, eye, look, Float.POSITIVE_INFINITY, mode.power, mode.blockBreaks, true, false);
            case SCATTER -> {
                Vec3 right = look.cross(new Vec3(0, 1, 0));
                right = right.lengthSqr() < 1.0E-4 ? Vec3.directionFromRotation(0, player.getYRot() - 90.0F) : right.normalize();
                Vec3 up = right.cross(look);
                for (int r = -2; r <= 2; r++) {
                    for (int u = -2; u <= 2; u++) {
                        Vec3 dir = look.scale(8.0).add(right.scale(r)).add(up.scale(u)).normalize();
                        shoot(serverLevel, player, eye, dir, Float.POSITIVE_INFINITY, mode.power, Integer.MAX_VALUE, false, false);
                    }
                }
            }
            default -> shoot(serverLevel, player, eye, look, Float.POSITIVE_INFINITY, mode.power, mode.blockBreaks, false, false);
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.8F);
        return InteractionResult.SUCCESS;
    }

    /** Modos horizontal e 3×3: atira reto (sem subir/descer) a partir do bloco mirado. */
    @Override
    public InteractionResult useOn(UseOnContext context) {
        Player player = context.getPlayer();
        ItemStack stack = context.getItemInHand();
        Mode mode = mode(stack);
        if (player == null || player.isShiftKeyDown() || (mode != Mode.HORIZONTAL && mode != Mode.THREE_BY_THREE)) {
            return InteractionResult.PASS;
        }
        if (!(context.getLevel() instanceof ServerLevel level) || !(player instanceof ServerPlayer serverPlayer)) return InteractionResult.SUCCESS;
        BlockPos pos = context.getClickedPos();
        Vec3 look = player.getLookAngle();
        boolean steep = Math.abs(look.y) >= 1.0 / Math.sqrt(2.0);
        if (steep && mode == Mode.HORIZONTAL) {
            serverPlayer.sendSystemMessage(Component.translatableWithFallback("message.ic2reborn.laser.too_steep", "Mining laser aiming angle too steep"));
            return InteractionResult.FAIL;
        }
        if (!EnergyItems.use(stack, EnergyUnits.fromCWh(mode.energyCWh))) return InteractionResult.FAIL;

        Vec3 dir;
        Vec3 start;
        if (!steep) {
            dir = new Vec3(look.x, 0.0, look.z).normalize();
            start = new Vec3(player.getX(), pos.getY() + 0.5, player.getZ()).add(dir.scale(0.2));
        } else {
            dir = new Vec3(0.0, Math.signum(look.y), 0.0);
            start = new Vec3(pos.getX() + 0.5, player.getEyeY(), pos.getZ() + 0.5).add(dir.scale(0.2));
        }
        shoot(level, player, start, dir, Float.POSITIVE_INFINITY, mode.power, Integer.MAX_VALUE, false, false);
        if (mode == Mode.THREE_BY_THREE) {
            Vec3 side = steep ? new Vec3(1, 0, 0) : new Vec3(-dir.z, 0, dir.x);
            Vec3 other = steep ? new Vec3(0, 0, 1) : new Vec3(0, 1, 0);
            for (int a = -1; a <= 1; a++) {
                for (int b = -1; b <= 1; b++) {
                    if (a == 0 && b == 0) continue;
                    shoot(level, player, start.add(side.scale(a)).add(other.scale(b)), dir, Float.POSITIVE_INFINITY, mode.power,
                            Integer.MAX_VALUE, false, false);
                }
            }
        }
        level.playSound(null, player.blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.6F, 1.8F);
        return InteractionResult.SUCCESS;
    }

    /** Anda o feixe até acabar força, alcance ou quebras (IC2: EntityMiningLaser.onUpdate). */
    public static void shoot(ServerLevel level, LivingEntity owner, Vec3 start, Vec3 dir, float range, float power, int blockBreaks,
                             boolean explosive, boolean smelt) {
        Vec3 position = start.add(dir.scale(0.2));
        Vec3 step = dir.normalize();
        DustParticleOptions particle = new DustParticleOptions(0xFF2020, 1.0F);
        for (int ticks = 0; ticks < 512 && range >= 1.0F && power > 0.0F && blockBreaks > 0; ticks++) {
            Vec3 next = position.add(step);
            BlockHitResult blockHit = level.clip(new ClipContext(position, next, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, owner));
            Entity entityHit = null;
            if (ticks >= 1) {
                List<Entity> entities = level.getEntities(owner, new AABB(position, next).inflate(0.4), Entity::isPickable);
                double best = Double.MAX_VALUE;
                for (Entity entity : entities) {
                    double distance = entity.distanceToSqr(position);
                    if (distance < best) {
                        best = distance;
                        entityHit = entity;
                    }
                }
            }
            level.sendParticles(particle, position.x, position.y, position.z, 1, 0.0, 0.0, 0.0, 0.0);

            if (entityHit != null || blockHit.getType() != HitResult.Type.MISS) {
                Vec3 at = entityHit != null ? entityHit.position() : blockHit.getLocation();
                if (explosive) {
                    level.explode(owner, at.x, at.y, at.z, 5.0F, Level.ExplosionInteraction.TNT);
                    return;
                }
                if (entityHit != null) {
                    int damage = (int) power;
                    if (damage > 0) {
                        entityHit.igniteForSeconds(damage * (smelt ? 2 : 1));
                        entityHit.hurtServer(level, level.damageSources().mobProjectile(owner, owner), damage);
                    }
                    return;
                }
                BlockPos pos = blockHit.getBlockPos();
                BlockState state = level.getBlockState(pos);
                if (state.isAir() || state.is(Blocks.GLASS) || state.is(Blocks.GLASS_PANE)
                        || net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(state.getBlock()).getPath().equals("reinforced_glass")) {
                    power -= 0.5F;
                } else {
                    float hardness = state.getDestroySpeed(level, pos);
                    if (hardness < 0.0F) return;
                    power -= hardness / 1.5F;
                    if (power < 0.0F) return;
                    if (owner instanceof ServerPlayer player && !player.mayInteract(level, pos)) return;
                    LaserDrops.breakBlock(level, pos, state, owner, smelt);
                    blockBreaks--;
                }
            } else {
                power -= 0.5F;
            }
            position = next;
            range -= 1.0F;
            if (!level.getFluidState(BlockPos.containing(position)).isEmpty()) return;
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, context, display, tooltip, flag);
        tooltip.accept(Component.translatableWithFallback("message.ic2reborn.tool.mode", "Mode: %s", modeName(mode(stack)))
                .withStyle(ChatFormatting.GRAY));
        tooltip.accept(Component.translatableWithFallback("tooltip.ic2reborn.laser", "Sneak + right-click: change mode")
                .withStyle(ChatFormatting.DARK_GRAY));
    }
}
