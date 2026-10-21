package net.honormc.backstage.client.editor;

import it.unimi.dsi.fastutil.longs.LongOpenHashSet;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.honormc.backstage.protocol.BlockPosPacking;

public final class GroupOutline {
    public enum Face {
        WEST(-1, 0, 0),
        EAST(1, 0, 0),
        DOWN(0, -1, 0),
        UP(0, 1, 0),
        NORTH(0, 0, -1),
        SOUTH(0, 0, 1);

        public final int dx;
        public final int dy;
        public final int dz;

        Face(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }

        public int axis() {
            return dx != 0 ? 0 : dy != 0 ? 1 : 2;
        }
    }

    public record Quad(int x, int y, int z, Face face) {
    }

    public record Edge(int x, int y, int z, int axis, int length) {
    }

    public record Shape(List<Quad> faces, List<Edge> edges, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
    }

    private GroupOutline() {
    }

    public static Shape bounds(long[] blocks) {
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long block : blocks) {
            minX = Math.min(minX, BlockPosPacking.x(block));
            minY = Math.min(minY, BlockPosPacking.y(block));
            minZ = Math.min(minZ, BlockPosPacking.z(block));
            maxX = Math.max(maxX, BlockPosPacking.x(block));
            maxY = Math.max(maxY, BlockPosPacking.y(block));
            maxZ = Math.max(maxZ, BlockPosPacking.z(block));
        }
        return new Shape(List.of(), List.of(), minX, minY, minZ, maxX, maxY, maxZ);
    }

    public static Shape of(long[] blocks) {
        LongOpenHashSet set = new LongOpenHashSet(blocks);
        List<Quad> faces = new ArrayList<>();
        Map<Long, LongOpenHashSet> lines = new HashMap<>();
        int minX = Integer.MAX_VALUE;
        int minY = Integer.MAX_VALUE;
        int minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE;
        int maxY = Integer.MIN_VALUE;
        int maxZ = Integer.MIN_VALUE;
        for (long block : set) {
            int x = BlockPosPacking.x(block);
            int y = BlockPosPacking.y(block);
            int z = BlockPosPacking.z(block);
            minX = Math.min(minX, x);
            minY = Math.min(minY, y);
            minZ = Math.min(minZ, z);
            maxX = Math.max(maxX, x);
            maxY = Math.max(maxY, y);
            maxZ = Math.max(maxZ, z);
            for (Face face : Face.values()) {
                if (set.contains(BlockPosPacking.pack(x + face.dx, y + face.dy, z + face.dz))) {
                    continue;
                }
                faces.add(new Quad(x, y, z, face));
                for (Face side : Face.values()) {
                    if (side.axis() == face.axis()) {
                        continue;
                    }
                    int nx = x + side.dx;
                    int ny = y + side.dy;
                    int nz = z + side.dz;
                    boolean continues = set.contains(BlockPosPacking.pack(nx, ny, nz))
                            && !set.contains(BlockPosPacking.pack(nx + face.dx, ny + face.dy, nz + face.dz));
                    if (continues) {
                        continue;
                    }
                    addEdge(x, y, z, face, side, lines);
                }
            }
        }
        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<Long, LongOpenHashSet> line : lines.entrySet()) {
            long key = line.getKey();
            int axis = (int) (key >>> 60);
            long[] starts = line.getValue().toLongArray();
            Arrays.sort(starts);
            int i = 0;
            while (i < starts.length) {
                int j = i;
                while (j + 1 < starts.length && starts[j + 1] == starts[j] + 1) {
                    j++;
                }
                int a = (int) ((key >>> 30) & 0x3FFFFFFFL) - (1 << 29);
                int b = (int) (key & 0x3FFFFFFFL) - (1 << 29);
                int s = (int) starts[i];
                int length = j - i + 1;
                edges.add(switch (axis) {
                    case 0 -> new Edge(s, a, b, 0, length);
                    case 1 -> new Edge(a, s, b, 1, length);
                    default -> new Edge(a, b, s, 2, length);
                });
                i = j + 1;
            }
        }
        if (set.isEmpty()) {
            return new Shape(faces, edges, 0, 0, 0, -1, -1, -1);
        }
        return new Shape(faces, edges, minX, minY, minZ, maxX, maxY, maxZ);
    }

    private static void addEdge(int x, int y, int z, Face face, Face side, Map<Long, LongOpenHashSet> lines) {
        int[] origin = {x, y, z};
        int[] normal = {face.dx, face.dy, face.dz};
        int[] offset = {side.dx, side.dy, side.dz};
        int axis = 3 - face.axis() - side.axis();
        int[] start = new int[3];
        for (int i = 0; i < 3; i++) {
            if (i == axis) {
                start[i] = origin[i];
            } else {
                int n = normal[i] + offset[i];
                start[i] = origin[i] + (n > 0 ? 1 : 0);
            }
        }
        int along = start[axis];
        int a;
        int b;
        switch (axis) {
            case 0 -> {
                a = start[1];
                b = start[2];
            }
            case 1 -> {
                a = start[0];
                b = start[2];
            }
            default -> {
                a = start[0];
                b = start[1];
            }
        }
        long line = ((long) axis << 60) | ((long) (a + (1 << 29)) & 0x3FFFFFFFL) << 30 | ((long) (b + (1 << 29)) & 0x3FFFFFFFL);
        lines.computeIfAbsent(line, k -> new LongOpenHashSet()).add(along);
    }
}
