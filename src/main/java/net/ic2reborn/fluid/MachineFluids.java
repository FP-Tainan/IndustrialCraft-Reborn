package net.ic2reborn.fluid;

import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.EmptyItemFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.base.FullItemFluidStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleSlotStorage;
import net.fabricmc.fabric.api.transfer.v1.storage.base.SingleVariantStorage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.fabricmc.fabric.api.transfer.v1.transaction.TransactionContext;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.world.Container;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;

import java.util.List;

/**
 * Fluidos das máquinas: células do IC2 no Transfer API do Fabric e o "slot de fluido" do IC2,
 * que esvazia baldes/células no tanque e devolve o recipiente vazio no slot de saída.
 */
public final class MachineFluids {
    private MachineFluids() {}

    /** Célula vazia enche com água ou lava (1 balde); células cheias esvaziam de volta. */
    public static void init() {
        Item emptyCell = IC2AutoItems.FLUID_CELL.get();
        registerCell(emptyCell, IC2AutoItems.WATER_CELL.get(), Fluids.WATER);
        registerCell(emptyCell, IC2AutoItems.LAVA_CELL.get(), Fluids.LAVA);
    }

    private static void registerCell(Item emptyCell, Item fullCell, Fluid fluid) {
        FluidStorage.combinedItemApiProvider(emptyCell).register(
                context -> new EmptyItemFluidStorage(context, fullCell, fluid, FluidConstants.BUCKET));
        FluidStorage.combinedItemApiProvider(fullCell).register(
                context -> new FullItemFluidStorage(context, emptyCell, FluidVariant.of(fluid), FluidConstants.BUCKET));
    }

    /**
     * Esvazia um item do slot {@code inputSlot} (balde, célula ou qualquer recipiente de fluido de
     * outros mods) dentro de {@code tank}. O recipiente que sobra vai para {@code outputSlot}; se não
     * couber, nada acontece.
     */
    public static boolean drainIntoTank(Container inventory, int inputSlot, int outputSlot, Storage<FluidVariant> tank) {
        ItemStack stack = inventory.getItem(inputSlot);
        if (stack.isEmpty()) return false;

        SlotContext context = new SlotContext(ItemVariant.of(stack));
        Storage<FluidVariant> itemFluids = context.find(FluidStorage.ITEM);
        if (itemFluids == null) return false;

        ItemStack leftover;
        try (Transaction transaction = Transaction.openOuter()) {
            long moved = StorageUtil.move(itemFluids, tank, variant -> true, Long.MAX_VALUE, transaction);
            if (moved <= 0) return false;
            leftover = context.leftover();
            if (leftover == null || !fits(inventory.getItem(outputSlot), leftover)) return false;
            transaction.commit();
        }

        stack.shrink(1);
        inventory.setItem(inputSlot, stack);
        if (!leftover.isEmpty()) {
            ItemStack output = inventory.getItem(outputSlot);
            if (output.isEmpty()) {
                inventory.setItem(outputSlot, leftover);
            } else {
                output.grow(leftover.getCount());
                inventory.setItem(outputSlot, output);
            }
        }
        return true;
    }

    private static boolean fits(ItemStack slot, ItemStack stack) {
        if (slot.isEmpty()) return true;
        return ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() + stack.getCount() <= slot.getMaxStackSize();
    }

    /** Contexto de um único item, isolado do inventário: o que ele vira fica guardado para depois. */
    private static final class SlotContext implements ContainerItemContext {
        private final SingleVariantStorage<ItemVariant> main = itemSlot();
        private final SingleVariantStorage<ItemVariant> overflow = itemSlot();

        SlotContext(ItemVariant variant) {
            this.main.variant = variant;
            this.main.amount = 1;
        }

        @Override
        public SingleSlotStorage<ItemVariant> getMainSlot() {
            return this.main;
        }

        @Override
        public long insertOverflow(ItemVariant item, long maxAmount, TransactionContext transaction) {
            return this.overflow.insert(item, maxAmount, transaction);
        }

        @Override
        public List<SingleSlotStorage<ItemVariant>> getAdditionalSlots() {
            return List.of();
        }

        /** O que sobrou do item (vazio se foi consumido); null se sobraram dois itens diferentes. */
        ItemStack leftover() {
            ItemStack main = this.main.amount > 0 ? this.main.variant.toStack((int) this.main.amount) : ItemStack.EMPTY;
            ItemStack extra = this.overflow.amount > 0 ? this.overflow.variant.toStack((int) this.overflow.amount) : ItemStack.EMPTY;
            if (main.isEmpty()) return extra;
            if (extra.isEmpty()) return main;
            if (!ItemStack.isSameItemSameComponents(main, extra)) return null;
            main.grow(extra.getCount());
            return main;
        }

        private static SingleVariantStorage<ItemVariant> itemSlot() {
            return new SingleVariantStorage<>() {
                @Override
                protected ItemVariant getBlankVariant() {
                    return ItemVariant.blank();
                }

                @Override
                protected long getCapacity(ItemVariant variant) {
                    return 64;
                }
            };
        }
    }
}
