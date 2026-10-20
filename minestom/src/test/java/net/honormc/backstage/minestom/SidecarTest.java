package net.honormc.backstage.minestom;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.honormc.backstage.protocol.BlockPosPacking;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Prop;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class SidecarTest {
    @TempDir
    Path dir;

    private static Map<String, Prop> allProps() {
        Map<String, Prop> props = new LinkedHashMap<>();
        props.put("name", new Prop.Str("Boss é"));
        props.put("hp", new Prop.Num(1250.5));
        props.put("whole", new Prop.Num(3));
        props.put("on", new Prop.Bool(true));
        props.put("off", new Prop.Bool(false));
        props.put("floor", new Prop.BlockProp("minecraft:oak_stairs[facing=north]"));
        props.put("drop", new Prop.ItemProp("{id:\"minecraft:diamond\",count:2}"));
        props.put("mixed", new Prop.ListProp(List.of(
                new Prop.Str("a"),
                new Prop.Num(1),
                new Prop.Bool(true),
                new Prop.BlockProp("minecraft:stone"),
                new Prop.ItemProp("{id:\"minecraft:stick\"}"),
                new Prop.ListProp(List.of(new Prop.Num(2), new Prop.ListProp(List.of()))))));
        props.put("empty", new Prop.ListProp(List.of()));
        return props;
    }

    private static List<Marker> allMarkers() {
        return List.of(
                new Point("boss_spawn", 10.5, -64, -3.25, 90f, -15.5f, List.of("boss", "spawn"), allProps()),
                new Point("bare", 0, 0, 0, 0, 0, List.of(), Map.of()),
                new Group("flag", new long[]{BlockPosPacking.pack(1, 2, 3), BlockPosPacking.pack(-30_000_000, -64, 5)},
                        List.of("flag"), allProps()),
                new Group("empty_group", new long[0], List.of(), Map.of()),
                new Region("arena", new Shape.Box(-10, 40, -10, 10, 80, 10), List.of("arena"), allProps()),
                new Region("pit", new Shape.Polygon(new int[]{0, 0, 5, 0, 5, 5, -2, 7}, 40, 80), List.of(), allProps()));
    }

    private MarkerFile file(String name) {
        return new MarkerFile(dir.resolve(name + ".markers.dat"));
    }

    private static MapMarkers filled(MarkerFile file, String map, List<Marker> markers) throws IOException {
        MapMarkers loaded = file.load(map);
        for (Marker marker : markers) {
            loaded.upsert(marker);
        }
        return loaded;
    }

    private static byte[] uncompressed(MarkerSnapshot snapshot) throws IOException {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        BinaryTagIO.writer().write(MarkerSidecar.write(snapshot), out, BinaryTagIO.Compression.NONE);
        return out.toByteArray();
    }

    private void writeRoot(Path path, CompoundBinaryTag root) throws IOException {
        BinaryTagIO.writer().write(root, path, BinaryTagIO.Compression.GZIP);
    }

    @Test
    void roundTripsEveryMarkerAndPropType() throws IOException {
        MarkerFile file = file("desert");
        MapMarkers markers = filled(file, "desert", allMarkers());
        assertTrue(file.save(markers));

        MapMarkers reloaded = file("desert").load("desert");
        assertTrue(reloaded.writable());
        assertEquals(markers.snapshot(), reloaded.snapshot());
        assertEquals(allMarkers().size(), reloaded.revision());
        assertEquals(markers.snapshot(), MarkerSidecar.readV1(MarkerSidecar.write(markers.snapshot()), "desert"));
    }

    @Test
    void layoutMatchesSpec() throws IOException {
        MarkerFile file = file("desert");
        file.save(filled(file, "desert", allMarkers()));
        CompoundBinaryTag root = BinaryTagIO.unlimitedReader().read(file.path(), BinaryTagIO.Compression.GZIP);
        assertEquals("backstage-markers", root.getString("format"));
        assertEquals(1, root.getInt("version"));
        assertEquals("desert", root.getString("map"));
        assertEquals(6L, root.getLong("revision"));
        CompoundBinaryTag point = root.getList("points").getCompound(0);
        assertEquals("bare", point.getString("id"));
        assertEquals(3, point.getList("pos").size());
        CompoundBinaryTag box = root.getList("regions").getCompound(0);
        assertEquals("box", box.getString("shape"));
        assertArrayEquals(new int[]{-10, 40, -10}, box.getIntArray("min"));
        assertArrayEquals(new int[]{10, 80, 10}, box.getIntArray("max"));
        CompoundBinaryTag polygon = root.getList("regions").getCompound(1);
        assertEquals("polygon", polygon.getString("shape"));
        assertEquals(40, polygon.getInt("minY"));
        CompoundBinaryTag props = box.getCompound("props");
        assertEquals("minecraft:oak_stairs[facing=north]", props.getCompound("floor").getString("block"));
        assertEquals(3.0, props.getDouble("whole"));
        assertEquals(1, props.getByte("on"));
        assertEquals("a", props.getList("mixed").getCompound(0).getString("v"));
        assertEquals(BlockPosPacking.pack(1, 2, 3), root.getList("groups").getCompound(1).getLongArray("blocks")[0]);
    }

    @Test
    void identicalDataWritesIdenticalBytes() throws IOException {
        List<Marker> reversed = new ArrayList<>(allMarkers());
        Collections.reverse(reversed);
        Map<String, Prop> shuffled = new LinkedHashMap<>();
        List<String> keys = new ArrayList<>(allProps().keySet());
        Collections.reverse(keys);
        keys.forEach(key -> shuffled.put(key, allProps().get(key)));
        reversed.set(reversed.size() - 1, new Point("boss_spawn", 10.5, -64, -3.25, 90f, -15.5f,
                List.of("boss", "spawn"), shuffled));

        MarkerFile a = file("a");
        MarkerFile b = file("b");
        MapMarkers first = filled(a, "desert", allMarkers());
        MapMarkers second = filled(b, "desert", reversed);
        a.save(first);
        b.save(second);
        assertArrayEquals(uncompressed(first.snapshot()), uncompressed(second.snapshot()));
        assertArrayEquals(Files.readAllBytes(a.path()), Files.readAllBytes(b.path()));
    }

    @Test
    void newerVersionBindsReadOnlyAndIsNeverOverwritten() throws IOException {
        Path path = dir.resolve("future.markers.dat");
        writeRoot(path, CompoundBinaryTag.builder()
                .putString("format", MarkerSidecar.FORMAT)
                .putInt("version", MarkerSidecar.VERSION + 1)
                .putString("map", "future")
                .putLong("revision", 99)
                .build());
        byte[] before = Files.readAllBytes(path);

        MarkerFile file = new MarkerFile(path);
        MapMarkers markers = file.load("future");
        assertFalse(markers.writable());
        assertEquals(0, markers.snapshot().size());
        assertThrows(IllegalStateException.class, () -> markers.upsert(new Point("a", 0, 0, 0, 0, 0, List.of(), Map.of())));
        assertThrows(IllegalStateException.class, () -> markers.remove(MarkerType.POINT, "a"));
        assertFalse(file.save(markers));

        MarkerSaver saver = new MarkerSaver();
        saver.flush(markers, file);
        saver.shutdown();
        assertArrayEquals(before, Files.readAllBytes(path));
        assertFalse(Files.exists(dir.resolve("future.markers.dat.bak")));
    }

    @Test
    void wrongFormatRefusesToBind() throws IOException {
        Path path = dir.resolve("other.markers.dat");
        writeRoot(path, CompoundBinaryTag.builder().putString("format", "something-else").putInt("version", 1).build());
        assertThrows(IOException.class, () -> new MarkerFile(path).load("other"));
        writeRoot(path, CompoundBinaryTag.builder().putInt("version", 1).build());
        assertThrows(IOException.class, () -> new MarkerFile(path).load("other"));
    }

    @Test
    void missingFileIsEmptyAndNotWrittenUntilFirstEdit() throws IOException {
        MarkerFile file = file("fresh");
        MapMarkers markers = file.load("fresh");
        assertTrue(markers.writable());
        assertEquals(0, markers.revision());
        assertFalse(file.save(markers));
        assertFalse(Files.exists(file.path()));
        markers.upsert(new Point("a", 0, 0, 0, 0, 0, List.of(), Map.of()));
        assertTrue(file.save(markers));
        assertTrue(Files.exists(file.path()));
        assertFalse(file.save(markers));
    }

    @Test
    void atomicWriteLeavesNoTmp() throws IOException {
        MarkerFile file = file("desert");
        MapMarkers markers = filled(file, "desert", allMarkers());
        file.save(markers);
        markers.remove(MarkerType.POINT, "bare");
        file.save(markers);
        assertTrue(Files.exists(file.path()));
        assertFalse(Files.exists(dir.resolve("desert.markers.dat.tmp")));
        try (var listing = Files.list(dir)) {
            assertTrue(listing.noneMatch(path -> path.getFileName().toString().endsWith(".tmp")));
        }
    }

    @Test
    void backupIsMadeOnceBeforeTheFirstSave() throws IOException {
        Path bak = dir.resolve("desert.markers.dat.bak");
        MarkerFile first = file("desert");
        first.save(filled(first, "desert", allMarkers()));
        assertFalse(Files.exists(bak));
        byte[] original = Files.readAllBytes(first.path());

        MarkerFile restarted = file("desert");
        MapMarkers markers = restarted.load("desert");
        markers.remove(MarkerType.POINT, "bare");
        restarted.save(markers);
        assertArrayEquals(original, Files.readAllBytes(bak));

        markers.remove(MarkerType.GROUP, "flag");
        restarted.save(markers);
        assertArrayEquals(original, Files.readAllBytes(bak));
    }

    @Test
    void sidecarSitsBesideThePolar() {
        assertEquals(dir.resolve("desert.markers.dat"), MarkerSidecar.pathFor(dir.resolve("desert.polar")));
        assertEquals(Path.of("maps", "a.b.markers.dat"), MarkerSidecar.pathFor(Path.of("maps", "a.b.polar")));
        assertEquals(Path.of("dev.markers.dat"), MarkerSidecar.pathFor(Path.of("dev.polar")));
        assertThrows(IllegalArgumentException.class, () -> MarkerSidecar.pathFor(Path.of("desert.zip")));
        assertThrows(IllegalArgumentException.class, () -> MarkerSidecar.pathFor(Path.of("desert.polar.bak")));
        assertThrows(IllegalArgumentException.class, () -> MarkerSidecar.pathFor(Path.of(".polar")));
        assertThrows(IllegalArgumentException.class, () -> MarkerFile.forPolar(Path.of("desert")));
    }
}
