package net.ic2reborn.crop;

import net.ic2reborn.registry.IC2AutoItems;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * Todas as plantas do IC2 ({@code ic2.core.crop.IC2Crops}) e as sementes-base: itens comuns que
 * viram a planta quando usados numa vareta vazia.
 */
public final class CropCards {
    private static final Map<String, CropCard> BY_ID = new LinkedHashMap<>();

    public static final CropCard WEED = register(new Weed());

    static {
        register(new Vanilla("wheat", 7, new CropCard.Properties(1, 0, 4, 0, 0, 2), new String[]{"Yellow", "Food", "Wheat"},
                () -> Items.WHEAT, () -> Items.WHEAT_SEEDS, 2));
        register(new Stem("pumpkin", new CropCard.Properties(1, 0, 1, 0, 3, 1), new String[]{"Orange", "Decoration", "Stem"}, 600, 200));
        register(new Stem("melon", new CropCard.Properties(2, 0, 4, 0, 2, 0), new String[]{"Green", "Food", "Stem"}, 700, 250));
        register(new ColorFlower("dandelion", new String[]{"Yellow", "Flower"}, () -> Items.DYE.yellow()));
        register(new ColorFlower("rose", new String[]{"Red", "Flower", "Rose"}, () -> Items.DYE.red()));
        register(new ColorFlower("blackthorn", new String[]{"Black", "Flower", "Rose"}, () -> Items.DYE.black()));
        register(new ColorFlower("tulip", new String[]{"Purple", "Flower", "Tulip"}, () -> Items.DYE.purple()));
        register(new ColorFlower("cyazint", new String[]{"Blue", "Flower"}, () -> Items.DYE.cyan()));
        register(new Venomilia());
        register(new Reed());
        register(new Stickreed());
        register(new Cocoa());
        register(new Flax());
        register(new MetalCommon("ferru", new String[]{"Gray", "Leaves", "Metal"}, "iron", () -> IC2AutoItems.DUST_SMALL_IRON.get()));
        register(new MetalUncommon("aurelia", new String[]{"Gold", "Leaves", "Metal"}, "gold", () -> IC2AutoItems.DUST_SMALL_GOLD.get()));
        register(new RedWheat());
        register(new NetherWart());
        register(new TerraWart());
        register(new Coffee());
        register(new Hops());
        register(new Vanilla("carrots", 3, new CropCard.Properties(2, 0, 4, 0, 0, 2), new String[]{"Orange", "Food", "Carrots"},
                () -> Items.CARROT, () -> Items.CARROT, 1));
        register(new Potato());
        register(new Mushroom("red_mushroom", new String[]{"Red", "Food", "Mushroom"}, () -> Items.RED_MUSHROOM));
        register(new Mushroom("brown_mushroom", new String[]{"Brown", "Food", "Mushroom"}, () -> Items.BROWN_MUSHROOM));
        register(new EatingPlant());
        register(new MetalCommon("cyprium", new String[]{"Orange", "Leaves", "Metal"}, "copper", () -> IC2AutoItems.DUST_SMALL_COPPER.get()));
        register(new MetalCommon("stagnium", new String[]{"Shiny", "Leaves", "Metal"}, "tin", () -> IC2AutoItems.DUST_SMALL_TIN.get()));
        register(new MetalCommon("plumbiscus", new String[]{"Dense", "Leaves", "Metal"}, "lead", () -> IC2AutoItems.DUST_SMALL_LEAD.get()));
        register(new MetalUncommon("shining", new String[]{"Silver", "Leaves", "Metal"}, "silver", () -> IC2AutoItems.DUST_SMALL_SILVER.get()));
        register(new Vanilla("beetroots", 3, new CropCard.Properties(1, 0, 4, 0, 1, 2), new String[]{"Red", "Food", "Beetroot"},
                () -> Items.BEETROOT, () -> Items.BEETROOT_SEEDS, 1));
        register(new Sapling("oak_sapling", "acorns", () -> Items.OAK_LOG, () -> Items.OAK_SAPLING, true));
        register(new Sapling("spruce_sapling", "pine_cones", () -> Items.SPRUCE_LOG, () -> Items.SPRUCE_SAPLING, false));
        register(new Sapling("birch_sapling", "catkins", () -> Items.BIRCH_LOG, () -> Items.BIRCH_SAPLING, false));
        register(new Sapling("jungle_sapling", "seedling", () -> Items.JUNGLE_LOG, () -> Items.JUNGLE_SAPLING, false));
        register(new Sapling("acacia_sapling", "seedling", () -> Items.ACACIA_LOG, () -> Items.ACACIA_SAPLING, false));
        register(new Sapling("dark_oak_sapling", "acorns", () -> Items.DARK_OAK_LOG, () -> Items.DARK_OAK_SAPLING, false));

        // plantas "genéricas" do IC2, descobertas pela comunidade
        generic("blazereed", "Mr. Brain", new CropCard.Properties(6, 0, 4, 1, 0, 0), new String[]{"Fire", "Blaze", "Reed", "Sulfur"},
                4, 0, 1, List.of(() -> Items.BLAZE_POWDER), List.of(() -> Items.BLAZE_ROD, () -> IC2AutoItems.DUST_SULFUR.get()));
        generic("bobs_yer_uncle_ranks_berries", "GenerikB", new CropCard.Properties(11, 4, 0, 8, 2, 9),
                new String[]{"Shiny", "Vine", "Emerald", "Berylium", "Crystal"},
                4, 0, 1, List.of(() -> IC2Items.BOBS_YER_UNCLE_RANKS_BERRY.get()), List.of(() -> Items.EMERALD));
        generic("corium", "Gregorius Techneticies", new CropCard.Properties(6, 0, 2, 3, 1, 0), new String[]{"Cow", "Silk", "Vine"},
                4, 0, 1, List.of(() -> Items.LEATHER), List.of());
        generic("corpse_plant", "Mr. Kenny", new CropCard.Properties(5, 0, 2, 1, 0, 3), new String[]{"Toxic", "Undead", "Vine", "Edible", "Rotten"},
                4, 0, 1, List.of(() -> Items.ROTTEN_FLESH), List.of(() -> Items.BONE, () -> Items.BONE_MEAL, () -> Items.BONE_MEAL));
        generic("creeper_weed", "General Spaz", new CropCard.Properties(7, 3, 0, 5, 1, 3),
                new String[]{"Creeper", "Vine", "Explosive", "Fire", "Sulfur", "Saltpeter", "Coal"},
                4, 0, 1, List.of(() -> Items.GUNPOWDER), List.of());
        generic("diareed", "Diareed", new CropCard.Properties(12, 5, 0, 10, 2, 10), new String[]{"Fire", "Shiny", "Reed", "Coal", "Diamond", "Crystal"},
                4, 0, 1, List.of(() -> IC2Items.DUST_SMALL_DIAMOND.get()), List.of(() -> Items.DIAMOND));
        generic("egg_plant", "Link", new CropCard.Properties(6, 0, 4, 1, 0, 0), new String[]{"Chicken", "Egg", "Edible", "Feather", "Flower", "Addictive"},
                3, 900, 2, List.of(() -> Items.EGG), List.of(() -> Items.CHICKEN, () -> Items.FEATHER, () -> Items.FEATHER, () -> Items.FEATHER));
        generic("ender_blossom", "RichardG", new CropCard.Properties(10, 5, 0, 2, 1, 6), new String[]{"Ender", "Flower", "Shiny"},
                4, 0, 1, List.of(() -> IC2Items.DUST_ENDER_PEARL.get()), List.of(() -> Items.ENDER_PEARL, () -> Items.ENDER_PEARL, () -> Items.ENDER_EYE));
        generic("meat_rose", "VintageBeef", new CropCard.Properties(7, 0, 4, 1, 3, 0), new String[]{"Edible", "Flower", "Cow", "Chicken", "Pig", "Sheep"},
                4, 1500, 1, List.of(() -> Items.DYE.pink()), List.of(() -> Items.BEEF, () -> Items.PORKCHOP, () -> Items.CHICKEN, () -> Items.MUTTON));
        generic("milk_wart", "Mr. Brain", new CropCard.Properties(6, 0, 3, 0, 1, 0), new String[]{"Edible", "Milk", "Cow"},
                3, 900, 1, List.of(() -> IC2Items.MILK_WART.get()), List.of());
        generic("oil_berries", "Spacetoad", new CropCard.Properties(9, 6, 1, 2, 1, 12), new String[]{"Fire", "Dark", "Reed", "Rotten", "Coal", "Oil"},
                3, 0, 1, List.of(() -> IC2Items.OIL_BERRY.get()), List.of());
        generic("slime_plant", "Neowulf", new CropCard.Properties(6, 3, 0, 0, 0, 2), new String[]{"Slime", "Bouncy", "Sticky", "Bush"},
                4, 0, 3, List.of(() -> Items.SLIME_BALL), List.of());
        generic("spidernip", "Mr. Kenny", new CropCard.Properties(4, 2, 1, 4, 1, 3), new String[]{"Toxic", "Silk", "Spider", "Flower", "Ingredient", "Addictive"},
                4, 600, 1, List.of(() -> Items.STRING), List.of(() -> Items.SPIDER_EYE, () -> Items.COBWEB));
        generic("tearstalks", "Neowulf", new CropCard.Properties(8, 1, 2, 0, 0, 0), new String[]{"Healing", "Nether", "Ingredient", "Reed", "Ghast"},
                4, 0, 1, List.of(() -> Items.GHAST_TEAR), List.of());
        generic("withereed", "CovertJaguar", new CropCard.Properties(8, 2, 0, 4, 1, 3), new String[]{"Fire", "Undead", "Reed", "Coal", "Rotten", "Wither"},
                4, 0, 1, List.of(() -> IC2AutoItems.DUST_COAL.get()), List.of(() -> Items.COAL, () -> Items.COAL));
    }

    /** Semente-base: o item vira essa planta, nesse tamanho e com esses atributos. */
    public record BaseSeed(CropCard crop, int size, int growth, int gain, int resistance) {}

    private static @Nullable Map<Item, BaseSeed> baseSeeds;

    private CropCards() {}

    private static CropCard register(CropCard card) {
        if (CropKind.byId(card.id()) == CropKind.NONE) throw new IllegalStateException("planta sem estado: " + card.id());
        BY_ID.put(card.id(), card);
        return card;
    }

    private static void generic(String id, String discoveredBy, CropCard.Properties properties, String[] attributes, int maxSize,
                                int growthSpeed, int afterHarvestSize, List<Supplier<Item>> drops, List<Supplier<Item>> specialDrops) {
        register(new Generic(id, discoveredBy, properties, attributes, maxSize, growthSpeed, afterHarvestSize, drops, specialDrops));
    }

    public static @Nullable CropCard byId(String id) {
        return BY_ID.get(id);
    }

    public static @Nullable CropCard byKind(CropKind kind) {
        return kind == CropKind.NONE ? null : BY_ID.get(kind.getSerializedName());
    }

    public static List<CropCard> all() {
        return Collections.unmodifiableList(new ArrayList<>(BY_ID.values()));
    }

    public static CropCard get(String id) {
        CropCard card = BY_ID.get(id);
        if (card == null) throw new IllegalArgumentException("planta desconhecida: " + id);
        return card;
    }

    public static @Nullable BaseSeed baseSeed(ItemStack stack) {
        if (baseSeeds == null) {
            Map<Item, BaseSeed> seeds = new LinkedHashMap<>();
            seeds.put(Items.WHEAT_SEEDS, new BaseSeed(get("wheat"), 1, 1, 1, 1));
            seeds.put(Items.PUMPKIN_SEEDS, new BaseSeed(get("pumpkin"), 1, 1, 1, 1));
            seeds.put(Items.MELON_SEEDS, new BaseSeed(get("melon"), 1, 1, 1, 1));
            seeds.put(Items.NETHER_WART, new BaseSeed(get("nether_wart"), 1, 1, 1, 1));
            seeds.put(IC2Items.TERRA_WART.get(), new BaseSeed(get("terra_wart"), 1, 1, 1, 1));
            seeds.put(IC2AutoItems.COFFEE_BEANS.get(), new BaseSeed(get("coffee"), 1, 1, 1, 1));
            seeds.put(Items.SUGAR_CANE, new BaseSeed(get("reed"), 1, 3, 0, 2));
            seeds.put(Items.COCOA_BEANS, new BaseSeed(get("cocoa"), 1, 0, 0, 0));
            seeds.put(Items.POPPY, new BaseSeed(get("rose"), 4, 1, 1, 1));
            seeds.put(Items.DANDELION, new BaseSeed(get("dandelion"), 4, 1, 1, 1));
            seeds.put(Items.CARROT, new BaseSeed(get("carrots"), 1, 1, 1, 1));
            seeds.put(Items.POTATO, new BaseSeed(get("potato"), 1, 1, 1, 1));
            seeds.put(Items.BROWN_MUSHROOM, new BaseSeed(get("brown_mushroom"), 1, 1, 1, 1));
            seeds.put(Items.RED_MUSHROOM, new BaseSeed(get("red_mushroom"), 1, 1, 1, 1));
            seeds.put(Items.CACTUS, new BaseSeed(get("eatingplant"), 1, 1, 1, 1));
            seeds.put(Items.BEETROOT_SEEDS, new BaseSeed(get("beetroots"), 1, 1, 1, 1));
            seeds.put(Items.OAK_SAPLING, new BaseSeed(get("oak_sapling"), 1, 1, 1, 1));
            seeds.put(Items.SPRUCE_SAPLING, new BaseSeed(get("spruce_sapling"), 1, 1, 1, 1));
            seeds.put(Items.BIRCH_SAPLING, new BaseSeed(get("birch_sapling"), 1, 1, 1, 1));
            seeds.put(Items.JUNGLE_SAPLING, new BaseSeed(get("jungle_sapling"), 1, 1, 1, 1));
            seeds.put(Items.ACACIA_SAPLING, new BaseSeed(get("acacia_sapling"), 1, 1, 1, 1));
            seeds.put(Items.DARK_OAK_SAPLING, new BaseSeed(get("dark_oak_sapling"), 1, 1, 1, 1));
            seeds.put(IC2Items.MILK_WART.get(), new BaseSeed(get("milk_wart"), 1, 1, 1, 1));
            baseSeeds = seeds;
        }
        return baseSeeds.get(stack.getItem());
    }

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, Identifier.fromNamespaceAndPath("c", path));
    }

    // ── plantas ───────────────────────────────────────────────────────────
    static final class Weed extends CropCard {
        Weed() { super("weed"); }
        @Override public Properties properties() { return new Properties(0, 0, 0, 1, 0, 5); }
        @Override public String[] attributes() { return new String[]{"Weed", "Bad"}; }
        @Override public int maxSize() { return 5; }
        @Override public boolean onLeftClick(CropBlockEntity crop, Player player) { return false; }
        @Override public boolean canBeHarvested(CropBlockEntity crop) { return false; }
        @Override public int growthDuration(CropBlockEntity crop) { return 300; }
    }

    /** Planta comum do Minecraft: com atributos baixos solta a semente normal. */
    static final class Vanilla extends CropCard {
        private final int maxSize;
        private final Properties properties;
        private final String[] attributes;
        private final Supplier<Item> product;
        private final Supplier<Item> seed;
        private final int afterHarvest;

        Vanilla(String id, int maxSize, Properties properties, String[] attributes, Supplier<Item> product, Supplier<Item> seed, int afterHarvest) {
            super(id);
            this.maxSize = maxSize;
            this.properties = properties;
            this.attributes = attributes;
            this.product = product;
            this.seed = seed;
            this.afterHarvest = afterHarvest;
        }

        @Override public String discoveredBy() { return "Notch"; }
        @Override public Properties properties() { return this.properties; }
        @Override public String[] attributes() { return this.attributes; }
        @Override public int maxSize() { return this.maxSize; }
        @Override public boolean canGrow(CropBlockEntity crop) { return crop.getSize() < this.maxSize && crop.getLightLevel() >= 9; }
        @Override protected ItemStack gain(CropBlockEntity crop) { return new ItemStack(this.product.get()); }
        @Override public int sizeAfterHarvest(CropBlockEntity crop) { return this.afterHarvest; }

        @Override
        public ItemStack seeds(CropBlockEntity crop) {
            return crop.getStatGain() <= 1 && crop.getStatGrowth() <= 1 && crop.getStatResistance() <= 1
                    ? new ItemStack(this.seed.get()) : super.seeds(crop);
        }
    }

    /** Abóbora e melancia (IC2: CropVanillaStem). */
    static final class Stem extends CropCard {
        private final Properties properties;
        private final String[] attributes;
        private final int lastStage;
        private final int stage;

        Stem(String id, Properties properties, String[] attributes, int lastStage, int stage) {
            super(id);
            this.properties = properties;
            this.attributes = attributes;
            this.lastStage = lastStage;
            this.stage = stage;
        }

        @Override public String discoveredBy() { return "Notch"; }
        @Override public Properties properties() { return this.properties; }
        @Override public String[] attributes() { return this.attributes; }
        @Override public int maxSize() { return 4; }
        @Override public boolean canGrow(CropBlockEntity crop) { return crop.getSize() < 4 && crop.getLightLevel() >= 9; }
        @Override public int weightInfluences(CropBlockEntity crop, int humidity, int nutrients, int air) { return (int) (humidity * 1.1 + nutrients * 0.9 + air); }
        @Override public int sizeAfterHarvest(CropBlockEntity crop) { return 3; }
        @Override public int growthDuration(CropBlockEntity crop) { return crop.getSize() == 3 ? this.lastStage : this.stage; }

        @Override
        protected ItemStack gain(CropBlockEntity crop) {
            if (id().equals("pumpkin")) return new ItemStack(Items.PUMPKIN);
            return crop.random().nextInt(3) == 0 ? new ItemStack(Items.MELON) : new ItemStack(Items.MELON_SLICE, crop.random().nextInt(4) + 2);
        }

        @Override
        public ItemStack seeds(CropBlockEntity crop) {
            if (crop.getStatGain() <= 1 && crop.getStatGrowth() <= 1 && crop.getStatResistance() <= 1) {
                return id().equals("pumpkin")
                        ? new ItemStack(Items.PUMPKIN_SEEDS, crop.random().nextInt(3) + 1)
                        : new ItemStack(Items.MELON_SEEDS, crop.random().nextInt(2) + 1);
            }
            return super.seeds(crop);
        }
    }

    static final class ColorFlower extends CropCard {
        private final String[] attributes;
        private final Supplier<Item> dye;

        ColorFlower(String id, String[] attributes, Supplier<Item> dye) {
            super(id);
            this.attributes = attributes;
            this.dye = dye;
        }

        @Override public String discoveredBy() { return id().equals("dandelion") || id().equals("rose") ? "Notch" : "Alblaka"; }
        @Override public Properties properties() { return new Properties(2, 1, 1, 0, 5, 1); }
        @Override public String[] attributes() { return this.attributes; }
        @Override public int maxSize() { return 4; }
        @Override public boolean canGrow(CropBlockEntity crop) { return crop.getSize() <= 3 && crop.getLightLevel() >= 12; }
        @Override protected ItemStack gain(CropBlockEntity crop) { return new ItemStack(this.dye.get()); }
        @Override public int sizeAfterHarvest(CropBlockEntity crop) { return 3; }
        @Override public int growthDuration(CropBlockEntity crop) { return crop.getSize() == 3 ? 600 : 400; }
    }

    static final class Venomilia extends CropCard {
        Venomilia() { super("venomilia"); }
        @Override public String discoveredBy() { return "raGan"; }
        @Override public Properties properties() { return new Properties(3, 3, 1, 3, 3, 3); }
        @Override public String[] attributes() { return new String[]{"Purple", "Flower", "Tulip", "Poison"}; }
        @Override public int maxSize() { return 6; }
        @Override public boolean canGrow(CropBlockEntity crop) { return crop.getSize() <= 4 && crop.getLightLevel() >= 12 || crop.getSize() == 5; }
        @Override public boolean canBeHarvested(CropBlockEntity crop) { return crop.getSize() >= 4; }
        @Override public int sizeAfterHarvest(CropBlockEntity crop) { return 3; }
        @Override public int growthDuration(CropBlockEntity crop) { return crop.getSize() >= 3 ? 600 : 400; }
        @Override public boolean isWeed(CropBlockEntity crop) { return crop.getSize() == 5 && crop.getStatGrowth() >= 8; }

        @Override
        protected ItemStack gain(CropBlockEntity crop) {
            if (crop.getSize() == 5) return new ItemStack(IC2AutoItems.GRIN_POWDER.get());
            return crop.getSize() >= 4 ? new ItemStack(Items.DYE.purple()) : ItemStack.EMPTY;
        }

        /** Madura (tamanho 5), envenena quem mexe sem estar agachado. */
        private void sting(CropBlockEntity crop, Player player) {
            if (crop.getSize() == 5 && !player.isShiftKeyDown()) {
                player.addEffect(new MobEffectInstance(MobEffects.POISON, (crop.random().nextInt(10) + 5) * 20, 0));
                crop.setSize(4);
            }
        }

        @Override public boolean onRightClick(CropBlockEntity crop, Player player) { sting(crop, player); return crop.performManualHarvest(); }
        @Override public boolean onLeftClick(CropBlockEntity crop, Player player) { sting(crop, player); return crop.pick(); }
    }

    static final class Reed extends CropCard {
        Reed() { super("reed"); }
        @Override public Properties properties() { return new Properties(2, 0, 0, 1, 0, 2); }
        @Override public String[] attributes() { return new String[]{"Reed"}; }
        @Override public int maxSize() { return 3; }
        @Override public int weightInfluences(CropBlockEntity crop, int humidity, int nutrients, int air) { return (int) (humidity * 1.2 + nutrients + air * 0.8); }
        @Override public boolean canBeHarvested(CropBlockEntity crop) { return crop.getSize() > 1; }
        @Override protected ItemStack gain(CropBlockEntity crop) { return new ItemStack(Items.SUGAR_CANE, crop.getSize() - 1); }
        @Override public int growthDuration(CropBlockEntity crop) { return 200; }
    }

    static final class Stickreed extends CropCard {
        Stickreed() { super("stickreed"); }
        @Override public Properties properties() { return new Properties(4, 2, 0, 1, 0, 1); }
        @Override public String[] attributes() { return new String[]{"Reed", "Resin"}; }
        @Override public int maxSize() { return 4; }
        @Override public int weightInfluences(CropBlockEntity crop, int humidity, int nutrients, int air) { return (int) (humidity * 1.2 + nutrients + air * 0.8); }
        @Override public boolean canBeHarvested(CropBlockEntity crop) { return crop.getSize() > 1; }
        @Override public int growthDuration(CropBlockEntity crop) { return crop.getSize() == 4 ? 400 : 100; }
        @Override public int sizeAfterHarvest(CropBlockEntity crop) { return crop.getSize() == 4 ? 3 - crop.random().nextInt(3) : 1; }

        @Override
        protected ItemStack gain(CropBlockEntity crop) {
            if (crop.getSize() <= 3) return new ItemStack(Items.SUGAR_CANE, crop.getSize() - 1);
            // resina do IC2 = borracha do Craft Energy
            return new ItemStack(BuiltInRegistries.ITEM.getOptional(Identifier.parse("craftenergy:rubber")).orElse(Items.SLIME_BALL));
        }
    }

    static final class Cocoa extends CropCard {
        Cocoa() { super("cocoa"); }
        @Override public Properties properties() { return new Properties(3, 1, 3, 0, 4, 0); }
        @Override public String[] attributes() { return new String[]{"Brown", "Food", "Stem"}; }
        @Override public int maxSize() { return 4; }
        @Override public boolean canGrow(CropBlockEntity crop) { return crop.getSize() <= 3 && crop.getStorageNutrients() >= 3; }
        @Override public int weightInfluences(CropBlockEntity crop, int humidity, int nutrients, int air) { return (int) (humidity * 0.8 + nutrients * 1.3 + air * 0.9); }
        @Override protected ItemStack gain(CropBlockEntity crop) { return new ItemStack(Items.COCOA_BEANS); }
        @Override public int growthDuration(CropBlockEntity crop) { return crop.getSize() == 3 ? 900 : 400; }
        @Override public int sizeAfterHarvest(CropBlockEntity crop) { return 3; }
    }

    static final class Flax extends CropCard {
        Flax() { super("flax"); }
        @Override public Properties properties() { return new Properties(2, 1, 1, 2, 0, 1); }
        @Override public String[] attributes() { return new String[]{"Silk", "Vine", "Addictive"}; }
        @Override public int maxSize() { return 4; }
        @Override public boolean canGrow(CropBlockEntity crop) { return crop.getSize() < 4 && crop.getLightLevel() >= 9; }
        @Override protected ItemStack gain(CropBlockEntity crop) { return new ItemStack(Items.STRING); }
    }

    static final class Mushroom extends CropCard {
        private final String[] attributes;
        private final Supplier<Item> drop;

        Mushroom(String id, String[] attributes, Supplier<Item> drop) {
            super(id);
            this.attributes = attributes;
            this.drop = drop;
        }

        @Override public Properties properties() { return new Properties(2, 0, 4, 0, 0, 4); }
        @Override public String[] attributes() { return this.attributes; }
        @Override public int maxSize() { return 3; }
        @Override public boolean canGrow(CropBlockEntity crop) { return crop.getSize() < 3 && crop.getStorageWater() > 0; }
        @Override protected ItemStack gain(CropBlockEntity crop) { return new ItemStack(this.drop.get()); }
        @Override public int growthDuration(CropBlockEntity crop) { return 200; }
    }

    /** Plantas de metal: o último estágio só cresce com minério ou bloco do metal embaixo. */
    static class MetalCommon extends CropCard {
        private final String[] attributes;
        private final TagKey<Item> ore;
        private final TagKey<Item> block;
        private final Supplier<Item> drop;

        MetalCommon(String id, String[] attributes, String metal, Supplier<Item> drop) {
            super(id);
            this.attributes = attributes;
            this.ore = itemTag("ores/" + metal);
            this.block = itemTag("storage_blocks/" + metal);
            this.drop = drop;
        }

        @Override public Properties properties() { return new Properties(6, 2, 0, 0, 1, 0); }
        @Override public String[] attributes() { return this.attributes; }
        @Override public int maxSize() { return 4; }
        @Override public int rootsLength() { return 5; }
        @Override protected ItemStack gain(CropBlockEntity crop) { return new ItemStack(this.drop.get()); }
        @Override public double dropGainChance() { return super.dropGainChance() / 2.0; }
        @Override public int growthDuration(CropBlockEntity crop) { return crop.getSize() == 3 ? 2000 : 800; }
        @Override public int sizeAfterHarvest(CropBlockEntity crop) { return 2; }

        protected boolean hasRoots(CropBlockEntity crop) {
            return crop.isBlockBelow(this.ore) || crop.isBlockBelow(this.block);
        }

        @Override
        public boolean canGrow(CropBlockEntity crop) {
            if (crop.getSize() < maxSize() - 1) return true;
            return crop.getSize() == maxSize() - 1 && hasRoots(crop);
        }
    }

    static final class MetalUncommon extends MetalCommon {
        MetalUncommon(String id, String[] attributes, String metal, Supplier<Item> drop) {
            super(id, attributes, metal, drop);
        }

        @Override public Properties properties() { return new Properties(6, 2, 0, 0, 2, 0); }
        @Override public int maxSize() { return 5; }
        @Override public double dropGainChance() { return Math.pow(0.95, properties().tier()); }
        @Override public int growthDuration(CropBlockEntity crop) { return crop.getSize() == 4 ? 2200 : 750; }
    }

    static final class RedWheat extends CropCard {
        RedWheat() { super("redwheat"); }
        @Override public Properties properties() { return new Properties(6, 3, 0, 0, 2, 0); }
        @Override public String[] attributes() { return new String[]{"Red", "Redstone", "Wheat"}; }
        @Override public int maxSize() { return 7; }
        @Override public boolean canGrow(CropBlockEntity crop) { return crop.getSize() < 7 && crop.getLightLevel() <= 10 && crop.getLightLevel() >= 5; }
        @Override public double dropGainChance() { return 0.5; }
        @Override public int growthDuration(CropBlockEntity crop) { return 600; }
        @Override public int sizeAfterHarvest(CropBlockEntity crop) { return 2; }
        @Override public int emittedLight(int size) { return size == 7 ? 7 : 0; }
        @Override public int redstoneSignal(int size) { return size == 7 ? 15 : 0; }

        @Override
        protected ItemStack gain(CropBlockEntity crop) {
            return crop.redstonePower() <= 0 && !crop.random().nextBoolean() ? new ItemStack(Items.WHEAT) : new ItemStack(Items.REDSTONE);
        }
    }

    static final class NetherWart extends CropCard {
        NetherWart() { super("nether_wart"); }
        @Override public Properties properties() { return new Properties(5, 4, 2, 0, 2, 1); }
        @Override public String[] attributes() { return new String[]{"Red", "Nether", "Ingredient", "Soulsand"}; }
        @Override public int maxSize() { return 3; }
        @Override public double dropGainChance() { return 2.0; }
        @Override public int rootsLength() { return 5; }
        @Override protected ItemStack gain(CropBlockEntity crop) { return new ItemStack(Items.NETHER_WART); }

        @Override
        public void tick(CropBlockEntity crop) {
            if (crop.isBlockBelow(Blocks.SOUL_SAND)) {
                if (canGrow(crop)) crop.addGrowthPoints(100);
            } else if (crop.isBlockBelow(Blocks.SNOW_BLOCK) && crop.random().nextInt(300) == 0) {
                crop.setCrop(get("terra_wart"));
            }
        }
    }

    static final class TerraWart extends CropCard {
        TerraWart() { super("terra_wart"); }
        @Override public Properties properties() { return new Properties(5, 2, 4, 0, 3, 0); }
        @Override public String[] attributes() { return new String[]{"Blue", "Aether", "Consumable", "Snow"}; }
        @Override public int maxSize() { return 3; }
        @Override public double dropGainChance() { return 0.8; }
        @Override public int rootsLength() { return 5; }
        @Override protected ItemStack gain(CropBlockEntity crop) { return new ItemStack(IC2Items.TERRA_WART.get()); }

        @Override
        public void tick(CropBlockEntity crop) {
            if (crop.isBlockBelow(Blocks.SNOW_BLOCK)) {
                if (canGrow(crop)) crop.addGrowthPoints(100);
            } else if (crop.isBlockBelow(Blocks.SOUL_SAND) && crop.random().nextInt(300) == 0) {
                crop.setCrop(get("nether_wart"));
            }
        }
    }

    static final class Coffee extends CropCard {
        Coffee() { super("coffee"); }
        @Override public Properties properties() { return new Properties(7, 1, 4, 1, 2, 0); }
        @Override public String[] attributes() { return new String[]{"Leaves", "Ingredient", "Beans"}; }
        @Override public int maxSize() { return 5; }
        @Override public boolean canGrow(CropBlockEntity crop) { return crop.getSize() < 5 && crop.getLightLevel() >= 9; }
        @Override public int weightInfluences(CropBlockEntity crop, int humidity, int nutrients, int air) { return (int) (0.4 * humidity + 1.4 * nutrients + 1.2 * air); }
        @Override public boolean canBeHarvested(CropBlockEntity crop) { return crop.getSize() >= 4; }
        @Override public int sizeAfterHarvest(CropBlockEntity crop) { return 3; }

        @Override
        public int growthDuration(CropBlockEntity crop) {
            int base = super.growthDuration(crop);
            if (crop.getSize() == 3) return (int) (base * 0.5);
            return crop.getSize() == 4 ? (int) (base * 1.5) : base;
        }

        @Override
        protected ItemStack gain(CropBlockEntity crop) {
            return crop.getSize() == 4 ? ItemStack.EMPTY : new ItemStack(IC2AutoItems.COFFEE_BEANS.get());
        }
    }

    static final class Hops extends CropCard {
        Hops() { super("hops"); }
        @Override public Properties properties() { return new Properties(5, 2, 2, 0, 1, 1); }
        @Override public String[] attributes() { return new String[]{"Green", "Ingredient", "Wheat"}; }
        @Override public int maxSize() { return 7; }
        @Override public int growthDuration(CropBlockEntity crop) { return 600; }
        @Override public boolean canGrow(CropBlockEntity crop) { return crop.getSize() < 7 && crop.getLightLevel() >= 9; }
        @Override protected ItemStack gain(CropBlockEntity crop) { return new ItemStack(IC2Items.HOPS.get()); }
        @Override public int sizeAfterHarvest(CropBlockEntity crop) { return 3; }
    }

    static final class Potato extends CropCard {
        Potato() { super("potato"); }
        @Override public String discoveredBy() { return "Notch"; }
        @Override public Properties properties() { return new Properties(2, 0, 4, 0, 0, 2); }
        @Override public String[] attributes() { return new String[]{"Yellow", "Food", "Potato"}; }
        @Override public int maxSize() { return 4; }
        @Override public boolean canGrow(CropBlockEntity crop) { return crop.getSize() < 4 && crop.getLightLevel() >= 9; }
        @Override public boolean canBeHarvested(CropBlockEntity crop) { return crop.getSize() >= 3; }

        @Override
        protected ItemStack gain(CropBlockEntity crop) {
            if (crop.getSize() >= 4 && crop.random().nextInt(20) <= 0) return new ItemStack(Items.POISONOUS_POTATO);
            return crop.getSize() >= 3 ? new ItemStack(Items.POTATO) : ItemStack.EMPTY;
        }

        @Override
        public ItemStack seeds(CropBlockEntity crop) {
            return crop.getStatGain() <= 1 && crop.getStatGrowth() <= 1 && crop.getStatResistance() <= 1
                    ? new ItemStack(Items.POTATO) : super.seeds(crop);
        }
    }

    /** Planta carnívora: morde e segura quem chega perto; acima do tamanho 3 precisa de lava embaixo. */
    static final class EatingPlant extends CropCard {
        EatingPlant() { super("eatingplant"); }
        @Override public Properties properties() { return new Properties(6, 1, 1, 3, 1, 4); }
        @Override public String[] attributes() { return new String[]{"Bad", "Food"}; }
        @Override public int maxSize() { return 6; }
        @Override public int rootsLength() { return 5; }
        @Override public boolean canBeHarvested(CropBlockEntity crop) { return crop.getSize() >= 4 && crop.getSize() < 6; }

        @Override
        public boolean canGrow(CropBlockEntity crop) {
            return crop.getSize() < 3
                    ? crop.getLightLevel() > 10
                    : crop.isBlockBelow(Blocks.LAVA) && crop.getSize() < maxSize() && crop.getLightLevel() > 10;
        }

        @Override
        protected ItemStack gain(CropBlockEntity crop) {
            return crop.getSize() >= 4 && crop.getSize() < 6 ? new ItemStack(Items.CACTUS) : ItemStack.EMPTY;
        }

        @Override
        public void tick(CropBlockEntity crop) {
            if (crop.getSize() == 1 || !(crop.getLevel() instanceof ServerLevel level)) return;
            BlockPos pos = crop.getBlockPos();
            if (crop.consumeEaten()) {
                net.minecraft.world.level.block.Block.popResource(level, pos, new ItemStack(Items.ROTTEN_FLESH));
            }
            Vec3 center = Vec3.atBottomCenterOf(pos);
            List<LivingEntity> victims = new ArrayList<>(level.getEntitiesOfClass(LivingEntity.class,
                    new AABB(center.x - 1, pos.getY(), center.z - 1, center.x + 1, pos.getY() + 2, center.z + 1)));
            Collections.shuffle(victims);
            for (LivingEntity entity : victims) {
                if (entity instanceof Player player && player.getAbilities().instabuild) continue;
                entity.setDeltaMovement((center.x - entity.getX()) * 0.5, Math.min(entity.getDeltaMovement().y, -0.05), (center.z - entity.getZ()) * 0.5);
                entity.hurtServer(level, level.damageSources().cactus(), crop.getSize() * 2.0F);
                entity.addEffect(new MobEffectInstance(MobEffects.SLOWNESS, 64, 50));
                entity.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 64, 0));
                if (canGrow(crop)) crop.addGrowthPoints(100);
                level.playSound(null, pos, SoundEvents.GENERIC_EAT.value(), SoundSource.BLOCKS, 1.0F, crop.random().nextFloat() * 0.1F + 0.9F);
                crop.markEaten();
                break;
            }
        }
    }

    static final class Sapling extends CropCard {
        private final String seedName;
        private final Supplier<Item> log;
        private final Supplier<Item> sapling;
        private final boolean apples;

        Sapling(String id, String seedName, Supplier<Item> log, Supplier<Item> sapling, boolean apples) {
            super(id);
            this.seedName = seedName;
            this.log = log;
            this.sapling = sapling;
            this.apples = apples;
        }

        @Override public String seedType() { return "crop.ic2reborn." + this.seedName; }
        @Override public Properties properties() { return new Properties(3, 1, 0, 4, 4, 0); }
        @Override public String[] attributes() { return new String[]{"Leaves", "Sapling", "Green"}; }
        @Override public int maxSize() { return 5; }
        @Override public boolean canGrow(CropBlockEntity crop) { return crop.getSize() < 5 && crop.getLightLevel() >= 9; }
        @Override public int growthDuration(CropBlockEntity crop) { return crop.getSize() >= 4 ? 150 : 600; }
        @Override public int sizeAfterHarvest(CropBlockEntity crop) { return 4; }

        @Override
        public List<ItemStack> gains(CropBlockEntity crop) {
            List<ItemStack> drops = new ArrayList<>();
            drops.add(new ItemStack(this.log.get()));
            if (crop.random().nextInt(100) >= 75) drops.add(new ItemStack(this.sapling.get()));
            if (this.apples && crop.random().nextInt(100) >= 75) drops.add(new ItemStack(Items.APPLE));
            return drops;
        }
    }

    /** Plantas genéricas: drops fixos e uma chance de drop especial. */
    static final class Generic extends CropCard {
        private final String discoveredBy;
        private final Properties properties;
        private final String[] attributes;
        private final int maxSize;
        private final int growthSpeed;
        private final int afterHarvestSize;
        private final List<Supplier<Item>> drops;
        private final List<Supplier<Item>> specialDrops;

        Generic(String id, String discoveredBy, Properties properties, String[] attributes, int maxSize, int growthSpeed,
                int afterHarvestSize, List<Supplier<Item>> drops, List<Supplier<Item>> specialDrops) {
            super(id);
            this.discoveredBy = discoveredBy;
            this.properties = properties;
            this.attributes = attributes;
            this.maxSize = maxSize;
            this.growthSpeed = growthSpeed;
            this.afterHarvestSize = afterHarvestSize;
            this.drops = drops;
            this.specialDrops = specialDrops;
        }

        @Override public String discoveredBy() { return this.discoveredBy; }
        @Override public Properties properties() { return this.properties; }
        @Override public String[] attributes() { return this.attributes; }
        @Override public int maxSize() { return this.maxSize; }
        @Override public int rootsLength() { return 5; }
        @Override public int sizeAfterHarvest(CropBlockEntity crop) { return this.afterHarvestSize; }
        @Override public boolean canCross(CropBlockEntity crop) { return crop.getSize() + 2 > this.maxSize; }
        @Override public boolean onRightClick(CropBlockEntity crop, Player player) { return canBeHarvested(crop) && crop.performManualHarvest(); }

        @Override
        public int growthDuration(CropBlockEntity crop) {
            return this.growthSpeed < 200 ? this.properties.tier() * 200 : this.properties.tier() * this.growthSpeed;
        }

        @Override
        public List<ItemStack> gains(CropBlockEntity crop) {
            List<ItemStack> gains = new ArrayList<>();
            for (Supplier<Item> drop : this.drops) {
                gains.add(new ItemStack(drop.get()));
            }
            if (!this.specialDrops.isEmpty()) {
                int roulette = crop.random().nextInt(this.specialDrops.size() * 2 + 2);
                if (roulette < this.specialDrops.size()) {
                    gains.add(new ItemStack(this.specialDrops.get(roulette).get()));
                }
            }
            return gains;
        }
    }
}
