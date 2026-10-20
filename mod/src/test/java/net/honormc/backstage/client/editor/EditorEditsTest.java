package net.honormc.backstage.client.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import net.honormc.backstage.protocol.ClientboundPacket;
import net.honormc.backstage.protocol.EditorCodec;
import net.honormc.backstage.protocol.EditorProtocol;
import net.honormc.backstage.protocol.ServerboundPacket;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import org.junit.jupiter.api.Test;

class EditorEditsTest {
    private final List<ServerboundPacket> sent = new ArrayList<>();
    private final List<String> chat = new ArrayList<>();
    private final EditorClient client = new EditorClient(new EditorClient.Output() {
        @Override
        public void send(byte[] body) {
            sent.add(EditorCodec.decodeServerbound(body));
        }

        @Override
        public void toast(String title, String message) {
        }

        @Override
        public void chat(String line) {
            chat.add(line);
        }
    }, "1.0.0");

    private static Point point(String id, String... tags) {
        return new Point(id, 1, 2, 3, 0, 0, List.of(tags), Map.of());
    }

    private void receive(ClientboundPacket packet) {
        client.receive(EditorCodec.encode(packet));
    }

    private void join(boolean writable) {
        client.channelsRegistered(List.of(EditorProtocol.CHANNEL));
        receive(new ClientboundPacket.Welcome(EditorProtocol.VERSION, "Build"));
        receive(new ClientboundPacket.MapState(true, "dev", writable));
        sent.clear();
    }

    @Test
    void editsAreRefusedWithoutAWritableMap() {
        assertEquals("The editor is not available on this server.",
                client.edit(new MarkerHistory.Change(null, point("a"))));
        client.channelsRegistered(List.of(EditorProtocol.CHANNEL));
        receive(new ClientboundPacket.Welcome(EditorProtocol.VERSION, "Build"));
        receive(new ClientboundPacket.MapState(false, "", false));
        assertEquals("No editable map here.", client.edit(new MarkerHistory.Change(null, point("a"))));
        receive(new ClientboundPacket.MapState(true, "dev", false));
        assertTrue(client.edit(new MarkerHistory.Change(null, point("a"))).contains("read-only"));
        assertEquals(1, sent.size());
    }

    @Test
    void editUndoAndRedoSendPacketsWithoutTouchingTheStore() {
        join(true);
        assertNull(client.edit(new MarkerHistory.Change(null, point("a"))));
        assertEquals(new ServerboundPacket.UpsertMarker("dev", point("a")), sent.getLast());
        assertEquals(0, client.store().count());

        receive(new ClientboundPacket.MarkerChanged("dev", 1, point("a")));
        assertNull(client.edit(new MarkerHistory.Change(point("a"), null)));
        assertEquals(new ServerboundPacket.RemoveMarker("dev", MarkerType.POINT, "a"), sent.getLast());
        assertEquals(1, client.store().count());

        assertNotNull(client.undo());
        assertEquals(new ServerboundPacket.UpsertMarker("dev", point("a")), sent.getLast());
        assertNotNull(client.redo());
        assertEquals(new ServerboundPacket.RemoveMarker("dev", MarkerType.POINT, "a"), sent.getLast());
        assertNull(client.redo());
        assertEquals("Nothing to redo.", client.lastStepProblem());
    }

    @Test
    void invalidEditsAreNotSentOrRecorded() {
        join(true);
        assertNotNull(client.edit(new MarkerHistory.Change(null, point("a", "Bad Tag".repeat(20)))));
        assertNotNull(client.edit(new MarkerHistory.Rename(MarkerType.POINT, "a", "Not Valid")));
        assertTrue(sent.isEmpty());
        assertNull(client.undo());
        assertEquals("Nothing to undo.", client.lastStepProblem());
    }

    @Test
    void rejectedEditIsDroppedFromHistory() {
        join(true);
        assertNull(client.edit(new MarkerHistory.Rename(MarkerType.POINT, "a", "b")));
        receive(new ClientboundPacket.EditRejected("dev", MarkerType.POINT, "a", "No point named a."));
        assertEquals("§cBackstage editor: No point named a.", chat.getLast());
        assertNull(client.undo());
    }

    @Test
    void historyClearsWhenTheMapChanges() {
        join(true);
        assertNull(client.edit(new MarkerHistory.Change(null, point("a"))));
        receive(new ClientboundPacket.MapState(true, "other", true));
        assertNull(client.undo());
        assertEquals("Nothing to undo.", client.lastStepProblem());
    }
}
