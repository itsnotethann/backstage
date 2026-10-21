package net.honormc.backstage.client.editor;

public final class GizmoMath {
    public enum Axis {
        X(1, 0, 0),
        Y(0, 1, 0),
        Z(0, 0, 1);

        public final int dx;
        public final int dy;
        public final int dz;

        Axis(int dx, int dy, int dz) {
            this.dx = dx;
            this.dy = dy;
            this.dz = dz;
        }
    }

    private GizmoMath() {
    }

    public static double alongAxis(MarkerPick.Ray ray, double ox, double oy, double oz, Axis axis) {
        double ax = axis.dx;
        double ay = axis.dy;
        double az = axis.dz;
        double rx = ray.dx();
        double ry = ray.dy();
        double rz = ray.dz();
        double wx = ox - ray.x();
        double wy = oy - ray.y();
        double wz = oz - ray.z();
        double b = ax * rx + ay * ry + az * rz;
        double d = ax * wx + ay * wy + az * wz;
        double e = rx * wx + ry * wy + rz * wz;
        double denominator = 1 - b * b;
        if (Math.abs(denominator) < 1e-6) {
            return Double.NaN;
        }
        return (b * e - d) / denominator;
    }

    public static double snap(double delta, boolean free) {
        return free ? delta : Math.rint(delta);
    }

    public static double[] dragDelta(MarkerPick.Ray start, MarkerPick.Ray now, double ox, double oy, double oz, Axis axis,
                                     boolean free) {
        double from = alongAxis(start, ox, oy, oz, axis);
        double to = alongAxis(now, ox, oy, oz, axis);
        if (Double.isNaN(from) || Double.isNaN(to)) {
            return new double[]{0, 0, 0};
        }
        double delta = snap(to - from, free);
        return new double[]{axis.dx * delta, axis.dy * delta, axis.dz * delta};
    }

    public static double segmentDistance(float px, float py, float ax, float ay, float bx, float by) {
        double vx = bx - ax;
        double vy = by - ay;
        double length = vx * vx + vy * vy;
        double t = length == 0 ? 0 : Math.clamp(((px - ax) * vx + (py - ay) * vy) / length, 0, 1);
        double cx = ax + vx * t - px;
        double cy = ay + vy * t - py;
        return Math.sqrt(cx * cx + cy * cy);
    }

    public static Axis hovered(float cursorX, float cursorY, float[] origin, float[][] tips, float threshold) {
        Axis best = null;
        double bestDistance = threshold;
        for (Axis axis : Axis.values()) {
            float[] tip = tips[axis.ordinal()];
            if (origin == null || tip == null) {
                continue;
            }
            double distance = segmentDistance(cursorX, cursorY, origin[0], origin[1], tip[0], tip[1]);
            if (distance <= bestDistance) {
                best = axis;
                bestDistance = distance;
            }
        }
        return best;
    }

    public static double worldSize(double distance, double fovY, float screenHeight, float pixels) {
        return pixels * 2 * distance * Math.tan(fovY / 2) / screenHeight;
    }

    public static double[] planeHit(MarkerPick.Ray ray, double planeY) {
        if (Math.abs(ray.dy()) < 1e-6) {
            return null;
        }
        double t = (planeY - ray.y()) / ray.dy();
        if (t < 0) {
            return null;
        }
        return new double[]{ray.x() + ray.dx() * t, planeY, ray.z() + ray.dz() * t};
    }

    public static float yawTowards(double fromX, double fromZ, double toX, double toZ) {
        return (float) Math.toDegrees(Math.atan2(-(toX - fromX), toZ - fromZ));
    }

    public static float snapYaw(float yaw, float step) {
        float snapped = Math.round(yaw / step) * step;
        snapped %= 360;
        if (snapped > 180) {
            snapped -= 360;
        } else if (snapped <= -180) {
            snapped += 360;
        }
        return snapped;
    }
}
