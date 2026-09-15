package net.ic2reborn.block.entity;

import net.craftenergy.content.CEItems;
import net.ic2reborn.item.BoozeMugItem;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * Fermentação do antigo barril do IC2 ({@code TileEntityBarrel}), agora no tanque básico: água, trigo e
 * lúpulo viram cerveja; cana-de-açúcar vira rum. Sem torneira ela envelhece (mosto, jovem, cerveja, ale e
 * sangue de dragão, cada etapa 3× mais longa, a partir de um dia de jogo). O extrator de seiva vira a
 * torneira e, com ela, a caneca vazia tira uma dose; agachado com a mão vazia, a torneira sai.
 */
public final class BrewingLogic {
    public static final int EMPTY = 0, BEER = 1, RUM = 2;
    public static final int MAX_AMOUNT = 32;

    private final MachineBlockEntity machine;
    private int type;
    private int amount;
    private int age;
    private boolean opened;
    private boolean tapped;
    private int hopsCount;
    private int wheatCount;
    private int solidRatio;
    private int hopsRatio;
    private int timeRatio;

    BrewingLogic(MachineBlockEntity machine) {
        this.machine = machine;
    }

    boolean tick() {
        if (isEmpty() || this.tapped) return false;
        this.age++;
        if (this.type == BEER && this.timeRatio < 5) {
            int step = this.timeRatio == 4 ? 6 : this.timeRatio;
            if (this.age >= 24_000 * Math.pow(3, step)) {
                this.age = 0;
                this.timeRatio++;
            }
        }
        return this.age % 200 == 0;
    }

    public boolean isEmpty() {
        return this.type == EMPTY || this.amount <= 0;
    }

    public boolean isTapped() {
        return this.tapped;
    }

    /** Clique no tanque básico; devolve se a fermentação usou o clique (aí a GUI não abre). */
    public boolean handleUse(Player player) {
        Level level = this.machine.getLevel();
        if (level == null) return false;
        ItemStack stack = player.getMainHandItem();
        if (stack.isEmpty()) {
            if (!this.tapped || !player.isShiftKeyDown()) return false;
            if (!level.isClientSide()) {
                this.tapped = false;
                give(player, new ItemStack(CEItems.TREETAP.get()));
                changed();
            }
            return true;
        }
        if (!this.tapped && stack.is(CEItems.TREETAP.get())) {
            if (!level.isClientSide()) {
                consume(player, stack, 1);
                this.tapped = true;
                changed();
            }
            return true;
        }
        if (this.tapped && stack.is(IC2AutoItems.MUG_EMPTY.get())) {
            if (!level.isClientSide() && !isEmpty()) {
                ItemStack booze = BoozeMugItem.create(calculateValue());
                drainLiquid(1);
                consume(player, stack, 1);
                give(player, booze);
            }
            return true;
        }
        if (!isIngredient(stack)) return false;
        if (!level.isClientSide()) addIngredient(player, stack);
        return true;
    }

    private static boolean isIngredient(ItemStack stack) {
        return stack.is(Items.WATER_BUCKET) || stack.is(IC2AutoItems.WATER_CELL.get()) || stack.is(Items.WHEAT)
                || stack.is(IC2Items.HOPS.get()) || stack.is(Items.SUGAR_CANE);
    }

    /** Coloca água, trigo, lúpulo ou cana; devolve se usou o item. */
    public boolean addIngredient(Player player, ItemStack stack) {
        if (this.opened) return false;
        if ((this.type == EMPTY || this.type == BEER) && (stack.is(Items.WATER_BUCKET) || stack.is(IC2AutoItems.WATER_CELL.get()))) {
            if (this.amount >= MAX_AMOUNT) return false;
            this.type = BEER;
            this.amount++;
            ItemStack container = new ItemStack(stack.is(Items.WATER_BUCKET) ? Items.BUCKET : IC2AutoItems.FLUID_CELL.get());
            consume(player, stack, 1);
            if (!player.getAbilities().instabuild) give(player, container);
            changed();
            return true;
        }
        if ((this.type == EMPTY || this.type == BEER) && (stack.is(Items.WHEAT) || stack.is(IC2Items.HOPS.get()))) {
            boolean wheat = stack.is(Items.WHEAT);
            int count = Math.min(stack.getCount(), 64 - (wheat ? this.wheatCount : this.hopsCount));
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
            int count = Math.min(stack.getCount(), MAX_AMOUNT - this.amount);
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

    private static void give(Player player, ItemStack stack) {
        if (!player.getInventory().add(stack)) player.drop(stack, false);
    }

    private void changed() {
        this.machine.setChanged();
        Level level = this.machine.getLevel();
        if (level != null) {
            level.sendBlockUpdated(this.machine.getBlockPos(), this.machine.getBlockState(), this.machine.getBlockState(), Block.UPDATE_ALL);
        }
    }

    /** Mexer na receita depois de fermentar estraga a cerveja (IC2: vira "coisa preta"). */
    private void alterComposition() {
        Level level = this.machine.getLevel();
        if (this.timeRatio <= 0) {
            this.age = 0;
        } else if (this.timeRatio == 1) {
            if (level != null && level.getRandom().nextBoolean()) {
                this.timeRatio = 0;
            } else if (level != null && level.getRandom().nextBoolean()) {
                this.timeRatio = 5;
            }
        } else if (this.timeRatio == 2) {
            if (level != null && level.getRandom().nextBoolean()) this.timeRatio = 5;
        } else {
            this.timeRatio = 5;
        }
    }

    /** Abre a fermentação: fixa a proporção de lúpulo e de sólidos da cerveja. */
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

    /** Tira uma dose; esvaziando, a fermentação volta ao começo (a torneira fica). */
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

    public static int unpack(int value, int shift, int bits) {
        return (value >>> shift) & ((1 << bits) - 1);
    }

    public static int timeNeededForRum(int amount) {
        return (int) (1200 * amount * Math.pow(0.95, amount - 1));
    }

    void write(ValueOutput output) {
        output.putInt("BrewType", this.type);
        output.putInt("BrewAmount", this.amount);
        output.putInt("BrewAge", this.age);
        output.putBoolean("BrewOpened", this.opened);
        output.putBoolean("BrewTapped", this.tapped);
        output.putInt("BrewHops", this.hopsCount);
        output.putInt("BrewWheat", this.wheatCount);
        output.putInt("BrewSolidRatio", this.solidRatio);
        output.putInt("BrewHopsRatio", this.hopsRatio);
        output.putInt("BrewTimeRatio", this.timeRatio);
    }

    void read(ValueInput input) {
        this.type = input.getIntOr("BrewType", EMPTY);
        this.amount = input.getIntOr("BrewAmount", 0);
        this.age = input.getIntOr("BrewAge", 0);
        this.opened = input.getBooleanOr("BrewOpened", false);
        this.tapped = input.getBooleanOr("BrewTapped", false);
        this.hopsCount = input.getIntOr("BrewHops", 0);
        this.wheatCount = input.getIntOr("BrewWheat", 0);
        this.solidRatio = input.getIntOr("BrewSolidRatio", 0);
        this.hopsRatio = input.getIntOr("BrewHopsRatio", 0);
        this.timeRatio = input.getIntOr("BrewTimeRatio", 0);
    }

    // ── testes ────────────────────────────────────────────────────────────
    public int amount() {
        return this.amount;
    }

    public int type() {
        return this.type;
    }
}
