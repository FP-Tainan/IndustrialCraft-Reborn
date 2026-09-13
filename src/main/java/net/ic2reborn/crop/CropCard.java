package net.ic2reborn.crop;

import net.ic2reborn.item.CropSeedItem;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * Uma planta do sistema de plantações do IC2 ({@code ic2.api.crops.CropCard}). Os valores padrão
 * são os do IC2; cada planta sobrescreve só o que muda.
 */
public abstract class CropCard {
    /**
     * Propriedades usadas no cruzamento: plantas parecidas cruzam entre si com mais facilidade.
     *
     * @param tier nível (quanto maior, mais difícil e mais lenta)
     */
    public record Properties(int tier, int chemistry, int consumable, int defensive, int colorful, int weed) {
        int[] all() {
            return new int[]{this.chemistry, this.consumable, this.defensive, this.colorful, this.weed};
        }
    }

    private final String id;

    protected CropCard(String id) {
        this.id = id;
    }

    public String id() {
        return this.id;
    }

    public abstract Properties properties();

    public abstract String[] attributes();

    public abstract int maxSize();

    public String discoveredBy() {
        return "IC2 Team";
    }

    public Component name() {
        return Component.translatable("crop.ic2reborn." + this.id);
    }

    /** Chave do nome do saco de sementes ("Sementes de %s", "Bolotas de %s"...). */
    public String seedType() {
        return "crop.ic2reborn.seeds";
    }

    /** Quantos blocos abaixo a raiz alcança (plantas de metal precisam de minério embaixo). */
    public int rootsLength() {
        return 1;
    }

    public int growthDuration(CropBlockEntity crop) {
        return properties().tier() * 200;
    }

    public boolean canGrow(CropBlockEntity crop) {
        return crop.getSize() < maxSize();
    }

    public int weightInfluences(CropBlockEntity crop, int humidity, int nutrients, int air) {
        return humidity + nutrients + air;
    }

    public boolean canCross(CropBlockEntity crop) {
        return crop.getSize() >= 3;
    }

    public boolean onRightClick(CropBlockEntity crop, Player player) {
        return crop.performManualHarvest();
    }

    public boolean canBeHarvested(CropBlockEntity crop) {
        return crop.getSize() == maxSize();
    }

    public double dropGainChance() {
        return Math.pow(0.95, properties().tier());
    }

    /** O que uma colheita dá (cada item pode vir mais de uma vez, conforme o ganho). */
    public List<ItemStack> gains(CropBlockEntity crop) {
        ItemStack gain = gain(crop);
        return gain.isEmpty() ? List.of() : List.of(gain);
    }

    protected ItemStack gain(CropBlockEntity crop) {
        return ItemStack.EMPTY;
    }

    public int sizeAfterHarvest(CropBlockEntity crop) {
        return 1;
    }

    public boolean onLeftClick(CropBlockEntity crop, Player player) {
        return crop.pick();
    }

    public float dropSeedChance(CropBlockEntity crop) {
        if (crop.getSize() == 1) return 0.0F;
        float base = 0.5F;
        if (crop.getSize() == 2) base /= 2.0F;
        for (int i = 0; i < properties().tier(); i++) {
            base *= 0.8F;
        }
        return base;
    }

    public ItemStack seeds(CropBlockEntity crop) {
        return CropSeedItem.create(this, crop.getStatGrowth(), crop.getStatGain(), crop.getStatResistance(), crop.getScanLevel());
    }

    public void tick(CropBlockEntity crop) {
    }

    public boolean isWeed(CropBlockEntity crop) {
        return crop.getSize() >= 2 && (this == CropCards.WEED || crop.getStatGrowth() >= 24);
    }

    /** Luz emitida pela planta nesse tamanho. */
    public int emittedLight(int size) {
        return 0;
    }

    /** Sinal de redstone emitido nesse tamanho. */
    public int redstoneSignal(int size) {
        return 0;
    }
}
