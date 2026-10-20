package net.honormc.backstage.protocol;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
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
import net.honormc.backstage.protocol.model.FieldType;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import org.junit.jupiter.api.Test;

class RoundTripTest {
    private static void roundTrip(ServerboundPacket packet) {
        byte[] body = EditorCodec.encode(packet);
        ServerboundPacket decoded = EditorCodec.decodeServerbound(body);
        assertEquals(packet, decoded);
        assertArrayEquals(body, EditorCodec.encode(decoded));
    }

    private static void roundTrip(ClientboundPacket packet) {
        byte[] body = EditorCodec.encode(packet);
        ClientboundPacket decoded = EditorCodec.decodeClientbound(body);
        assertEquals(packet, decoded);
        assertArrayEquals(body, EditorCodec.encode(decoded));
    }

    @Test
    void hello() {
        roundTrip(new Hello(EditorProtocol.VERSION, "1.0.0+26.2"));
        roundTrip(new Hello(0, ""));
        roundTrip(new Hello(Integer.MIN_VALUE, "x"));
    }

    @Test
    void resync() {
        roundTrip(new Resync());
        assertArrayEquals(new byte[]{1}, EditorCodec.encode(new Resync()));
    }

    @Test
    void upsertMarker() {
        for (Marker marker : Samples.allMarkers()) {
            roundTrip(new UpsertMarker("desert", marker));
            roundTrip(new UpsertMarker("", marker));
        }
    }

    @Test
    void removeMarker() {
        for (MarkerType type : MarkerType.values()) {
            roundTrip(new RemoveMarker("desert", type, "boss_spawn"));
            roundTrip(new RemoveMarker("", type, "a"));
        }
    }

    @Test
    void renameMarker() {
        for (MarkerType type : MarkerType.values()) {
            roundTrip(new RenameMarker("desert", type, "old.name", "new-name_2"));
            roundTrip(new RenameMarker("", type, "a", "b"));
        }
    }

    @Test
    void welcome() {
        roundTrip(new Welcome(EditorProtocol.VERSION, "Build"));
        roundTrip(new Welcome(0, ""));
    }

    @Test
    void denied() {
        for (DeniedReason reason : DeniedReason.values()) {
            roundTrip(new Denied(reason, EditorProtocol.VERSION));
            roundTrip(new Denied(reason, 0));
        }
    }

    @Test
    void registrySync() {
        roundTrip(new RegistrySync(Samples.fullRegistry()));
        roundTrip(new RegistrySync(Samples.emptyRegistry()));
    }

    @Test
    void mapState() {
        roundTrip(new MapState(true, "desert", true));
        roundTrip(new MapState(true, "desert", false));
        roundTrip(new MapState(false, "", false));
    }

    @Test
    void syncBegin() {
        roundTrip(new SyncBegin("desert", Long.MAX_VALUE, 12));
        roundTrip(new SyncBegin("desert", Long.MIN_VALUE, Integer.MAX_VALUE));
        roundTrip(new SyncBegin("", 0, 0));
    }

    @Test
    void syncBatch() {
        roundTrip(new SyncBatch("desert", Samples.allMarkers()));
        roundTrip(new SyncBatch("", List.of()));
    }

    @Test
    void syncEnd() {
        roundTrip(new SyncEnd("desert"));
        roundTrip(new SyncEnd(""));
    }

    @Test
    void markerChanged() {
        for (Marker marker : Samples.allMarkers()) {
            roundTrip(new MarkerChanged("desert", 12, marker));
            roundTrip(new MarkerChanged("", 0, marker));
        }
    }

    @Test
    void markerRemoved() {
        for (MarkerType type : MarkerType.values()) {
            roundTrip(new MarkerRemoved("desert", 13, type, "flag"));
            roundTrip(new MarkerRemoved("", 0, type, "a"));
        }
    }

    @Test
    void editRejected() {
        for (MarkerType type : MarkerType.values()) {
            roundTrip(new EditRejected("desert", type, "arena", "map is read-only"));
            roundTrip(new EditRejected("", type, "a", ""));
        }
    }

    @Test
    void packetIdsMatchSpec() {
        assertEquals(0, EditorCodec.encode(new Hello(1, ""))[0]);
        assertEquals(1, EditorCodec.encode(new Resync())[0]);
        assertEquals(2, EditorCodec.encode(new UpsertMarker("", Samples.emptyPoint()))[0]);
        assertEquals(3, EditorCodec.encode(new RemoveMarker("", MarkerType.POINT, "a"))[0]);
        assertEquals(4, EditorCodec.encode(new RenameMarker("", MarkerType.POINT, "a", "b"))[0]);
        assertEquals(0, EditorCodec.encode(new Welcome(1, ""))[0]);
        assertEquals(1, EditorCodec.encode(new Denied(DeniedReason.REVOKED, 1))[0]);
        assertEquals(2, EditorCodec.encode(new RegistrySync(Samples.emptyRegistry()))[0]);
        assertEquals(3, EditorCodec.encode(new MapState(false, "", false))[0]);
        assertEquals(4, EditorCodec.encode(new SyncBegin("", 0, 0))[0]);
        assertEquals(5, EditorCodec.encode(new SyncBatch("", List.of()))[0]);
        assertEquals(6, EditorCodec.encode(new SyncEnd(""))[0]);
        assertEquals(7, EditorCodec.encode(new MarkerChanged("", 0, Samples.emptyPoint()))[0]);
        assertEquals(8, EditorCodec.encode(new MarkerRemoved("", 0, MarkerType.POINT, "a"))[0]);
        assertEquals(9, EditorCodec.encode(new EditRejected("", MarkerType.POINT, "a", ""))[0]);
    }

    @Test
    void protocolConstants() {
        assertEquals("backstage:editor", EditorProtocol.CHANNEL);
        assertEquals(1, EditorProtocol.VERSION);
    }

    @Test
    void enumIdsMatchSpec() {
        assertEquals(List.of(0, 1, 2), List.of(MarkerType.POINT.id(), MarkerType.GROUP.id(), MarkerType.REGION.id()));
        assertEquals(List.of(0, 1, 2, 3), List.of(DeniedReason.NO_PERMISSION.id(), DeniedReason.CLIENT_OUTDATED.id(),
                DeniedReason.SERVER_OUTDATED.id(), DeniedReason.REVOKED.id()));
        int expected = 0;
        for (FieldType type : List.of(
                FieldType.POINT_REF,
                FieldType.GROUP_REF,
                FieldType.REGION_REF,
                FieldType.NUMBER,
                FieldType.STRING,
                FieldType.BOOL,
                FieldType.BLOCK,
                FieldType.ITEM,
                FieldType.CHOICE)) {
            assertEquals(expected++, type.id());
        }
    }
}
