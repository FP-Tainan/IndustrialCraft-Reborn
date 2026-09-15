package net.ic2reborn.test;

import net.craftenergy.api.EnergyUnits;
import net.craftenergy.content.item.EnergyItems;
import net.fabricmc.fabric.api.gametest.v1.GameTest;
import net.ic2reborn.block.entity.MachineBlockEntity;
import net.ic2reborn.energy.MachineEnergyProfile;
import net.ic2reborn.menu.MachineGuiType;
import net.ic2reborn.recipe.MachineRecipes;
import net.ic2reborn.registry.IC2AutoBlocks;
import net.ic2reborn.registry.IC2AutoItems;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/** Advanced Solar Panels: painéis em tensões altas, gerador quântico e transformador molecular. */
public class AdvancedSolarGameTest {
    private static final BlockPos POS = new BlockPos(1, 1, 1);

    private static MachineBlockEntity place(GameTestHelper helper, Block block) {
        helper.setBlock(POS, block);
        return helper.getBlockEntity(POS, MachineBlockEntity.class);
    }

    /** Avançado 1.000 MV, híbrido 2.400, supremo 13.800 e quântico 69.000, com a produção de dia do IC2 × 500. */
    @GameTest(maxTicks = 20)
    public void solarPanelsRunAtHighVoltages(GameTestHelper helper) {
        Map<Block, long[]> expected = Map.of(
                IC2AutoBlocks.ADVANCED_SOLAR_PANEL.get(), new long[]{1_000, 4_000},
                IC2AutoBlocks.HYBRID_SOLAR_PANEL.get(), new long[]{2_400, 32_000},
                IC2AutoBlocks.ULTIMATE_SOLAR_PANEL.get(), new long[]{13_800, 256_000},
                IC2AutoBlocks.QUANTUM_SOLAR_PANEL.get(), new long[]{69_000, 2_048_000});
        expected.forEach((block, values) -> {
            MachineEnergyProfile profile = MachineEnergyProfile.of(MachineGuiType.fromBlockId(BuiltInRegistries.BLOCK.getKey(block).getPath()));
            if (profile.role() != MachineEnergyProfile.Role.GENERATOR || profile.voltage() != values[0] || profile.power() != values[1]) {
                helper.fail(block + " deveria gerar " + values[1] + " CW a " + values[0] + " MV: " + profile);
            }
        });
        helper.succeed();
    }

    /** Os slots de carga do painel carregam uma bateria com a energia guardada. */
    @GameTest(maxTicks = 40)
    public void advancedSolarPanelChargesBattery(GameTestHelper helper) {
        MachineBlockEntity panel = place(helper, IC2AutoBlocks.ADVANCED_SOLAR_PANEL.get());
        panel.setStoredEnergy(panel.getEnergyProfile().capacity());
        ItemStack battery = new ItemStack(IC2AutoItems.RE_BATTERY.get());
        panel.getInventory().setItem(0, battery);
        helper.succeedWhen(() -> {
            if (EnergyItems.getStored(panel.getInventory().getItem(0)) <= 0) helper.fail("a bateria ainda não carregou");
        });
    }

    /** Gerador quântico: os botões mudam tensão e produção; redstone desliga. */
    @GameTest(maxTicks = 60)
    public void quantumGeneratorFollowsButtonsAndRedstone(GameTestHelper helper) {
        MachineBlockEntity generator = place(helper, IC2AutoBlocks.QUANTUM_GENERATOR.get());
        if (!generator.handleMenuButton(MachineBlockEntity.BUTTON_QUANTUM_VOLTAGE + 4)) helper.fail("botão de tensão ignorado");
        if (!generator.handleMenuButton(MachineBlockEntity.BUTTON_QUANTUM_PRODUCTION + 5)) helper.fail("botão de produção ignorado");
        AtomicBoolean powered = new AtomicBoolean();
        helper.succeedWhen(() -> {
            if (!powered.get()) {
                if (generator.getEnergyProfile().voltage() != 69_000) helper.fail("deveria sair a 69.000 MV");
                if (generator.getStoredEnergy() != 356_000) helper.fail("deveria oferecer 356.000 CW, oferece " + generator.getStoredEnergy());
                helper.setBlock(POS.east(), Blocks.REDSTONE_BLOCK);
                powered.set(true);
                helper.fail("esperando a redstone");
            }
            if (generator.getStoredEnergy() != 0) helper.fail("com redstone deveria desligar");
        });
    }

    /** Transformador molecular: terra vira argila com 25.000 CWh. */
    @GameTest(maxTicks = 40)
    public void molecularTransformerTurnsDirtIntoClay(GameTestHelper helper) {
        MachineBlockEntity transformer = place(helper, IC2AutoBlocks.MOLECULAR_TRANSFORMER.get());
        transformer.getInventory().setItem(0, new ItemStack(Items.DIRT));
        transformer.setStoredEnergy(EnergyUnits.fromCWh(30_000));
        helper.succeedWhen(() -> {
            if (transformer.getInventory().getItem(1).getItem() != Items.CLAY_BALL) helper.fail("ainda sem argila");
            if (!transformer.getInventory().getItem(0).isEmpty()) helper.fail("a terra deveria ser consumida");
        });
    }

    /** As receitas do transformador molecular carregam com a energia em CWh (EU × 0,5). */
    @GameTest(maxTicks = 20)
    public void molecularRecipesLoad(GameTestHelper helper) {
        var recipe = MachineRecipes.INSTANCE.find("molecular_transformer", new ItemStack(Items.WITHER_SKELETON_SKULL), ItemStack.EMPTY, null, 0);
        if (recipe.isEmpty()) helper.fail("sem receita para o crânio de esqueleto wither");
        if (recipe.get().energy() != 125_000_000 || recipe.get().createResult().getItem() != Items.NETHER_STAR) {
            helper.fail("crânio → estrela do Nether deveria custar 125.000.000 CWh");
        }
        helper.succeed();
    }
}
