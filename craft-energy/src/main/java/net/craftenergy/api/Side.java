package net.craftenergy.api;

/**
 * Face de um bloco. A ordem é a mesma de {@code net.minecraft.core.Direction}
 * (DOWN, UP, NORTH, SOUTH, WEST, EAST), então {@code Side.values()[direction.ordinal()]}
 * converte direto.
 */
public enum Side {
    DOWN(0, -1, 0),
    UP(0, 1, 0),
    NORTH(0, 0, -1),
    SOUTH(0, 0, 1),
    WEST(-1, 0, 0),
    EAST(1, 0, 0);

    public final int dx;
    public final int dy;
    public final int dz;

    Side(int dx, int dy, int dz) {
        this.dx = dx;
        this.dy = dy;
        this.dz = dz;
    }

    public Side opposite() {
        return switch (this) {
            case DOWN -> UP;
            case UP -> DOWN;
            case NORTH -> SOUTH;
            case SOUTH -> NORTH;
            case WEST -> EAST;
            case EAST -> WEST;
        };
    }
}
