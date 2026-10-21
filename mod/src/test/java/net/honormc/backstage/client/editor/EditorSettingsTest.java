package net.honormc.backstage.client.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import net.honormc.backstage.client.editor.EditorShortcuts.Action;
import net.honormc.backstage.protocol.BlockPosPacking;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.lwjgl.glfw.GLFW;

class EditorSettingsTest {
    private static final List<Integer> WASD = List.of(GLFW.GLFW_KEY_W, GLFW.GLFW_KEY_A, GLFW.GLFW_KEY_S, GLFW.GLFW_KEY_D,
            GLFW.GLFW_KEY_SPACE, GLFW.GLFW_KEY_LEFT_SHIFT, GLFW.GLFW_KEY_LEFT_CONTROL);

    @Test
    void layoutRoundTripsThroughJson(@TempDir Path dir) {
        EditorLayout layout = new EditorLayout();
        layout.dockWidth = 340;
        layout.outlinerShare = 0.6f;
        layout.historyOpen = true;
        layout.labels = false;
        layout.renderRadius = 200;
        layout.tool = "region";
        layout.brushRadius = 4;
        layout.brushMode = "erase";
        layout.sameBlock = true;
        layout.boxAsGroup = true;
        layout.flySpeed = 0.12f;
        Path file = dir.resolve("backstage").resolve("editor.json");
        layout.write(file);
        assertTrue(Files.isRegularFile(file));
        EditorLayout read = EditorLayout.read(file);
        assertEquals(layout, read);
        assertEquals(layout, EditorLayout.fromJson(layout.toJson()));
    }

    @Test
    void layoutFallsBackAndClamps(@TempDir Path dir) throws Exception {
        assertEquals(new EditorLayout(), EditorLayout.read(dir.resolve("missing.json")));
        Path broken = dir.resolve("broken.json");
        Files.writeString(broken, "{ not json");
        assertEquals(new EditorLayout(), EditorLayout.read(broken));
        EditorLayout odd = EditorLayout.fromJson("{\"dockWidth\": 9000, \"outlinerShare\": -1, \"renderRadius\": 2,"
                + " \"brushRadius\": 99, \"brushMode\": \"spray\", \"flySpeed\": 5, \"unknown\": true}");
        assertEquals(EditorLayout.MAX_DOCK, odd.dockWidth);
        assertEquals(0.2f, odd.outlinerShare);
        assertEquals(EditorLayout.MIN_RADIUS, odd.renderRadius);
        assertEquals(EditorLayout.MAX_BRUSH, odd.brushRadius);
        assertEquals("paint", odd.brushMode);
        assertEquals(EditorLayout.MAX_FLY, odd.flySpeed);
        assertTrue(odd.points);
    }

    @Test
    void shortcutsResolveWithModifiers() {
        EditorShortcuts shortcuts = EditorShortcuts.defaults();
        int f8 = GLFW.GLFW_KEY_F8;
        assertEquals(Action.TOOL_POINT, shortcuts.resolve(GLFW.GLFW_KEY_2, 0, f8, WASD));
        assertEquals(Action.UNDO, shortcuts.resolve(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL, f8, WASD));
        assertEquals(Action.REDO_ALT, shortcuts.resolve(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_SHIFT, f8, WASD));
        assertEquals(Action.REDO, shortcuts.resolve(GLFW.GLFW_KEY_Y, GLFW.GLFW_MOD_CONTROL, f8, WASD));
        assertEquals(Action.SEARCH, shortcuts.resolve(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL, f8, WASD));
        assertEquals(Action.FOCUS, shortcuts.resolve(GLFW.GLFW_KEY_F, 0, f8, WASD));
        assertEquals(Action.LEAVE, shortcuts.resolve(f8, 0, f8, WASD));
        assertEquals(Action.LEAVE, shortcuts.resolve(GLFW.GLFW_KEY_ESCAPE, 0, f8, WASD));
        assertNull(shortcuts.resolve(GLFW.GLFW_KEY_Z, 0, f8, WASD));
        assertNull(shortcuts.resolve(GLFW.GLFW_KEY_W, 0, f8, WASD));
        assertEquals(List.of("Ctrl", "Shift", "Z"), shortcuts.key(Action.REDO_ALT).caps());
    }

    @Test
    void conflictingBindingsArePredictableAndReported() {
        EditorShortcuts shortcuts = EditorShortcuts.defaults();
        assertTrue(shortcuts.conflicts(GLFW.GLFW_KEY_F8, "F8", WASD).isEmpty());

        int toggle = GLFW.GLFW_KEY_3;
        assertEquals(Action.LEAVE, shortcuts.resolve(GLFW.GLFW_KEY_3, 0, toggle, WASD));
        List<EditorShortcuts.Conflict> conflicts = shortcuts.conflicts(toggle, "Toggle editor", WASD);
        assertEquals(1, conflicts.size());
        assertEquals(Action.TOOL_GROUP, conflicts.getFirst().action());
        assertEquals("Toggle editor", conflicts.getFirst().with());
        assertEquals(Action.UNDO, shortcuts.resolve(GLFW.GLFW_KEY_Z, GLFW.GLFW_MOD_CONTROL, GLFW.GLFW_KEY_Z, WASD));

        List<Integer> remapped = List.of(GLFW.GLFW_KEY_F, GLFW.GLFW_KEY_R, GLFW.GLFW_KEY_SPACE);
        assertNull(shortcuts.resolve(GLFW.GLFW_KEY_F, 0, GLFW.GLFW_KEY_F8, remapped), "movement wins over a bare shortcut");
        assertEquals(Action.SEARCH, shortcuts.resolve(GLFW.GLFW_KEY_F, GLFW.GLFW_MOD_CONTROL, GLFW.GLFW_KEY_F8, remapped));
        List<Action> shadowed = shortcuts.conflicts(GLFW.GLFW_KEY_F8, "F8", remapped).stream()
                .map(EditorShortcuts.Conflict::action).toList();
        assertEquals(List.of(Action.FOCUS, Action.SNAP_YAW), shadowed);
    }

    private static Point point(String id, List<String> tags) {
        return new Point(id, 0, 64, 0, 0, 0, tags, Map.of());
    }

    @Test
    void multiSelectTagEditsOnlyTouchWhatChanges() {
        Point a = point("a", List.of("boss", "spawn"));
        Point b = point("b", List.of("spawn"));
        Group g = new Group("well", new long[]{BlockPosPacking.pack(1, 2, 3)}, List.of("spawn", "boss"), Map.of());
        List<Marker> markers = List.of(a, b, g);
        assertEquals(List.of("spawn"), MultiEdits.sharedTags(markers));
        assertEquals(List.of(), MultiEdits.sharedTags(List.of()));

        List<MarkerHistory.Change> add = MultiEdits.addTag(markers, "boss");
        assertEquals(1, add.size());
        assertEquals("b", add.getFirst().id());
        assertEquals(List.of("spawn", "boss"), add.getFirst().after().tags());

        List<MarkerHistory.Change> remove = MultiEdits.removeTag(markers, "spawn");
        assertEquals(3, remove.size());
        for (MarkerHistory.Change change : remove) {
            assertTrue(!change.after().tags().contains("spawn"));
            assertEquals(change.before().id(), change.after().id());
        }
        assertTrue(MultiEdits.addTag(markers, "spawn").isEmpty());
    }

    @Test
    void duplicatesGetTheNextFreeIdAndMovesTranslate() {
        Point a = point("spawn_2", List.of("boss"));
        Region r = new Region("arena", new Shape.Box(0, 60, 0, 4, 70, 4), List.of(), Map.of());
        List<Marker> copies = MultiEdits.duplicate(List.of(a, r), List.of("spawn_1", "spawn_2", "spawn_3", "arena"));
        assertEquals("spawn_4", copies.get(0).id());
        assertEquals("arena_1", copies.get(1).id());
        assertEquals(List.of("boss"), copies.get(0).tags());

        Point moved = (Point) MultiEdits.translated(a, 1.5, 0, -2);
        assertEquals(1.5, moved.x());
        assertEquals(-2, moved.z());
        Region shifted = (Region) MultiEdits.translated(r, 2.2, -1, 0);
        assertEquals(new Shape.Box(2, 59, 0, 6, 69, 4), shifted.shape());
        Region poly = new Region("p", new Shape.Polygon(new int[]{0, 0, 4, 0, 4, 4}, 1, 3), List.of(), Map.of());
        Shape.Polygon p = (Shape.Polygon) ((Region) MultiEdits.translated(poly, 1, 1, 1)).shape();
        assertEquals(List.of(1, 1, 5, 1, 5, 5), Arrays.stream(p.xz()).boxed().toList());
        assertEquals(2, p.minY());
        Group g = new Group("g", new long[]{BlockPosPacking.pack(1, 2, 3)}, List.of(), Map.of());
        Group gm = (Group) MultiEdits.translated(g, 1, 1, 1);
        assertEquals(BlockPosPacking.pack(2, 3, 4), gm.blocks()[0]);
    }

    @Test
    void fuzzyPrefersPrefixesAndBoundaries() {
        assertTrue(Fuzzy.score("boss", "boss_spawn") > Fuzzy.score("boss", "the_boss_spawn"));
        assertTrue(Fuzzy.score("bsp", "boss_spawn") != Fuzzy.NO_MATCH);
        assertEquals(Fuzzy.NO_MATCH, Fuzzy.score("xyz", "boss_spawn"));
        assertEquals(0, Fuzzy.score("", "anything"));
        assertNotEquals(Fuzzy.NO_MATCH, Fuzzy.score("undo", "Edit Undo"));
    }
}
