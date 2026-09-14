package net.ic2reborn.entity;

import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2Entities;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.item.PrimedTnt;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/** TNT industrial acesa (IC2: {@code EntityItnt}): pavio de 3 segundos e explosão de força 5,5. */
public class ItntEntity extends PrimedTnt {
    public static final float POWER = 5.5F;
    private int countdown = 60;

    public ItntEntity(EntityType<? extends ItntEntity> type, Level level) {
        super(type, level);
        setBlockState(IC2AutoBlocks.ITNT.get().defaultBlockState());
    }

    public ItntEntity(Level level, double x, double y, double z) {
        this(IC2Entities.ITNT.get(), level);
        setPos(x, y, z);
        double angle = level.getRandom().nextDouble() * Math.PI * 2;
        setDeltaMovement(-Math.sin(angle) * 0.02, 0.2, -Math.cos(angle) * 0.02);
        this.xo = x;
        this.yo = y;
        this.zo = z;
    }

    public void setCountdown(int ticks) {
        this.countdown = Math.max(1, ticks);
    }

    /** O pavio do vanilla nunca chega a zero: a explosão, mais forte, é feita aqui. */
    @Override
    public void tick() {
        setFuse(this.countdown + 1);
        super.tick();
        if (isRemoved()) return;
        if (--this.countdown <= 0) {
            discard();
            if (level() instanceof ServerLevel server) {
                server.explode(this, getX(), getY(0.0625), getZ(), POWER, Level.ExplosionInteraction.TNT);
            }
        }
    }

    @Override
    protected void addAdditionalSaveData(ValueOutput output) {
        super.addAdditionalSaveData(output);
        output.putInt("ItntFuse", this.countdown);
    }

    @Override
    protected void readAdditionalSaveData(ValueInput input) {
        super.readAdditionalSaveData(input);
        this.countdown = Math.max(1, input.getIntOr("ItntFuse", 60));
    }
}
