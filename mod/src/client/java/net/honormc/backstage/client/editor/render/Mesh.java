package net.honormc.backstage.client.editor.render;

import static org.lwjgl.opengl.GL33C.*;

import it.unimi.dsi.fastutil.floats.FloatArrayList;
import java.nio.FloatBuffer;
import org.lwjgl.system.MemoryUtil;

public final class Mesh {
    public static final int FLOATS = 8;

    private final FloatArrayList fill = new FloatArrayList();
    private final FloatArrayList lines = new FloatArrayList();
    public final double originX;
    public final double originY;
    public final double originZ;
    private int buffer;
    private int fillCount;
    private int lineCount;
    private boolean uploaded;

    public Mesh(double originX, double originY, double originZ) {
        this.originX = originX;
        this.originY = originY;
        this.originZ = originZ;
    }

    public boolean empty() {
        return fill.isEmpty() && lines.isEmpty();
    }

    public void quad(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz,
                     double dx, double dy, double dz) {
        vertex(fill, ax, ay, az, ax, ay, az, 0, -1);
        vertex(fill, bx, by, bz, bx, by, bz, 0, -1);
        vertex(fill, cx, cy, cz, cx, cy, cz, 0, -1);
        vertex(fill, ax, ay, az, ax, ay, az, 0, -1);
        vertex(fill, cx, cy, cz, cx, cy, cz, 0, -1);
        vertex(fill, dx, dy, dz, dx, dy, dz, 0, -1);
    }

    public void triangle(double ax, double ay, double az, double bx, double by, double bz, double cx, double cy, double cz) {
        vertex(fill, ax, ay, az, ax, ay, az, 0, -1);
        vertex(fill, bx, by, bz, bx, by, bz, 0, -1);
        vertex(fill, cx, cy, cz, cx, cy, cz, 0, -1);
    }

    public void line(double ax, double ay, double az, double bx, double by, double bz) {
        float length = (float) Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        vertex(lines, ax, ay, az, bx, by, bz, 1, 0);
        vertex(lines, ax, ay, az, bx, by, bz, -1, 0);
        vertex(lines, bx, by, bz, ax, ay, az, -1, length);
        vertex(lines, ax, ay, az, bx, by, bz, -1, 0);
        vertex(lines, bx, by, bz, ax, ay, az, 1, length);
        vertex(lines, bx, by, bz, ax, ay, az, -1, length);
    }

    public void box(double x0, double y0, double z0, double x1, double y1, double z1, boolean faces) {
        double[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
        for (int i = 0; i < 4; i++) {
            double[] a = corners[i];
            double[] b = corners[(i + 1) % 4];
            line(a[0], y0, a[1], b[0], y0, b[1]);
            line(a[0], y1, a[1], b[0], y1, b[1]);
            line(a[0], y0, a[1], a[0], y1, a[1]);
            if (faces) {
                quad(a[0], y0, a[1], b[0], y0, b[1], b[0], y1, b[1], a[0], y1, a[1]);
            }
        }
        if (faces) {
            quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            quad(x0, y1, z0, x1, y1, z0, x1, y1, z1, x0, y1, z1);
        }
    }

    public void circle(double cx, double cy, double cz, double radius, int axis, int segments) {
        double px = 0;
        double py = 0;
        double pz = 0;
        for (int i = 0; i <= segments; i++) {
            double a = Math.PI * 2 * i / segments;
            double u = Math.cos(a) * radius;
            double v = Math.sin(a) * radius;
            double x = cx + (axis == 0 ? 0 : u);
            double y = cy + (axis == 1 ? 0 : axis == 0 ? u : v);
            double z = cz + (axis == 2 ? 0 : v);
            if (i > 0) {
                line(px, py, pz, x, y, z);
            }
            px = x;
            py = y;
            pz = z;
        }
    }

    private void vertex(FloatArrayList out, double x, double y, double z, double ox, double oy, double oz, float side, float dist) {
        out.add((float) (x - originX));
        out.add((float) (y - originY));
        out.add((float) (z - originZ));
        out.add((float) (ox - originX));
        out.add((float) (oy - originY));
        out.add((float) (oz - originZ));
        out.add(side);
        out.add(dist);
        uploaded = false;
    }

    void bind() {
        if (buffer == 0) {
            buffer = glGenBuffers();
        }
        glBindBuffer(GL_ARRAY_BUFFER, buffer);
        if (!uploaded) {
            fillCount = fill.size() / FLOATS;
            lineCount = lines.size() / FLOATS;
            FloatBuffer data = MemoryUtil.memAllocFloat(Math.max(1, fill.size() + lines.size()));
            try {
                data.put(fill.elements(), 0, fill.size());
                data.put(lines.elements(), 0, lines.size());
                data.flip();
                glBufferData(GL_ARRAY_BUFFER, data, GL_STATIC_DRAW);
            } finally {
                MemoryUtil.memFree(data);
            }
            uploaded = true;
        }
        int stride = FLOATS * Float.BYTES;
        glVertexAttribPointer(0, 3, GL_FLOAT, false, stride, 0);
        glVertexAttribPointer(1, 3, GL_FLOAT, false, stride, 3L * Float.BYTES);
        glVertexAttribPointer(2, 1, GL_FLOAT, false, stride, 6L * Float.BYTES);
        glVertexAttribPointer(3, 1, GL_FLOAT, false, stride, 7L * Float.BYTES);
    }

    int fillCount() {
        return fillCount;
    }

    int lineCount() {
        return lineCount;
    }

    public void delete() {
        if (buffer != 0) {
            glDeleteBuffers(buffer);
            buffer = 0;
        }
    }
}
