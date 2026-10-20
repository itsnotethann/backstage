package net.honormc.backstage.client.editor;

import java.util.Collection;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;

public final class MarkerPick {
    public static final double POINT_RADIUS = 0.6;
    public static final double RAY_STEP = 0.25;

    public record Ray(double x, double y, double z, double dx, double dy, double dz, double reach) {
    }

    private MarkerPick() {
    }

    public static Point point(Ray ray, Collection<Point> points) {
        Point best = null;
        double bestT = Double.MAX_VALUE;
        for (Point point : points) {
            double cx = point.x() - ray.x();
            double cy = point.y() + 0.15 - ray.y();
            double cz = point.z() - ray.z();
            double t = cx * ray.dx() + cy * ray.dy() + cz * ray.dz();
            if (t < 0 || t > ray.reach()) {
                continue;
            }
            double ex = cx - ray.dx() * t;
            double ey = cy - ray.dy() * t;
            double ez = cz - ray.dz() * t;
            if (ex * ex + ey * ey + ez * ez <= POINT_RADIUS * POINT_RADIUS && t < bestT) {
                best = point;
                bestT = t;
            }
        }
        return best;
    }

    public static Group group(long block, Collection<Group> groups) {
        for (Group group : groups) {
            for (long member : group.blocks()) {
                if (member == block) {
                    return group;
                }
            }
        }
        return null;
    }

    public static Region region(Ray ray, double feetX, double feetY, double feetZ, Collection<Region> regions) {
        for (Region region : regions) {
            if (contains(region.shape(), feetX, feetY, feetZ)) {
                return region;
            }
        }
        for (double t = 0; t <= ray.reach(); t += RAY_STEP) {
            double x = ray.x() + ray.dx() * t;
            double y = ray.y() + ray.dy() * t;
            double z = ray.z() + ray.dz() * t;
            for (Region region : regions) {
                if (contains(region.shape(), x, y, z)) {
                    return region;
                }
            }
        }
        return null;
    }

    public static Marker pick(Ray ray, Long block, double feetX, double feetY, double feetZ, Collection<Point> points,
                              Collection<Group> groups, Collection<Region> regions) {
        Point point = point(ray, points);
        if (point != null) {
            return point;
        }
        if (block != null) {
            Group group = group(block, groups);
            if (group != null) {
                return group;
            }
        }
        return region(ray, feetX, feetY, feetZ, regions);
    }

    public static boolean contains(Shape shape, double x, double y, double z) {
        return switch (shape) {
            case Shape.Box b -> x >= b.minX() && x < b.maxX() + 1 && y >= b.minY() && y < b.maxY() + 1
                    && z >= b.minZ() && z < b.maxZ() + 1;
            case Shape.Polygon p -> y >= p.minY() && y < p.maxY() + 1 && insidePolygon(p.xz(), x, z);
        };
    }

    static boolean insidePolygon(int[] xz, double x, double z) {
        boolean inside = false;
        int n = xz.length / 2;
        for (int i = 0, j = n - 1; i < n; j = i++) {
            double xi = xz[2 * i] + 0.5;
            double zi = xz[2 * i + 1] + 0.5;
            double xj = xz[2 * j] + 0.5;
            double zj = xz[2 * j + 1] + 0.5;
            if ((zi > z) != (zj > z) && x < (xj - xi) * (z - zi) / (zj - zi) + xi) {
                inside = !inside;
            }
        }
        return inside;
    }

    public static double[] centre(Shape shape) {
        return switch (shape) {
            case Shape.Box b -> new double[]{(b.minX() + b.maxX() + 1) / 2.0, (b.minY() + b.maxY() + 1) / 2.0,
                    (b.minZ() + b.maxZ() + 1) / 2.0};
            case Shape.Polygon p -> {
                int[] xz = p.xz();
                double sx = 0;
                double sz = 0;
                for (int i = 0; i < xz.length; i += 2) {
                    sx += xz[i] + 0.5;
                    sz += xz[i + 1] + 0.5;
                }
                yield new double[]{sx / p.vertices(), (p.minY() + p.maxY() + 1) / 2.0, sz / p.vertices()};
            }
        };
    }

    public static double distanceSquared(Shape shape, double x, double y, double z) {
        double minX;
        double minY;
        double minZ;
        double maxX;
        double maxY;
        double maxZ;
        switch (shape) {
            case Shape.Box b -> {
                minX = b.minX();
                minY = b.minY();
                minZ = b.minZ();
                maxX = b.maxX() + 1;
                maxY = b.maxY() + 1;
                maxZ = b.maxZ() + 1;
            }
            case Shape.Polygon p -> {
                int[] xz = p.xz();
                minX = Double.MAX_VALUE;
                minZ = Double.MAX_VALUE;
                maxX = -Double.MAX_VALUE;
                maxZ = -Double.MAX_VALUE;
                for (int i = 0; i < xz.length; i += 2) {
                    minX = Math.min(minX, xz[i] + 0.5);
                    maxX = Math.max(maxX, xz[i] + 0.5);
                    minZ = Math.min(minZ, xz[i + 1] + 0.5);
                    maxZ = Math.max(maxZ, xz[i + 1] + 0.5);
                }
                minY = p.minY();
                maxY = p.maxY() + 1;
            }
        }
        double dx = Math.max(0, Math.max(minX - x, x - maxX));
        double dy = Math.max(0, Math.max(minY - y, y - maxY));
        double dz = Math.max(0, Math.max(minZ - z, z - maxZ));
        return dx * dx + dy * dy + dz * dz;
    }
}
