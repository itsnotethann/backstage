package net.honormc.brush.client.lua;

import it.unimi.dsi.fastutil.longs.Long2ObjectLinkedOpenHashMap;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.state.BlockState;

/**
 * The result of running a script: world position to new state, in the order
 * the script wrote them (a later write to the same block wins). Plus the
 * bounding box, for the preview outline.
 */
public final class Edit {

    private final Long2ObjectLinkedOpenHashMap<BlockState> changes = new Long2ObjectLinkedOpenHashMap<>();
    private int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
    private int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;

    void put(long pos, BlockState state) {
        changes.put(pos, state);
        int x = BlockPos.getX(pos), y = BlockPos.getY(pos), z = BlockPos.getZ(pos);
        if (x < minX) minX = x;
        if (y < minY) minY = y;
        if (z < minZ) minZ = z;
        if (x > maxX) maxX = x;
        if (y > maxY) maxY = y;
        if (z > maxZ) maxZ = z;
    }

    /** The state this edit will write at {@code pos}, or null if it leaves it alone. */
    BlockState pending(long pos) {
        return changes.get(pos);
    }

    public Long2ObjectLinkedOpenHashMap<BlockState> changes() {
        return changes;
    }

    public int size() {
        return changes.size();
    }

    public boolean isEmpty() {
        return changes.isEmpty();
    }

    public BlockPos min() {
        return new BlockPos(minX, minY, minZ);
    }

    public BlockPos max() {
        return new BlockPos(maxX, maxY, maxZ);
    }
}
