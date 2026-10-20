package net.honormc.backstage.client.editor;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.honormc.backstage.protocol.BlockPosPacking;
import net.honormc.backstage.protocol.EditorProtocol;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Prop;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;
import org.junit.jupiter.api.Test;

class MarkerToolsTest {
    private static Point point(String id, String... tags) {
        return new Point(id, 1, 2, 3, 0, 0, List.of(tags), Map.of());
    }

    private static long pos(int x, int y, int z) {
        return BlockPosPacking.pack(x, y, z);
    }

    @Test
    void idsTakeTheFirstFreeNumber() {
        assertEquals("point_1", MarkerIds.next("point", Set.of()));
        assertEquals("point_3", MarkerIds.next("point", Set.of("point_1", "point_2", "point_4")));
        assertEquals("region_2", MarkerIds.next("region", Set.of("region_1", "point_2")));
    }

    @Test
    void groupDraftAddsRemovesAndCaps() {
        GroupDraft draft = new GroupDraft("flag", new long[]{pos(0, 0, 0)});
        assertEquals(GroupDraft.Outcome.PRESENT, draft.add(pos(0, 0, 0)));
        assertEquals(GroupDraft.Outcome.ADDED, draft.add(pos(1, 0, 0)));
        assertTrue(draft.remove(pos(0, 0, 0)));
        assertFalse(draft.remove(pos(0, 0, 0)));
        assertArrayEquals(new long[]{pos(1, 0, 0)}, draft.blocks());

        long[] many = new long[EditorProtocol.MAX_GROUP_BLOCKS + 10];
        for (int i = 0; i < many.length; i++) {
            many[i] = pos(i, 5, 0);
        }
        assertEquals(EditorProtocol.MAX_GROUP_BLOCKS - 1, draft.addAll(many));
        assertTrue(draft.full());
        assertEquals(EditorProtocol.MAX_GROUP_BLOCKS, draft.size());
        assertEquals(GroupDraft.Outcome.FULL, draft.add(pos(0, 9, 9)));
        assertEquals(GroupDraft.Outcome.PRESENT, draft.add(pos(1, 0, 0)));
        assertTrue(draft.remove(pos(1, 0, 0)));
        assertEquals(GroupDraft.Outcome.ADDED, draft.add(pos(0, 9, 9)));

        GroupDraft loaded = new GroupDraft("big", many);
        assertEquals(EditorProtocol.MAX_GROUP_BLOCKS, loaded.size());
    }

    @Test
    void boxBoundsAreNormalisedAndInclusive() {
        Shape.Box box = BlockSelection.bounds(5, 10, -2, 3, 8, -4);
        assertEquals(new Shape.Box(3, 8, -4, 5, 10, -2), box);
        BlockSelection.Result all = BlockSelection.box(box, p -> true, 1000);
        assertEquals(27, all.blocks().length);
        assertFalse(all.capped());
        BlockSelection.Result floor = BlockSelection.box(box, p -> BlockPosPacking.y(p) == 8, 1000);
        assertEquals(9, floor.blocks().length);
        BlockSelection.Result capped = BlockSelection.box(box, p -> true, 10);
        assertEquals(10, capped.blocks().length);
        assertTrue(capped.capped());
    }

    @Test
    void floodFillStaysConnectedInsideItsRadiusAndCap() {
        Set<Long> stone = new HashSet<>();
        for (int x = 0; x < 10; x++) {
            stone.add(pos(x, 0, 0));
        }
        stone.add(pos(20, 0, 0));
        BlockSelection.Result line = BlockSelection.flood(pos(0, 0, 0), stone::contains, 100, 64);
        assertEquals(10, line.blocks().length);
        assertFalse(line.capped());

        BlockSelection.Result near = BlockSelection.flood(pos(0, 0, 0), stone::contains, 100, 3);
        assertEquals(4, near.blocks().length);
        for (long block : near.blocks()) {
            assertTrue(BlockPosPacking.x(block) <= 3);
        }

        BlockSelection.Result capped = BlockSelection.flood(pos(0, 0, 0), stone::contains, 5, 64);
        assertEquals(5, capped.blocks().length);
        assertTrue(capped.capped());

        assertEquals(0, BlockSelection.flood(pos(50, 0, 0), stone::contains, 100, 64).blocks().length);

        BlockSelection.Result cube = BlockSelection.flood(pos(0, 0, 0), p -> true, 100_000, 2);
        assertEquals(125, cube.blocks().length);
    }

    @Test
    void polygonBuilderValidates() {
        PolygonBuilder few = new PolygonBuilder();
        few.add(0, 64, 0);
        few.add(4, 64, 0);
        assertThrows(IllegalArgumentException.class, () -> few.build(null, null));

        PolygonBuilder repeat = new PolygonBuilder();
        assertNull(repeat.add(0, 64, 0));
        assertNotNull(repeat.add(0, 70, 0));

        PolygonBuilder line = new PolygonBuilder();
        line.add(0, 64, 0);
        line.add(1, 64, 0);
        line.add(2, 64, 0);
        assertThrows(IllegalArgumentException.class, () -> line.build(null, null));

        PolygonBuilder bowtie = new PolygonBuilder();
        bowtie.add(0, 64, 0);
        bowtie.add(4, 64, 4);
        bowtie.add(4, 64, 0);
        bowtie.add(0, 64, 4);
        assertThrows(IllegalArgumentException.class, () -> bowtie.build(null, null));

        PolygonBuilder fold = new PolygonBuilder();
        fold.add(0, 64, 0);
        fold.add(4, 64, 0);
        fold.add(2, 64, 0);
        fold.add(2, 64, 3);
        assertThrows(IllegalArgumentException.class, () -> fold.build(null, null));

        PolygonBuilder closed = new PolygonBuilder();
        closed.add(0, 64, 0);
        closed.add(4, 64, 0);
        closed.add(4, 64, 4);
        closed.add(0, 64, 0);
        assertThrows(IllegalArgumentException.class, () -> closed.build(null, null));

        PolygonBuilder pentagon = new PolygonBuilder();
        pentagon.add(0, 60, 0);
        pentagon.add(6, 62, 0);
        pentagon.add(8, 64, 5);
        pentagon.add(3, 70, 9);
        pentagon.add(-2, 61, 5);
        Shape.Polygon built = pentagon.build(null, null);
        assertEquals(5, built.vertices());
        assertEquals(60, built.minY());
        assertEquals(70, built.maxY());
        assertArrayEquals(new int[]{0, 0, 6, 0, 8, 5, 3, 9, -2, 5}, built.xz());
        Shape.Polygon tall = pentagon.build(40, 90);
        assertEquals(40, tall.minY());
        assertEquals(90, tall.maxY());
        assertThrows(IllegalArgumentException.class, () -> pentagon.build(90, 40));

        PolygonBuilder full = new PolygonBuilder();
        for (int i = 0; i < EditorProtocol.MAX_POLYGON_VERTICES; i++) {
            assertNull(full.add(i, 0, i * i));
        }
        assertNotNull(full.add(-1, 0, -1));
    }

    @Test
    void historyStepsThroughInverseOps() {
        MarkerHistory history = new MarkerHistory();
        Point a = point("a");
        Point tagged = point("a", "boss");
        history.record(new MarkerHistory.Change(null, a));
        history.record(new MarkerHistory.Change(a, tagged));
        history.record(new MarkerHistory.Rename(MarkerType.POINT, "a", "b"));
        history.record(new MarkerHistory.Change(point("b", "boss"), null));

        MarkerHistory.Op undoDelete = history.undo().orElseThrow();
        assertEquals(new MarkerHistory.Change(null, point("b", "boss")), undoDelete);
        assertEquals("upsert", kind(undoDelete));
        MarkerHistory.Op undoRename = history.undo().orElseThrow();
        assertEquals(new MarkerHistory.Rename(MarkerType.POINT, "b", "a"), undoRename);
        MarkerHistory.Op undoTag = history.undo().orElseThrow();
        assertEquals(new MarkerHistory.Change(tagged, a), undoTag);
        MarkerHistory.Op undoCreate = history.undo().orElseThrow();
        assertEquals("remove", kind(undoCreate));
        assertTrue(history.undo().isEmpty());

        assertEquals(new MarkerHistory.Change(null, a), history.redo().orElseThrow());
        assertEquals(new MarkerHistory.Change(a, tagged), history.redo().orElseThrow());
        assertEquals(2, history.undoSize());
        assertEquals(2, history.redoSize());
        history.record(new MarkerHistory.Change(tagged, point("a")));
        assertEquals(0, history.redoSize());
    }

    @Test
    void historyIsCappedAndRevertsRejectedEdits() {
        MarkerHistory history = new MarkerHistory();
        for (int i = 0; i < MarkerHistory.LIMIT + 5; i++) {
            history.record(new MarkerHistory.Change(null, point("p" + i)));
        }
        assertEquals(MarkerHistory.LIMIT, history.undoSize());

        history.undo();
        history.undo();
        assertEquals(2, history.redoSize());
        history.record(new MarkerHistory.Change(null, point("x")));
        assertEquals(0, history.redoSize());
        assertFalse(history.rejected(MarkerType.POINT, "other"));
        assertTrue(history.rejected(MarkerType.POINT, "x"));
        assertEquals(2, history.redoSize());
        assertEquals(MarkerHistory.LIMIT - 2, history.undoSize());

        MarkerHistory.Op redone = history.redo().orElseThrow();
        assertTrue(history.rejected(redone.type(), redone.id()));
        assertEquals(2, history.redoSize());

        MarkerHistory.Op undone = history.undo().orElseThrow();
        int undoSize = history.undoSize();
        assertTrue(history.rejected(undone.type(), undone.id()));
        assertEquals(undoSize + 1, history.undoSize());
        assertFalse(history.rejected(undone.type(), undone.id()));

        assertThrows(IllegalArgumentException.class, () -> new MarkerHistory.Change(point("a"), point("b")));
        assertThrows(IllegalArgumentException.class, () -> new MarkerHistory.Change(null, null));
    }

    private static String kind(MarkerHistory.Op op) {
        return switch (op.packet("dev")) {
            case net.honormc.backstage.protocol.ServerboundPacket.UpsertMarker u -> "upsert";
            case net.honormc.backstage.protocol.ServerboundPacket.RemoveMarker r -> "remove";
            case net.honormc.backstage.protocol.ServerboundPacket.RenameMarker r -> "rename";
            default -> "other";
        };
    }

    @Test
    void propValuesParsePerType() {
        assertEquals(new Prop.Str("hello world"), PropParser.parse("str", "hello world"));
        assertEquals(new Prop.Num(2.5), PropParser.parse("num", "2.5"));
        assertEquals(new Prop.Num(-40), PropParser.parse("num", "-40"));
        assertThrows(IllegalArgumentException.class, () -> PropParser.parse("num", "lots"));
        assertThrows(IllegalArgumentException.class, () -> PropParser.parse("num", "NaN"));
        assertThrows(IllegalArgumentException.class, () -> PropParser.parse("num", "Infinity"));
        assertEquals(new Prop.Bool(true), PropParser.parse("bool", "true"));
        assertEquals(new Prop.Bool(false), PropParser.parse("bool", "FALSE"));
        assertThrows(IllegalArgumentException.class, () -> PropParser.parse("bool", "yes"));
        assertEquals(new Prop.BlockProp("minecraft:oak_stairs[facing=north,half=top]"),
                PropParser.parse("block", "oak_stairs[facing=north,half=top]"));
        assertEquals(new Prop.BlockProp("minecraft:stone"), PropParser.parse("block", "minecraft:stone"));
        assertThrows(IllegalArgumentException.class, () -> PropParser.parse("block", "Oak Stairs"));
        assertThrows(IllegalArgumentException.class, () -> PropParser.parse("block", "stone[facing]"));
        assertEquals(new Prop.ItemProp("{id:\"minecraft:diamond\",count:1}"), PropParser.parse("item", "diamond"));
        assertEquals(new Prop.ItemProp("{id:\"minecraft:stick\",count:3}"),
                PropParser.parse("item", "{id:\"minecraft:stick\",count:3}"));
        assertThrows(IllegalArgumentException.class, () -> PropParser.parse("item", "{count:3"));
        assertThrows(IllegalArgumentException.class, () -> PropParser.parse("list", "1"));
        assertEquals("num 3", PropParser.describe(new Prop.Num(3)));
        assertEquals("num 2.5", PropParser.describe(new Prop.Num(2.5)));
    }

    @Test
    void outlinerFiltersByTypeAndTag() {
        Region arena = new Region("arena", new Shape.Box(0, 0, 0, 1, 1, 1), List.of("pvp"), Map.of());
        Group flag = new Group("flag", new long[]{pos(0, 0, 0)}, List.of("boss"), Map.of());
        List<Marker> all = List.of(point("b", "boss", "chest"), point("a", "chest"), arena, flag);
        assertEquals(List.of(point("a", "chest"), point("b", "boss", "chest"), flag, arena), Outliner.filter(all, null, null));
        assertEquals(List.of(point("a", "chest"), point("b", "boss", "chest")), Outliner.filter(all, MarkerType.POINT, null));
        assertEquals(List.of(point("b", "boss", "chest"), flag), Outliner.filter(all, null, "boss"));
        assertEquals(List.of(), Outliner.filter(all, MarkerType.REGION, "boss"));
        assertEquals(MarkerType.GROUP, Outliner.type("groups"));
        assertNull(Outliner.type("all"));
    }

    @Test
    void checksMatchTheProtocolLimits() {
        assertNull(MarkerCheck.marker(point("ok", "boss")));
        assertNotNull(MarkerCheck.id("Not Valid"));
        assertNotNull(MarkerCheck.tag("Has Space"));
        assertNotNull(MarkerCheck.marker(new Point("p", Double.NaN, 0, 0, 0, 0, List.of(), Map.of())));
        assertNotNull(MarkerCheck.marker(new Region("r", new Shape.Box(2, 0, 0, 1, 0, 0), List.of(), Map.of())));
        assertNotNull(MarkerCheck.marker(new Group("g", new long[EditorProtocol.MAX_GROUP_BLOCKS + 1], List.of(), Map.of())));
        String[] tags = new String[EditorProtocol.MAX_TAGS + 1];
        for (int i = 0; i < tags.length; i++) {
            tags[i] = "t" + i;
        }
        assertNotNull(MarkerCheck.marker(point("p", tags)));
        Prop deep = new Prop.Num(1);
        for (int i = 0; i <= EditorProtocol.MAX_PROP_DEPTH; i++) {
            deep = new Prop.ListProp(List.of(deep));
        }
        assertNotNull(MarkerCheck.marker(new Point("p", 0, 0, 0, 0, 0, List.of(), Map.of("deep", deep))));

        long[] blocks = new long[EditorProtocol.MAX_GROUP_BLOCKS];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = pos(i, 0, 0);
        }
        Map<String, Prop> props = new java.util.LinkedHashMap<>();
        for (int i = 0; i < 8; i++) {
            props.put("k" + i, new Prop.Str("x".repeat(1000)));
        }
        Group huge = new Group("huge", blocks, List.of(), props);
        assertNull(MarkerCheck.marker(huge));
        assertNotNull(MarkerCheck.packet(new net.honormc.backstage.protocol.ServerboundPacket.UpsertMarker("dev", huge)));
        assertNull(MarkerCheck.packet(new net.honormc.backstage.protocol.ServerboundPacket.UpsertMarker("dev",
                new Group("full", blocks, List.of(), Map.of()))));
    }

    @Test
    void pickPrefersPointsThenGroupsThenRegions() {
        MarkerPick.Ray ray = new MarkerPick.Ray(0.5, 65.5, 0.5, 0, 0, 1, 32);
        Point near = new Point("near", 0.5, 65.4, 5.5, 0, 0, List.of(), Map.of());
        Point far = new Point("far", 0.5, 65.4, 9.5, 0, 0, List.of(), Map.of());
        Point aside = new Point("aside", 4.5, 65.4, 5.5, 0, 0, List.of(), Map.of());
        Group group = new Group("wall", new long[]{pos(0, 65, 12)}, List.of(), Map.of());
        Region around = new Region("around", new Shape.Box(-5, 60, -5, 5, 70, 5), List.of(), Map.of());
        Region ahead = new Region("ahead", new Shape.Polygon(new int[]{-2, 20, 2, 20, 0, 24}, 60, 70), List.of(), Map.of());

        assertSame(near, MarkerPick.pick(ray, pos(0, 65, 12), 0.5, 64, 0.5, List.of(far, near, aside), List.of(group),
                List.of(around)));
        assertSame(group, MarkerPick.pick(ray, pos(0, 65, 12), 0.5, 64, 0.5, List.of(aside), List.of(group),
                List.of(around)));
        assertSame(around, MarkerPick.pick(ray, null, 0.5, 64, 0.5, List.of(), List.of(group), List.of(ahead, around)));
        MarkerPick.Ray past = new MarkerPick.Ray(0.5, 65.5, 10.5, 0, 0, 1, 32);
        assertSame(ahead, MarkerPick.pick(past, null, 50, 64, 50, List.of(), List.of(), List.of(ahead, around)));
        assertNull(MarkerPick.pick(new MarkerPick.Ray(0.5, 65.5, 0.5, 0, 0, -1, 32), null, 50, 64, 50, List.of(),
                List.of(), List.of(ahead)));
        assertTrue(MarkerPick.contains(ahead.shape(), 0.5, 65, 21.5));
        assertFalse(MarkerPick.contains(ahead.shape(), 0.5, 75, 21.5));
        assertEquals(0, MarkerPick.distanceSquared(around.shape(), 0, 65, 0));
        assertEquals(25, MarkerPick.distanceSquared(around.shape(), 11, 65, 0));
    }
}
