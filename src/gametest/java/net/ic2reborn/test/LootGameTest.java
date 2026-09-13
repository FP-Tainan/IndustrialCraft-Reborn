package net.ic2reborn.test;

import net.craftenergy.content.CEBlocks;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2Blocks;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.storage.loot.LootTable;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Stream;

/** Garante que todo bloco do IC2 Reborn e do Craft Energy encontra a própria loot table (loot_table/blocks/). */
public class LootGameTest {

    @GameTest(maxTicks = 20)
    public void everyBlockHasItsLootTable(GameTestHelper helper) {
        var registries = helper.getLevel().getServer().reloadableRegistries();
        List<String> missing = new ArrayList<>();

        Stream.of(IC2AutoBlocks.BLOCKS, IC2Blocks.BLOCKS, CEBlocks.BLOCKS)
                .flatMap(register -> register.getEntries().stream())
                .forEach(entry -> {
                    Block block = entry.get();
                    Optional<ResourceKey<LootTable>> key = block.getLootTable();
                    if (key.isEmpty() || registries.getLootTable(key.get()) == LootTable.EMPTY) {
                        missing.add(entry.getId().toString());
                    }
                });

        if (!missing.isEmpty()) {
            helper.fail(missing.size() + " blocos sem loot table: " + missing);
        }
        helper.succeed();
    }
}
