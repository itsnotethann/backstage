package net.honormc.backstage.client.editor;

import it.unimi.dsi.fastutil.ints.IntArrayList;
import net.honormc.backstage.protocol.EditorProtocol;
import net.honormc.backstage.protocol.model.Shape;

public final class PolygonBuilder {
    private final IntArrayList xz = new IntArrayList();
    private int minY = Integer.MAX_VALUE;
    private int maxY = Integer.MIN_VALUE;

    public String add(int x, int y, int z) {
        int count = vertices();
        if (count >= EditorProtocol.MAX_POLYGON_VERTICES) {
            return "A polygon has at most " + EditorProtocol.MAX_POLYGON_VERTICES + " vertices.";
        }
        if (count > 0 && xz.getInt(xz.size() - 2) == x && xz.getInt(xz.size() - 1) == z) {
            return "That vertex is the same as the last one.";
        }
        xz.add(x);
        xz.add(z);
        minY = Math.min(minY, y);
        maxY = Math.max(maxY, y);
        return null;
    }

    public int vertices() {
        return xz.size() / 2;
    }

    public int minY() {
        return minY;
    }

    public int maxY() {
        return maxY;
    }

    public Shape.Polygon build(Integer heightMin, Integer heightMax) {
        int count = vertices();
        if (count < EditorProtocol.MIN_POLYGON_VERTICES) {
            throw new IllegalArgumentException("A polygon needs at least " + EditorProtocol.MIN_POLYGON_VERTICES
                    + " vertices, this one has " + count + ".");
        }
        int[] points = xz.toIntArray();
        if (points[0] == points[points.length - 2] && points[1] == points[points.length - 1]) {
            throw new IllegalArgumentException("The last vertex repeats the first; the polygon closes by itself.");
        }
        if (selfIntersects(points)) {
            throw new IllegalArgumentException("The polygon's edges cross each other.");
        }
        if (area2(points) == 0) {
            throw new IllegalArgumentException("All vertices lie on one line.");
        }
        int low = heightMin != null ? heightMin : minY;
        int high = heightMax != null ? heightMax : maxY;
        if (low > high) {
            throw new IllegalArgumentException("minY " + low + " is above maxY " + high + ".");
        }
        return new Shape.Polygon(points, low, high);
    }

    static long area2(int[] xz) {
        long sum = 0;
        int n = xz.length / 2;
        for (int i = 0; i < n; i++) {
            int j = (i + 1) % n;
            sum += (long) xz[2 * i] * xz[2 * j + 1] - (long) xz[2 * j] * xz[2 * i + 1];
        }
        return sum;
    }

    static boolean selfIntersects(int[] xz) {
        int n = xz.length / 2;
        for (int i = 0; i < n; i++) {
            for (int j = i + 1; j < n; j++) {
                if (j == i + 1 || (i == 0 && j == n - 1)) {
                    if (overlapsAdjacent(xz, i, j, n)) {
                        return true;
                    }
                    continue;
                }
                if (crosses(xz, i, (i + 1) % n, j, (j + 1) % n)) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean overlapsAdjacent(int[] xz, int i, int j, int n) {
        int shared = j == i + 1 ? j : i;
        int a = j == i + 1 ? i : j;
        int b = (shared + 1) % n;
        long ax = xz[2 * a] - xz[2 * shared];
        long az = xz[2 * a + 1] - xz[2 * shared + 1];
        long bx = xz[2 * b] - xz[2 * shared];
        long bz = xz[2 * b + 1] - xz[2 * shared + 1];
        return ax * bz - az * bx == 0 && ax * bx + az * bz > 0;
    }

    private static boolean crosses(int[] xz, int a, int b, int c, int d) {
        long o1 = orient(xz, a, b, c);
        long o2 = orient(xz, a, b, d);
        long o3 = orient(xz, c, d, a);
        long o4 = orient(xz, c, d, b);
        if (Long.signum(o1) * Long.signum(o2) < 0 && Long.signum(o3) * Long.signum(o4) < 0) {
            return true;
        }
        return o1 == 0 && onSegment(xz, a, b, c) || o2 == 0 && onSegment(xz, a, b, d)
                || o3 == 0 && onSegment(xz, c, d, a) || o4 == 0 && onSegment(xz, c, d, b);
    }

    private static long orient(int[] xz, int p, int q, int r) {
        return (long) (xz[2 * q] - xz[2 * p]) * (xz[2 * r + 1] - xz[2 * p + 1])
                - (long) (xz[2 * q + 1] - xz[2 * p + 1]) * (xz[2 * r] - xz[2 * p]);
    }

    private static boolean onSegment(int[] xz, int p, int q, int r) {
        return Math.min(xz[2 * p], xz[2 * q]) <= xz[2 * r] && xz[2 * r] <= Math.max(xz[2 * p], xz[2 * q])
                && Math.min(xz[2 * p + 1], xz[2 * q + 1]) <= xz[2 * r + 1]
                && xz[2 * r + 1] <= Math.max(xz[2 * p + 1], xz[2 * q + 1]);
    }
}
