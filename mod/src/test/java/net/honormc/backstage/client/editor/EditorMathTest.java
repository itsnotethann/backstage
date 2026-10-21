package net.honormc.backstage.client.editor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.honormc.backstage.protocol.BlockPosPacking;
import net.honormc.backstage.protocol.model.Point;
import org.joml.Matrix4f;
import org.junit.jupiter.api.Test;

class EditorMathTest {
    private static Marquee.View view() {
        Matrix4f projection = new Matrix4f().setPerspective((float) Math.toRadians(70), 16 / 9f, 0.05f, 512, true);
        Matrix4f view = new Matrix4f().lookAt(0, 0, 0, 0, 0, -1, 0, 1, 0);
        return new Marquee.View(projection.mul(view), 0, 64, 0, 1600, 900);
    }

    private static Point point(String id, double x, double y, double z) {
        return new Point(id, x, y, z, 0, 0, List.of(), Map.of());
    }

    @Test
    void projectionPutsTheLookTargetInTheMiddle() {
        float[] centre = view().project(0, 64, -10);
        assertEquals(800, centre[0], 0.01);
        assertEquals(450, centre[1], 0.01);
        float[] right = view().project(3, 64, -10);
        assertTrue(right[0] > 800);
        float[] up = view().project(0, 66, -10);
        assertTrue(up[1] < 450);
        assertNull(view().project(0, 64, 10));
    }

    @Test
    void marqueeSelectsMarkersWhoseCentreIsInsideTheRect() {
        List<Point> points = List.of(point("ahead", 0, 64, -10), point("left", -6, 64, -10), point("behind", 0, 64, 10),
                point("far_right", 30, 64, -10));
        Marquee.Rect rect = Marquee.Rect.between(900, 300, 500, 600);
        List<Point> inside = Marquee.select(points, p -> new double[]{p.x(), p.y(), p.z()}, view(), rect);
        assertEquals(List.of("ahead"), inside.stream().map(Point::id).toList());
        Marquee.Rect wide = new Marquee.Rect(0, 0, 1600, 900);
        List<Point> all = Marquee.select(points, p -> new double[]{p.x(), p.y(), p.z()}, view(), wide);
        assertEquals(List.of("ahead", "left"), all.stream().map(Point::id).toList());
    }

    @Test
    void axisDragFollowsTheRayAndSnapsToBlocks() {
        double length = Math.sqrt(1.25);
        MarkerPick.Ray from = new MarkerPick.Ray(0, 5, 10, 0, -0.5 / length, -1 / length, 64);
        MarkerPick.Ray to = new MarkerPick.Ray(0, 5, 10, 2.6 / Math.sqrt(2.6 * 2.6 + 1.25), -0.5 / Math.sqrt(2.6 * 2.6 + 1.25),
                -1 / Math.sqrt(2.6 * 2.6 + 1.25), 64);
        double along = GizmoMath.alongAxis(from, 0, 0, 0, GizmoMath.Axis.X);
        assertEquals(0, along, 1e-9);
        double moved = GizmoMath.alongAxis(to, 0, 0, 0, GizmoMath.Axis.X);
        assertEquals(26, moved, 1e-6);
        double[] snapped = GizmoMath.dragDelta(from, to, 0, 0, 0, GizmoMath.Axis.X, false);
        assertArrayEquals(new double[]{26, 0, 0}, snapped, 1e-9);
        assertEquals(2, GizmoMath.snap(2.4, false));
        assertEquals(2.4, GizmoMath.snap(2.4, true));
        MarkerPick.Ray parallel = new MarkerPick.Ray(0, 0, 0, 1, 0, 0, 64);
        assertTrue(Double.isNaN(GizmoMath.alongAxis(parallel, 0, 0, 0, GizmoMath.Axis.X)));
        assertArrayEquals(new double[]{0, 0, 0}, GizmoMath.dragDelta(parallel, parallel, 0, 0, 0, GizmoMath.Axis.X, false));
    }

    @Test
    void hoveredAxisIsTheNearestSegmentWithinTheThreshold() {
        float[] origin = {100, 100};
        float[][] tips = {{190, 100}, {100, 10}, {40, 140}};
        assertEquals(GizmoMath.Axis.X, GizmoMath.hovered(150, 104, origin, tips, 8));
        assertEquals(GizmoMath.Axis.Y, GizmoMath.hovered(97, 40, origin, tips, 8));
        assertEquals(GizmoMath.Axis.Z, GizmoMath.hovered(60, 127, origin, tips, 8));
        assertNull(GizmoMath.hovered(160, 160, origin, tips, 8));
        assertEquals(4, GizmoMath.segmentDistance(150, 104, 100, 100, 190, 100), 1e-6);
    }

    @Test
    void yawFromAPlaneHitAndSnapping() {
        assertEquals(0, GizmoMath.yawTowards(0, 0, 0, 5), 1e-4);
        assertEquals(90, GizmoMath.yawTowards(0, 0, -5, 0), 1e-4);
        assertEquals(-90, GizmoMath.yawTowards(0, 0, 5, 0), 1e-4);
        assertEquals(45, GizmoMath.snapYaw(52, 45));
        assertEquals(180, GizmoMath.snapYaw(-170, 45));
        assertEquals(-135, GizmoMath.snapYaw(-140, 45));
        double[] hit = GizmoMath.planeHit(new MarkerPick.Ray(0, 10, 0, 0, -1, 0, 64), 4);
        assertArrayEquals(new double[]{0, 4, 0}, hit, 1e-9);
        assertNull(GizmoMath.planeHit(new MarkerPick.Ray(0, 10, 0, 0, 1, 0, 64), 4));
    }

    private static long pack(int x, int y, int z) {
        return BlockPosPacking.pack(x, y, z);
    }

    @Test
    void singleBlockOutlineIsACube() {
        GroupOutline.Shape shape = GroupOutline.of(new long[]{pack(3, 64, -2)});
        assertEquals(6, shape.faces().size());
        assertEquals(12, shape.edges().size());
        for (GroupOutline.Edge edge : shape.edges()) {
            assertEquals(1, edge.length());
        }
    }

    @Test
    void sharedFacesMergeIntoOneShape() {
        GroupOutline.Shape bar = GroupOutline.of(new long[]{pack(0, 0, 0), pack(1, 0, 0)});
        assertEquals(10, bar.faces().size());
        assertEquals(12, bar.edges().size());
        assertEquals(4, bar.edges().stream().filter(e -> e.axis() == 0 && e.length() == 2).count());

        long[] slab = new long[9];
        int n = 0;
        for (int x = 0; x < 3; x++) {
            for (int z = 0; z < 3; z++) {
                slab[n++] = pack(x, 5, z);
            }
        }
        GroupOutline.Shape flat = GroupOutline.of(slab);
        assertEquals(9 * 2 + 12, flat.faces().size());
        assertEquals(12, flat.edges().size());
        assertEquals(0, flat.minX());
        assertEquals(2, flat.maxZ());
    }

    @Test
    void concaveCornersKeepTheirCrease() {
        GroupOutline.Shape step = GroupOutline.of(new long[]{pack(0, 0, 0), pack(1, 0, 0), pack(1, 1, 0)});
        Set<String> edges = new HashSet<>();
        for (GroupOutline.Edge edge : step.edges()) {
            edges.add(edge.x() + "," + edge.y() + "," + edge.z() + "," + edge.axis() + "," + edge.length());
        }
        assertTrue(edges.contains("1,1,0,2,1"), "crease where the step meets the top of the first block");
        assertEquals(14, step.faces().size());
    }

    @Test
    void emptyGroupHasNoOutline() {
        GroupOutline.Shape shape = GroupOutline.of(new long[0]);
        assertTrue(shape.faces().isEmpty());
        assertTrue(shape.edges().isEmpty());
    }

    @Test
    void polygonClosesOnTheFirstVertexOnly() {
        PolygonBuilder builder = new PolygonBuilder();
        builder.add(0, 64, 0);
        builder.add(10, 64, 0);
        assertFalse(builder.closesAt(0, 0), "two vertices cannot close");
        builder.add(10, 64, 10);
        assertTrue(builder.closesAt(0, 0));
        assertFalse(builder.closesAt(10, 10));
        assertFalse(builder.closesAt(0, 1));
        builder.undoLast();
        assertEquals(2, builder.vertices());
        assertFalse(builder.closesAt(0, 0));
        assertArrayEquals(new int[]{0, 0, 10, 0}, builder.xz());
    }
}
