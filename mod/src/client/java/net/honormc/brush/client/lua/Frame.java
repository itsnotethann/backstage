package net.honormc.brush.client.lua;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Maps script coordinates to world coordinates.
 *
 * <p>Build scripts are written facing north: {@code +x} is to the right (east),
 * {@code +y} is up, {@code +z} is forward (north), and block states are written
 * as if forward were north. When the player faces another way, positions and
 * states are rotated together, so a script's doorway always faces the player.
 *
 * <p>Paint scripts use {@link #identity}: script axes are world axes.
 */
public final class Frame {

    private final BlockPos origin;
    private final int rx, rz, fx, fz;
    private final Rotation rotation;
    private final Rotation inverse;

    private Frame(BlockPos origin, int rx, int rz, int fx, int fz, Rotation rotation) {
        this.origin = origin;
        this.rx = rx;
        this.rz = rz;
        this.fx = fx;
        this.fz = fz;
        this.rotation = rotation;
        this.inverse = switch (rotation) {
            case CLOCKWISE_90 -> Rotation.COUNTERCLOCKWISE_90;
            case COUNTERCLOCKWISE_90 -> Rotation.CLOCKWISE_90;
            default -> rotation;
        };
    }

    /** A build frame looking along {@code forward} (horizontal). */
    public static Frame facing(BlockPos origin, Direction forward) {
        Direction right = forward.getClockWise();
        Rotation rotation = switch (forward) {
            case EAST -> Rotation.CLOCKWISE_90;
            case SOUTH -> Rotation.CLOCKWISE_180;
            case WEST -> Rotation.COUNTERCLOCKWISE_90;
            default -> Rotation.NONE;
        };
        return new Frame(origin, right.getStepX(), right.getStepZ(), forward.getStepX(), forward.getStepZ(), rotation);
    }

    /** Script axes equal world axes; no state rotation. */
    public static Frame identity(BlockPos origin) {
        return new Frame(origin, 1, 0, 0, 1, Rotation.NONE);
    }

    public long toWorld(int x, int y, int z) {
        return BlockPos.asLong(
                origin.getX() + rx * x + fx * z,
                origin.getY() + y,
                origin.getZ() + rz * x + fz * z);
    }

    public int worldX(int x, int z) {
        return origin.getX() + rx * x + fx * z;
    }

    public int worldZ(int x, int z) {
        return origin.getZ() + rz * x + fz * z;
    }

    public BlockPos origin() {
        return origin;
    }

    /** Script-authored state to the state placed in the world. */
    public BlockState toWorld(BlockState state) {
        return rotation == Rotation.NONE ? state : state.rotate(rotation);
    }

    /** World state back into the script's north-facing terms. */
    public BlockState toScript(BlockState state) {
        return inverse == Rotation.NONE ? state : state.rotate(inverse);
    }
}
