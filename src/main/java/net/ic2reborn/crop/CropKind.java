package net.ic2reborn.crop;

import net.minecraft.util.StringRepresentable;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Qual planta o bloco de plantação mostra (estado {@code crop}); um valor por {@link CropCard}. */
public enum CropKind implements StringRepresentable {
    NONE,
    WEED, WHEAT, PUMPKIN, MELON, DANDELION, ROSE, BLACKTHORN, TULIP, CYAZINT, VENOMILIA, REED, STICKREED,
    COCOA, FLAX, FERRU, AURELIA, REDWHEAT, NETHER_WART, TERRA_WART, COFFEE, HOPS, CARROTS, POTATO,
    RED_MUSHROOM, BROWN_MUSHROOM, EATINGPLANT, CYPRIUM, STAGNIUM, PLUMBISCUS, SHINING, BEETROOTS,
    OAK_SAPLING, SPRUCE_SAPLING, BIRCH_SAPLING, JUNGLE_SAPLING, ACACIA_SAPLING, DARK_OAK_SAPLING,
    BLAZEREED, BOBS_YER_UNCLE_RANKS_BERRIES, CORIUM, CORPSE_PLANT, CREEPER_WEED, DIAREED, EGG_PLANT,
    ENDER_BLOSSOM, MEAT_ROSE, MILK_WART, OIL_BERRIES, SLIME_PLANT, SPIDERNIP, TEARSTALKS, WITHEREED;

    private static final Map<String, CropKind> BY_ID = new HashMap<>();

    static {
        for (CropKind kind : values()) {
            BY_ID.put(kind.getSerializedName(), kind);
        }
    }

    private final String id = name().toLowerCase(Locale.ROOT);

    @Override
    public String getSerializedName() {
        return this.id;
    }

    public static CropKind byId(String id) {
        return BY_ID.getOrDefault(id, NONE);
    }
}
