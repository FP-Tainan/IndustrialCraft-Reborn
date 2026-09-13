package net.craftenergy.fabric;

import net.craftenergy.content.CraftEnergyContent;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerBlockEntityEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerChunkEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLevelEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerLifecycleEvents;
import net.fabricmc.fabric.api.event.lifecycle.v1.ServerTickEvents;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class CraftEnergyMod implements ModInitializer {
    public static final Logger LOGGER = LoggerFactory.getLogger("Craft Energy");

    @Override
    public void onInitialize() {
        CraftEnergyContent.init();

        ServerTickEvents.END_LEVEL_TICK.register(EnergyNetworkManager::tickLevel);
        ServerChunkEvents.CHUNK_UNLOAD.register((level, chunk) -> EnergyNetworkManager.onChunkUnload(level, chunk.getPos()));
        ServerBlockEntityEvents.BLOCK_ENTITY_LOAD.register((blockEntity, level) -> EnergyNetworkManager.onBlockEntityLoad(level, blockEntity));
        ServerBlockEntityEvents.BLOCK_ENTITY_UNLOAD.register((blockEntity, level) -> EnergyNetworkManager.onBlockEntityUnload(level, blockEntity));
        ServerLevelEvents.UNLOAD.register((server, level) -> EnergyNetworkManager.remove(level));
        ServerLifecycleEvents.SERVER_STOPPED.register(server -> EnergyNetworkManager.clear());

        LOGGER.info("Craft Energy carregado: CW = MV × RA");
    }
}
