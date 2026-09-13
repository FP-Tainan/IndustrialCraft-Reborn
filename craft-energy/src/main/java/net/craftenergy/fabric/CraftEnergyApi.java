package net.craftenergy.fabric;

import net.craftenergy.api.EnergyNode;
import net.craftenergy.api.Side;
import net.fabricmc.fabric.api.lookup.v1.block.BlockApiLookup;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;

/**
 * Ponto de entrada do Craft Energy para outros mods.
 *
 * <p>Para um bloco participar da rede, registre um provedor em {@link #NODE}:
 * <pre>{@code
 * CraftEnergyApi.NODE.registerForBlockEntity((machine, face) -> machine.energyNode(), MY_BLOCK_ENTITY);
 * CraftEnergyApi.NODE.registerForBlocks((level, pos, state, be, face) -> COPPER_CABLE_NODE, MY_CABLE_BLOCK);
 * }</pre>
 * O contexto é a face do bloco consultado; devolva {@code null} nas faces que não conectam.
 *
 * <p>Block entities são detectados sozinhos ao carregar/descarregar. Blocos sem block entity
 * (cabos) precisam chamar {@link #markChanged} quando são colocados, removidos ou mudam de
 * conexão.
 */
public final class CraftEnergyApi {
    public static final String MOD_ID = "craftenergy";

    public static final BlockApiLookup<EnergyNode, Direction> NODE = BlockApiLookup.get(
            Identifier.fromNamespaceAndPath(MOD_ID, "energy_node"), EnergyNode.class, Direction.class);

    private static final Side[] SIDES = Side.values();
    private static final Direction[] DIRECTIONS = Direction.values();

    private CraftEnergyApi() {}

    /** Avisa que a conexão elétrica em {@code pos} mudou; a rede é reconstruída no próximo tick. */
    public static void markChanged(Level level, BlockPos pos) {
        if (level instanceof ServerLevel serverLevel) {
            EnergyNetworkManager.get(serverLevel).invalidate(pos);
        }
    }

    public static Side side(Direction direction) {
        return SIDES[direction.ordinal()];
    }

    public static Direction direction(Side side) {
        return DIRECTIONS[side.ordinal()];
    }
}
