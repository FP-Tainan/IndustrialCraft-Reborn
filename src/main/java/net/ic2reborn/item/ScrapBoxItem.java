package net.ic2reborn.item;

import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.util.RandomSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/** Caixa de sucata do IC2: aberta (botão direito ou dispensador) sorteia um item da tabela do IC2. */
public class ScrapBoxItem extends Item {
    private record Drop(String id, float weight) {}

    /** {@code ScrapboxRecipeManager} do IC2, com os nomes atuais dos itens. */
    private static final List<Drop> DROPS = List.of(
            new Drop("minecraft:wooden_hoe", 5.01F), new Drop("minecraft:dirt", 5.0F), new Drop("minecraft:stick", 4.0F),
            new Drop("minecraft:grass_block", 3.0F), new Drop("minecraft:gravel", 3.0F), new Drop("minecraft:netherrack", 2.0F),
            new Drop("minecraft:rotten_flesh", 2.0F), new Drop("minecraft:apple", 1.5F), new Drop("minecraft:bread", 1.5F),
            new Drop("ic2reborn:filled_tin_can", 1.5F), new Drop("minecraft:wooden_sword", 1.0F),
            new Drop("minecraft:wooden_shovel", 1.0F), new Drop("minecraft:wooden_pickaxe", 1.0F),
            new Drop("minecraft:soul_sand", 1.0F), new Drop("minecraft:oak_sign", 1.0F), new Drop("minecraft:leather", 1.0F),
            new Drop("minecraft:feather", 1.0F), new Drop("minecraft:bone", 1.0F), new Drop("minecraft:cooked_porkchop", 0.9F),
            new Drop("minecraft:cooked_beef", 0.9F), new Drop("minecraft:pumpkin", 0.9F), new Drop("minecraft:cooked_chicken", 0.9F),
            new Drop("minecraft:minecart", 0.01F), new Drop("minecraft:redstone", 0.9F), new Drop("craftenergy:rubber", 0.8F),
            new Drop("minecraft:glowstone_dust", 0.8F), new Drop("ic2reborn:dust_coal", 0.8F), new Drop("ic2reborn:dust_copper", 0.8F),
            new Drop("ic2reborn:dust_tin", 0.8F), new Drop("craftenergy:single_use_battery", 0.7F),
            new Drop("ic2reborn:dust_iron", 0.7F), new Drop("ic2reborn:dust_gold", 0.7F), new Drop("minecraft:slime_ball", 0.6F),
            new Drop("minecraft:iron_ore", 0.5F), new Drop("minecraft:golden_helmet", 0.01F), new Drop("minecraft:gold_ore", 0.5F),
            new Drop("minecraft:cake", 0.5F), new Drop("minecraft:diamond", 0.1F), new Drop("minecraft:emerald", 0.05F),
            new Drop("minecraft:ender_pearl", 0.08F), new Drop("minecraft:blaze_rod", 0.04F), new Drop("minecraft:egg", 0.8F),
            new Drop("minecraft:copper_ore", 0.7F), new Drop("craftenergy:tin_ore", 0.7F));

    private static @Nullable List<Item> items;
    private static float[] cumulative;

    public ScrapBoxItem(Properties properties) {
        super(properties);
    }

    private static void resolve() {
        if (items != null) return;
        List<Item> resolved = new ArrayList<>();
        List<Float> weights = new ArrayList<>();
        for (Drop drop : DROPS) {
            BuiltInRegistries.ITEM.getOptional(Identifier.parse(drop.id())).ifPresent(item -> {
                resolved.add(item);
                weights.add(drop.weight());
            });
        }
        cumulative = new float[weights.size()];
        float total = 0.0F;
        for (int i = 0; i < weights.size(); i++) {
            total += weights.get(i);
            cumulative[i] = total;
        }
        items = resolved;
    }

    /** Sorteia um item da caixa. */
    public static ItemStack roll(RandomSource random) {
        resolve();
        float pick = random.nextFloat() * cumulative[cumulative.length - 1];
        for (int i = 0; i < cumulative.length; i++) {
            if (pick < cumulative[i]) return new ItemStack(items.get(i));
        }
        return new ItemStack(items.getLast());
    }

    @Override
    public InteractionResult use(Level level, Player player, InteractionHand hand) {
        if (!level.isClientSide()) {
            ItemStack stack = player.getItemInHand(hand);
            ItemStack drop = roll(level.getRandom());
            if (!player.getAbilities().instabuild) stack.shrink(1);
            if (!player.getInventory().add(drop)) player.drop(drop, false);
        }
        return InteractionResult.SUCCESS;
    }
}
