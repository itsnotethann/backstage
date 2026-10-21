package net.honormc.backstage.client.editor.render;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.honormc.backstage.client.editor.EditorLayout;
import net.honormc.backstage.client.editor.EditorScale;
import net.honormc.backstage.client.editor.EditorTools;
import net.honormc.backstage.client.editor.EditorTools.Selection;
import net.honormc.backstage.client.editor.GroupDraft;
import net.honormc.backstage.client.editor.GroupOutline;
import net.honormc.backstage.client.editor.MarkerPick;
import net.honormc.backstage.client.editor.MarkerStore;
import net.honormc.backstage.client.editor.Theme;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

public final class WorldOverlay {
    public static final int TOTAL_BLOCK_CAP = 60_000;

    private static final double INFLATE = 0.003;
    private static final float LINE = 2.2f;
    private static final float GLOW = 7f;

    public record Visible(Marker marker, boolean selected, boolean hovered) {
    }

    private final Map<Marker, Mesh> cache = new IdentityHashMap<>();
    private Mesh draftMesh;
    private long draftVersion = -1;
    private final List<Mesh> transientMeshes = new ArrayList<>();
    private final OverlayPass pass = new OverlayPass();
    private final List<Visible> visible = new ArrayList<>();
    private int drawnBlocks;

    public List<Visible> visible() {
        return visible;
    }

    public int drawnBlocks() {
        return drawnBlocks;
    }

    public void clear() {
        cache.values().forEach(Mesh::delete);
        cache.clear();
        if (draftMesh != null) {
            draftMesh.delete();
            draftMesh = null;
        }
        draftVersion = -1;
        visible.clear();
    }

    public void collect(MarkerStore store, EditorTools tools, EditorLayout layout, Vec3 eye) {
        visible.clear();
        double r2 = (double) layout.renderRadius * layout.renderRadius;
        Marker hovered = tools.hovered();
        Set<Marker> live = Collections.newSetFromMap(new IdentityHashMap<>());
        GroupDraft draft = tools.draft();
        int blocks = 0;
        for (Marker marker : store.all()) {
            Selection key = new Selection(marker.type(), marker.id());
            if (tools.hidden(key)) {
                continue;
            }
            boolean selected = tools.selected(key);
            boolean shown = switch (marker.type()) {
                case POINT -> layout.points;
                case GROUP -> layout.groups;
                case REGION -> layout.regions;
            };
            if (!selected && (!shown || distance(marker, eye) > r2)) {
                continue;
            }
            if (marker instanceof Group group) {
                if (draft != null && draft.id().equals(group.id())) {
                    continue;
                }
                int size = group.blocks().length;
                if (!selected && blocks + size > TOTAL_BLOCK_CAP) {
                    continue;
                }
                blocks += size;
            }
            live.add(marker);
            visible.add(new Visible(marker, selected, marker == hovered || hovered != null && marker.equals(hovered)));
        }
        if (draft != null) {
            blocks += draft.size();
        }
        drawnBlocks = blocks;
        cache.entrySet().removeIf(entry -> {
            if (!live.contains(entry.getKey())) {
                entry.getValue().delete();
                return true;
            }
            return false;
        });
    }

    private static double distance(Marker marker, Vec3 eye) {
        return switch (marker) {
            case Point p -> eye.distanceToSqr(p.x(), p.y(), p.z());
            case Region r -> MarkerPick.distanceSquared(r.shape(), eye.x, eye.y, eye.z);
            case Group g -> {
                long[] blocks = g.blocks();
                if (blocks.length == 0) {
                    yield Double.MAX_VALUE;
                }
                GroupOutline.Shape bounds = GroupOutline.bounds(blocks);
                yield MarkerPick.distanceSquared(new Shape.Box(bounds.minX(), bounds.minY(), bounds.minZ(), bounds.maxX(),
                        bounds.maxY(), bounds.maxZ()), eye.x, eye.y, eye.z);
            }
        };
    }

    public void render(OverlayFrame frame, EditorTools tools, boolean xray) {
        if (!pass.begin(frame.target(), frame.projection(), frame.view(), frame.time())) {
            return;
        }
        try {
            float scale = EditorScale.ui();
            double cx = frame.cameraX();
            double cy = frame.cameraY();
            double cz = frame.cameraZ();
            for (Visible v : visible) {
                Mesh mesh = cache.computeIfAbsent(v.marker(), WorldOverlay::build);
                int colour = Theme.typeColour(v.marker().type());
                float fillAlpha = v.marker().type() == MarkerType.REGION ? 0.08f : 0.14f;
                if (v.hovered()) {
                    colour = Theme.mix(colour, Theme.WHITE, 0.35f);
                    fillAlpha += 0.06f;
                }
                drawMesh(mesh, cx, cy, cz, colour, fillAlpha, v.selected(), xray, scale, 0, 0, 0);
            }
            GroupDraft draft = tools.draft();
            if (draft != null) {
                if (draftMesh == null || draftVersion != tools.draftVersion()) {
                    if (draftMesh != null) {
                        draftMesh.delete();
                    }
                    draftMesh = groupMesh(draft.blocks());
                    draftVersion = tools.draftVersion();
                }
                drawMesh(draftMesh, cx, cy, cz, Theme.GROUP, 0.2f, true, xray, scale, 0, 0, 0);
            }
            double[] delta = tools.moveDelta();
            if (delta != null) {
                for (Marker marker : tools.selectedMarkers()) {
                    Mesh ghost = cache.get(marker);
                    if (ghost == null) {
                        ghost = transientMesh(build(marker));
                    }
                    drawMesh(ghost, cx, cy, cz, Theme.SPOT, 0.12f, true, true, scale, delta[0], delta[1], delta[2]);
                }
            }
            previews(tools, cx, cy, cz, scale, xray);
        } finally {
            pass.end();
            transientMeshes.forEach(Mesh::delete);
            transientMeshes.clear();
        }
    }

    private Mesh transientMesh(Mesh mesh) {
        transientMeshes.add(mesh);
        return mesh;
    }

    private void drawMesh(Mesh mesh, double cx, double cy, double cz, int colour, float fillAlpha, boolean selected,
                          boolean xray, float scale, double ox, double oy, double oz) {
        int line = selected ? Theme.SPOT : colour;
        pass.fill(mesh, cx, cy, cz, colour, fillAlpha, true, ox, oy, oz);
        pass.lines(mesh, cx, cy, cz, line, selected ? 0.32f : 0.16f, GLOW * scale, true, true, false, ox, oy, oz);
        pass.lines(mesh, cx, cy, cz, line, 1f, LINE * scale * (selected ? 1.25f : 1), true, false, selected, ox, oy, oz);
        if (xray) {
            pass.lines(mesh, cx, cy, cz, line, 0.4f, LINE * scale, false, false, false, ox, oy, oz);
        }
    }

    private void previews(EditorTools tools, double cx, double cy, double cz, float scale, boolean xray) {
        EditorTools.Cursor cursor = tools.cursor();
        switch (tools.tool()) {
            case GROUP -> {
                if (cursor != null && cursor.block() != null && tools.dragging() == null) {
                    BlockPos b = cursor.block();
                    int r = tools.brushRadius;
                    Mesh sphere = transientMesh(new Mesh(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5));
                    double radius = r + 0.5;
                    for (int axis = 0; axis < 3; axis++) {
                        sphere.circle(b.getX() + 0.5, b.getY() + 0.5, b.getZ() + 0.5, radius, axis, 40);
                    }
                    sphere.box(b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1, true);
                    boolean erase = tools.brushMode == EditorTools.BrushMode.ERASE ^ cursor.ctrl();
                    int colour = erase ? Theme.DANGER : Theme.GROUP;
                    pass.fill(sphere, cx, cy, cz, colour, 0.1f, true, 0, 0, 0);
                    pass.lines(sphere, cx, cy, cz, colour, 0.85f, 1.6f * scale, false, false, true, 0, 0, 0);
                }
            }
            case BOX -> {
                Shape.Box box = tools.boxDraft();
                if (tools.liveShape() instanceof Shape.Box live) {
                    box = live;
                }
                if (box != null) {
                    Mesh mesh = transientMesh(new Mesh(box.minX(), box.minY(), box.minZ()));
                    mesh.box(box.minX() - INFLATE, box.minY() - INFLATE, box.minZ() - INFLATE, box.maxX() + 1 + INFLATE,
                            box.maxY() + 1 + INFLATE, box.maxZ() + 1 + INFLATE, true);
                    int colour = tools.boxAsGroup ? Theme.GROUP : Theme.REGION;
                    drawMesh(mesh, cx, cy, cz, colour, 0.12f, true, xray, scale, 0, 0, 0);
                } else if (cursor != null && cursor.block() != null) {
                    BlockPos b = cursor.block();
                    Mesh mesh = transientMesh(new Mesh(b.getX(), b.getY(), b.getZ()));
                    mesh.box(b.getX(), b.getY(), b.getZ(), b.getX() + 1, b.getY() + 1, b.getZ() + 1, true);
                    pass.fill(mesh, cx, cy, cz, Theme.SPOT, 0.12f, true, 0, 0, 0);
                    pass.lines(mesh, cx, cy, cz, Theme.SPOT, 0.9f, 1.6f * scale, true, false, false, 0, 0, 0);
                }
            }
            case REGION -> {
                List<int[]> poly = tools.polygonDraft();
                if (tools.liveShape() != null) {
                    Mesh mesh = transientMesh(new Mesh(0, 0, 0));
                    region(mesh, tools.liveShape());
                    drawMesh(mesh, cx, cy, cz, Theme.REGION, 0.1f, true, xray, scale, 0, 0, 0);
                }
                if (!poly.isEmpty()) {
                    int[] first = poly.getFirst();
                    Mesh mesh = transientMesh(new Mesh(first[0], first[1], first[2]));
                    for (int i = 0; i + 1 < poly.size(); i++) {
                        int[] a = poly.get(i);
                        int[] b = poly.get(i + 1);
                        mesh.line(a[0] + 0.5, a[1] + 1.02, a[2] + 0.5, b[0] + 0.5, b[1] + 1.02, b[2] + 0.5);
                    }
                    for (int[] v : poly) {
                        mesh.line(v[0] + 0.5, v[1] + 1.02, v[2] + 0.5, v[0] + 0.5, v[1] + 3, v[2] + 0.5);
                    }
                    if (cursor != null && cursor.block() != null && tools.dragging() == null) {
                        int[] last = poly.getLast();
                        BlockPos b = cursor.block();
                        mesh.line(last[0] + 0.5, last[1] + 1.02, last[2] + 0.5, b.getX() + 0.5, b.getY() + 1.02, b.getZ() + 0.5);
                        if (poly.size() >= 2) {
                            mesh.line(b.getX() + 0.5, b.getY() + 1.02, b.getZ() + 0.5, first[0] + 0.5, first[1] + 1.02,
                                    first[2] + 0.5);
                        }
                    }
                    pass.lines(mesh, cx, cy, cz, Theme.REGION, 0.3f, GLOW * scale, true, true, false, 0, 0, 0);
                    pass.lines(mesh, cx, cy, cz, Theme.REGION, 1f, LINE * scale, false, false, true, 0, 0, 0);
                }
            }
            case POINT -> {
                Vec3 ghost = tools.ghostPoint();
                if (ghost != null) {
                    Mesh mesh = transientMesh(new Mesh(ghost.x, ghost.y, ghost.z));
                    point(mesh, ghost.x, ghost.y, ghost.z, cameraYawFallback());
                    pass.fill(mesh, cx, cy, cz, Theme.POINT, 0.35f, true, 0, 0, 0);
                    pass.lines(mesh, cx, cy, cz, Theme.POINT, 0.7f, 1.6f * scale, true, false, true, 0, 0, 0);
                }
                Float yaw = tools.liveYaw();
                if (yaw != null && !tools.selectedMarkers().isEmpty() && tools.selectedMarkers().getFirst() instanceof Point p) {
                    Mesh mesh = transientMesh(new Mesh(p.x(), p.y(), p.z()));
                    point(mesh, p.x(), p.y(), p.z(), yaw);
                    drawMesh(mesh, cx, cy, cz, Theme.POINT, 0.4f, true, xray, scale, 0, 0, 0);
                }
            }
            default -> {
            }
        }
        for (Marker marker : tools.selectedMarkers()) {
            if (marker instanceof Point p && tools.tool() == EditorTools.Tool.POINT && tools.selection().size() == 1) {
                Mesh ring = transientMesh(new Mesh(p.x(), p.y(), p.z()));
                ring.circle(p.x(), p.y() + 0.15, p.z(), 1.2, 1, 48);
                pass.lines(ring, cx, cy, cz, Theme.POINT, 0.45f, 1.5f * scale, false, false, false, 0, 0, 0);
            }
        }
    }

    private static float cameraYawFallback() {
        LocalPlayer player = Minecraft.getInstance().player;
        return player == null ? 0 : Mth.wrapDegrees(player.getYRot());
    }

    static Mesh build(Marker marker) {
        return switch (marker) {
            case Point p -> {
                Mesh mesh = new Mesh(p.x(), p.y(), p.z());
                point(mesh, p.x(), p.y(), p.z(), p.yaw());
                yield mesh;
            }
            case Group g -> groupMesh(g.blocks());
            case Region r -> {
                double[] c = MarkerPick.centre(r.shape());
                Mesh mesh = new Mesh(c[0], c[1], c[2]);
                region(mesh, r.shape());
                yield mesh;
            }
        };
    }

    static void point(Mesh mesh, double x, double y, double z, float yaw) {
        double h = 0.22;
        double cy = y + 0.25;
        double[][] ring = {{h, 0}, {0, h}, {-h, 0}, {0, -h}};
        for (int i = 0; i < 4; i++) {
            double[] a = ring[i];
            double[] b = ring[(i + 1) % 4];
            mesh.triangle(x + a[0], cy, z + a[1], x + b[0], cy, z + b[1], x, cy + h * 1.3, z);
            mesh.triangle(x + a[0], cy, z + a[1], x, cy - h * 1.3, z, x + b[0], cy, z + b[1]);
            mesh.line(x + a[0], cy, z + a[1], x, cy + h * 1.3, z);
            mesh.line(x + a[0], cy, z + a[1], x, cy - h * 1.3, z);
            mesh.line(x + a[0], cy, z + a[1], x + b[0], cy, z + b[1]);
        }
        double rad = Math.toRadians(yaw);
        double dx = -Math.sin(rad);
        double dz = Math.cos(rad);
        double tipX = x + dx * 0.9;
        double tipZ = z + dz * 0.9;
        mesh.line(x, y + 0.02, z, tipX, y + 0.02, tipZ);
        mesh.line(tipX, y + 0.02, tipZ, tipX - dx * 0.25 + dz * 0.15, y + 0.02, tipZ - dz * 0.25 - dx * 0.15);
        mesh.line(tipX, y + 0.02, tipZ, tipX - dx * 0.25 - dz * 0.15, y + 0.02, tipZ - dz * 0.25 + dx * 0.15);
        mesh.line(x, y + 0.02, z, x, cy - h * 1.3, z);
    }

    static Mesh groupMesh(long[] blocks) {
        GroupOutline.Shape shape = GroupOutline.of(blocks);
        Mesh mesh = new Mesh(shape.minX(), shape.minY(), shape.minZ());
        for (GroupOutline.Quad q : shape.faces()) {
            face(mesh, q);
        }
        for (GroupOutline.Edge e : shape.edges()) {
            double x = e.x();
            double y = e.y();
            double z = e.z();
            switch (e.axis()) {
                case 0 -> mesh.line(x, y, z, x + e.length(), y, z);
                case 1 -> mesh.line(x, y, z, x, y + e.length(), z);
                default -> mesh.line(x, y, z, x, y, z + e.length());
            }
        }
        return mesh;
    }

    private static void face(Mesh mesh, GroupOutline.Quad q) {
        double x0 = q.x() - INFLATE;
        double y0 = q.y() - INFLATE;
        double z0 = q.z() - INFLATE;
        double x1 = q.x() + 1 + INFLATE;
        double y1 = q.y() + 1 + INFLATE;
        double z1 = q.z() + 1 + INFLATE;
        switch (q.face()) {
            case WEST -> mesh.quad(x0, y0, z0, x0, y0, z1, x0, y1, z1, x0, y1, z0);
            case EAST -> mesh.quad(x1, y0, z0, x1, y1, z0, x1, y1, z1, x1, y0, z1);
            case DOWN -> mesh.quad(x0, y0, z0, x1, y0, z0, x1, y0, z1, x0, y0, z1);
            case UP -> mesh.quad(x0, y1, z0, x0, y1, z1, x1, y1, z1, x1, y1, z0);
            case NORTH -> mesh.quad(x0, y0, z0, x0, y1, z0, x1, y1, z0, x1, y0, z0);
            case SOUTH -> mesh.quad(x0, y0, z1, x1, y0, z1, x1, y1, z1, x0, y1, z1);
        }
    }

    static void region(Mesh mesh, Shape shape) {
        switch (shape) {
            case Shape.Box b -> mesh.box(b.minX() - INFLATE, b.minY() - INFLATE, b.minZ() - INFLATE, b.maxX() + 1 + INFLATE,
                    b.maxY() + 1 + INFLATE, b.maxZ() + 1 + INFLATE, true);
            case Shape.Polygon p -> {
                int[] xz = p.xz();
                int n = p.vertices();
                double y0 = p.minY();
                double y1 = p.maxY() + 1;
                for (int i = 0; i < n; i++) {
                    int j = (i + 1) % n;
                    double ax = xz[2 * i] + 0.5;
                    double az = xz[2 * i + 1] + 0.5;
                    double bx = xz[2 * j] + 0.5;
                    double bz = xz[2 * j + 1] + 0.5;
                    mesh.line(ax, y0, az, bx, y0, bz);
                    mesh.line(ax, y1, az, bx, y1, bz);
                    mesh.line(ax, y0, az, ax, y1, az);
                    mesh.quad(ax, y0, az, bx, y0, bz, bx, y1, bz, ax, y1, az);
                }
            }
        }
    }
}
