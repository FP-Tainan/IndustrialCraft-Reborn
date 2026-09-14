package net.ic2reborn.block.entity;

import net.ic2reborn.block.BarrelBlock;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2BlockEntities;
import net.ic2reborn.registry.IC2Components;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponentGetter;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Barril do IC2 ({@code TileEntityBarrel}): água, trigo e lúpulo viram cerveja; cana-de-açúcar vira
 * rum. Fechado ele envelhece (a cerveja passa de mosto a jovem, cerveja, ale e sangue de dragão, cada
 * etapa 3× mais longa, a partir de um dia de jogo). Com a torneira aberta, a caneca vazia tira uma dose.
 */
public class BarrelBlockEntity extends BlockEntity {
    public static final int EMPTY = 0, BEER = 1, RUM = 2;
    public static final int MAX_AMOUNT = 32;

    private int type;
    private int amount;
    private int age;
    private boolean opened;
    private int hopsCount;
    private int wheatCount;
    private int solidRatio;
    private int hopsRatio;
    private int timeRatio;

    public BarrelBlockEntity(BlockPos pos, BlockState state) {
        super(IC2BlockEntities.BARREL.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, BarrelBlockEntity barrel) {
        if (barrel.isEmpty() || state.getValue(BarrelBlock.TAPPED)) return;
        barrel.age++;
        if (barrel.type == BEER && barrel.timeRatio < 5) {
            int step = barrel.timeRatio == 4 ? 6 : barrel.timeRatio;
            if (barrel.age >= 24_000 * Math.pow(3, step)) {
                barrel.age = 0;
                barrel.timeRatio++;
            }
        }
        if (barrel.age % 200 == 0) barrel.setChanged();
    }

    public boolean isEmpty() {
        return this.type == EMPTY || this.amount <= 0;
    }

    /** Coloca água, trigo, lúpulo ou cana; devolve se usou o item. Agachado, só um por vez. */
    public boolean addIngredient(Player player, ItemStack stack) {
        if (this.opened) return false;
        boolean one = player.isShiftKeyDown();
        if ((this.type == EMPTY || this.type == BEER) && (stack.is(Items.WATER_BUCKET) || stack.is(IC2AutoItems.WATER_CELL.get()))) {
            if (this.amount >= MAX_AMOUNT) return false;
            this.type = BEER;
            this.amount++;
            ItemStack container = new ItemStack(stack.is(Items.WATER_BUCKET) ? Items.BUCKET : IC2AutoItems.FLUID_CELL.get());
            consume(player, stack, 1);
            if (!player.getAbilities().instabuild && !player.getInventory().add(container)) player.drop(container, false);
            changed();
            return true;
        }
        if ((this.type == EMPTY || this.type == BEER) && (stack.is(Items.WHEAT) || stack.is(IC2Items.HOPS.get()))) {
            boolean wheat = stack.is(Items.WHEAT);
            int room = 64 - (wheat ? this.wheatCount : this.hopsCount);
            int count = Math.min(one ? 1 : stack.getCount(), room);
            if (count <= 0) return false;
            this.type = BEER;
            if (wheat) {
                this.wheatCount += count;
            } else {
                this.hopsCount += count;
            }
            consume(player, stack, count);
            alterComposition();
            changed();
            return true;
        }
        if ((this.type == EMPTY || this.type == RUM) && stack.is(Items.SUGAR_CANE)) {
            if (this.age > 600) return false;
            int count = Math.min(one ? 1 : stack.getCount(), MAX_AMOUNT - this.amount);
            if (count <= 0) return false;
            this.type = RUM;
            this.amount += count;
            consume(player, stack, count);
            changed();
            return true;
        }
        return false;
    }

    private static void consume(Player player, ItemStack stack, int count) {
        if (!player.getAbilities().instabuild) stack.shrink(count);
    }

    private void changed() {
        setChanged();
        if (this.level != null) this.level.sendBlockUpdated(this.worldPosition, getBlockState(), getBlockState(), 3);
    }

    /** Mexer na receita depois de fermentar estraga a cerveja (IC2: vira "coisa preta"). */
    private void alterComposition() {
        if (this.timeRatio <= 0) {
            this.age = 0;
        } else if (this.timeRatio == 1) {
            if (this.level != null && this.level.getRandom().nextBoolean()) {
                this.timeRatio = 0;
            } else if (this.level != null && this.level.getRandom().nextBoolean()) {
                this.timeRatio = 5;
            }
        } else if (this.timeRatio == 2) {
            if (this.level != null && this.level.getRandom().nextBoolean()) this.timeRatio = 5;
        } else {
            this.timeRatio = 5;
        }
    }

    /** Abre o barril: fixa a proporção de lúpulo e de sólidos da cerveja. */
    private void open() {
        if (this.opened) return;
        this.opened = true;
        if (this.type != BEER) return;
        float ratio = this.hopsCount <= 0 ? 0.0F : (float) this.hopsCount / Math.max(1, this.wheatCount);
        if (ratio <= 0.25F) {
            this.hopsRatio = 0;
        } else if (ratio <= 1.0F / 3.0F) {
            this.hopsRatio = 1;
        } else if (ratio <= 0.5F) {
            this.hopsRatio = 2;
        } else if (ratio < 2.0F) {
            this.hopsRatio = 3;
        } else {
            this.hopsRatio = (int) Math.min(6.0, Math.floor(ratio) + 2.0);
            if (ratio >= 5.0F) this.timeRatio = 5;
        }
        ratio = this.amount <= 0 ? Float.POSITIVE_INFINITY : (float) (this.hopsCount + this.wheatCount) / this.amount;
        if (ratio <= 5.0F / 12.0F) {
            this.solidRatio = 0;
        } else if (ratio <= 0.5F) {
            this.solidRatio = 1;
        } else if (ratio < 1.0F) {
            this.solidRatio = 2;
        } else if (ratio == 1.0F) {
            this.solidRatio = 3;
        } else if (ratio < 2.0F) {
            this.solidRatio = 4;
        } else if (ratio < 2.4F) {
            this.solidRatio = 5;
        } else {
            this.solidRatio = 6;
            if (ratio >= 4.0F) this.timeRatio = 5;
        }
    }

    /** Tira uma dose; esvaziando, o barril volta ao começo. */
    public boolean drainLiquid(int count) {
        if (isEmpty() || count > this.amount) return false;
        open();
        if (this.type == RUM) {
            int progress = this.age * 100 / timeNeededForRum(this.amount);
            this.amount -= count;
            this.age = this.amount <= 0 ? 0 : progress * timeNeededForRum(this.amount) / 100;
        } else {
            this.amount -= count;
        }
        if (this.amount <= 0) {
            this.type = EMPTY;
            this.opened = false;
            this.amount = 0;
            this.hopsCount = this.wheatCount = this.hopsRatio = this.solidRatio = this.timeRatio = 0;
            this.age = 0;
        }
        changed();
        return true;
    }

    /** Valor empacotado como no IC2 (tipo, quantidade, proporções, tempo ou progresso do rum). */
    public int calculateValue() {
        if (isEmpty()) return 0;
        open();
        if (this.type == BEER) {
            int value = this.timeRatio;
            value = (value << 3) | this.hopsRatio;
            value = (value << 3) | this.solidRatio;
            value = (value << 5) | (this.amount - 1);
            return (value << 2) | BEER;
        }
        int progress = Math.min(100, this.age * 100 / timeNeededForRum(this.amount));
        int value = (progress << 5) | (this.amount - 1);
        return (value << 2) | RUM;
    }

    private void loadValue(int value) {
        this.type = unpack(value, 0, 2);
        this.amount = this.type == EMPTY ? 0 : unpack(value, 2, 5) + 1;
        if (this.type == BEER) {
            this.opened = true;
            this.solidRatio = unpack(value, 7, 3);
            this.hopsRatio = unpack(value, 10, 3);
            this.timeRatio = unpack(value, 13, 3);
        } else if (this.type == RUM) {
            this.opened = false;
            this.age = timeNeededForRum(this.amount) * unpack(value, 7, 7) / 100;
        }
    }

    public static int unpack(int value, int shift, int bits) {
        return (value >>> shift) & ((1 << bits) - 1);
    }

    public static int timeNeededForRum(int amount) {
        return (int) (1200 * amount * Math.pow(0.95, amount - 1));
    }

    // ── item e salvamento ─────────────────────────────────────────────────
    @Override
    protected void collectImplicitComponents(DataComponentMap.Builder components) {
        super.collectImplicitComponents(components);
        if (!isEmpty()) components.set(IC2Components.BOOZE.get(), calculateValue());
    }

    @Override
    protected void applyImplicitComponents(DataComponentGetter components) {
        super.applyImplicitComponents(components);
        Integer value = components.get(IC2Components.BOOZE.get());
        if (value != null) loadValue(value);
    }

    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putInt("Type", this.type);
        output.putInt("Amount", this.amount);
        output.putInt("Age", this.age);
        output.putInt("Opened", this.opened ? 1 : 0);
        output.putInt("Hops", this.hopsCount);
        output.putInt("Wheat", this.wheatCount);
        output.putInt("SolidRatio", this.solidRatio);
        output.putInt("HopsRatio", this.hopsRatio);
        output.putInt("TimeRatio", this.timeRatio);
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.type = input.getIntOr("Type", EMPTY);
        this.amount = input.getIntOr("Amount", 0);
        this.age = input.getIntOr("Age", 0);
        this.opened = input.getIntOr("Opened", 0) != 0;
        this.hopsCount = input.getIntOr("Hops", 0);
        this.wheatCount = input.getIntOr("Wheat", 0);
        this.solidRatio = input.getIntOr("SolidRatio", 0);
        this.hopsRatio = input.getIntOr("HopsRatio", 0);
        this.timeRatio = input.getIntOr("TimeRatio", 0);
    }

    // ── testes ────────────────────────────────────────────────────────────
    public int amount() {
        return this.amount;
    }

    public int type() {
        return this.type;
    }
}
