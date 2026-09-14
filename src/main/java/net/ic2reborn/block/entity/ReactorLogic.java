package net.ic2reborn.block.entity;

import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.ic2reborn.fluid.MachineFluids;
import net.ic2reborn.menu.MachineGuiType;
import net.ic2reborn.reactor.Reactor;
import net.ic2reborn.reactor.ReactorComponentItem;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.tags.BlockTags;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.BaseFireBlock;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import net.minecraft.world.phys.AABB;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Reator nuclear do IC2 ({@code TileEntityNuclearReactorElectric}): grade de 3 a 9 colunas × 6 linhas
 * (cada câmara encostada soma uma coluna); a cada segundo processa os componentes em duas passadas
 * (energia e calor). Com a casca 5×5×5 de vasos, portas e escotilha e as 6 câmaras, vira reator a
 * fluido: o calor das ventoinhas aquece o refrigerante em vez de dar energia. Também cuida da porta de
 * fluidos e dos injetores de refrigerante (RSH/LZH).
 */
final class ReactorLogic implements Reactor {
    static final int GRID_SLOTS = 54;
    static final int COLUMNS = 9;
    static final int ROWS = 6;
    static final int COOLANT_IN = 54, HOT_IN = 55, COOLANT_OUT = 56, HOT_OUT = 57;
    /** 1 de produção = 5 EU/t = 2.500 CW. */
    private static final float CW_PER_OUTPUT = 2_500.0F;
    /** Reator a fluido: cada HU das ventoinhas vale 40 HU no refrigerante. */
    private static final float HU_OUTPUT_MODIFIER = 40.0F;
    private static final float EXPLOSION_LIMIT = 20.0F;
    private static final long MB = FluidConstants.BUCKET / 1_000;
    /** Injetor: 1.000 EU por condensador restaurado (500.000 CW·tick). */
    private static final long INJECTOR_COST = 500_000;
    static final int INJECTOR_DISCHARGE = 9;

    private final MachineBlockEntity machine;
    private final MachineGuiType type;

    private int heat;
    private int maxHeat = 10_000;
    private float hem = 1.0F;
    private float output;
    private int emitHeatBuffer;
    private int emitHeat;
    private boolean fluidCooled;
    private int size = 3;
    private int ticker;
    private boolean redstone;
    private final List<BlockPos> redstonePorts = new ArrayList<>();

    private ReactorLogic(MachineBlockEntity machine, MachineGuiType type) {
        this.machine = machine;
        this.type = type;
    }

    static @Nullable ReactorLogic create(MachineBlockEntity machine, MachineGuiType type) {
        return switch (type) {
            case NUCLEAR_REACTOR, REACTOR_FLUID_PORT, REACTOR_COOLANT_INJECTOR -> new ReactorLogic(machine, type);
            default -> null;
        };
    }

    // ── slots e GUI ───────────────────────────────────────────────────────
    boolean canPlace(int slot, ItemStack stack) {
        if (this.type == MachineGuiType.NUCLEAR_REACTOR && slot < GRID_SLOTS) {
            return stack.getItem() instanceof ReactorComponentItem && slot % COLUMNS < this.size;
        }
        if (this.type == MachineGuiType.REACTOR_COOLANT_INJECTOR && slot < INJECTOR_DISCHARGE) {
            return stack.is(coolantBlock());
        }
        return true;
    }

    @Nullable Integer dataValue(int field) {
        if (this.type != MachineGuiType.NUCLEAR_REACTOR) return null;
        return switch (field) {
            case MachineBlockEntity.DATA_MODE -> this.size | (this.fluidCooled ? 16 : 0);
            case MachineBlockEntity.DATA_HEAT -> this.heat;
            case MachineBlockEntity.DATA_MAX_HEAT -> this.maxHeat;
            case MachineBlockEntity.DATA_PROGRESS -> this.fluidCooled ? this.emitHeat : Math.round(this.output * CW_PER_OUTPUT);
            // pulsos de fissão do ciclo, em décimos de MMEV
            case MachineBlockEntity.DATA_MAX_PROGRESS -> Math.round(this.output * 10);
            default -> null;
        };
    }

    boolean working() {
        return this.heat >= 1_000 || this.output > 0 || this.emitHeat > 0;
    }

    // ── tick ──────────────────────────────────────────────────────────────
    boolean tick(Level level) {
        return switch (this.type) {
            case NUCLEAR_REACTOR -> tickReactor(level);
            case REACTOR_FLUID_PORT -> tickFluidPort(level);
            case REACTOR_COOLANT_INJECTOR -> tickInjector(level);
            default -> false;
        };
    }

    private boolean tickReactor(Level level) {
        if (!this.fluidCooled && this.output > 0) {
            this.machine.addGeneratedEnergy(Math.round(this.output * CW_PER_OUTPUT));
        }
        if (++this.ticker % 20 != 0) return false;

        BlockPos pos = this.machine.getBlockPos();
        this.size = 3;
        this.redstone = level.hasNeighborSignal(pos);
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof MachineBlockEntity chamber
                    && chamber.getGuiType() == MachineGuiType.REACTOR_CHAMBER) {
                this.size++;
                this.redstone |= level.hasNeighborSignal(chamber.getBlockPos());
            }
        }
        this.fluidCooled = this.size == COLUMNS && hasFluidShell(level);
        if (this.fluidCooled) {
            for (BlockPos port : this.redstonePorts) this.redstone |= level.hasNeighborSignal(port);
        }

        dropUnfitting(level);
        this.output = 0;
        this.maxHeat = 10_000;
        this.hem = 1.0F;
        for (int pass = 0; pass < 2; pass++) {
            for (int y = 0; y < ROWS; y++) {
                for (int x = 0; x < this.size; x++) {
                    ItemStack stack = getItemAt(x, y);
                    if (stack.getItem() instanceof ReactorComponentItem component) {
                        component.processChamber(stack, this, x, y, pass == 0);
                    }
                }
            }
        }

        if (this.fluidCooled) {
            coolWithFluid();
        } else {
            this.emitHeatBuffer = 0;
            this.emitHeat = 0;
        }
        this.machine.setChanged();
        return !calculateHeatEffects(level);
    }

    /** Reator a fluido: o calor tirado pelas ventoinhas aquece refrigerante (ou água); o que não couber volta ao reator. */
    private void coolWithFluid() {
        MachineBlockEntity.MachineTank input = this.machine.mainTank();
        MachineBlockEntity.MachineTank output = this.machine.secondTank();
        if (input == null || output == null) return;
        SimpleContainer inventory = this.machine.getInventory();
        MachineFluids.fillFromTank(inventory, HOT_IN, HOT_OUT, output);

        int huOutput = (int) (HU_OUTPUT_MODIFIER * this.emitHeatBuffer);
        this.emitHeatBuffer = 0;
        this.emitHeat = 0;
        SteamLogic.Heating heating = input.amount < MB ? null : SteamLogic.heating(input.variant.getFluid());
        long room = (output.capacity() - output.amount) / MB;
        if (heating != null && room > 0) {
            FluidVariant hot = FluidVariant.of(heating.hot());
            if (output.isResourceBlank() || output.variant.equals(hot)) {
                long wanted = huOutput / heating.huPerMb();
                long mb = Math.min(Math.min(wanted, room), input.amount / MB);
                if (wanted < room) this.emitHeatBuffer = (int) (huOutput % heating.huPerMb() / HU_OUTPUT_MODIFIER);
                if (mb > 0) {
                    input.consume(mb * MB);
                    output.fill(hot, mb * MB);
                    huOutput -= (int) (mb * heating.huPerMb());
                    this.emitHeat = (int) (mb * heating.huPerMb());
                }
            }
        }
        addHeat((int) (huOutput / HU_OUTPUT_MODIFIER));
    }

    /** Casca 5×5×5: todas as posições a distância 2 do núcleo são vaso, escotilha, porta de fluidos ou porta de redstone. */
    private boolean hasFluidShell(Level level) {
        this.redstonePorts.clear();
        BlockPos center = this.machine.getBlockPos();
        for (BlockPos pos : BlockPos.betweenClosed(center.offset(-2, -2, -2), center.offset(2, 2, 2))) {
            int dx = Math.abs(pos.getX() - center.getX());
            int dy = Math.abs(pos.getY() - center.getY());
            int dz = Math.abs(pos.getZ() - center.getZ());
            if (Math.max(dx, Math.max(dy, dz)) != 2) continue;
            Block block = level.getBlockState(pos).getBlock();
            if (block == IC2AutoBlocks.REACTOR_REDSTONE_PORT.get()) {
                this.redstonePorts.add(pos.immutable());
            } else if (block != IC2AutoBlocks.REACTOR_VESSEL.get() && block != IC2AutoBlocks.REACTOR_ACCESS_HATCH.get()
                    && block != IC2AutoBlocks.REACTOR_FLUID_PORT.get()) {
                return false;
            }
        }
        return true;
    }

    /** Tira da grade o que não é componente, o que está fora das colunas atuais e o excesso de pilhas. */
    private void dropUnfitting(Level level) {
        SimpleContainer inventory = this.machine.getInventory();
        BlockPos pos = this.machine.getBlockPos();
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.isEmpty()) continue;
            if (!(stack.getItem() instanceof ReactorComponentItem) || slot % COLUMNS >= this.size) {
                inventory.setItem(slot, ItemStack.EMPTY);
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, stack);
            } else if (stack.getCount() > 1) {
                ItemStack extra = stack.split(stack.getCount() - 1);
                inventory.setItem(slot, stack);
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.0, pos.getZ() + 0.5, extra);
            }
        }
    }

    /** Efeitos do calor do IC2: a partir de 40% queima madeira em volta, 50% evapora água, 70% irradia, 85% põe fogo e lava, 100% explode. */
    private boolean calculateHeatEffects(Level level) {
        if (this.heat < 4_000) return false;
        float power = (float) this.heat / this.maxHeat;
        if (power >= 1.0F) {
            explode(level);
            return true;
        }
        RandomSource random = level.getRandom();
        BlockPos center = this.machine.getBlockPos();
        if (power >= 0.85F && random.nextFloat() <= 0.2F * this.hem) {
            BlockPos pos = randomNear(center, random);
            BlockState state = level.getBlockState(pos);
            if (state.isAir()) {
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
            } else if (state.getDestroySpeed(level, pos) >= 0.0F && level.getBlockEntity(pos) == null) {
                level.setBlockAndUpdate(pos, state.is(BlockTags.MINEABLE_WITH_PICKAXE)
                        ? Blocks.LAVA.defaultBlockState() : BaseFireBlock.getState(level, pos));
            }
        }
        if (power >= 0.7F && level instanceof net.minecraft.server.level.ServerLevel server) {
            AABB area = new AABB(center).inflate(3.5);
            for (LivingEntity entity : level.getEntitiesOfClass(LivingEntity.class, area)) {
                int damage = (int) (random.nextInt(4) * this.hem);
                if (damage > 0) entity.hurtServer(server, level.damageSources().source(net.ic2reborn.effect.IC2Effects.RADIATION_DAMAGE), damage);
            }
        }
        if (power >= 0.5F && random.nextFloat() <= this.hem) {
            BlockPos pos = randomNear(center, random);
            if (level.getFluidState(pos).is(FluidTags.WATER)) level.setBlockAndUpdate(pos, Blocks.AIR.defaultBlockState());
        }
        if (power >= 0.4F && random.nextFloat() <= this.hem) {
            BlockPos pos = randomNear(center, random);
            BlockState state = level.getBlockState(pos);
            if (level.getBlockEntity(pos) == null && (state.is(BlockTags.LOGS) || state.is(BlockTags.PLANKS)
                    || state.is(BlockTags.LEAVES) || state.is(BlockTags.WOOL))) {
                level.setBlockAndUpdate(pos, BaseFireBlock.getState(level, pos));
            }
        }
        return false;
    }

    private static BlockPos randomNear(BlockPos center, RandomSource random) {
        BlockPos pos;
        do {
            pos = center.offset(random.nextInt(5) - 2, random.nextInt(5) - 2, random.nextInt(5) - 2);
        } while (pos.equals(center));
        return pos;
    }

    /** Derretimento: 10 + influência dos componentes (barras somam, refletores tiram, placas reduzem). */
    private void explode(Level level) {
        float boomPower = 10.0F;
        float boomModifier = 1.0F;
        SimpleContainer inventory = this.machine.getInventory();
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            ItemStack stack = inventory.getItem(slot);
            if (stack.getItem() instanceof ReactorComponentItem component) {
                float influence = component.influenceExplosion(stack, this);
                if (influence > 0.0F && influence < 1.0F) {
                    boomModifier *= influence;
                } else {
                    boomPower += influence;
                }
            }
            inventory.setItem(slot, ItemStack.EMPTY);
        }
        boomPower = Math.min(EXPLOSION_LIMIT, Math.max(1.0F, boomPower * this.hem * boomModifier));
        BlockPos center = this.machine.getBlockPos();
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(center.relative(direction)) instanceof MachineBlockEntity chamber
                    && chamber.getGuiType() == MachineGuiType.REACTOR_CHAMBER) {
                level.removeBlock(chamber.getBlockPos(), false);
            }
        }
        level.removeBlock(center, false);
        level.explode(null, center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5, boomPower, Level.ExplosionInteraction.BLOCK);
    }

    /** Porta de fluidos: com ejetor de fluido manda o refrigerante quente para fora; com puxador traz refrigerante frio. */
    private boolean tickFluidPort(Level level) {
        MachineBlockEntity core = this.machine.reactorCore();
        if (core == null || core.reactorLogic() == null || !core.reactorLogic().fluidCooled) return false;
        MachineBlockEntity.MachineTank input = core.mainTank();
        MachineBlockEntity.MachineTank output = core.secondTank();
        if (input == null || output == null) return false;
        boolean eject = false, pull = false;
        for (int slot : this.machine.getGuiType().layout().upgradeSlots()) {
            if (this.machine.getInventory().getItem(slot).getItem() instanceof net.ic2reborn.item.UpgradeItem upgrade) {
                eject |= upgrade.kind() == net.ic2reborn.item.UpgradeItem.Kind.FLUID_EJECTOR;
                pull |= upgrade.kind() == net.ic2reborn.item.UpgradeItem.Kind.FLUID_PULLING;
            }
        }
        boolean moved = false;
        BlockPos pos = this.machine.getBlockPos();
        for (Direction direction : Direction.values()) {
            BlockPos target = pos.relative(direction);
            if (isReactorPart(level, target)) continue;
            Storage<FluidVariant> storage = FluidStorage.SIDED.find(level, target, direction.getOpposite());
            if (storage == null) continue;
            if (eject && output.amount > 0) moved |= StorageUtil.move(output, storage, variant -> true, FluidConstants.BUCKET, null) > 0;
            if (pull) {
                moved |= StorageUtil.move(storage, input, variant -> SteamLogic.heatable(variant.getFluid()), FluidConstants.BUCKET, null) > 0;
            }
        }
        return moved;
    }

    private static boolean isReactorPart(Level level, BlockPos pos) {
        Block block = level.getBlockState(pos).getBlock();
        return block == IC2AutoBlocks.REACTOR_VESSEL.get() || block == IC2AutoBlocks.REACTOR_ACCESS_HATCH.get()
                || block == IC2AutoBlocks.REACTOR_FLUID_PORT.get() || block == IC2AutoBlocks.REACTOR_REDSTONE_PORT.get()
                || block == IC2AutoBlocks.NUCLEAR_REACTOR.get() || block == IC2AutoBlocks.REACTOR_CHAMBER.get();
    }

    /**
     * Injetor de refrigerante do IC2: encostado no reator (ou numa câmara), restaura os condensadores
     * com mais de 85% de calor gastando um bloco de redstone (RSH) ou de lápis-lazúli (LZH) e 1.000 EU.
     */
    private boolean tickInjector(Level level) {
        MachineBlockEntity core = this.machine.reactorCore();
        if (core == null) return false;
        SimpleContainer own = this.machine.getInventory();
        SimpleContainer grid = core.getInventory();
        net.minecraft.world.item.Item target = coolantTarget();
        boolean changed = false;
        for (int slot = 0; slot < GRID_SLOTS; slot++) {
            ItemStack component = grid.getItem(slot);
            if (!component.is(target) || !(component.getItem() instanceof ReactorComponentItem condensator)) continue;
            if (ReactorComponentItem.damage(component) <= condensator.maxDamage() * 0.85) continue;
            if (this.machine.getStoredEnergy() < INJECTOR_COST) break;
            int coolant = findCoolant(own);
            if (coolant < 0) break;
            own.getItem(coolant).shrink(1);
            own.setItem(coolant, own.getItem(coolant));
            this.machine.useEnergy(INJECTOR_COST);
            ReactorComponentItem.setDamage(component, 0);
            grid.setItem(slot, component);
            changed = true;
        }
        return changed;
    }

    private int findCoolant(SimpleContainer inventory) {
        for (int slot = 0; slot < INJECTOR_DISCHARGE; slot++) {
            if (inventory.getItem(slot).is(coolantBlock())) return slot;
        }
        return -1;
    }

    private boolean isLzh() {
        return net.minecraft.core.registries.BuiltInRegistries.BLOCK.getKey(this.machine.getBlockState().getBlock()).getPath().equals("rci_lzh");
    }

    private net.minecraft.world.item.Item coolantBlock() {
        return isLzh() ? Items.LAPIS_BLOCK : Items.REDSTONE_BLOCK;
    }

    private net.minecraft.world.item.Item coolantTarget() {
        return isLzh() ? IC2AutoItems.LZH_CONDENSATOR.get() : IC2AutoItems.RSH_CONDENSATOR.get();
    }

    boolean isFluidCooledCore() {
        return this.fluidCooled;
    }

    // ── Reactor ───────────────────────────────────────────────────────────
    @Override
    public ItemStack getItemAt(int x, int y) {
        if (x < 0 || x >= this.size || y < 0 || y >= ROWS) return ItemStack.EMPTY;
        return this.machine.getInventory().getItem(y * COLUMNS + x);
    }

    @Override
    public void setItemAt(int x, int y, ItemStack stack) {
        if (x < 0 || x >= this.size || y < 0 || y >= ROWS) return;
        this.machine.getInventory().setItem(y * COLUMNS + x, stack);
    }

    @Override
    public int getHeat() {
        return this.heat;
    }

    @Override
    public void setHeat(int heat) {
        this.heat = Math.max(0, heat);
    }

    @Override
    public void addHeat(int amount) {
        this.heat = Math.max(0, this.heat + amount);
    }

    @Override
    public int getMaxHeat() {
        return this.maxHeat;
    }

    @Override
    public void setMaxHeat(int maxHeat) {
        this.maxHeat = maxHeat;
    }

    @Override
    public float getHeatEffectModifier() {
        return this.hem;
    }

    @Override
    public void setHeatEffectModifier(float modifier) {
        this.hem = modifier;
    }

    @Override
    public void addOutput(float output) {
        this.output += output;
    }

    @Override
    public void addEmitHeat(int heat) {
        this.emitHeatBuffer += heat;
    }

    @Override
    public boolean produceEnergy() {
        return this.redstone;
    }

    @Override
    public boolean isFluidCooled() {
        return this.fluidCooled;
    }

    // ── salvar/carregar ───────────────────────────────────────────────────
    void write(ValueOutput out) {
        out.putInt("ReactorHeat", this.heat);
        out.putFloat("ReactorOutput", this.output);
        out.putInt("ReactorSize", this.size);
        out.putInt("ReactorFluid", this.fluidCooled ? 1 : 0);
    }

    void read(ValueInput in) {
        this.heat = Math.max(0, in.getIntOr("ReactorHeat", 0));
        this.output = in.getFloatOr("ReactorOutput", 0);
        this.size = Math.max(3, Math.min(COLUMNS, in.getIntOr("ReactorSize", 3)));
        this.fluidCooled = in.getIntOr("ReactorFluid", 0) != 0;
    }
}
