package net.honormc.backstage.client.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.function.Function;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public final class Marquee {
    public record Rect(float x0, float y0, float x1, float y1) {
        public static Rect between(float ax, float ay, float bx, float by) {
            return new Rect(Math.min(ax, bx), Math.min(ay, by), Math.max(ax, bx), Math.max(ay, by));
        }

        public boolean contains(float x, float y) {
            return x >= x0 && x <= x1 && y >= y0 && y <= y1;
        }

        public float width() {
            return x1 - x0;
        }

        public float height() {
            return y1 - y0;
        }
    }

    public record View(Matrix4fc viewProjection, double cameraX, double cameraY, double cameraZ, float width, float height) {
        public float[] project(double x, double y, double z) {
            Vector4f clip = new Vector4f((float) (x - cameraX), (float) (y - cameraY), (float) (z - cameraZ), 1)
                    .mul(viewProjection);
            if (clip.w <= 1e-4f) {
                return null;
            }
            float nx = clip.x / clip.w;
            float ny = clip.y / clip.w;
            return new float[]{(nx + 1) / 2 * width, (1 - ny) / 2 * height};
        }
    }

    private Marquee() {
    }

    public static <T> List<T> select(Collection<T> items, Function<T, double[]> centre, View view, Rect rect) {
        List<T> inside = new ArrayList<>();
        for (T item : items) {
            double[] c = centre.apply(item);
            if (c == null) {
                continue;
            }
            float[] screen = view.project(c[0], c[1], c[2]);
            if (screen != null && rect.contains(screen[0], screen[1])) {
                inside.add(item);
            }
        }
        return inside;
    }
}
