package net.honormc.backstage.minestom;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.honormc.backstage.protocol.model.KindDef;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EditorRulesTest {
    private static final UUID EDITOR = UUID.fromString("11111111-2222-3333-4444-555555555555");
    private static final UUID STRANGER = UUID.fromString("99999999-8888-7777-6666-555555555555");

    @TempDir
    Path dir;

    private static Point point(String id) {
        return new Point(id, 1, 2, 3, 0, 0, List.of(), Map.of());
    }

    @Test
    void defaultPermissionIsOpLevelFourOrListedEditor() {
        EditorConfig config = new EditorConfig(Set.of(EDITOR), "Build");
        assertTrue(EditorPermission.allows(4, STRANGER, config));
        assertTrue(EditorPermission.allows(5, STRANGER, config));
        assertFalse(EditorPermission.allows(3, STRANGER, config));
        assertFalse(EditorPermission.allows(0, STRANGER, config));
        assertTrue(EditorPermission.allows(0, EDITOR, config));
        assertFalse(EditorPermission.allows(0, EDITOR, new EditorConfig(Set.of(), "Build")));
    }

    @Test
    void configWritesDefaultWhenMissingAndReadsEditors() throws Exception {
        Path file = dir.resolve("config").resolve("editor").resolve("config.yml");
        EditorConfig defaults = EditorConfig.load(file);
        assertTrue(Files.exists(file));
        assertEquals(EditorConfig.DEFAULT_FILE, Files.readString(file));
        assertEquals("Build", defaults.serverName());
        assertEquals(Set.of(new UUID(0, 0)), defaults.editors());

        Files.writeString(file, "server-name: \"Desert build\"\neditors:\n  - \"" + EDITOR + "\"\n");
        EditorConfig edited = EditorConfig.load(file);
        assertEquals("Desert build", edited.serverName());
        assertEquals(Set.of(EDITOR), edited.editors());

        Files.writeString(file, "editors:\n  - not-a-uuid\n");
        assertThrows(IllegalArgumentException.class, () -> EditorConfig.load(file));
    }

    @Test
    void editsBumpRevisionAndNotify() {
        MapMarkers markers = new MapMarkers(MarkerSnapshot.empty("desert"), true);
        List<String> events = new ArrayList<>();
        markers.listener(new MapMarkers.Listener() {
            @Override
            public void changed(MapMarkers source, long revision, Marker marker) {
                events.add("changed " + revision + " " + marker.id());
            }

            @Override
            public void removed(MapMarkers source, long revision, MarkerType type, String id) {
                events.add("removed " + revision + " " + id);
            }
        });
        assertEquals(1, markers.upsert(point("a")));
        assertEquals(2, markers.upsert(point("b")));
        assertFalse(markers.rename(MarkerType.POINT, "a", "b"));
        assertFalse(markers.rename(MarkerType.POINT, "missing", "c"));
        assertTrue(markers.rename(MarkerType.POINT, "a", "c"));
        assertFalse(markers.remove(MarkerType.POINT, "a"));
        assertFalse(markers.remove(MarkerType.GROUP, "b"));
        assertTrue(markers.remove(MarkerType.POINT, "b"));
        assertEquals(4, markers.revision());
        assertEquals(List.of("changed 1 a", "changed 2 b", "removed 3 a", "changed 3 c", "removed 4 b"), events);
        assertEquals(List.of(point("c")), markers.points());
        assertThrows(IllegalArgumentException.class, () -> markers.upsert(point("Not valid")));
    }

    @Test
    void registrySnapshotsAndNotifies() {
        List<String> changes = new ArrayList<>();
        EditorRegistry registry = new EditorRegistry(() -> changes.add("changed"));
        assertEquals(List.of(MarkerType.POINT, MarkerType.GROUP, MarkerType.REGION), registry.snapshot().types());
        registry.kind(new KindDef("world_boss", "World boss", MarkerType.POINT, List.of()));
        registry.kind(new KindDef("world_boss", "Boss", MarkerType.POINT, List.of()));
        registry.types(Set.of(MarkerType.REGION, MarkerType.POINT));
        assertEquals(List.of(MarkerType.POINT, MarkerType.REGION), registry.snapshot().types());
        assertEquals(1, registry.snapshot().kinds().size());
        assertEquals("Boss", registry.snapshot().kinds().getFirst().label());
        assertEquals(3, changes.size());
        assertThrows(IllegalArgumentException.class,
                () -> registry.kind(new KindDef("Bad Name", "", MarkerType.POINT, List.of())));
    }
}
