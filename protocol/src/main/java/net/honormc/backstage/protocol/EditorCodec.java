package net.honormc.backstage.protocol;

import static net.honormc.backstage.protocol.EditorProtocol.MAX_CLIENTBOUND_BODY;
import static net.honormc.backstage.protocol.EditorProtocol.MAX_GROUP_BLOCKS;
import static net.honormc.backstage.protocol.EditorProtocol.MAX_LIST;
import static net.honormc.backstage.protocol.EditorProtocol.MAX_POLYGON_VERTICES;
import static net.honormc.backstage.protocol.EditorProtocol.MAX_PROPS;
import static net.honormc.backstage.protocol.EditorProtocol.MAX_PROP_DEPTH;
import static net.honormc.backstage.protocol.EditorProtocol.MAX_SERVERBOUND_BODY;
import static net.honormc.backstage.protocol.EditorProtocol.MAX_STRING_BYTES;
import static net.honormc.backstage.protocol.EditorProtocol.MAX_TAGS;
import static net.honormc.backstage.protocol.EditorProtocol.MAX_TAG_BYTES;
import static net.honormc.backstage.protocol.EditorProtocol.MIN_POLYGON_VERTICES;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.honormc.backstage.protocol.ClientboundPacket.Denied;
import net.honormc.backstage.protocol.ClientboundPacket.EditRejected;
import net.honormc.backstage.protocol.ClientboundPacket.MapState;
import net.honormc.backstage.protocol.ClientboundPacket.MarkerChanged;
import net.honormc.backstage.protocol.ClientboundPacket.MarkerRemoved;
import net.honormc.backstage.protocol.ClientboundPacket.RegistrySync;
import net.honormc.backstage.protocol.ClientboundPacket.SyncBatch;
import net.honormc.backstage.protocol.ClientboundPacket.SyncBegin;
import net.honormc.backstage.protocol.ClientboundPacket.SyncEnd;
import net.honormc.backstage.protocol.ClientboundPacket.Welcome;
import net.honormc.backstage.protocol.ServerboundPacket.Hello;
import net.honormc.backstage.protocol.ServerboundPacket.RemoveMarker;
import net.honormc.backstage.protocol.ServerboundPacket.RenameMarker;
import net.honormc.backstage.protocol.ServerboundPacket.Resync;
import net.honormc.backstage.protocol.ServerboundPacket.UpsertMarker;
import net.honormc.backstage.protocol.model.FieldDef;
import net.honormc.backstage.protocol.model.FieldType;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.KindDef;
import net.honormc.backstage.protocol.model.ListDef;
import net.honormc.backstage.protocol.model.ListEntry;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Prop;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Registry;
import net.honormc.backstage.protocol.model.Shape;

public final class EditorCodec {
    private static final int SHAPE_BOX = 0;
    private static final int SHAPE_POLYGON = 1;

    private static final int PROP_STR = 0;
    private static final int PROP_NUM = 1;
    private static final int PROP_BOOL = 2;
    private static final int PROP_BLOCK = 3;
    private static final int PROP_ITEM = 4;
    private static final int PROP_LIST = 5;

    private EditorCodec() {
    }

    public static byte[] encode(ServerboundPacket packet) {
        WireWriter out = new WireWriter();
        switch (packet) {
            case Hello p -> {
                out.writeVarInt(0);
                out.writeVarInt(p.protocol());
                writeString(out, p.modVersion());
            }
            case Resync p -> out.writeVarInt(1);
            case UpsertMarker p -> {
                out.writeVarInt(2);
                writeString(out, p.map());
                writeMarker(out, p.marker());
            }
            case RemoveMarker p -> {
                out.writeVarInt(3);
                writeString(out, p.map());
                out.writeVarInt(p.type().id());
                writeId(out, p.id());
            }
            case RenameMarker p -> {
                out.writeVarInt(4);
                writeString(out, p.map());
                out.writeVarInt(p.type().id());
                writeId(out, p.from());
                writeId(out, p.to());
            }
        }
        return finish(out, MAX_SERVERBOUND_BODY, "serverbound");
    }

    public static byte[] encode(ClientboundPacket packet) {
        WireWriter out = new WireWriter();
        switch (packet) {
            case Welcome p -> {
                out.writeVarInt(0);
                out.writeVarInt(p.protocol());
                writeString(out, p.serverName());
            }
            case Denied p -> {
                out.writeVarInt(1);
                out.writeVarInt(p.reason().id());
                out.writeVarInt(p.serverProtocol());
            }
            case RegistrySync p -> {
                out.writeVarInt(2);
                writeRegistry(out, p.registry());
            }
            case MapState p -> {
                out.writeVarInt(3);
                out.writeBool(p.bound());
                writeString(out, p.map());
                out.writeBool(p.writable());
            }
            case SyncBegin p -> {
                out.writeVarInt(4);
                writeString(out, p.map());
                out.writeVarLong(p.revision());
                out.writeVarInt(p.total());
            }
            case SyncBatch p -> {
                out.writeVarInt(5);
                writeString(out, p.map());
                out.writeCount(p.markers().size(), MAX_LIST);
                for (Marker marker : p.markers()) {
                    writeMarker(out, marker);
                }
            }
            case SyncEnd p -> {
                out.writeVarInt(6);
                writeString(out, p.map());
            }
            case MarkerChanged p -> {
                out.writeVarInt(7);
                writeString(out, p.map());
                out.writeVarLong(p.revision());
                writeMarker(out, p.marker());
            }
            case MarkerRemoved p -> {
                out.writeVarInt(8);
                writeString(out, p.map());
                out.writeVarLong(p.revision());
                out.writeVarInt(p.type().id());
                writeId(out, p.id());
            }
            case EditRejected p -> {
                out.writeVarInt(9);
                writeString(out, p.map());
                out.writeVarInt(p.type().id());
                writeId(out, p.id());
                writeString(out, p.reason());
            }
        }
        return finish(out, MAX_CLIENTBOUND_BODY, "clientbound");
    }

    public static ServerboundPacket decodeServerbound(byte[] body) {
        checkBody(body, MAX_SERVERBOUND_BODY, "serverbound");
        WireReader in = new WireReader(body);
        int id = in.readVarInt();
        ServerboundPacket packet = switch (id) {
            case 0 -> new Hello(in.readVarInt(), readString(in));
            case 1 -> new Resync();
            case 2 -> new UpsertMarker(readString(in), readMarker(in));
            case 3 -> new RemoveMarker(readString(in), readMarkerType(in), readId(in));
            case 4 -> new RenameMarker(readString(in), readMarkerType(in), readId(in), readId(in));
            default -> throw new ProtocolException("unknown serverbound packet id " + id);
        };
        in.end();
        return packet;
    }

    public static ClientboundPacket decodeClientbound(byte[] body) {
        checkBody(body, MAX_CLIENTBOUND_BODY, "clientbound");
        WireReader in = new WireReader(body);
        int id = in.readVarInt();
        ClientboundPacket packet = switch (id) {
            case 0 -> new Welcome(in.readVarInt(), readString(in));
            case 1 -> new Denied(readDeniedReason(in), in.readVarInt());
            case 2 -> new RegistrySync(readRegistry(in));
            case 3 -> new MapState(in.readBool(), readString(in), in.readBool());
            case 4 -> new SyncBegin(readString(in), in.readVarLong(), in.readVarInt());
            case 5 -> {
                String map = readString(in);
                int count = in.readCount(MAX_LIST);
                List<Marker> markers = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    markers.add(readMarker(in));
                }
                yield new SyncBatch(map, markers);
            }
            case 6 -> new SyncEnd(readString(in));
            case 7 -> new MarkerChanged(readString(in), in.readVarLong(), readMarker(in));
            case 8 -> new MarkerRemoved(readString(in), in.readVarLong(), readMarkerType(in), readId(in));
            case 9 -> new EditRejected(readString(in), readMarkerType(in), readId(in), readString(in));
            default -> throw new ProtocolException("unknown clientbound packet id " + id);
        };
        in.end();
        return packet;
    }

    private static byte[] finish(WireWriter out, int limit, String direction) {
        if (out.size() > limit) {
            throw new ProtocolException(direction + " body of " + out.size() + " bytes exceeds " + limit);
        }
        return out.toByteArray();
    }

    private static void checkBody(byte[] body, int limit, String direction) {
        if (body.length > limit) {
            throw new ProtocolException(direction + " body of " + body.length + " bytes exceeds " + limit);
        }
    }

    private static void writeString(WireWriter out, String value) {
        out.writeString(value, MAX_STRING_BYTES);
    }

    private static String readString(WireReader in) {
        return in.readString(MAX_STRING_BYTES);
    }

    private static void writeId(WireWriter out, String id) {
        if (!EditorProtocol.isValidId(id)) {
            throw new ProtocolException("invalid id '" + id + "'");
        }
        out.writeString(id, MAX_STRING_BYTES);
    }

    private static String readId(WireReader in) {
        String id = in.readString(MAX_STRING_BYTES);
        if (!EditorProtocol.isValidId(id)) {
            throw new ProtocolException("invalid id '" + id + "'");
        }
        return id;
    }

    private static MarkerType readMarkerType(WireReader in) {
        int id = in.readVarInt();
        return MarkerType.byId(id).orElseThrow(() -> new ProtocolException("unknown marker type " + id));
    }

    private static FieldType readFieldType(WireReader in) {
        int id = in.readVarInt();
        return FieldType.byId(id).orElseThrow(() -> new ProtocolException("unknown field type " + id));
    }

    private static DeniedReason readDeniedReason(WireReader in) {
        int id = in.readVarInt();
        return DeniedReason.byId(id).orElseThrow(() -> new ProtocolException("unknown denied reason " + id));
    }

    private static void writeMarker(WireWriter out, Marker marker) {
        out.writeVarInt(marker.type().id());
        switch (marker) {
            case Point p -> {
                writeId(out, p.id());
                out.writeDouble(p.x());
                out.writeDouble(p.y());
                out.writeDouble(p.z());
                out.writeFloat(p.yaw());
                out.writeFloat(p.pitch());
            }
            case Group g -> {
                writeId(out, g.id());
                long[] blocks = g.blocks();
                out.writeCount(blocks.length, MAX_GROUP_BLOCKS);
                for (long block : blocks) {
                    out.writeLong(block);
                }
            }
            case Region r -> {
                writeId(out, r.id());
                writeShape(out, r.shape());
            }
        }
        writeTags(out, marker.tags());
        writeProps(out, marker.props());
    }

    private static Marker readMarker(WireReader in) {
        return switch (readMarkerType(in)) {
            case POINT -> new Point(readId(in), in.readDouble(), in.readDouble(), in.readDouble(), in.readFloat(),
                    in.readFloat(), readTags(in), readProps(in));
            case GROUP -> {
                String id = readId(in);
                long[] blocks = new long[in.readCount(MAX_GROUP_BLOCKS)];
                for (int i = 0; i < blocks.length; i++) {
                    blocks[i] = in.readLong();
                }
                yield new Group(id, blocks, readTags(in), readProps(in));
            }
            case REGION -> new Region(readId(in), readShape(in), readTags(in), readProps(in));
        };
    }

    private static void writeShape(WireWriter out, Shape shape) {
        switch (shape) {
            case Shape.Box b -> {
                out.writeVarInt(SHAPE_BOX);
                out.writeVarInt(b.minX());
                out.writeVarInt(b.minY());
                out.writeVarInt(b.minZ());
                out.writeVarInt(b.maxX());
                out.writeVarInt(b.maxY());
                out.writeVarInt(b.maxZ());
            }
            case Shape.Polygon p -> {
                out.writeVarInt(SHAPE_POLYGON);
                int vertices = p.vertices();
                if (vertices < MIN_POLYGON_VERTICES || vertices > MAX_POLYGON_VERTICES) {
                    throw new ProtocolException("polygon of " + vertices + " vertices is outside "
                            + MIN_POLYGON_VERTICES + ".." + MAX_POLYGON_VERTICES);
                }
                out.writeVarInt(vertices);
                for (int value : p.xz()) {
                    out.writeVarInt(value);
                }
                out.writeVarInt(p.minY());
                out.writeVarInt(p.maxY());
            }
        }
    }

    private static Shape readShape(WireReader in) {
        int tag = in.readVarInt();
        return switch (tag) {
            case SHAPE_BOX -> new Shape.Box(in.readVarInt(), in.readVarInt(), in.readVarInt(), in.readVarInt(),
                    in.readVarInt(), in.readVarInt());
            case SHAPE_POLYGON -> {
                int vertices = in.readVarInt();
                if (vertices < MIN_POLYGON_VERTICES || vertices > MAX_POLYGON_VERTICES) {
                    throw new ProtocolException("polygon of " + vertices + " vertices is outside "
                            + MIN_POLYGON_VERTICES + ".." + MAX_POLYGON_VERTICES);
                }
                int[] xz = new int[vertices * 2];
                for (int i = 0; i < xz.length; i++) {
                    xz[i] = in.readVarInt();
                }
                yield new Shape.Polygon(xz, in.readVarInt(), in.readVarInt());
            }
            default -> throw new ProtocolException("unknown shape " + tag);
        };
    }

    private static void writeTags(WireWriter out, List<String> tags) {
        out.writeCount(tags.size(), MAX_TAGS);
        for (String tag : tags) {
            out.writeString(tag, MAX_TAG_BYTES);
        }
    }

    private static List<String> readTags(WireReader in) {
        int count = in.readCount(MAX_TAGS);
        List<String> tags = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            tags.add(in.readString(MAX_TAG_BYTES));
        }
        return tags;
    }

    private static void writeProps(WireWriter out, Map<String, Prop> props) {
        out.writeCount(props.size(), MAX_PROPS);
        for (Map.Entry<String, Prop> entry : props.entrySet()) {
            writeString(out, entry.getKey());
            writeProp(out, entry.getValue(), 0);
        }
    }

    private static Map<String, Prop> readProps(WireReader in) {
        int count = in.readCount(MAX_PROPS);
        Map<String, Prop> props = new LinkedHashMap<>();
        for (int i = 0; i < count; i++) {
            String key = readString(in);
            if (props.put(key, readProp(in, 0)) != null) {
                throw new ProtocolException("duplicate prop key '" + key + "'");
            }
        }
        return props;
    }

    private static void writeProp(WireWriter out, Prop prop, int depth) {
        switch (prop) {
            case Prop.Str p -> {
                out.writeVarInt(PROP_STR);
                writeString(out, p.value());
            }
            case Prop.Num p -> {
                out.writeVarInt(PROP_NUM);
                out.writeDouble(p.value());
            }
            case Prop.Bool p -> {
                out.writeVarInt(PROP_BOOL);
                out.writeBool(p.value());
            }
            case Prop.BlockProp p -> {
                out.writeVarInt(PROP_BLOCK);
                writeString(out, p.state());
            }
            case Prop.ItemProp p -> {
                out.writeVarInt(PROP_ITEM);
                writeString(out, p.snbt());
            }
            case Prop.ListProp p -> {
                if (depth >= MAX_PROP_DEPTH) {
                    throw new ProtocolException("prop lists nested deeper than " + MAX_PROP_DEPTH);
                }
                out.writeVarInt(PROP_LIST);
                out.writeCount(p.values().size(), MAX_LIST);
                for (Prop value : p.values()) {
                    writeProp(out, value, depth + 1);
                }
            }
        }
    }

    private static Prop readProp(WireReader in, int depth) {
        int tag = in.readVarInt();
        return switch (tag) {
            case PROP_STR -> new Prop.Str(readString(in));
            case PROP_NUM -> new Prop.Num(in.readDouble());
            case PROP_BOOL -> new Prop.Bool(in.readBool());
            case PROP_BLOCK -> new Prop.BlockProp(readString(in));
            case PROP_ITEM -> new Prop.ItemProp(readString(in));
            case PROP_LIST -> {
                if (depth >= MAX_PROP_DEPTH) {
                    throw new ProtocolException("prop lists nested deeper than " + MAX_PROP_DEPTH);
                }
                int count = in.readCount(MAX_LIST);
                List<Prop> values = new ArrayList<>(count);
                for (int i = 0; i < count; i++) {
                    values.add(readProp(in, depth + 1));
                }
                yield new Prop.ListProp(values);
            }
            default -> throw new ProtocolException("unknown prop tag " + tag);
        };
    }

    private static void writeRegistry(WireWriter out, Registry registry) {
        out.writeCount(registry.types().size(), MAX_LIST);
        for (MarkerType type : registry.types()) {
            out.writeVarInt(type.id());
        }
        out.writeCount(registry.kinds().size(), MAX_LIST);
        for (KindDef kind : registry.kinds()) {
            writeId(out, kind.name());
            writeString(out, kind.label());
            out.writeVarInt(kind.anchor().id());
            out.writeCount(kind.fields().size(), MAX_LIST);
            for (FieldDef field : kind.fields()) {
                writeId(out, field.name());
                writeString(out, field.label());
                out.writeVarInt(field.type().id());
                out.writeBool(field.required());
                checkListRef(field.type(), field.listRef());
                writeString(out, field.listRef());
            }
        }
        out.writeCount(registry.lists().size(), MAX_LIST);
        for (ListDef list : registry.lists()) {
            writeId(out, list.name());
            out.writeCount(list.entries().size(), MAX_LIST);
            for (ListEntry entry : list.entries()) {
                writeString(out, entry.value());
                writeString(out, entry.label());
            }
        }
    }

    private static Registry readRegistry(WireReader in) {
        int typeCount = in.readCount(MAX_LIST);
        List<MarkerType> types = new ArrayList<>(typeCount);
        for (int i = 0; i < typeCount; i++) {
            types.add(readMarkerType(in));
        }
        int kindCount = in.readCount(MAX_LIST);
        List<KindDef> kinds = new ArrayList<>(kindCount);
        for (int i = 0; i < kindCount; i++) {
            String name = readId(in);
            String label = readString(in);
            MarkerType anchor = readMarkerType(in);
            int fieldCount = in.readCount(MAX_LIST);
            List<FieldDef> fields = new ArrayList<>(fieldCount);
            for (int j = 0; j < fieldCount; j++) {
                String fieldName = readId(in);
                String fieldLabel = readString(in);
                FieldType type = readFieldType(in);
                boolean required = in.readBool();
                String listRef = readString(in);
                checkListRef(type, listRef);
                fields.add(new FieldDef(fieldName, fieldLabel, type, required, listRef));
            }
            kinds.add(new KindDef(name, label, anchor, fields));
        }
        int listCount = in.readCount(MAX_LIST);
        List<ListDef> lists = new ArrayList<>(listCount);
        for (int i = 0; i < listCount; i++) {
            String name = readId(in);
            int entryCount = in.readCount(MAX_LIST);
            List<ListEntry> entries = new ArrayList<>(entryCount);
            for (int j = 0; j < entryCount; j++) {
                entries.add(new ListEntry(readString(in), readString(in)));
            }
            lists.add(new ListDef(name, entries));
        }
        return new Registry(types, kinds, lists);
    }

    private static void checkListRef(FieldType type, String listRef) {
        if (type == FieldType.CHOICE ? !EditorProtocol.isValidId(listRef) : !listRef.isEmpty()) {
            throw new ProtocolException("field of type " + type + " has invalid listRef '" + listRef + "'");
        }
    }
}
