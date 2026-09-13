package net.ic2reborn.entity;

import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Entities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.throwableitemprojectile.ThrowableItemProjectile;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

/**
 * Dinamite do IC2 ({@code EntityDynamite}): pavio de 5 s, quica no chão e explode. A grudenta
 * cola no bloco atingido e o pavio queima 4× mais rápido. Molhada, não explode.
 */
public class DynamiteEntity extends ThrowableItemProjectile {
    private int fuse = 100;
    private @Nullable BlockPos stuckTo;

    public DynamiteEntity(EntityType<? extends DynamiteEntity> type, Level level) {
        super(type, level);
    }

    public DynamiteEntity(Level level, LivingEntity owner, ItemStack stack) {
        super(IC2Entities.DYNAMITE.get(), owner, level, stack);
    }

    public DynamiteEntity(Level level, double x, double y, double z, ItemStack stack) {
        super(IC2Entities.DYNAMITE.get(), x, y, z, level, stack);
    }

    @Override
    protected Item getDefaultItem() {
        return IC2AutoItems.DYNAMITE.get();
    }

    public boolean isSticky() {
        return getItem().is(IC2AutoItems.DYNAMITE_STICKY.get());
    }

    public void setFuse(int fuse) {
        this.fuse = fuse;
    }

    public int fuse() {
        return this.fuse;
    }

    @Override
    public void tick() {
        if (this.stuckTo != null) {
            if (level().getBlockState(this.stuckTo).isAir()) {
                this.stuckTo = null;
                setNoGravity(false);
            } else {
                setDeltaMovement(Vec3.ZERO);
                this.fuse -= 3;
            }
        }
        if (this.stuckTo == null) super.tick();
        if (isInWater()) this.fuse += 2_000;

        if (this.fuse-- <= 0) {
            if (level() instanceof ServerLevel server) {
                server.explode(this, getX(), getY(), getZ(), 2.0F, Level.ExplosionInteraction.TNT);
            }
            discard();
        } else if (this.fuse % 2 == 0) {
            level().addParticle(ParticleTypes.SMOKE, getX(), getY() + 0.5, getZ(), 0.0, 0.0, 0.0);
        }
    }

    @Override
    protected void onHitBlock(BlockHitResult result) {
        super.onHitBlock(result);
        if (isSticky()) {
            this.stuckTo = result.getBlockPos();
            setPos(result.getLocation());
            setDeltaMovement(Vec3.ZERO);
            setNoGravity(true);
            return;
        }
        // quica: perde velocidade e inverte o eixo da face atingida
        Vec3 motion = getDeltaMovement();
        double keep = 0.75 - this.random.nextFloat();
        switch (result.getDirection().getAxis()) {
            case Y -> setDeltaMovement(motion.x * keep, -motion.y * 0.3, motion.z * keep);
            case X -> setDeltaMovement(-motion.x * 0.3, motion.y * keep, motion.z * keep);
            default -> setDeltaMovement(motion.x * keep, motion.y * keep, -motion.z * 0.3);
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("Fuse", this.fuse);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.fuse = input.getIntOr("Fuse", 100);
    }
}
