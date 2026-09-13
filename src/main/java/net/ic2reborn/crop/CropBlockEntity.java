package net.ic2reborn.crop;

import net.fabricmc.fabric.api.tag.convention.v2.ConventionalBiomeTags;
import net.fabricmc.fabric.api.transfer.v1.context.ContainerItemContext;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidConstants;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidStorage;
import net.fabricmc.fabric.api.transfer.v1.fluid.FluidVariant;
import net.fabricmc.fabric.api.transfer.v1.storage.Storage;
import net.fabricmc.fabric.api.transfer.v1.transaction.Transaction;
import net.ic2reborn.fluid.IC2Fluids;
import net.ic2reborn.item.CropSeedItem;
import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2BlockEntities;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.FarmlandBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * Plantação do IC2 ({@code TileEntityCrop}): a vareta, a planta que está nela e os seus atributos.
 *
 * <p>A cada 256 ticks (com início aleatório) a planta cresce conforme umidade, nutrientes e ar;
 * varetas vazias podem pegar erva daninha, e varetas duplas cruzam as plantas vizinhas. Os
 * atributos Crescimento (Gr), Ganho (Ga) e Resistência (Re) vão de 0 a 31.
 */
public class CropBlockEntity extends BlockEntity {
    public static final int TICK_RATE = 256;
    private static final int MAX_STAT = 31;

    private long ticker = -1;
    private @Nullable CropCard crop;
    private int biomeHumidityBonus;
    private int statGrowth;
    private int statGain;
    private int statResistance;
    private int storageNutrients;
    private int storageWater;
    private int storageWeedEx;
    private int terrainAirQuality = -1;
    private int terrainHumidity = -1;
    private int terrainNutrients = -1;
    private int size = 1;
    private int growthPoints;
    private int scanLevel;
    private boolean crossingBase;
    private boolean eaten;
    private boolean removing;

    public CropBlockEntity(BlockPos pos, BlockState state) {
        super(IC2BlockEntities.CROP.get(), pos, state);
    }

    public static void serverTick(Level level, BlockPos pos, BlockState state, CropBlockEntity crop) {
        if (crop.ticker < 0) {
            crop.ticker = level.getRandom().nextInt(TICK_RATE);
            crop.updateBiomeHumidityBonus();
        }
        crop.ticker++;
        if (crop.ticker % TICK_RATE == 0) {
            crop.performTick();
        }
    }

    // ── tick ──────────────────────────────────────────────────────────────
    /** Um ciclo da plantação (normalmente a cada 256 ticks). */
    public void performTick() {
        if (!(this.level instanceof ServerLevel)) return;
        RandomSource random = random();
        long tick = Math.max(0, this.ticker);
        if (tick % ((long) TICK_RATE * 10 << 2) == 0) updateBiomeHumidityBonus();
        if (tick % (TICK_RATE << 2) == 0) updateTerrainHumidity();
        if ((tick + TICK_RATE) % (TICK_RATE << 2) == 0) updateTerrainNutrients();
        if ((tick + TICK_RATE * 2L) % (TICK_RATE << 2) == 0) updateTerrainAirQuality();

        if (this.crop == null && (!this.crossingBase || !attemptCrossing()) && (!this.crossingBase || !attemptSpreading())) {
            if (random.nextInt(100) != 0 || this.storageWeedEx > 0) {
                if (this.storageWeedEx > 0 && random.nextInt(10) == 0) this.storageWeedEx--;
                changed();
                return;
            }
            reset();
            this.crop = CropCards.WEED;
            this.size = 1;
        }

        CropCard current = this.crop;
        current.tick(this);
        if (this.crop == null) {
            changed();
            return;
        }
        if (this.crop.canGrow(this)) {
            performGrowthTick();
            if (this.crop == null) {
                changed();
                return;
            }
            if (this.growthPoints >= this.crop.growthDuration(this)) {
                this.growthPoints = 0;
                this.size++;
            }
        }
        if (this.storageNutrients > 0) this.storageNutrients--;
        if (this.storageWater > 0) this.storageWater--;
        if (this.crop.isWeed(this) && random.nextInt(50) - this.statGrowth <= 2) {
            performWeedWork();
        }
        changed();
    }

    private void performGrowthTick() {
        if (this.crop == null) return;
        RandomSource random = random();
        int baseGrowth = 3 + random.nextInt(7) + this.statGrowth;
        int minimumQuality = Math.max(0, (this.crop.properties().tier() - 1) * 4 + this.statGrowth + this.statGain + this.statResistance);
        int providedQuality = this.crop.weightInfluences(this, this.terrainHumidity, this.terrainNutrients, this.terrainAirQuality) * 5;
        int totalGrowth;
        if (providedQuality >= minimumQuality) {
            totalGrowth = baseGrowth * (100 + (providedQuality - minimumQuality)) / 100;
        } else {
            int aux = (minimumQuality - providedQuality) * 4;
            if (aux > 100 && random.nextInt(32) > this.statResistance) {
                // condições ruins demais: a planta morre
                reset();
                totalGrowth = 0;
            } else {
                totalGrowth = Math.max(0, baseGrowth * (100 - aux) / 100);
            }
        }
        this.growthPoints += totalGrowth;
    }

    /** Erva daninha invade a vareta vizinha (ou vira grama num bloco vazio de terra). */
    private void performWeedWork() {
        if (this.level == null) return;
        BlockPos target = this.worldPosition.relative(Direction.Plane.HORIZONTAL.getRandomDirection(random()));
        if (this.level.getBlockEntity(target) instanceof CropBlockEntity neighbour) {
            CropCard neighbourCrop = neighbour.crop;
            if (neighbourCrop == null || !neighbourCrop.isWeed(neighbour)
                    && random().nextInt(32) >= neighbour.statResistance && !neighbour.useWeedEx()) {
                int newGrowth = Math.max(this.statGrowth, neighbour.statGrowth);
                if (newGrowth < MAX_STAT && random().nextBoolean()) newGrowth++;
                neighbour.reset();
                neighbour.crop = CropCards.WEED;
                neighbour.size = 1;
                neighbour.statGrowth = newGrowth;
                neighbour.changed();
            }
        } else if (this.level.getBlockState(target).isAir()) {
            BlockState soil = this.level.getBlockState(target.below());
            if (soil.is(Blocks.DIRT) || soil.is(Blocks.GRASS_BLOCK) || soil.is(Blocks.FARMLAND)) {
                this.level.setBlock(target.below(), Blocks.GRASS_BLOCK.defaultBlockState(), Block.UPDATE_ALL);
                this.level.setBlock(target, Blocks.SHORT_GRASS.defaultBlockState(), Block.UPDATE_ALL);
            }
        }
    }

    private boolean useWeedEx() {
        if (this.storageWeedEx > 0) {
            this.storageWeedEx -= 5;
            return true;
        }
        return false;
    }

    // ── cruzamento ────────────────────────────────────────────────────────
    private void checkCrossingAvailability(BlockPos pos, List<CropBlockEntity> neighbours) {
        if (this.level != null && this.level.getBlockEntity(pos) instanceof CropBlockEntity side
                && side.crop != null && side.crop.canCross(side)) {
            int base = 4;
            if (side.statGrowth >= 16) base++;
            if (side.statGrowth >= 30) base++;
            if (side.statResistance >= 28) base += 27 - side.statResistance;
            if (base >= random().nextInt(20)) neighbours.add(side);
        }
    }

    /** Vareta dupla entre duas ou mais plantas maduras: sorteia uma planta nova, puxando para as parecidas. */
    private boolean attemptCrossing() {
        RandomSource random = random();
        if (random.nextInt(3) != 0) return false;
        List<CropBlockEntity> neighbours = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            checkCrossingAvailability(this.worldPosition.relative(direction), neighbours);
        }
        if (neighbours.size() < 2) return false;

        List<CropCard> cards = CropCards.all();
        int[] ratios = new int[cards.size()];
        int total = 0;
        for (int index = 0; index < cards.size(); index++) {
            CropCard candidate = cards.get(index);
            if (candidate.canGrow(this)) {
                for (CropBlockEntity neighbour : neighbours) {
                    total += calculateRatioFor(candidate, neighbour.crop);
                }
            }
            ratios[index] = total;
        }
        if (total <= 0) return false;

        int search = random.nextInt(total);
        int min = 0;
        int max = ratios.length - 1;
        while (min < max) {
            int cur = (min + max) / 2;
            if (search < ratios[cur]) {
                max = cur;
            } else {
                min = cur + 1;
            }
        }

        this.crossingBase = false;
        setCrop(cards.get(min));
        this.size = 1;
        int growth = 0;
        int gain = 0;
        int resistance = 0;
        for (CropBlockEntity neighbour : neighbours) {
            growth += neighbour.statGrowth;
            gain += neighbour.statGain;
            resistance += neighbour.statResistance;
        }
        int count = neighbours.size();
        this.statGrowth = clampStat(growth / count + random.nextInt(1 + 2 * count) - count);
        this.statGain = clampStat(gain / count + random.nextInt(1 + 2 * count) - count);
        this.statResistance = clampStat(resistance / count + random.nextInt(1 + 2 * count) - count);
        return true;
    }

    private static int calculateRatioFor(CropCard newCrop, CropCard oldCrop) {
        if (newCrop == oldCrop) return 500;
        int value = 0;
        int[] propOld = oldCrop.properties().all();
        int[] propNew = newCrop.properties().all();
        for (int i = 0; i < 5; i++) {
            value += -Math.abs(propOld[i] - propNew[i]) + 2;
        }
        for (String attributeNew : newCrop.attributes()) {
            for (String attributeOld : oldCrop.attributes()) {
                if (attributeNew.equalsIgnoreCase(attributeOld)) value += 5;
            }
        }
        int diff = newCrop.properties().tier() - oldCrop.properties().tier();
        if (diff > 1) value -= 2 * diff;
        if (diff < -3) value -= -diff;
        return Math.max(value, 0);
    }

    /** Vareta dupla com uma única planta vizinha: a planta se espalha para ela. */
    private boolean attemptSpreading() {
        if (this.level == null) return false;
        List<CropBlockEntity> neighbours = new ArrayList<>(4);
        for (Direction direction : Direction.Plane.HORIZONTAL) {
            if (this.level.getBlockEntity(this.worldPosition.relative(direction)) instanceof CropBlockEntity side) {
                neighbours.add(side);
            }
        }
        if (neighbours.size() != 1) return false;
        CropBlockEntity side = neighbours.getFirst();
        if (side.crop == null || !side.crop.canGrow(this) || !side.crop.canCross(side)) return false;

        int base = 4;
        if (side.statGrowth >= 16) base++;
        if (side.statGrowth >= 30) base++;
        if (side.statResistance >= 28) base += 27 - side.statResistance;
        if (base < random().nextInt(16)) return false;

        this.crossingBase = false;
        setCrop(side.crop);
        this.size = 1;
        this.statGrowth = side.statGrowth;
        this.statResistance = side.statResistance;
        this.statGain = side.statGain;
        return true;
    }

    // ── terreno ───────────────────────────────────────────────────────────
    private void updateBiomeHumidityBonus() {
        if (this.level == null) return;
        Holder<Biome> biome = this.level.getBiome(this.worldPosition);
        float rainfall = biome.is(ConventionalBiomeTags.IS_WET) || biome.is(ConventionalBiomeTags.IS_AQUATIC) ? 0.9F
                : biome.is(ConventionalBiomeTags.IS_DRY) ? 0.0F : 0.5F;
        int rainfallBonus = Math.max(-10, Math.min(10, (int) (25.0F * rainfall - 12.5F)));
        float temperature = biome.value().getBaseTemperature();
        int coefficientBonus = (int) (Math.abs(rainfallBonus) * (-2.0 * Math.pow(temperature, 2.0) + 4.0F * temperature - 1.0));
        coefficientBonus = Math.max(-10, Math.min(10, coefficientBonus));
        this.biomeHumidityBonus = rainfallBonus + coefficientBonus;
    }

    private void updateTerrainHumidity() {
        if (this.level == null) return;
        int humidity = this.biomeHumidityBonus;
        BlockState soil = this.level.getBlockState(this.worldPosition.below());
        if (soil.hasProperty(FarmlandBlock.MOISTURE) && soil.getValue(FarmlandBlock.MOISTURE) >= 7) humidity += 2;
        if (this.storageWater >= 5) humidity += 2;
        humidity += (this.storageWater + 24) / 25;
        this.terrainHumidity = humidity;
    }

    private void updateTerrainNutrients() {
        if (this.level == null) return;
        int nutrients = biomeNutrientBonus(this.level.getBiome(this.worldPosition));
        for (int i = 1; i < 5 && this.level.getBlockState(this.worldPosition.below(i)).is(Blocks.DIRT); i++) {
            nutrients++;
        }
        nutrients += (this.storageNutrients + 19) / 20;
        this.terrainNutrients = nutrients;
    }

    /** IC2: o maior bônus de nutrientes entre os tipos do bioma (nunca negativo). */
    private static int biomeNutrientBonus(Holder<Biome> biome) {
        int bonus = 0;
        if (biome.is(ConventionalBiomeTags.IS_JUNGLE) || biome.is(ConventionalBiomeTags.IS_SWAMP)) bonus = Math.max(bonus, 10);
        if (biome.is(ConventionalBiomeTags.IS_MUSHROOM) || biome.is(ConventionalBiomeTags.IS_FOREST)) bonus = Math.max(bonus, 5);
        if (biome.is(ConventionalBiomeTags.IS_RIVER)) bonus = Math.max(bonus, 2);
        return bonus;
    }

    private void updateTerrainAirQuality() {
        if (this.level == null) return;
        int value = Math.max(0, Math.min(2, (int) Math.floor((this.worldPosition.getY() - 40) / 15.0)));
        int fresh = 9;
        for (int x = this.worldPosition.getX() - 1; x < this.worldPosition.getX() + 1 && fresh > 0; x++) {
            for (int z = this.worldPosition.getZ() - 1; z < this.worldPosition.getZ() + 1 && fresh > 0; z++) {
                BlockPos pos = new BlockPos(x, this.worldPosition.getY(), z);
                if (this.level.getBlockState(pos).isRedstoneConductor(this.level, pos)
                        || this.level.getBlockEntity(pos) instanceof CropBlockEntity) {
                    fresh--;
                }
            }
        }
        value += fresh / 2;
        if (this.level.canSeeSky(this.worldPosition.above())) value += 4;
        this.terrainAirQuality = value;
    }

    // ── interação ─────────────────────────────────────────────────────────
    /**
     * Clique direito com um item: vareta (vira vareta dupla), fertilizante, água ou herbicida,
     * semente-base ou saco de sementes. Retorna false se o item não serve aqui.
     */
    public boolean useItem(Player player, InteractionHand hand) {
        ItemStack held = player.getItemInHand(hand);
        if (held.isEmpty()) return false;
        boolean creative = player.getAbilities().instabuild;

        if (this.crop == null && !this.crossingBase && held.getItem() == IC2AutoItems.CROP_STICK.get()) {
            if (!creative) held.shrink(1);
            this.crossingBase = true;
            changed();
            return true;
        }
        if (this.crop != null && held.getItem() == IC2AutoItems.FERTILIZER.get()) {
            applyFertilizer(true);
            if (!creative) held.shrink(1);
            changed();
            return true;
        }
        if (FluidStorage.ITEM.find(held, ContainerItemContext.forPlayerInteraction(player, hand)) != null) {
            if (applyFluid(player, hand, Fluids.WATER, 200, true) || applyFluid(player, hand, IC2Fluids.WEED_EX.fluid(), 100, false)) {
                changed();
            }
            return true;
        }
        if (this.crop == null && !this.crossingBase) {
            CropCards.BaseSeed baseSeed = CropCards.baseSeed(held);
            if (baseSeed != null) {
                reset();
                setCrop(baseSeed.crop());
                this.size = baseSeed.size();
                this.statGrowth = baseSeed.growth();
                this.statGain = baseSeed.gain();
                this.statResistance = baseSeed.resistance();
                if (!creative) held.shrink(1);
                changed();
                return true;
            }
            CropSeedItem.CropSeed seed = CropSeedItem.data(held);
            if (seed != null && tryPlantIn(CropCards.byId(seed.crop()), 1, seed.growth(), seed.gain(), seed.resistance(), seed.scan())) {
                if (!creative) held.shrink(1);
                return true;
            }
        }
        return false;
    }

    /** Clique direito sem item útil: colhe. */
    public boolean harvestClick(Player player) {
        return this.crop != null && this.crop.onRightClick(this, player);
    }

    /** Clique esquerdo: arranca a planta (com chance de sementes) ou tira a vareta extra. */
    public void leftClick(Player player) {
        if (this.crop != null) {
            this.crop.onLeftClick(this, player);
        } else if (this.crossingBase && this.level != null) {
            this.crossingBase = false;
            Block.popResource(this.level, this.worldPosition, new ItemStack(IC2AutoItems.CROP_STICK.get()));
            changed();
        }
    }

    public boolean tryPlantIn(@Nullable CropCard card, int newSize, int growth, int gain, int resistance, int scan) {
        if (card == null || card == CropCards.WEED || this.crossingBase || !card.canGrow(this)) return false;
        reset();
        setCrop(card);
        this.size = newSize;
        this.statGain = clampStat(gain);
        this.statGrowth = clampStat(growth);
        this.statResistance = clampStat(resistance);
        this.scanLevel = scan;
        changed();
        return true;
    }

    public boolean applyFertilizer(boolean manual) {
        if (this.storageNutrients >= 100) return false;
        this.storageNutrients += manual ? 100 : 90;
        return true;
    }

    /** Tira água (limite 200) ou herbicida (limite 100) de um balde, célula ou recipiente de outros mods. */
    private boolean applyFluid(Player player, InteractionHand hand, Fluid fluid, int limit, boolean water) {
        int current = water ? this.storageWater : this.storageWeedEx;
        if (current >= limit) return false;
        Storage<FluidVariant> storage = ContainerItemContext.forPlayerInteraction(player, hand).find(FluidStorage.ITEM);
        if (storage == null) return false;
        long moved;
        try (Transaction transaction = Transaction.openOuter()) {
            FluidVariant variant = FluidVariant.of(fluid);
            moved = storage.extract(variant, (limit - current) * FluidConstants.BUCKET / 1000, transaction);
            if (moved <= 0) {
                // baldes e células só esvaziam inteiros
                moved = storage.extract(variant, FluidConstants.BUCKET, transaction);
            }
            if (moved <= 0) return false;
            transaction.commit();
        }
        int millibuckets = (int) Math.min(limit - current, moved * 1000 / FluidConstants.BUCKET);
        if (water) {
            this.storageWater += millibuckets;
        } else {
            this.storageWeedEx += millibuckets;
        }
        return true;
    }

    /** Arranca a planta; conforme o tamanho e os atributos, solta sementes. */
    public boolean pick() {
        if (this.crop == null || this.level == null) return false;
        RandomSource random = random();
        boolean bonus = this.crop.canBeHarvested(this);
        float firstChance = (float) (this.crop.dropSeedChance(this) * Math.pow(1.1, this.statResistance));
        int dropCount = 0;
        if (bonus) {
            if (random.nextFloat() <= (firstChance + 1.0F) * 0.8F) dropCount++;
            float chance = this.crop.dropSeedChance(this) + this.statGrowth / 100.0F;
            for (int index = 23; index < this.statGain; index++) {
                chance *= 0.95F;
            }
            if (random.nextFloat() <= chance) dropCount++;
        } else if (random.nextFloat() <= firstChance * 1.5F) {
            dropCount++;
        }
        List<ItemStack> drops = new ArrayList<>(dropCount);
        for (int index = 0; index < dropCount; index++) {
            drops.add(this.crop.seeds(this));
        }
        reset();
        for (ItemStack drop : drops) {
            Block.popResource(this.level, this.worldPosition, drop);
        }
        if (!this.removing) changed();
        return true;
    }

    public boolean performManualHarvest() {
        List<ItemStack> drops = performHarvest();
        if (drops == null || drops.isEmpty() || this.level == null) return false;
        for (ItemStack stack : drops) {
            Block.popResource(this.level, this.worldPosition, stack);
        }
        return true;
    }

    /** Colheita: quantidade sorteada pelo ganho; cada item pode vir em dobro com chance de Ga%. */
    public @Nullable List<ItemStack> performHarvest() {
        if (this.crop == null || !this.crop.canBeHarvested(this)) return null;
        RandomSource random = random();
        double chance = this.crop.dropGainChance() * Math.pow(1.03, this.statGain);
        int dropCount = (int) Math.max(0L, Math.round(random.nextGaussian() * chance * 0.6827 + chance));
        List<ItemStack> result = new ArrayList<>();
        for (int i = 0; i < dropCount; i++) {
            for (ItemStack drop : this.crop.gains(this)) {
                if (!drop.isEmpty() && random.nextInt(100) <= this.statGain) drop.grow(1);
                if (!drop.isEmpty()) result.add(drop);
            }
        }
        this.size = this.crop.sizeAfterHarvest(this);
        changed();
        return result;
    }

    public void reset() {
        this.crop = null;
        this.statGain = 0;
        this.statResistance = 0;
        this.statGrowth = 0;
        this.terrainAirQuality = -1;
        this.terrainHumidity = -1;
        this.terrainNutrients = -1;
        this.growthPoints = 0;
        this.scanLevel = 0;
        this.size = 1;
        this.eaten = false;
    }

    /** Quebrar o bloco arranca a planta (IC2: onBlockBreak → pick). */
    @Override
    public void preRemoveSideEffects(BlockPos pos, BlockState state) {
        super.preRemoveSideEffects(pos, state);
        this.removing = true;
        pick();
    }

    // ── acesso para as plantas ────────────────────────────────────────────
    public @Nullable CropCard getCrop() {
        return this.crop;
    }

    public void setCrop(@Nullable CropCard crop) {
        this.crop = crop;
        updateTerrainHumidity();
        updateTerrainNutrients();
        updateTerrainAirQuality();
    }

    public int getSize() {
        return this.size;
    }

    public void setSize(int size) {
        this.size = size;
        changed();
    }

    public int getStatGrowth() {
        return this.statGrowth;
    }

    public int getStatGain() {
        return this.statGain;
    }

    public int getStatResistance() {
        return this.statResistance;
    }

    public int getScanLevel() {
        return this.scanLevel;
    }

    public int getStorageNutrients() {
        return this.storageNutrients;
    }

    public int getStorageWater() {
        return this.storageWater;
    }

    public int getStorageWeedEx() {
        return this.storageWeedEx;
    }

    public boolean isCrossingBase() {
        return this.crossingBase;
    }

    public void addGrowthPoints(int points) {
        this.growthPoints += points;
    }

    public int getLightLevel() {
        return this.level == null ? 0 : this.level.getMaxLocalRawBrightness(this.worldPosition);
    }

    public int redstonePower() {
        return this.level == null ? 0 : this.level.getBestNeighborSignal(this.worldPosition);
    }

    public RandomSource random() {
        return this.level != null ? this.level.getRandom() : RandomSource.create();
    }

    void markEaten() {
        this.eaten = true;
    }

    boolean consumeEaten() {
        boolean was = this.eaten;
        this.eaten = false;
        return was;
    }

    /** Algum bloco (antes de achar ar) nos próximos blocos abaixo é {@code block}? */
    public boolean isBlockBelow(Block block) {
        if (this.crop == null || this.level == null) return false;
        for (int index = 1; index < this.crop.rootsLength(); index++) {
            BlockState state = this.level.getBlockState(this.worldPosition.below(index));
            if (state.isAir()) return false;
            if (state.is(block)) return true;
        }
        return false;
    }

    /** Idem, para um bloco cujo item está na tag (minério e bloco de metal). */
    public boolean isBlockBelow(TagKey<Item> tag) {
        if (this.crop == null || this.level == null) return false;
        for (int index = 1; index < this.crop.rootsLength(); index++) {
            BlockState state = this.level.getBlockState(this.worldPosition.below(index));
            if (state.isAir()) return false;
            if (BuiltInRegistries.ITEM.wrapAsHolder(state.getBlock().asItem()).is(tag)) return true;
        }
        return false;
    }

    private static int clampStat(int value) {
        return Math.max(0, Math.min(MAX_STAT, value));
    }

    /** Salva e mostra no bloco a planta, o tamanho e a vareta dupla. */
    private void changed() {
        setChanged();
        if (this.level == null || this.level.isClientSide() || this.removing) return;
        BlockState state = getBlockState();
        if (!(state.getBlock() instanceof CropBlock)) return;
        CropKind kind = this.crop == null ? CropKind.NONE : CropKind.byId(this.crop.id());
        BlockState next = state.setValue(CropBlock.CROP, kind)
                .setValue(CropBlock.SIZE, Math.max(1, Math.min(7, this.size)))
                .setValue(CropBlock.CROSSING, this.crossingBase);
        if (next != state) {
            this.level.setBlock(this.worldPosition, next, Block.UPDATE_ALL);
        }
    }

    // ── salvar/carregar ───────────────────────────────────────────────────
    @Override
    protected void saveAdditional(ValueOutput output) {
        super.saveAdditional(output);
        output.putBoolean("CrossingBase", this.crossingBase);
        output.putInt("StorageNutrients", this.storageNutrients);
        output.putInt("StorageWater", this.storageWater);
        output.putInt("StorageWeedEx", this.storageWeedEx);
        if (this.crop != null) {
            output.putString("Crop", this.crop.id());
            output.putInt("StatGrowth", this.statGrowth);
            output.putInt("StatGain", this.statGain);
            output.putInt("StatResistance", this.statResistance);
            output.putInt("Size", this.size);
            output.putInt("GrowthPoints", this.growthPoints);
            output.putInt("ScanLevel", this.scanLevel);
            output.putBoolean("Eaten", this.eaten);
        }
    }

    @Override
    protected void loadAdditional(ValueInput input) {
        super.loadAdditional(input);
        this.crossingBase = input.getBooleanOr("CrossingBase", false);
        this.storageNutrients = input.getIntOr("StorageNutrients", 0);
        this.storageWater = input.getIntOr("StorageWater", 0);
        this.storageWeedEx = input.getIntOr("StorageWeedEx", 0);
        this.crop = input.getString("Crop").map(CropCards::byId).orElse(null);
        if (this.crop != null) {
            this.statGrowth = input.getIntOr("StatGrowth", 0);
            this.statGain = input.getIntOr("StatGain", 0);
            this.statResistance = input.getIntOr("StatResistance", 0);
            this.size = input.getIntOr("Size", 1);
            this.growthPoints = input.getIntOr("GrowthPoints", 0);
            this.scanLevel = input.getIntOr("ScanLevel", 0);
            this.eaten = input.getBooleanOr("Eaten", false);
        }
    }
}
