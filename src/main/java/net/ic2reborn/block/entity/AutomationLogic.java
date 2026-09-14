package net.ic2reborn.block.entity;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.transfer.v1.item.ItemStorage;
import net.fabricmc.fabric.api.transfer.v1.item.ItemVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.storage.StorageUtil;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.ic2reborn.item.ScannerItem;
import net.ic2reborn.menu.MachineGuiType;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Containers;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.enchantment.Enchantments;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.BonemealableBlock;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.IntStream;

/**
 * Automação do IC2: terraformador com as plantas (cultivo, irrigação, resfriamento, desertificação,
 * aplanamento e cogumelos), minerador avançado, fabricador em lote, Energy-O-Mat e Trade-O-Mat.
 */
final class AutomationLogic {
    // minerador avançado: descarga 0, scanner 1, upgrades 2–5, filtros 6–20
    static final int MINER_SCANNER = 1, MINER_FILTER_START = 6, MINER_FILTER_END = 21;
    /** IC2: 512 EU por bloco minerado, 64 EU do scanner por bloco olhado. */
    private static final long MINE_ENERGY = 256_000;
    private static final long SCAN_ENERGY = EnergyUnits.fromCWh(32);
    private static final int MINER_BLOCKS_PER_CYCLE = 32;

    // fabricador em lote: descarga 0, molde 1–9, saída 10, ingredientes 11–19, recipientes 20–28
    static final int GRID_START = 1, CRAFT_OUTPUT = 10, INGREDIENT_START = 11, CONTAINER_START = 20;

    // O-Mats: pedido 0; Energy-O-Mat: upgrade 1, pagamento 2, carga 3; Trade-O-Mat: oferta 1, pagamento 2, saída 3
    static final int OMAT_DEMAND = 0, OMAT_OFFER = 1, OMAT_INPUT = 2, OMAT_OUTPUT = 3;

    private final MachineBlockEntity machine;
    private final MachineGuiType type;
    private boolean working;
    private int ticker;
    private @Nullable UUID owner;

    // terraformador
    private @Nullable BlockPos lastPos;
    private int failedAttempts;

    // minerador
    private @Nullable BlockPos mineTarget;
    private boolean blacklist = true;
    private boolean silkTouch;

    // fabricador em lote
    private int progress;
    private @Nullable RecipeHolder<CraftingRecipe> recipe;
    private ItemStack preview = ItemStack.EMPTY;

    // O-Mats
    private int euOffer = 1_000;
    private long paidFor;
    private int totalTrades;
    private int stock;

    private AutomationLogic(MachineBlockEntity machine, MachineGuiType type) {
        this.machine = machine;
        this.type = type;
    }

    static @Nullable AutomationLogic create(MachineBlockEntity machine, MachineGuiType type) {
        return switch (type) {
            case TERRAFORMER, ADVANCED_MINER, BATCH_CRAFTER, ENERGY_O_MAT, TRADE_O_MAT -> new AutomationLogic(machine, type);
            default -> null;
        };
    }

    boolean working() {
        return this.working;
    }

    // ── slots ─────────────────────────────────────────────────────────────
    boolean canPlace(int slot, ItemStack stack) {
        return switch (this.type) {
            case ADVANCED_MINER -> slot != MINER_SCANNER || stack.getItem() instanceof ScannerItem;
            case BATCH_CRAFTER -> {
                if (slot < INGREDIENT_START || slot >= CONTAINER_START) yield true;
                ItemStack pattern = this.machine.getInventory().getItem(GRID_START + slot - INGREDIENT_START);
                yield !pattern.isEmpty() && ItemStack.isSameItemSameComponents(pattern, stack);
            }
            case TERRAFORMER -> blueprint(stack) != null;
            default -> true;
        };
    }

    int[] ejectSlots() {
        return this.type == MachineGuiType.BATCH_CRAFTER ? IntStream.concat(IntStream.of(CRAFT_OUTPUT), IntStream.range(CONTAINER_START, CONTAINER_START + 9)).toArray() : null;
    }

    int[] pullSlots() {
        return this.type == MachineGuiType.BATCH_CRAFTER ? IntStream.range(INGREDIENT_START, INGREDIENT_START + 9).toArray() : null;
    }

    // ── dono (O-Mats) ─────────────────────────────────────────────────────
    void onPlacedBy(LivingEntity placer) {
        if ((this.type == MachineGuiType.ENERGY_O_MAT || this.type == MachineGuiType.TRADE_O_MAT) && placer instanceof Player player) {
            this.owner = player.getUUID();
            this.machine.setChanged();
        }
    }

    /** Só o dono configura os O-Mats (pedido, oferta, preço) e pode quebrá-los. */
    boolean isOwner(Player player) {
        return this.owner == null || this.owner.equals(player.getUUID()) || player.getAbilities().instabuild;
    }

    /** Terraformador: clicar tira a planta; com uma planta na mão, coloca. */
    boolean handleUse(Player player) {
        if (this.type != MachineGuiType.TERRAFORMER) return false;
        if (player.level().isClientSide()) return true;
        SimpleContainer inventory = this.machine.getInventory();
        ItemStack current = inventory.getItem(0);
        if (!current.isEmpty()) {
            inventory.setItem(0, ItemStack.EMPTY);
            if (!player.getInventory().add(current)) player.drop(current, false);
            return true;
        }
        ItemStack held = player.getMainHandItem();
        if (blueprint(held) != null) {
            inventory.setItem(0, held.split(1));
        }
        return true;
    }

    // ── GUI ───────────────────────────────────────────────────────────────
    @Nullable Integer dataValue(int field) {
        return switch (this.type) {
            case ADVANCED_MINER -> switch (field) {
                case MachineBlockEntity.DATA_MODE -> (this.blacklist ? 1 : 0) | (this.silkTouch ? 2 : 0);
                case MachineBlockEntity.DATA_HEAT -> this.mineTarget == null ? Integer.MIN_VALUE : this.mineTarget.getY();
                default -> null;
            };
            case BATCH_CRAFTER -> field == MachineBlockEntity.DATA_HEAT
                    ? (this.preview.isEmpty() ? 0 : BuiltInRegistries.ITEM.getId(this.preview.getItem()) + 1) : null;
            case ENERGY_O_MAT -> switch (field) {
                case MachineBlockEntity.DATA_MODE -> this.euOffer;
                case MachineBlockEntity.DATA_HEAT -> (int) Math.min(Integer.MAX_VALUE, this.paidFor / 500);
                default -> null;
            };
            case TRADE_O_MAT -> switch (field) {
                case MachineBlockEntity.DATA_MODE -> this.totalTrades;
                case MachineBlockEntity.DATA_HEAT -> this.stock;
                default -> null;
            };
            default -> null;
        };
    }

    boolean handleButton(int id) {
        int button = id - MachineBlockEntity.BUTTON_AUTOMATION;
        boolean handled = switch (this.type) {
            case ADVANCED_MINER -> switch (button) {
                case 0 -> {
                    this.mineTarget = null;
                    yield true;
                }
                case 1 -> {
                    this.blacklist = !this.blacklist;
                    yield true;
                }
                case 2 -> {
                    this.silkTouch = !this.silkTouch;
                    yield true;
                }
                default -> false;
            };
            case ENERGY_O_MAT -> {
                int[] steps = {-100_000, -10_000, -1_000, -100, 100_000, 10_000, 1_000, 100};
                if (button < 10 || button >= 10 + steps.length) yield false;
                this.euOffer = Math.max(100, Math.min(100_000_000, this.euOffer + steps[button - 10]));
                yield true;
            }
            default -> false;
        };
        if (handled) this.machine.setChanged();
        return handled;
    }

    /** Energy-O-Mat: só puxa da rede o que foi pago. */
    long demandLimit() {
        return this.type == MachineGuiType.ENERGY_O_MAT ? Math.max(0, this.paidFor) : Long.MAX_VALUE;
    }

    // ── tick ──────────────────────────────────────────────────────────────
    boolean tick(Level level) {
        this.working = false;
        if (!(level instanceof ServerLevel server)) return false;
        return switch (this.type) {
            case TERRAFORMER -> tickTerraformer(server);
            case ADVANCED_MINER -> tickMiner(server);
            case BATCH_CRAFTER -> tickBatchCrafter(server);
            case ENERGY_O_MAT -> tickEnergyOMat(server);
            case TRADE_O_MAT -> tickTradeOMat(server);
            default -> false;
        };
    }

    // ── terraformador ─────────────────────────────────────────────────────
    private enum Blueprint {
        CULTIVATION(20_000, 40), IRRIGATION(3_000, 60), CHILLING(2_000, 50), DESERTIFICATION(2_500, 40),
        FLATIFICATION(4_000, 40), MUSHROOM(8_000, 25);

        /** EU por operação e alcance (IC2: TfbpType). */
        final long consume;
        final int range;

        Blueprint(int eu, int range) {
            this.consume = eu * 500L;
            this.range = range;
        }
    }

    private static @Nullable Blueprint blueprint(ItemStack stack) {
        if (stack.is(IC2AutoItems.TFBP_CULTIVATION.get())) return Blueprint.CULTIVATION;
        if (stack.is(IC2AutoItems.TFBP_IRRIGATION.get())) return Blueprint.IRRIGATION;
        if (stack.is(IC2AutoItems.TFBP_CHILLING.get())) return Blueprint.CHILLING;
        if (stack.is(IC2AutoItems.TFBP_DESERTIFICATION.get())) return Blueprint.DESERTIFICATION;
        if (stack.is(IC2AutoItems.TFBP_FLATIFICATION.get())) return Blueprint.FLATIFICATION;
        if (stack.is(IC2AutoItems.TFBP_MUSHROOM.get())) return Blueprint.MUSHROOM;
        return null;
    }

    /**
     * Terraformador do IC2: a cada tick com energia escolhe uma coluna perto da última que deu certo
     * (ou, depois de falhas, cada vez mais longe) e aplica a planta nela.
     */
    private boolean tickTerraformer(ServerLevel level) {
        Blueprint blueprint = blueprint(this.machine.getInventory().getItem(0));
        if (blueprint == null || this.machine.getStoredEnergy() < blueprint.consume) return false;
        this.working = true;
        RandomSource random = level.getRandom();
        BlockPos pos = this.machine.getBlockPos();
        BlockPos next;
        if (this.lastPos != null) {
            int range = blueprint.range / 10;
            next = new BlockPos(this.lastPos.getX() - random.nextInt(range + 1) + random.nextInt(range + 1), pos.getY(),
                    this.lastPos.getZ() - random.nextInt(range + 1) + random.nextInt(range + 1));
        } else {
            this.failedAttempts = Math.min(this.failedAttempts, 4);
            int range = blueprint.range * (this.failedAttempts + 1) / 5;
            next = new BlockPos(pos.getX() - random.nextInt(range + 1) + random.nextInt(range + 1), pos.getY(),
                    pos.getZ() - random.nextInt(range + 1) + random.nextInt(range + 1));
        }
        if (terraform(level, blueprint, next)) {
            this.machine.useEnergy(blueprint.consume);
            this.failedAttempts = 0;
            this.lastPos = next;
        } else {
            this.machine.useEnergy(blueprint.consume / 10);
            this.failedAttempts++;
            this.lastPos = null;
        }
        return true;
    }

    private boolean terraform(ServerLevel level, Blueprint blueprint, BlockPos column) {
        if (!level.isLoaded(column)) return false;
        int surfaceY = level.getHeight(Heightmap.Types.WORLD_SURFACE, column.getX(), column.getZ()) - 1;
        BlockPos top = new BlockPos(column.getX(), surfaceY, column.getZ());
        BlockState state = level.getBlockState(top);
        RandomSource random = level.getRandom();
        return switch (blueprint) {
            case CULTIVATION -> {
                if (state.is(Blocks.SAND) || state.is(Blocks.DIRT) || state.is(Blocks.COARSE_DIRT)) {
                    level.setBlockAndUpdate(top, Blocks.GRASS_BLOCK.defaultBlockState());
                    yield true;
                }
                if (state.is(Blocks.GRASS_BLOCK) && level.isEmptyBlock(top.above())) {
                    BlockState plant = random.nextInt(8) == 0 ? Blocks.OAK_SAPLING.defaultBlockState()
                            : random.nextInt(4) == 0 ? (random.nextBoolean() ? Blocks.POPPY : Blocks.DANDELION).defaultBlockState()
                            : Blocks.SHORT_GRASS.defaultBlockState();
                    level.setBlockAndUpdate(top.above(), plant);
                    yield true;
                }
                yield false;
            }
            case IRRIGATION -> {
                if (state.is(Blocks.FARMLAND)) {
                    level.setBlockAndUpdate(top, state.setValue(net.minecraft.world.level.block.FarmlandBlock.MOISTURE, 7));
                    yield true;
                }
                if (state.getBlock() instanceof BonemealableBlock growable && growable.isValidBonemealTarget(level, top, state)) {
                    growable.performBonemeal(level, random, top, state);
                    yield true;
                }
                yield false;
            }
            case CHILLING -> {
                if (state.is(Blocks.WATER)) {
                    level.setBlockAndUpdate(top, Blocks.ICE.defaultBlockState());
                    yield true;
                }
                if (state.is(Blocks.LAVA)) {
                    level.setBlockAndUpdate(top, Blocks.OBSIDIAN.defaultBlockState());
                    yield true;
                }
                BlockState snow = Blocks.SNOW.defaultBlockState();
                if (level.isEmptyBlock(top.above()) && snow.canSurvive(level, top.above())) {
                    level.setBlockAndUpdate(top.above(), snow);
                    yield true;
                }
                yield false;
            }
            case DESERTIFICATION -> {
                if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT) || state.is(Blocks.FARMLAND) || state.is(Blocks.MYCELIUM)
                        || state.is(Blocks.PODZOL) || state.is(Blocks.COARSE_DIRT)) {
                    level.setBlockAndUpdate(top, Blocks.SAND.defaultBlockState());
                    yield true;
                }
                if (state.is(Blocks.ICE) || state.is(Blocks.SNOW_BLOCK)) {
                    level.setBlockAndUpdate(top, Blocks.WATER.defaultBlockState());
                    yield true;
                }
                if (state.is(Blocks.SNOW) || state.is(BlockTags.LEAVES) || state.is(BlockTags.FLOWERS) || state.is(Blocks.SHORT_GRASS)
                        || state.is(Blocks.TALL_GRASS) || state.is(Blocks.FERN)) {
                    level.setBlockAndUpdate(top, Blocks.AIR.defaultBlockState());
                    yield true;
                }
                yield false;
            }
            case FLATIFICATION -> {
                int targetY = this.machine.getBlockPos().getY();
                if (surfaceY > targetY) {
                    if (state.getDestroySpeed(level, top) < 0 || level.getBlockEntity(top) != null) yield false;
                    level.removeBlock(top, false);
                    yield true;
                }
                if (surfaceY < targetY - 1 && !(state.getBlock() instanceof LiquidBlock)) {
                    level.setBlockAndUpdate(top.above(), Blocks.DIRT.defaultBlockState());
                    yield true;
                }
                yield false;
            }
            case MUSHROOM -> {
                if (state.is(Blocks.GRASS_BLOCK) || state.is(Blocks.DIRT)) {
                    level.setBlockAndUpdate(top, Blocks.MYCELIUM.defaultBlockState());
                    yield true;
                }
                if (state.is(Blocks.MYCELIUM) && level.isEmptyBlock(top.above()) && random.nextInt(5) == 0) {
                    level.setBlockAndUpdate(top.above(), (random.nextBoolean() ? Blocks.RED_MUSHROOM : Blocks.BROWN_MUSHROOM).defaultBlockState());
                    yield true;
                }
                if ((state.is(Blocks.RED_MUSHROOM) || state.is(Blocks.BROWN_MUSHROOM)) && state.getBlock() instanceof BonemealableBlock growable) {
                    growable.performBonemeal(level, random, top, state);
                    yield true;
                }
                yield false;
            }
        };
    }

    // ── minerador avançado ────────────────────────────────────────────────
    /**
     * Minerador avançado do IC2: com o scanner, a cada segundo percorre um quadrado de 2×alcance+1 camada
     * por camada a partir de baixo dele, e minera o primeiro bloco que o filtro (lista negra ou branca)
     * deixar. O que sai vai para inventários vizinhos (ou cai em cima dele).
     */
    private boolean tickMiner(ServerLevel level) {
        SimpleContainer inventory = this.machine.getInventory();
        ItemStack scanner = inventory.getItem(MINER_SCANNER);
        if (scanner.getItem() instanceof ScannerItem && this.machine.getStoredEnergy() > 0) {
            long moved = EnergyItems.charge(scanner, this.machine.getStoredEnergy(), Integer.MAX_VALUE, false);
            if (moved > 0) this.machine.useEnergy(moved);
        }
        BlockPos pos = this.machine.getBlockPos();
        if (level.hasNeighborSignal(pos) || this.machine.getStoredEnergy() < MINE_ENERGY) return false;
        if (!(scanner.getItem() instanceof ScannerItem scannerItem) || EnergyItems.getStored(scanner) < SCAN_ENERGY) return false;
        this.working = true;
        if (++this.ticker < 20) return false;
        this.ticker = 0;

        int range = Math.max(1, scannerItem.scanRange());
        int minY = level.getMinY();
        if (this.mineTarget == null) this.mineTarget = new BlockPos(pos.getX() - range - 1, pos.getY() - 1, pos.getZ() - range);
        if (this.mineTarget.getY() < minY) return false;

        int x = this.mineTarget.getX(), y = this.mineTarget.getY(), z = this.mineTarget.getZ();
        for (int scanned = 0; scanned < MINER_BLOCKS_PER_CYCLE && EnergyItems.getStored(scanner) >= SCAN_ENERGY; scanned++) {
            if (x < pos.getX() + range) {
                x++;
            } else if (z < pos.getZ() + range) {
                x = pos.getX() - range;
                z++;
            } else {
                x = pos.getX() - range;
                z = pos.getZ() - range;
                y--;
                if (y < minY) {
                    this.mineTarget = new BlockPos(x, y, z);
                    return true;
                }
            }
            EnergyItems.discharge(scanner, SCAN_ENERGY, Integer.MAX_VALUE, false);
            BlockPos target = new BlockPos(x, y, z);
            this.mineTarget = target;
            if (!level.isLoaded(target)) break;
            BlockState state = level.getBlockState(target);
            if (state.isAir()) continue;
            List<ItemStack> drops = minableDrops(level, target, state);
            if (drops == null) continue;
            for (ItemStack drop : drops) deliver(level, drop);
            level.destroyBlock(target, false);
            this.machine.useEnergy(MINE_ENERGY);
            break;
        }
        inventory.setItem(MINER_SCANNER, scanner);
        return true;
    }

    private @Nullable List<ItemStack> minableDrops(ServerLevel level, BlockPos pos, BlockState state) {
        if (!state.getFluidState().isEmpty() || state.getDestroySpeed(level, pos) < 0 || level.getBlockEntity(pos) != null) return null;
        ItemStack tool = new ItemStack(Items.DIAMOND_PICKAXE);
        if (this.silkTouch) {
            tool.enchant(level.registryAccess().lookupOrThrow(Registries.ENCHANTMENT).getOrThrow(Enchantments.SILK_TOUCH), 1);
        }
        List<ItemStack> drops = Block.getDrops(state, level, pos, null, null, tool);
        if (drops.isEmpty()) return null;
        boolean listed = false;
        SimpleContainer inventory = this.machine.getInventory();
        for (ItemStack drop : drops) {
            for (int slot = MINER_FILTER_START; slot < MINER_FILTER_END; slot++) {
                if (ItemStack.isSameItem(inventory.getItem(slot), drop)) listed = true;
            }
        }
        return listed == this.blacklist ? null : drops;
    }

    /** Coloca o item nos inventários vizinhos; o que não couber cai em cima da máquina. */
    private void deliver(ServerLevel level, ItemStack stack) {
        BlockPos pos = this.machine.getBlockPos();
        for (Direction direction : Direction.values()) {
            if (stack.isEmpty()) return;
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos.relative(direction), direction.getOpposite());
            if (storage == null) continue;
            try (Transaction transaction = Transaction.openOuter()) {
                long inserted = storage.insert(ItemVariant.of(stack), stack.getCount(), transaction);
                transaction.commit();
                stack.shrink((int) inserted);
            }
        }
        if (!stack.isEmpty()) Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, stack);
    }

    // ── fabricador em lote ────────────────────────────────────────────────
    /** Fabricador em lote do IC2: fabrica sozinho a receita do molde usando os ingredientes da fileira de baixo. */
    private boolean tickBatchCrafter(ServerLevel level) {
        SimpleContainer inventory = this.machine.getInventory();
        List<ItemStack> pattern = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) pattern.add(inventory.getItem(GRID_START + i).copyWithCount(1));
        CraftingInput patternInput = CraftingInput.of(3, 3, pattern);
        if (this.recipe == null || !this.recipe.value().matches(patternInput, level)) {
            Optional<RecipeHolder<CraftingRecipe>> found = level.recipeAccess().getRecipeFor(RecipeType.CRAFTING, patternInput, level);
            this.recipe = found.orElse(null);
        }
        this.preview = this.recipe == null ? ItemStack.EMPTY : this.recipe.value().assemble(patternInput);
        int ticks = Math.max(1, this.machine.getEnergyProfile().operationTicks());
        this.machine.setProgressDisplay(this.progress, ticks);
        if (this.recipe == null || this.preview.isEmpty() || !fits(inventory.getItem(CRAFT_OUTPUT), this.preview)) {
            this.progress = 0;
            return false;
        }
        List<ItemStack> actual = new ArrayList<>(9);
        for (int i = 0; i < 9; i++) {
            ItemStack wanted = pattern.get(i);
            ItemStack available = inventory.getItem(INGREDIENT_START + i);
            if (!wanted.isEmpty() && (available.isEmpty() || !ItemStack.isSameItemSameComponents(wanted, available))) {
                this.progress = 0;
                return false;
            }
            actual.add(wanted.isEmpty() ? ItemStack.EMPTY : available.copyWithCount(1));
        }
        if (!this.machine.useEnergy(this.machine.getEnergyProfile().power())) return false;
        this.working = true;
        if (++this.progress < ticks) return true;
        this.progress = 0;

        CraftingInput input = CraftingInput.of(3, 3, actual);
        ItemStack result = this.recipe.value().assemble(input);
        List<ItemStack> remaining = this.recipe.value().getRemainingItems(input);
        for (int i = 0; i < 9; i++) {
            if (!pattern.get(i).isEmpty()) {
                ItemStack stack = inventory.getItem(INGREDIENT_START + i);
                stack.shrink(1);
                inventory.setItem(INGREDIENT_START + i, stack);
            }
        }
        addTo(inventory, CRAFT_OUTPUT, result);
        for (ItemStack leftover : remaining) {
            if (leftover.isEmpty()) continue;
            boolean placed = false;
            for (int slot = CONTAINER_START; slot < CONTAINER_START + 9 && !placed; slot++) {
                if (fits(inventory.getItem(slot), leftover)) {
                    addTo(inventory, slot, leftover);
                    placed = true;
                }
            }
            if (!placed) {
                BlockPos pos = this.machine.getBlockPos();
                Containers.dropItemStack(level, pos.getX() + 0.5, pos.getY() + 1.2, pos.getZ() + 0.5, leftover);
            }
        }
        return true;
    }

    private static boolean fits(ItemStack slot, ItemStack stack) {
        return slot.isEmpty() || ItemStack.isSameItemSameComponents(slot, stack) && slot.getCount() + stack.getCount() <= slot.getMaxStackSize();
    }

    private static void addTo(SimpleContainer inventory, int slot, ItemStack stack) {
        ItemStack current = inventory.getItem(slot);
        if (current.isEmpty()) {
            inventory.setItem(slot, stack.copy());
        } else {
            current.grow(stack.getCount());
            inventory.setItem(slot, current);
        }
    }

    // ── O-Mats ────────────────────────────────────────────────────────────
    /** Tira o pagamento do slot e guarda nos inventários vizinhos; false se não couber. */
    private boolean collectPayment(ServerLevel level, @Nullable Transaction outer) {
        SimpleContainer inventory = this.machine.getInventory();
        ItemStack demand = inventory.getItem(OMAT_DEMAND);
        ItemStack paid = inventory.getItem(OMAT_INPUT);
        return !demand.isEmpty() && ItemStack.isSameItemSameComponents(demand, paid) && paid.getCount() >= demand.getCount();
    }

    private Storage<ItemVariant> neighbors(ServerLevel level) {
        List<Storage<ItemVariant>> parts = new ArrayList<>();
        BlockPos pos = this.machine.getBlockPos();
        for (Direction direction : Direction.values()) {
            if (level.getBlockEntity(pos.relative(direction)) instanceof MachineBlockEntity other
                    && (other.getGuiType() == MachineGuiType.TRADE_O_MAT || other.getGuiType() == MachineGuiType.ENERGY_O_MAT)) continue;
            Storage<ItemVariant> storage = ItemStorage.SIDED.find(level, pos.relative(direction), direction.getOpposite());
            if (storage != null) parts.add(storage);
        }
        return new net.fabricmc.fabric.api.transfer.v1.storage.base.CombinedStorage<>(parts);
    }

    /**
     * Energy-O-Mat do IC2: quem paga o pedido ganha crédito de energia (o preço em EU); a máquina puxa da
     * rede só o que foi pago e carrega o item do slot de carga. O pagamento vai para inventários vizinhos.
     */
    private boolean tickEnergyOMat(ServerLevel level) {
        SimpleContainer inventory = this.machine.getInventory();
        boolean changed = false;
        long gained = this.machine.getStoredEnergy() - this.machine.lastEnergySnapshot();
        if (gained > 0) this.paidFor = Math.max(0, this.paidFor - gained);

        if (collectPayment(level, null)) {
            ItemStack demand = inventory.getItem(OMAT_DEMAND);
            try (Transaction transaction = Transaction.openOuter()) {
                long stored = neighbors(level).insert(ItemVariant.of(demand), demand.getCount(), transaction);
                if (stored == demand.getCount()) {
                    transaction.commit();
                    ItemStack paid = inventory.getItem(OMAT_INPUT);
                    paid.shrink(demand.getCount());
                    inventory.setItem(OMAT_INPUT, paid);
                    this.paidFor += this.euOffer * 500L;
                    changed = true;
                }
            }
        }
        ItemStack charge = inventory.getItem(OMAT_OUTPUT);
        if (!charge.isEmpty() && this.machine.getStoredEnergy() > 0) {
            long moved = EnergyItems.charge(charge, this.machine.getStoredEnergy(), Integer.MAX_VALUE, false);
            if (moved > 0) {
                this.machine.useEnergy(moved);
                inventory.setItem(OMAT_OUTPUT, charge);
                this.working = true;
                changed = true;
            }
        }
        this.machine.snapshotEnergy();
        return changed;
    }

    /**
     * Trade-O-Mat do IC2: quem coloca o pedido recebe a oferta, tirada dos inventários vizinhos (o
     * estoque do dono); o pagamento vai para esses inventários.
     */
    private boolean tickTradeOMat(ServerLevel level) {
        SimpleContainer inventory = this.machine.getInventory();
        ItemStack offer = inventory.getItem(OMAT_OFFER);
        if (++this.ticker % 64 == 0 && !offer.isEmpty()) {
            long available = StorageUtil.simulateExtract(neighbors(level), ItemVariant.of(offer), Long.MAX_VALUE, null);
            this.stock = (int) Math.min(Integer.MAX_VALUE, available / Math.max(1, offer.getCount()));
        }
        if (offer.isEmpty() || !collectPayment(level, null) || !fits(inventory.getItem(OMAT_OUTPUT), offer)) return false;
        ItemStack demand = inventory.getItem(OMAT_DEMAND);
        Storage<ItemVariant> stockRoom = neighbors(level);
        try (Transaction transaction = Transaction.openOuter()) {
            long taken = stockRoom.extract(ItemVariant.of(offer), offer.getCount(), transaction);
            long stored = stockRoom.insert(ItemVariant.of(demand), demand.getCount(), transaction);
            if (taken != offer.getCount() || stored != demand.getCount()) return false;
            transaction.commit();
        }
        ItemStack paid = inventory.getItem(OMAT_INPUT);
        paid.shrink(demand.getCount());
        inventory.setItem(OMAT_INPUT, paid);
        addTo(inventory, OMAT_OUTPUT, offer.copy());
        this.totalTrades++;
        this.stock = Math.max(0, this.stock - 1);
        this.working = true;
        level.playSound(null, this.machine.getBlockPos(), net.minecraft.sounds.SoundEvents.VILLAGER_YES, net.minecraft.sounds.SoundSource.BLOCKS, 0.5F, 1.0F);
        return true;
    }

    // ── salvar/carregar ───────────────────────────────────────────────────
    void write(ValueOutput output) {
        if (this.owner != null) output.putString("AutomationOwner", this.owner.toString());
        if (this.mineTarget != null) output.putLong("MineTarget", this.mineTarget.asLong());
        output.putInt("MinerBlacklist", this.blacklist ? 1 : 0);
        output.putInt("MinerSilk", this.silkTouch ? 1 : 0);
        output.putInt("CraftProgress", this.progress);
        output.putInt("EuOffer", this.euOffer);
        output.putLong("PaidFor", this.paidFor);
        output.putInt("TotalTrades", this.totalTrades);
    }

    void read(ValueInput input) {
        this.owner = input.getString("AutomationOwner").map(value -> {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }).orElse(null);
        this.mineTarget = input.getLong("MineTarget").map(BlockPos::of).orElse(null);
        this.blacklist = input.getIntOr("MinerBlacklist", 1) != 0;
        this.silkTouch = input.getIntOr("MinerSilk", 0) != 0;
        this.progress = Math.max(0, input.getIntOr("CraftProgress", 0));
        this.euOffer = Math.max(100, input.getIntOr("EuOffer", 1_000));
        this.paidFor = Math.max(0, input.getLongOr("PaidFor", 0));
        this.totalTrades = Math.max(0, input.getIntOr("TotalTrades", 0));
    }
}
