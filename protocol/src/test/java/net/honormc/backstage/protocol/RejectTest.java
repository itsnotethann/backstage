package net.honormc.backstage.protocol;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import net.honormc.backstage.protocol.ClientboundPacket.MarkerChanged;
import net.honormc.backstage.protocol.ClientboundPacket.RegistrySync;
import net.honormc.backstage.protocol.ClientboundPacket.SyncBatch;
import net.honormc.backstage.protocol.ServerboundPacket.RemoveMarker;
import net.honormc.backstage.protocol.ServerboundPacket.Resync;
import net.honormc.backstage.protocol.ServerboundPacket.UpsertMarker;
import net.honormc.backstage.protocol.model.FieldDef;
import net.honormc.backstage.protocol.model.FieldType;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.KindDef;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Prop;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Registry;
import net.honormc.backstage.protocol.model.Shape;
import org.junit.jupiter.api.Test;

class RejectTest {
    private static byte[] body(Consumer<WireWriter> content) {
        WireWriter out = new WireWriter();
        content.accept(out);
        return out.toByteArray();
    }

    private static void rejectsServerbound(byte[] body) {
        assertThrows(ProtocolException.class, () -> EditorCodec.decodeServerbound(body));
    }

    private static void rejectsClientbound(byte[] body) {
        assertThrows(ProtocolException.class, () -> EditorCodec.decodeClientbound(body));
    }

    private static byte[] upsertPoint(Consumer<WireWriter> id, int tagCount, Consumer<WireWriter> tags,
                                      Consumer<WireWriter> props) {
        return body(out -> {
            out.writeVarInt(2);
            out.writeString("desert", 100);
            out.writeVarInt(MarkerType.POINT.id());
            id.accept(out);
            out.writeDouble(0);
            out.writeDouble(0);
            out.writeDouble(0);
            out.writeFloat(0);
            out.writeFloat(0);
            out.writeVarInt(tagCount);
            tags.accept(out);
            props.accept(out);
        });
    }

    private static byte[] upsertPointWithProps(Consumer<WireWriter> props) {
        return upsertPoint(out -> out.writeString("a", 100), 0, out -> {
        }, props);
    }

    private static void nestedList(WireWriter out, int depth) {
        out.writeVarInt(5);
        if (depth == 1) {
            out.writeVarInt(0);
        } else {
            out.writeVarInt(1);
            nestedList(out, depth - 1);
        }
    }

    private static byte[] upsertGroup(int count) {
        return body(out -> {
            out.writeVarInt(2);
            out.writeString("desert", 100);
            out.writeVarInt(MarkerType.GROUP.id());
            out.writeString("flag", 100);
            out.writeVarInt(count);
            for (int i = 0; i < count; i++) {
                out.writeLong(i);
            }
            out.writeVarInt(0);
            out.writeVarInt(0);
        });
    }

    private static byte[] upsertPolygon(int vertices) {
        return body(out -> {
            out.writeVarInt(2);
            out.writeString("desert", 100);
            out.writeVarInt(MarkerType.REGION.id());
            out.writeString("pit", 100);
            out.writeVarInt(1);
            out.writeVarInt(vertices);
            for (int i = 0; i < vertices * 2; i++) {
                out.writeVarInt(i);
            }
            out.writeVarInt(0);
            out.writeVarInt(10);
            out.writeVarInt(0);
            out.writeVarInt(0);
        });
    }

    private static Prop nestedProp(int depth) {
        Prop prop = new Prop.ListProp(List.of());
        for (int i = 1; i < depth; i++) {
            prop = new Prop.ListProp(List.of(prop));
        }
        return prop;
    }

    private static Point pointWith(List<String> tags, Map<String, Prop> props) {
        return new Point("a", 0, 0, 0, 0, 0, tags, props);
    }

    @Test
    void unknownPacketId() {
        rejectsServerbound(body(out -> out.writeVarInt(5)));
        rejectsServerbound(body(out -> out.writeVarInt(-1)));
        rejectsClientbound(body(out -> out.writeVarInt(10)));
        rejectsClientbound(body(out -> out.writeVarInt(Integer.MAX_VALUE)));
        rejectsServerbound(new byte[0]);
        rejectsClientbound(new byte[0]);
    }

    @Test
    void trailingBytes() {
        rejectsServerbound(body(out -> {
            out.writeVarInt(1);
            out.writeByte(0);
        }));
        byte[] valid = EditorCodec.encode(new UpsertMarker("desert", Samples.fullGroup()));
        byte[] padded = Arrays.copyOf(valid, valid.length + 1);
        rejectsServerbound(padded);
        byte[] batch = EditorCodec.encode(new SyncBatch("desert", Samples.allMarkers()));
        rejectsClientbound(Arrays.copyOf(batch, batch.length + 3));
    }

    @Test
    void truncatedInput() {
        for (Marker marker : Samples.allMarkers()) {
            byte[] serverbound = EditorCodec.encode(new UpsertMarker("desert", marker));
            for (int length = 0; length < serverbound.length; length++) {
                rejectsServerbound(Arrays.copyOf(serverbound, length));
            }
        }
        byte[] registry = EditorCodec.encode(new RegistrySync(Samples.fullRegistry()));
        for (int length = 0; length < registry.length; length++) {
            rejectsClientbound(Arrays.copyOf(registry, length));
        }
        byte[] batch = EditorCodec.encode(new SyncBatch("desert", Samples.allMarkers()));
        for (int length = 0; length < batch.length; length++) {
            rejectsClientbound(Arrays.copyOf(batch, length));
        }
    }

    @Test
    void badBool() {
        rejectsClientbound(body(out -> {
            out.writeVarInt(3);
            out.writeByte(2);
            out.writeString("desert", 100);
            out.writeBool(true);
        }));
        rejectsClientbound(body(out -> {
            out.writeVarInt(3);
            out.writeBool(true);
            out.writeString("desert", 100);
            out.writeByte(0xFF);
        }));
        rejectsServerbound(upsertPointWithProps(out -> {
            out.writeVarInt(1);
            out.writeString("boss", 100);
            out.writeVarInt(2);
            out.writeByte(7);
        }));
    }

    @Test
    void overLimitStrings() {
        String tooLong = Samples.repeat('a', EditorProtocol.MAX_STRING_BYTES + 1);
        rejectsClientbound(body(out -> {
            out.writeVarInt(0);
            out.writeVarInt(1);
            out.writeString(tooLong, Integer.MAX_VALUE);
        }));
        rejectsClientbound(body(out -> {
            out.writeVarInt(6);
            out.writeVarInt(EditorProtocol.MAX_STRING_BYTES + 1);
        }));
        rejectsServerbound(upsertPoint(out -> out.writeString("a", 100), 1,
                out -> out.writeString(Samples.repeat('t', EditorProtocol.MAX_TAG_BYTES + 1), 100),
                out -> out.writeVarInt(0)));
        assertDoesNotThrow(() -> EditorCodec.decodeServerbound(upsertPoint(out -> out.writeString("a", 100), 1,
                out -> out.writeString(Samples.repeat('t', EditorProtocol.MAX_TAG_BYTES), 100),
                out -> out.writeVarInt(0))));
        rejectsClientbound(body(out -> {
            out.writeVarInt(0);
            out.writeVarInt(1);
            out.writeVarInt(2);
            out.writeByte(0xC3);
            out.writeByte(0x28);
        }));
    }

    @Test
    void overLimitLists() {
        rejectsServerbound(upsertPoint(out -> out.writeString("a", 100), EditorProtocol.MAX_TAGS + 1, out -> {
            for (int i = 0; i <= EditorProtocol.MAX_TAGS; i++) {
                out.writeString("t", 100);
            }
        }, out -> out.writeVarInt(0)));
        rejectsServerbound(upsertPointWithProps(out -> {
            out.writeVarInt(EditorProtocol.MAX_PROPS + 1);
            for (int i = 0; i <= EditorProtocol.MAX_PROPS; i++) {
                out.writeString("k" + i, 100);
                out.writeVarInt(1);
                out.writeDouble(i);
            }
        }));
        rejectsServerbound(upsertPointWithProps(out -> {
            out.writeVarInt(1);
            out.writeString("list", 100);
            out.writeVarInt(5);
            out.writeVarInt(EditorProtocol.MAX_LIST + 1);
        }));
        rejectsClientbound(body(out -> {
            out.writeVarInt(5);
            out.writeString("desert", 100);
            out.writeVarInt(EditorProtocol.MAX_LIST + 1);
        }));
        rejectsClientbound(body(out -> {
            out.writeVarInt(2);
            out.writeVarInt(-1);
        }));
        rejectsServerbound(upsertPointWithProps(out -> {
            out.writeVarInt(2);
            out.writeString("k", 100);
            out.writeVarInt(1);
            out.writeDouble(1);
            out.writeString("k", 100);
            out.writeVarInt(1);
            out.writeDouble(2);
        }));
    }

    @Test
    void propsDepth() {
        assertDoesNotThrow(() -> EditorCodec.decodeServerbound(upsertPointWithProps(out -> {
            out.writeVarInt(1);
            out.writeString("deep", 100);
            nestedList(out, EditorProtocol.MAX_PROP_DEPTH);
        })));
        rejectsServerbound(upsertPointWithProps(out -> {
            out.writeVarInt(1);
            out.writeString("deep", 100);
            nestedList(out, EditorProtocol.MAX_PROP_DEPTH + 1);
        }));
        rejectsServerbound(upsertPointWithProps(out -> {
            out.writeVarInt(1);
            out.writeString("bad", 100);
            out.writeVarInt(6);
        }));
    }

    @Test
    void groupSize() {
        assertDoesNotThrow(() -> EditorCodec.decodeServerbound(upsertGroup(EditorProtocol.MAX_GROUP_BLOCKS)));
        rejectsServerbound(upsertGroup(EditorProtocol.MAX_GROUP_BLOCKS + 1));
        rejectsClientbound(body(out -> {
            out.writeVarInt(7);
            out.writeString("desert", 100);
            out.writeVarLong(1);
            out.writeVarInt(MarkerType.GROUP.id());
            out.writeString("flag", 100);
            out.writeVarInt(EditorProtocol.MAX_GROUP_BLOCKS + 1);
        }));
    }

    @Test
    void polygonSize() {
        assertDoesNotThrow(() -> EditorCodec.decodeServerbound(upsertPolygon(EditorProtocol.MIN_POLYGON_VERTICES)));
        assertDoesNotThrow(() -> EditorCodec.decodeServerbound(upsertPolygon(EditorProtocol.MAX_POLYGON_VERTICES)));
        rejectsServerbound(upsertPolygon(EditorProtocol.MIN_POLYGON_VERTICES - 1));
        rejectsServerbound(upsertPolygon(0));
        rejectsServerbound(upsertPolygon(EditorProtocol.MAX_POLYGON_VERTICES + 1));
        rejectsServerbound(body(out -> {
            out.writeVarInt(2);
            out.writeString("desert", 100);
            out.writeVarInt(MarkerType.REGION.id());
            out.writeString("pit", 100);
            out.writeVarInt(2);
        }));
    }

    @Test
    void invalidIds() {
        List<String> invalid = List.of("", "Boss", "a b", "x:y", "a/b", "é", Samples.repeat('a', 65));
        for (String id : invalid) {
            rejectsServerbound(upsertPoint(out -> out.writeString(id, 100), 0, out -> {
            }, out -> out.writeVarInt(0)));
            rejectsServerbound(body(out -> {
                out.writeVarInt(3);
                out.writeString("desert", 100);
                out.writeVarInt(MarkerType.POINT.id());
                out.writeString(id, 100);
            }));
            rejectsClientbound(body(out -> {
                out.writeVarInt(8);
                out.writeString("desert", 100);
                out.writeVarLong(1);
                out.writeVarInt(MarkerType.GROUP.id());
                out.writeString(id, 100);
            }));
        }
        assertDoesNotThrow(() -> EditorCodec.decodeServerbound(upsertPoint(
                out -> out.writeString("a-z_0.9" + Samples.repeat('x', 57), 100), 0, out -> {
                }, out -> out.writeVarInt(0))));
    }

    @Test
    void unknownEnumValues() {
        rejectsServerbound(body(out -> {
            out.writeVarInt(3);
            out.writeString("desert", 100);
            out.writeVarInt(3);
            out.writeString("a", 100);
        }));
        rejectsClientbound(body(out -> {
            out.writeVarInt(1);
            out.writeVarInt(4);
            out.writeVarInt(1);
        }));
        rejectsClientbound(body(out -> {
            out.writeVarInt(2);
            out.writeVarInt(0);
            out.writeVarInt(1);
            out.writeString("k", 100);
            out.writeString("K", 100);
            out.writeVarInt(MarkerType.POINT.id());
            out.writeVarInt(1);
            out.writeString("f", 100);
            out.writeString("F", 100);
            out.writeVarInt(9);
            out.writeBool(false);
            out.writeString("", 100);
            out.writeVarInt(0);
        }));
    }

    @Test
    void listRefRule() {
        rejectsClientbound(body(out -> {
            out.writeVarInt(2);
            out.writeVarInt(0);
            out.writeVarInt(1);
            out.writeString("k", 100);
            out.writeString("K", 100);
            out.writeVarInt(MarkerType.POINT.id());
            out.writeVarInt(1);
            out.writeString("f", 100);
            out.writeString("F", 100);
            out.writeVarInt(FieldType.NUMBER.id());
            out.writeBool(false);
            out.writeString("effects", 100);
            out.writeVarInt(0);
        }));
        Registry badChoice = new Registry(List.of(), List.of(new KindDef("k", "K", MarkerType.POINT,
                List.of(new FieldDef("f", "F", FieldType.CHOICE, true, "")))), List.of());
        assertThrows(ProtocolException.class, () -> EditorCodec.encode(new RegistrySync(badChoice)));
    }

    @Test
    void oversizeBodyRejectedOnDecode() {
        rejectsServerbound(new byte[EditorProtocol.MAX_SERVERBOUND_BODY + 1]);
        rejectsClientbound(new byte[EditorProtocol.MAX_CLIENTBOUND_BODY + 1]);
    }

    @Test
    void encodeRejectsOversizeBodies() {
        Map<String, Prop> big = new LinkedHashMap<>();
        big.put("a", new Prop.Str(Samples.repeat('a', 20_000)));
        big.put("b", new Prop.Str(Samples.repeat('b', 20_000)));
        assertThrows(ProtocolException.class, () -> EditorCodec.encode(new UpsertMarker("desert", pointWith(List.of(), big))));

        Map<String, Prop> fits = Map.of("a", new Prop.Str(Samples.repeat('a', 31_900)));
        assertDoesNotThrow(() -> EditorCodec.encode(new UpsertMarker("desert", pointWith(List.of(), fits))));

        List<Marker> markers = new ArrayList<>();
        for (int i = 0; i < 40; i++) {
            markers.add(new Point("p" + i, 0, 0, 0, 0, 0, List.of(), big));
        }
        assertDoesNotThrow(() -> EditorCodec.encode(new SyncBatch("desert", markers.subList(0, 20))));
        assertThrows(ProtocolException.class, () -> EditorCodec.encode(new SyncBatch("desert", markers)));
    }

    @Test
    void encodeRejectsInvalidRecords() {
        assertThrows(ProtocolException.class, () -> EditorCodec.encode(new RemoveMarker("desert", MarkerType.POINT, "Bad")));
        assertThrows(ProtocolException.class, () -> EditorCodec.encode(new UpsertMarker("desert",
                new Group("g", new long[EditorProtocol.MAX_GROUP_BLOCKS + 1], List.of(), Map.of()))));
        assertThrows(ProtocolException.class, () -> EditorCodec.encode(new UpsertMarker("desert",
                new Region("r", new Shape.Polygon(new int[]{0, 0, 1, 1}, 0, 1), List.of(), Map.of()))));
        assertThrows(ProtocolException.class, () -> EditorCodec.encode(new UpsertMarker("desert",
                pointWith(List.of(), Map.of("deep", nestedProp(EditorProtocol.MAX_PROP_DEPTH + 1))))));
        assertDoesNotThrow(() -> EditorCodec.encode(new UpsertMarker("desert",
                pointWith(List.of(), Map.of("deep", nestedProp(EditorProtocol.MAX_PROP_DEPTH))))));
        List<String> tags = new ArrayList<>();
        for (int i = 0; i <= EditorProtocol.MAX_TAGS; i++) {
            tags.add("t" + i);
        }
        assertThrows(ProtocolException.class, () -> EditorCodec.encode(new MarkerChanged("desert", 1,
                pointWith(tags, Map.of()))));
        assertThrows(ProtocolException.class, () -> EditorCodec.encode(new MarkerChanged("desert", 1,
                pointWith(List.of(Samples.repeat('t', EditorProtocol.MAX_TAG_BYTES + 1)), Map.of()))));
        assertThrows(IllegalArgumentException.class, () -> new Shape.Polygon(new int[]{0, 0, 1}, 0, 1));
        assertDoesNotThrow(() -> EditorCodec.encode(new Resync()));
    }
}
