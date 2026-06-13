package net.ic2reborn;

import com.mojang.logging.LogUtils;
import net.ic2reborn.registry.IC2Blocks;
import net.ic2reborn.registry.IC2Items;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;
import org.slf4j.Logger;

@Mod(IC2Reborn.MODID)
public class IC2Reborn {

    public static final String MODID = "ic2reborn";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, MODID);

    public static final RegistryObject<CreativeModeTab> IC2_TAB =
            TABS.register("ic2reborn_tab", () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.ic2reborn"))
                    .icon(() -> new ItemStack(IC2Items.TREETAP.get()))
                    .displayItems((params, output) -> {
                        output.accept(IC2Items.TREETAP.get());
                        output.accept(IC2Blocks.RUBBER_LOG.get());
                        output.accept(IC2Blocks.RUBBER_LEAVES.get());
                        output.accept(IC2Blocks.RUBBER_SAPLING.get());
                        output.accept(Items.RESIN_CLUMP);
                        output.accept(Items.RESIN_BRICK);
                        output.accept(IC2Items.RUBBER.get());
                        output.accept(IC2Blocks.TIN_ORE.get());
                        output.accept(IC2Blocks.DEEPSLATE_TIN_ORE.get());
                        output.accept(IC2Blocks.LEAD_ORE.get());
                        output.accept(IC2Blocks.DEEPSLATE_LEAD_ORE.get());
                        output.accept(IC2Blocks.URANIUM_ORE.get());
                        output.accept(IC2Blocks.DEEPSLATE_URANIUM_ORE.get());
                        output.accept(IC2Items.RAW_TIN.get());
                        output.accept(IC2Items.RAW_LEAD.get());
                        output.accept(IC2Items.RAW_URANIUM.get());
                        output.accept(IC2Items.INGOT_TIN.get());
                        output.accept(IC2Items.INGOT_LEAD.get());
                        output.accept(IC2Items.INGOT_URANIUM.get());
                    })
                    .build());

    public IC2Reborn(FMLJavaModLoadingContext context) {
        var modBusGroup = context.getModBusGroup();
        IC2Blocks.BLOCKS.register(modBusGroup);
        IC2Items.ITEMS.register(modBusGroup);
        TABS.register(modBusGroup);
        LOGGER.info("IC2 Reborn — Módulo 1: Natureza carregado!");
    }
}
