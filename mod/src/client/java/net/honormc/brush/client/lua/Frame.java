package net.honormc.brush.client.lua;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;

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

    public BlockState toWorld(BlockState state) {
        return rotation == Rotation.NONE ? state : state.rotate(rotation);
    }

    public BlockState toScript(BlockState state) {
        return inverse == Rotation.NONE ? state : state.rotate(inverse);
    }
}
