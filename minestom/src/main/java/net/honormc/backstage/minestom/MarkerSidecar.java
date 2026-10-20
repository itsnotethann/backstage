package net.honormc.backstage.minestom;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Prop;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;
import net.kyori.adventure.nbt.BinaryTag;
import net.kyori.adventure.nbt.BinaryTagTypes;
import net.kyori.adventure.nbt.ByteBinaryTag;
import net.kyori.adventure.nbt.CompoundBinaryTag;
import net.kyori.adventure.nbt.DoubleBinaryTag;
import net.kyori.adventure.nbt.IntArrayBinaryTag;
import net.kyori.adventure.nbt.IntBinaryTag;
import net.kyori.adventure.nbt.ListBinaryTag;
import net.kyori.adventure.nbt.LongArrayBinaryTag;
import net.kyori.adventure.nbt.StringBinaryTag;

public final class MarkerSidecar {
    public static final String FORMAT = "backstage-markers";
    public static final int VERSION = 1;
    private static final String POLAR = ".polar";
    private static final String SUFFIX = ".markers.dat";

    private MarkerSidecar() {
    }

    public static Path pathFor(Path polar) {
        String name = polar.getFileName().toString();
        if (!name.endsWith(POLAR) || name.length() == POLAR.length()) {
            throw new IllegalArgumentException("map file '" + polar + "' does not end in " + POLAR);
        }
        return polar.resolveSibling(name.substring(0, name.length() - POLAR.length()) + SUFFIX);
    }

    static CompoundBinaryTag write(MarkerSnapshot snapshot) {
        return CompoundBinaryTag.builder()
                .putString("format", FORMAT)
                .putInt("version", VERSION)
                .putString("map", snapshot.map())
                .putLong("revision", snapshot.revision())
                .put("points", list(snapshot.points().stream().map(MarkerSidecar::writePoint).toList()))
                .put("groups", list(snapshot.groups().stream().map(MarkerSidecar::writeGroup).toList()))
                .put("regions", list(snapshot.regions().stream().map(MarkerSidecar::writeRegion).toList()))
                .build();
    }

    static MarkerSnapshot readV1(CompoundBinaryTag root, String map) {
        List<Point> points = new ArrayList<>();
        for (BinaryTag tag : root.getList("points", BinaryTagTypes.COMPOUND)) {
            points.add(readPoint((CompoundBinaryTag) tag));
        }
        List<Group> groups = new ArrayList<>();
        for (BinaryTag tag : root.getList("groups", BinaryTagTypes.COMPOUND)) {
            groups.add(readGroup((CompoundBinaryTag) tag));
        }
        List<Region> regions = new ArrayList<>();
        for (BinaryTag tag : root.getList("regions", BinaryTagTypes.COMPOUND)) {
            regions.add(readRegion((CompoundBinaryTag) tag));
        }
        return new MarkerSnapshot(map, root.getLong("revision"), points, groups, regions);
    }

    private static ListBinaryTag list(List<? extends BinaryTag> tags) {
        return tags.isEmpty() ? ListBinaryTag.empty() : ListBinaryTag.from(tags);
    }

    private static CompoundBinaryTag writePoint(Point point) {
        return CompoundBinaryTag.builder()
                .putString("id", point.id())
                .put("pos", ListBinaryTag.from(List.of(
                        DoubleBinaryTag.doubleBinaryTag(point.x()),
                        DoubleBinaryTag.doubleBinaryTag(point.y()),
                        DoubleBinaryTag.doubleBinaryTag(point.z()))))
                .putFloat("yaw", point.yaw())
                .putFloat("pitch", point.pitch())
                .put("tags", writeTags(point.tags()))
                .put("props", writeProps(point.props()))
                .build();
    }

    private static Point readPoint(CompoundBinaryTag tag) {
        ListBinaryTag pos = tag.getList("pos", BinaryTagTypes.DOUBLE);
        if (pos.size() != 3) {
            throw new IllegalArgumentException("point '" + tag.getString("id") + "' pos must hold 3 doubles");
        }
        return new Point(tag.getString("id"), pos.getDouble(0), pos.getDouble(1), pos.getDouble(2),
                tag.getFloat("yaw"), tag.getFloat("pitch"), readTags(tag), readProps(tag.getCompound("props")));
    }

    private static CompoundBinaryTag writeGroup(Group group) {
        return CompoundBinaryTag.builder()
                .putString("id", group.id())
                .put("blocks", LongArrayBinaryTag.longArrayBinaryTag(group.blocks()))
                .put("tags", writeTags(group.tags()))
                .put("props", writeProps(group.props()))
                .build();
    }

    private static Group readGroup(CompoundBinaryTag tag) {
        return new Group(tag.getString("id"), tag.getLongArray("blocks"), readTags(tag),
                readProps(tag.getCompound("props")));
    }

    private static CompoundBinaryTag writeRegion(Region region) {
        CompoundBinaryTag.Builder builder = CompoundBinaryTag.builder().putString("id", region.id());
        switch (region.shape()) {
            case Shape.Box box -> builder
                    .putString("shape", "box")
                    .put("min", IntArrayBinaryTag.intArrayBinaryTag(box.minX(), box.minY(), box.minZ()))
                    .put("max", IntArrayBinaryTag.intArrayBinaryTag(box.maxX(), box.maxY(), box.maxZ()));
            case Shape.Polygon polygon -> builder
                    .putString("shape", "polygon")
                    .put("xz", IntArrayBinaryTag.intArrayBinaryTag(polygon.xz()))
                    .put("minY", IntBinaryTag.intBinaryTag(polygon.minY()))
                    .put("maxY", IntBinaryTag.intBinaryTag(polygon.maxY()));
        }
        return builder
                .put("tags", writeTags(region.tags()))
                .put("props", writeProps(region.props()))
                .build();
    }

    private static Region readRegion(CompoundBinaryTag tag) {
        String id = tag.getString("id");
        String shapeName = tag.getString("shape");
        Shape shape = switch (shapeName) {
            case "box" -> {
                int[] min = tag.getIntArray("min");
                int[] max = tag.getIntArray("max");
                if (min.length != 3 || max.length != 3) {
                    throw new IllegalArgumentException("region '" + id + "' min and max must hold 3 ints");
                }
                yield new Shape.Box(min[0], min[1], min[2], max[0], max[1], max[2]);
            }
            case "polygon" -> new Shape.Polygon(tag.getIntArray("xz"), tag.getInt("minY"), tag.getInt("maxY"));
            default -> throw new IllegalArgumentException("region '" + id + "' has unknown shape '" + shapeName + "'");
        };
        return new Region(id, shape, readTags(tag), readProps(tag.getCompound("props")));
    }

    private static ListBinaryTag writeTags(List<String> tags) {
        return list(tags.stream().map(StringBinaryTag::stringBinaryTag).toList());
    }

    private static List<String> readTags(CompoundBinaryTag tag) {
        List<String> tags = new ArrayList<>();
        for (BinaryTag entry : tag.getList("tags", BinaryTagTypes.STRING)) {
            tags.add(((StringBinaryTag) entry).value());
        }
        return tags;
    }

    private static CompoundBinaryTag writeProps(Map<String, Prop> props) {
        CompoundBinaryTag.Builder builder = CompoundBinaryTag.builder();
        for (Map.Entry<String, Prop> entry : new TreeMap<>(props).entrySet()) {
            builder.put(entry.getKey(), writeProp(entry.getValue()));
        }
        return builder.build();
    }

    private static Map<String, Prop> readProps(CompoundBinaryTag tag) {
        Map<String, Prop> props = new TreeMap<>();
        for (String key : tag.keySet()) {
            props.put(key, readProp(tag.get(key)));
        }
        return props;
    }

    private static BinaryTag writeProp(Prop prop) {
        return switch (prop) {
            case Prop.Str p -> StringBinaryTag.stringBinaryTag(p.value());
            case Prop.Num p -> DoubleBinaryTag.doubleBinaryTag(p.value());
            case Prop.Bool p -> p.value() ? ByteBinaryTag.ONE : ByteBinaryTag.ZERO;
            case Prop.BlockProp p -> CompoundBinaryTag.builder().putString("block", p.state()).build();
            case Prop.ItemProp p -> CompoundBinaryTag.builder().putString("item", p.snbt()).build();
            case Prop.ListProp p -> list(p.values().stream()
                    .map(value -> CompoundBinaryTag.builder().put("v", writeProp(value)).build())
                    .toList());
        };
    }

    private static Prop readProp(BinaryTag tag) {
        return switch (tag) {
            case StringBinaryTag s -> new Prop.Str(s.value());
            case DoubleBinaryTag d -> new Prop.Num(d.value());
            case ByteBinaryTag b when b.value() == 0 || b.value() == 1 -> new Prop.Bool(b.value() == 1);
            case CompoundBinaryTag c when c.size() == 1 && c.get("block") instanceof StringBinaryTag s ->
                    new Prop.BlockProp(s.value());
            case CompoundBinaryTag c when c.size() == 1 && c.get("item") instanceof StringBinaryTag s ->
                    new Prop.ItemProp(s.value());
            case ListBinaryTag l -> {
                List<Prop> values = new ArrayList<>(l.size());
                for (BinaryTag entry : l) {
                    if (!(entry instanceof CompoundBinaryTag c) || c.get("v") == null) {
                        throw new IllegalArgumentException("prop list entries must be {v: <value>} compounds");
                    }
                    values.add(readProp(c.get("v")));
                }
                yield new Prop.ListProp(values);
            }
            default -> throw new IllegalArgumentException("unsupported prop tag " + tag.type());
        };
    }
}
