package net.honormc.backstage.client.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.honormc.backstage.protocol.ClientboundPacket;
import net.honormc.backstage.protocol.DeniedReason;
import net.honormc.backstage.protocol.EditorCodec;
import net.honormc.backstage.protocol.EditorProtocol;
import net.honormc.backstage.protocol.ServerboundPacket;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Registry;
import org.junit.jupiter.api.Test;

class EditorClientTest {
    private final List<ServerboundPacket> sent = new ArrayList<>();
    private final List<String> toasts = new ArrayList<>();
    private final List<String> chat = new ArrayList<>();
    private final EditorClient client = new EditorClient(new EditorClient.Output() {
        @Override
        public void send(byte[] body) {
            sent.add(EditorCodec.decodeServerbound(body));
        }

        @Override
        public void toast(String title, String message) {
            toasts.add(title + ": " + message);
        }

        @Override
        public void chat(String line) {
            chat.add(line);
        }
    }, "1.0.0");

    private static Point point(String id) {
        return new Point(id, 1, 2, 3, 0, 0, List.of(), Map.of());
    }

    private void receive(ClientboundPacket packet) {
        client.receive(EditorCodec.encode(packet));
    }

    private void welcome() {
        client.channelsRegistered(List.of("minecraft:brand", EditorProtocol.CHANNEL));
        receive(new ClientboundPacket.Welcome(EditorProtocol.VERSION, "Build"));
    }

    @Test
    void helloOnlyAfterServerRegistersTheChannel() {
        client.channelsRegistered(List.of("minecraft:brand", "backstage:edit_chunk"));
        assertTrue(sent.isEmpty());
        assertInstanceOf(EditorState.None.class, client.state());

        client.channelsRegistered(List.of(EditorProtocol.CHANNEL));
        assertEquals(List.of(new ServerboundPacket.Hello(EditorProtocol.VERSION, "1.0.0")), sent);
        assertInstanceOf(EditorState.Handshaking.class, client.state());

        client.channelsRegistered(List.of(EditorProtocol.CHANNEL));
        assertEquals(1, sent.size());
    }

    @Test
    void packetsBeforeHelloAreIgnored() {
        receive(new ClientboundPacket.Welcome(EditorProtocol.VERSION, "Build"));
        assertInstanceOf(EditorState.None.class, client.state());
        assertTrue(toasts.isEmpty());
    }

    @Test
    void welcomeShowsToastAndSyncSwapsAtEnd() {
        welcome();
        assertEquals(List.of("Editor available: Build"), toasts);
        Registry registry = new Registry(List.of(MarkerType.POINT), List.of(), List.of());
        receive(new ClientboundPacket.RegistrySync(registry));
        receive(new ClientboundPacket.MapState(true, "desert", true));
        receive(new ClientboundPacket.SyncBegin("desert", 5, 2));
        receive(new ClientboundPacket.SyncBatch("desert", List.of(point("a"))));
        receive(new ClientboundPacket.SyncBatch("desert", List.of(new Group("g", new long[]{1}, List.of(), Map.of()))));
        assertEquals(0, client.store().count());
        receive(new ClientboundPacket.SyncEnd("desert"));

        EditorState.Available state = assertInstanceOf(EditorState.Available.class, client.state());
        assertEquals("Build", state.serverName());
        assertEquals("desert", state.map());
        assertTrue(state.bound());
        assertTrue(state.writable());
        assertEquals(2, state.markerCount());
        assertEquals(registry, state.registry());
        assertEquals(5, client.store().revision());
    }

    @Test
    void changesPatchTheStoreForTheCurrentMapOnly() {
        welcome();
        receive(new ClientboundPacket.MapState(true, "desert", true));
        receive(new ClientboundPacket.SyncBegin("desert", 0, 0));
        receive(new ClientboundPacket.SyncEnd("desert"));
        receive(new ClientboundPacket.MarkerChanged("desert", 1, point("a")));
        receive(new ClientboundPacket.MarkerChanged("desert", 2, point("b")));
        receive(new ClientboundPacket.MarkerChanged("other", 3, point("c")));
        receive(new ClientboundPacket.MarkerRemoved("desert", 4, MarkerType.POINT, "a"));
        assertEquals(1, ((EditorState.Available) client.state()).markerCount());
        assertEquals(Map.of("b", point("b")), client.store().markers(MarkerType.POINT));
        assertEquals(4, client.store().revision());

        receive(new ClientboundPacket.MapState(false, "", false));
        EditorState.Available state = (EditorState.Available) client.state();
        assertFalse(state.bound());
        assertEquals(0, state.markerCount());
        assertTrue(client.describe().contains("§7Map: §fno editable map here"));
    }

    @Test
    void changesDuringSyncSurviveTheSwap() {
        welcome();
        receive(new ClientboundPacket.MapState(true, "desert", true));
        receive(new ClientboundPacket.SyncBegin("desert", 3, 1));
        receive(new ClientboundPacket.SyncBatch("desert", List.of(point("a"))));
        receive(new ClientboundPacket.MarkerChanged("desert", 4, point("b")));
        receive(new ClientboundPacket.SyncEnd("desert"));
        assertEquals(2, client.store().count());
    }

    @Test
    void deniedHandlingPerReason() {
        client.channelsRegistered(List.of(EditorProtocol.CHANNEL));
        receive(new ClientboundPacket.Denied(DeniedReason.NO_PERMISSION, EditorProtocol.VERSION));
        assertEquals(new EditorState.Denied(DeniedReason.NO_PERMISSION, EditorProtocol.VERSION), client.state());
        assertTrue(chat.isEmpty());
        assertTrue(toasts.isEmpty());
        assertTrue(client.describe().getFirst().contains("denied"));

        client.disconnected();
        client.channelsRegistered(List.of(EditorProtocol.CHANNEL));
        receive(new ClientboundPacket.Denied(DeniedReason.CLIENT_OUTDATED, 7));
        assertEquals(1, chat.size());
        assertTrue(chat.getFirst().contains(String.valueOf(EditorProtocol.VERSION)) && chat.getFirst().contains("7"));

        client.disconnected();
        client.channelsRegistered(List.of(EditorProtocol.CHANNEL));
        receive(new ClientboundPacket.Denied(DeniedReason.SERVER_OUTDATED, 0));
        assertEquals(2, chat.size());
        assertTrue(chat.get(1).contains("0"));

        client.disconnected();
        welcome();
        receive(new ClientboundPacket.Denied(DeniedReason.REVOKED, EditorProtocol.VERSION));
        assertInstanceOf(EditorState.Denied.class, client.state());
        assertEquals(3, chat.size());
    }

    @Test
    void readvertisingAfterDenialRetriesHello() {
        client.channelsRegistered(List.of(EditorProtocol.CHANNEL));
        receive(new ClientboundPacket.Denied(DeniedReason.NO_PERMISSION, EditorProtocol.VERSION));
        client.channelsRegistered(List.of(EditorProtocol.CHANNEL));
        assertEquals(2, sent.size());
        receive(new ClientboundPacket.Welcome(EditorProtocol.VERSION, "Build"));
        assertInstanceOf(EditorState.Available.class, client.state());
    }

    @Test
    void disconnectResetsEverything() {
        welcome();
        receive(new ClientboundPacket.MapState(true, "desert", true));
        receive(new ClientboundPacket.MarkerChanged("desert", 1, point("a")));
        client.disconnected();
        assertInstanceOf(EditorState.None.class, client.state());
        assertEquals(0, client.store().count());
        assertFalse(client.resync());
    }

    @Test
    void resyncOnlyWhenAvailable() {
        assertFalse(client.resync());
        welcome();
        assertTrue(client.resync());
        assertEquals(new ServerboundPacket.Resync(), sent.getLast());
    }

    @Test
    void decodeFailureResetsWithoutThrowing() {
        welcome();
        receive(new ClientboundPacket.MapState(true, "desert", true));
        client.receive(new byte[]{99});
        assertInstanceOf(EditorState.None.class, client.state());
        assertEquals(0, client.store().count());
        client.receive(new byte[]{(byte) 0x80});
        assertInstanceOf(EditorState.None.class, client.state());
    }
}
