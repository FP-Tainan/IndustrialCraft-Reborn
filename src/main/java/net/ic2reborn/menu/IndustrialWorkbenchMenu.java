package net.ic2reborn.menu;

import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2Menus;
import net.minecraft.core.BlockPos;
import net.minecraft.core.NonNullList;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Container;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerLevelAccess;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Bancada industrial do IC2 ({@code ContainerIndustrialWorkbench}): grade 3×3 que guarda os itens,
 * 18 slots de estoque que reabastecem a grade depois de cada fabricação, e duas combinações de
 * ferramenta — martelo de forja (placas) e alicate (cabos) — com o resultado ao lado.
 */
public class IndustrialWorkbenchMenu extends AbstractContainerMenu {
    /** Slots do bloco: grade 0–8, estoque 9–26, martelo 27, entrada do martelo 28, alicate 29, entrada do alicate 30. */
    public static final int GRID = 0, STORAGE = 9, STORAGE_END = 27, HAMMER = 27, HAMMER_INPUT = 28, CUTTER = 29, CUTTER_INPUT = 30;
    public static final int SIZE = 31;
    private static final int RESULT_MAIN = 0, RESULT_HAMMER = 1, RESULT_CUTTER = 2;

    private final Container machine;
    private final ContainerLevelAccess access;
    private final Player player;
    private final SimpleContainer results = new SimpleContainer(3);
    private final int machineSlotsEnd;
    private List<ItemStack> lastInputs = List.of();

    /** Cliente. */
    public IndustrialWorkbenchMenu(int id, Inventory inventory, BlockPos pos) {
        this(id, inventory, new SimpleContainer(SIZE), ContainerLevelAccess.NULL);
    }

    /** Servidor. */
    public IndustrialWorkbenchMenu(int id, Inventory inventory, Container machine, ContainerLevelAccess access) {
        super(IC2Menus.INDUSTRIAL_WORKBENCH.get(), id);
        this.machine = machine;
        this.access = access;
        this.player = inventory.player;

        addSlot(new ResultSlot(RESULT_MAIN, 124, 61));
        for (int y = 0; y < 3; y++) {
            for (int x = 0; x < 3; x++) addSlot(new Slot(machine, GRID + x + y * 3, 30 + x * 18, 43 + y * 18));
        }
        for (int y = 0; y < 2; y++) {
            for (int x = 0; x < 9; x++) addSlot(new Slot(machine, STORAGE + x + y * 9, 8 + x * 18, 106 + y * 18));
        }
        addSlot(new ToolSlot(machine, HAMMER, 7, 17, "hammer"));
        addSlot(new Slot(machine, HAMMER_INPUT, 25, 17));
        addSlot(new ResultSlot(RESULT_HAMMER, 69, 17));
        addSlot(new ToolSlot(machine, CUTTER, 91, 17, "cutter"));
        addSlot(new Slot(machine, CUTTER_INPUT, 109, 17));
        addSlot(new ResultSlot(RESULT_CUTTER, 153, 17));
        this.machineSlotsEnd = this.slots.size();

        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, 9 + col + row * 9, 8 + col * 18, 146 + row * 18));
        }
        for (int col = 0; col < 9; col++) addSlot(new Slot(inventory, col, 8 + col * 18, 204));
    }

    // ── receitas ──────────────────────────────────────────────────────────
    @Override
    public void broadcastChanges() {
        if (this.player.level() instanceof ServerLevel level) refreshResults(level);
        super.broadcastChanges();
    }

    private void refreshResults(ServerLevel level) {
        List<ItemStack> inputs = new ArrayList<>(SIZE);
        for (int slot = 0; slot < SIZE; slot++) inputs.add(this.machine.getItem(slot).copy());
        if (sameStacks(inputs, this.lastInputs)) return;
        this.lastInputs = inputs;
        this.results.setItem(RESULT_MAIN, craft(level, gridInput()).map(match -> match.recipe().value().assemble(match.input().input())).orElse(ItemStack.EMPTY));
        this.results.setItem(RESULT_HAMMER, craft(level, comboInput(HAMMER, HAMMER_INPUT)).map(match -> match.recipe().value().assemble(match.input().input())).orElse(ItemStack.EMPTY));
        this.results.setItem(RESULT_CUTTER, craft(level, comboInput(CUTTER, CUTTER_INPUT)).map(match -> match.recipe().value().assemble(match.input().input())).orElse(ItemStack.EMPTY));
    }

    private static boolean sameStacks(List<ItemStack> a, List<ItemStack> b) {
        if (a.size() != b.size()) return false;
        for (int i = 0; i < a.size(); i++) {
            if (!ItemStack.matches(a.get(i), b.get(i))) return false;
        }
        return true;
    }

    private record Match(CraftingInput.Positioned input, RecipeHolder<CraftingRecipe> recipe) {}

    private CraftingInput.Positioned gridInput() {
        List<ItemStack> items = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) items.add(this.machine.getItem(GRID + i));
        return CraftingInput.ofPositioned(3, 3, items);
    }

    private CraftingInput.Positioned comboInput(int tool, int input) {
        return CraftingInput.ofPositioned(2, 1, List.of(this.machine.getItem(tool), this.machine.getItem(input)));
    }

    private static Optional<Match> craft(Level level, CraftingInput.Positioned positioned) {
        if (!(level instanceof ServerLevel server) || positioned.input().isEmpty()) return Optional.empty();
        return server.recipeAccess().getRecipeFor(RecipeType.CRAFTING, positioned.input(), server)
                .map(recipe -> new Match(positioned, recipe));
    }

    /** Consome os ingredientes de uma fabricação; na grade, reabastece do estoque. */
    private void consume(int result) {
        if (!(this.player.level() instanceof ServerLevel level)) return;
        boolean main = result == RESULT_MAIN;
        CraftingInput.Positioned positioned = main ? gridInput()
                : result == RESULT_HAMMER ? comboInput(HAMMER, HAMMER_INPUT) : comboInput(CUTTER, CUTTER_INPUT);
        Optional<Match> match = craft(level, positioned);
        if (match.isEmpty()) return;
        CraftingInput input = positioned.input();
        NonNullList<ItemStack> remaining = match.get().recipe().value().getRemainingItems(input);
        for (int row = 0; row < input.height(); row++) {
            for (int col = 0; col < input.width(); col++) {
                int slot = main ? GRID + (positioned.top() + row) * 3 + positioned.left() + col
                        : (result == RESULT_HAMMER ? HAMMER : CUTTER) + positioned.left() + col;
                ItemStack current = this.machine.getItem(slot);
                ItemStack leftover = remaining.get(col + row * input.width());
                if (!current.isEmpty()) {
                    ItemStack kind = current.copyWithCount(1);
                    current.shrink(1);
                    this.machine.setItem(slot, current);
                    if (main && current.isEmpty()) refill(slot, kind);
                }
                if (!leftover.isEmpty()) giveBack(slot, leftover);
            }
        }
        this.lastInputs = List.of();
        refreshResults(level);
    }

    private void refill(int slot, ItemStack kind) {
        for (int storage = STORAGE; storage < STORAGE_END; storage++) {
            ItemStack stack = this.machine.getItem(storage);
            if (!stack.isEmpty() && ItemStack.isSameItemSameComponents(stack, kind)) {
                this.machine.setItem(slot, stack);
                this.machine.setItem(storage, ItemStack.EMPTY);
                return;
            }
        }
    }

    private void giveBack(int slot, ItemStack leftover) {
        ItemStack current = this.machine.getItem(slot);
        if (current.isEmpty()) {
            this.machine.setItem(slot, leftover);
        } else if (ItemStack.isSameItemSameComponents(current, leftover) && current.getCount() + leftover.getCount() <= current.getMaxStackSize()) {
            current.grow(leftover.getCount());
            this.machine.setItem(slot, current);
        } else if (!this.player.getInventory().add(leftover)) {
            this.player.drop(leftover, false);
        }
    }

    // ── slots ─────────────────────────────────────────────────────────────
    private final class ResultSlot extends Slot {
        private final int result;

        ResultSlot(int result, int x, int y) {
            super(IndustrialWorkbenchMenu.this.results, result, x, y);
            this.result = result;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return false;
        }

        @Override
        public void onTake(Player player, ItemStack stack) {
            stack.onCraftedBy(player, stack.getCount());
            consume(this.result);
            super.onTake(player, stack);
        }
    }

    /** Aceita só a ferramenta do lado: martelo de forja ou alicate. */
    private static final class ToolSlot extends Slot {
        private final String tool;

        ToolSlot(Container container, int index, int x, int y, String tool) {
            super(container, index, x, y);
            this.tool = tool;
        }

        @Override
        public boolean mayPlace(ItemStack stack) {
            return BuiltInRegistries.ITEM.getKey(stack.getItem()).getPath().contains(this.tool);
        }
    }

    // ── menu ──────────────────────────────────────────────────────────────
    @Override
    public boolean stillValid(Player player) {
        return stillValid(this.access, player, IC2AutoBlocks.INDUSTRIAL_WORKBENCH.get());
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        Slot slot = this.slots.get(index);
        if (!slot.hasItem()) return ItemStack.EMPTY;
        if (slot instanceof ResultSlot) {
            // shift-clique no resultado fabrica até encher o inventário (ou acabar a receita)
            for (int i = 0; i < 64 && slot.hasItem(); i++) {
                ItemStack result = slot.getItem().copy();
                if (!moveItemStackTo(result, this.machineSlotsEnd, this.slots.size(), true)) break;
                slot.onTake(player, slot.getItem().copy());
            }
            return ItemStack.EMPTY;
        }
        ItemStack stack = slot.getItem();
        ItemStack original = stack.copy();
        if (index < this.machineSlotsEnd) {
            if (!moveItemStackTo(stack, this.machineSlotsEnd, this.slots.size(), true)) return ItemStack.EMPTY;
        } else if (!moveItemStackTo(stack, 10, 28, false)) {
            return ItemStack.EMPTY;
        }
        if (stack.isEmpty()) {
            slot.setByPlayer(ItemStack.EMPTY);
        } else {
            slot.setChanged();
        }
        return original;
    }
}
