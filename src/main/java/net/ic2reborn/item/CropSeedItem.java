package net.ic2reborn.item;

import com.mojang.serialization.Codec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.ic2reborn.crop.CropCard;
import net.ic2reborn.crop.CropCards;
import net.ic2reborn.registry.IC2Components;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.item.component.TooltipDisplay;
import org.jetbrains.annotations.Nullable;

import java.util.function.Consumer;

/**
 * Saco de sementes do IC2 ({@code ItemCropSeed}): guarda a planta e os atributos. Sem análise
 * (nível 0) aparece como semente desconhecida; a partir do nível 4 mostra Gr/Ga/Re.
 */
public class CropSeedItem extends Item {
    /** Planta, atributos e nível de análise do saco de sementes. */
    public record CropSeed(String crop, int growth, int gain, int resistance, int scan) {
        public static final Codec<CropSeed> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                Codec.STRING.fieldOf("crop").forGetter(CropSeed::crop),
                Codec.INT.fieldOf("growth").forGetter(CropSeed::growth),
                Codec.INT.fieldOf("gain").forGetter(CropSeed::gain),
                Codec.INT.fieldOf("resistance").forGetter(CropSeed::resistance),
                Codec.INT.fieldOf("scan").forGetter(CropSeed::scan)
        ).apply(instance, CropSeed::new));

        public static final StreamCodec<ByteBuf, CropSeed> STREAM_CODEC = StreamCodec.composite(
                ByteBufCodecs.STRING_UTF8, CropSeed::crop,
                ByteBufCodecs.VAR_INT, CropSeed::growth,
                ByteBufCodecs.VAR_INT, CropSeed::gain,
                ByteBufCodecs.VAR_INT, CropSeed::resistance,
                ByteBufCodecs.VAR_INT, CropSeed::scan,
                CropSeed::new);
    }

    public CropSeedItem(Properties properties) {
        super(properties.stacksTo(1));
    }

    public static ItemStack create(CropCard crop, int growth, int gain, int resistance, int scan) {
        ItemStack stack = new ItemStack(IC2Items.CROP_SEED_BAG.get());
        stack.set(IC2Components.CROP_SEED.get(), new CropSeed(crop.id(), growth, gain, resistance, scan));
        return stack;
    }

    public static @Nullable CropSeed data(ItemStack stack) {
        return stack.getItem() instanceof CropSeedItem ? stack.get(IC2Components.CROP_SEED.get()) : null;
    }

    @Override
    public Component getName(ItemStack stack) {
        CropSeed seed = data(stack);
        if (seed == null || seed.scan() == 0) {
            return Component.translatableWithFallback("item.ic2reborn.crop_seed_bag.unknown", "Unknown Seeds");
        }
        CropCard card = CropCards.byId(seed.crop());
        if (card == null) {
            return Component.translatableWithFallback("item.ic2reborn.crop_seed_bag.invalid", "Invalid Seeds");
        }
        return Component.translatable(card.seedType(), card.name());
    }

    @Override
    public void appendHoverText(ItemStack stack, TooltipContext context, TooltipDisplay display,
                                Consumer<Component> tooltip, TooltipFlag flag) {
        CropSeed seed = data(stack);
        if (seed != null && seed.scan() >= 4) {
            tooltip.accept(Component.literal("Gr " + seed.growth()).withStyle(ChatFormatting.DARK_GREEN));
            tooltip.accept(Component.literal("Ga " + seed.gain()).withStyle(ChatFormatting.GOLD));
            tooltip.accept(Component.literal("Re " + seed.resistance()).withStyle(ChatFormatting.DARK_AQUA));
        }
    }
}
