package net.honormc.backstage.minestom;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;
import org.junit.jupiter.api.Test;

class EditRejectionTest {
    private static final EnumSet<MarkerType> ALL = EnumSet.allOf(MarkerType.class);

    private static MapMarkers markers(String map, boolean writable) {
        return new MapMarkers(MarkerSnapshot.empty(map), writable);
    }

    private static Region region(Shape shape) {
        return new Region("arena", shape, List.of(), Map.of());
    }

    @Test
    void acceptsEditsOnTheCurrentWritableMap() {
        assertNull(EditorServer.rejection(markers("desert", true), ALL, "desert", MarkerType.GROUP));
    }

    @Test
    void rejectsUnboundOtherOrReadOnlyMaps() {
        assertEquals("No editable map here.", EditorServer.rejection(null, ALL, "desert", MarkerType.POINT));
        assertEquals("You are on map dev, not desert.",
                EditorServer.rejection(markers("dev", true), ALL, "desert", MarkerType.POINT));
        assertTrue(EditorServer.rejection(markers("desert", false), ALL, "desert", MarkerType.POINT)
                .contains("read-only"));
    }

    @Test
    void rejectsTypesTheServerNarrowedAway() {
        String reason = EditorServer.rejection(markers("desert", true), EnumSet.of(MarkerType.POINT), "desert",
                MarkerType.REGION);
        assertEquals("This server does not use region markers.", reason);
    }

    @Test
    void upsertRejectsInvertedShapesAndNonFinitePoints() {
        MapMarkers markers = markers("desert", true);
        assertThrows(IllegalArgumentException.class, () -> markers.upsert(region(new Shape.Box(5, 0, 0, 4, 1, 1))));
        assertThrows(IllegalArgumentException.class, () -> markers.upsert(region(new Shape.Box(0, 9, 0, 1, 8, 1))));
        assertThrows(IllegalArgumentException.class, () -> markers.upsert(region(new Shape.Box(0, 0, 3, 1, 1, 2))));
        assertThrows(IllegalArgumentException.class,
                () -> markers.upsert(region(new Shape.Polygon(new int[]{0, 0, 4, 0, 0, 4}, 10, 9))));
        assertThrows(IllegalArgumentException.class,
                () -> markers.upsert(new Point("p", Double.NaN, 0, 0, 0, 0, List.of(), Map.of())));
        assertThrows(IllegalArgumentException.class,
                () -> markers.upsert(new Point("p", 0, 0, 0, Float.POSITIVE_INFINITY, 0, List.of(), Map.of())));
        assertEquals(0, markers.revision());
        assertDoesNotThrow(() -> markers.upsert(region(new Shape.Box(0, 0, 0, 0, 0, 0))));
        assertDoesNotThrow(() -> markers.upsert(new Group("g", new long[0], List.of(), Map.of())));
        assertEquals(2, markers.revision());
    }
}
