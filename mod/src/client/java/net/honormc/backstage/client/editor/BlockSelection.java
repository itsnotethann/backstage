package net.honormc.backstage.client.editor;

import it.unimi.dsi.fastutil.longs.LongArrayFIFOQueue;
import it.unimi.dsi.fastutil.longs.LongArrayList;
import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.function.LongPredicate;
import net.honormc.backstage.protocol.BlockPosPacking;
import net.honormc.backstage.protocol.model.Shape;

public final class BlockSelection {
    public static final int FILL_RADIUS = 64;

    public record Result(long[] blocks, boolean capped) {
    }

    private BlockSelection() {
    }

    public static Shape.Box bounds(int x1, int y1, int z1, int x2, int y2, int z2) {
        return new Shape.Box(Math.min(x1, x2), Math.min(y1, y2), Math.min(z1, z2), Math.max(x1, x2),
                Math.max(y1, y2), Math.max(z1, z2));
    }

    public static Result box(Shape.Box box, LongPredicate include, int limit) {
        LongArrayList blocks = new LongArrayList();
        for (int y = box.minY(); y <= box.maxY(); y++) {
            for (int z = box.minZ(); z <= box.maxZ(); z++) {
                for (int x = box.minX(); x <= box.maxX(); x++) {
                    long pos = BlockPosPacking.pack(x, y, z);
                    if (!include.test(pos)) {
                        continue;
                    }
                    if (blocks.size() >= limit) {
                        return new Result(blocks.toLongArray(), true);
                    }
                    blocks.add(pos);
                }
            }
        }
        return new Result(blocks.toLongArray(), false);
    }

    public static Result flood(long start, LongPredicate same, int limit, int radius) {
        LongArrayList blocks = new LongArrayList();
        if (limit <= 0) {
            return new Result(new long[0], true);
        }
        if (!same.test(start)) {
            return new Result(new long[0], false);
        }
        int sx = BlockPosPacking.x(start);
        int sy = BlockPosPacking.y(start);
        int sz = BlockPosPacking.z(start);
        LongOpenHashSet seen = new LongOpenHashSet();
        LongArrayFIFOQueue queue = new LongArrayFIFOQueue();
        seen.add(start);
        queue.enqueue(start);
        boolean capped = false;
        int[][] steps = {{1, 0, 0}, {-1, 0, 0}, {0, 1, 0}, {0, -1, 0}, {0, 0, 1}, {0, 0, -1}};
        while (!queue.isEmpty()) {
            long pos = queue.dequeueLong();
            if (blocks.size() >= limit) {
                capped = true;
                break;
            }
            blocks.add(pos);
            int x = BlockPosPacking.x(pos);
            int y = BlockPosPacking.y(pos);
            int z = BlockPosPacking.z(pos);
            for (int[] step : steps) {
                int nx = x + step[0];
                int ny = y + step[1];
                int nz = z + step[2];
                if (Math.abs(nx - sx) > radius || Math.abs(ny - sy) > radius || Math.abs(nz - sz) > radius) {
                    continue;
                }
                long next = BlockPosPacking.pack(nx, ny, nz);
                if (seen.add(next) && same.test(next)) {
                    queue.enqueue(next);
                }
            }
        }
        return new Result(blocks.toLongArray(), capped);
    }
}
