package net.honormc.backstage.client.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import net.honormc.backstage.Backstage;
import net.honormc.backstage.protocol.ClientboundPacket;
import net.honormc.backstage.protocol.DeniedReason;
import net.honormc.backstage.protocol.EditorCodec;
import net.honormc.backstage.protocol.EditorProtocol;
import net.honormc.backstage.protocol.ProtocolException;
import net.honormc.backstage.protocol.ServerboundPacket;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Registry;

public final class EditorClient {
    public interface Output {
        void send(byte[] body);

        void toast(String title, String message);

        void chat(String line);
    }

    private final Output out;
    private final String modVersion;
    private final MarkerStore store = new MarkerStore();
    private final MarkerHistory history = new MarkerHistory();
    private EditorState state = new EditorState.None();
    private boolean decodeFailureLogged;
    private String lastStepProblem;

    public EditorClient(Output out, String modVersion) {
        this.out = out;
        this.modVersion = modVersion;
    }

    public synchronized EditorState state() {
        return state;
    }

    public MarkerStore store() {
        return store;
    }

    public synchronized void channelsRegistered(Collection<String> channels) {
        boolean idle = state instanceof EditorState.None || state instanceof EditorState.Denied;
        if (idle && channels.contains(EditorProtocol.CHANNEL)) {
            state = new EditorState.Handshaking();
            out.send(EditorCodec.encode(new ServerboundPacket.Hello(EditorProtocol.VERSION, modVersion)));
        }
    }

    public synchronized void disconnected() {
        state = new EditorState.None();
        store.clear();
        history.clear();
        decodeFailureLogged = false;
    }

    public synchronized boolean resync() {
        if (!(state instanceof EditorState.Available)) {
            return false;
        }
        out.send(EditorCodec.encode(new ServerboundPacket.Resync()));
        return true;
    }

    public synchronized String editableMap() {
        if (!(state instanceof EditorState.Available available)) {
            return null;
        }
        return available.bound() && available.writable() ? available.map() : null;
    }

    public synchronized String cannotEdit() {
        if (!(state instanceof EditorState.Available available)) {
            return "The editor is not available on this server.";
        }
        if (!available.bound()) {
            return "No editable map here.";
        }
        if (!available.writable()) {
            return "Map " + available.map() + " is read-only.";
        }
        return null;
    }

    public synchronized String edit(MarkerHistory.Op op) {
        String problem = send(op);
        if (problem == null) {
            history.record(op);
        }
        return problem;
    }

    public synchronized MarkerHistory.Op undo() {
        return step(history.undo().orElse(null), "Nothing to undo.");
    }

    public synchronized MarkerHistory.Op redo() {
        return step(history.redo().orElse(null), "Nothing to redo.");
    }

    public synchronized String lastStepProblem() {
        return lastStepProblem;
    }

    private MarkerHistory.Op step(MarkerHistory.Op op, String empty) {
        lastStepProblem = cannotEdit();
        if (lastStepProblem != null) {
            if (op != null) {
                history.revert();
            }
            return null;
        }
        if (op == null) {
            lastStepProblem = empty;
            return null;
        }
        lastStepProblem = send(op);
        if (lastStepProblem != null) {
            history.revert();
            return null;
        }
        return op;
    }

    private String send(MarkerHistory.Op op) {
        String problem = cannotEdit();
        if (problem != null) {
            return problem;
        }
        ServerboundPacket packet = op.packet(editableMap());
        problem = MarkerCheck.packet(packet);
        if (problem != null) {
            return problem;
        }
        out.send(EditorCodec.encode(packet));
        return null;
    }

    public synchronized void receive(byte[] body) {
        if (state instanceof EditorState.None) {
            return;
        }
        ClientboundPacket packet;
        try {
            packet = EditorCodec.decodeClientbound(body);
        } catch (ProtocolException e) {
            if (!decodeFailureLogged) {
                decodeFailureLogged = true;
                Backstage.LOG.warn("Backstage editor: could not decode a server packet, editor disabled: {}", e.getMessage());
            }
            state = new EditorState.None();
            store.clear();
            history.clear();
            return;
        }
        switch (packet) {
            case ClientboundPacket.Welcome p -> welcome(p);
            case ClientboundPacket.Denied p -> denied(p);
            default -> {
                if (state instanceof EditorState.Available available) {
                    state = apply(available, packet);
                }
            }
        }
    }

    private void welcome(ClientboundPacket.Welcome welcome) {
        if (!(state instanceof EditorState.Handshaking)) {
            return;
        }
        store.clear();
        history.clear();
        state = new EditorState.Available(welcome.serverName(), "", false, false, 0, EditorState.EMPTY_REGISTRY);
        out.toast("Editor available", welcome.serverName());
    }

    private void denied(ClientboundPacket.Denied denied) {
        state = new EditorState.Denied(denied.reason(), denied.serverProtocol());
        store.clear();
        history.clear();
        switch (denied.reason()) {
            case NO_PERMISSION -> {
            }
            case CLIENT_OUTDATED -> out.chat("§eBackstage editor: this mod speaks protocol " + EditorProtocol.VERSION
                    + ", the server speaks " + denied.serverProtocol() + ". Update the mod.");
            case SERVER_OUTDATED -> out.chat("§eBackstage editor: this mod speaks protocol " + EditorProtocol.VERSION
                    + ", the server speaks " + denied.serverProtocol() + ". The server needs an update.");
            case REVOKED -> out.chat("§eBackstage editor: your editor access was revoked.");
        }
    }

    private EditorState.Available apply(EditorState.Available available, ClientboundPacket packet) {
        switch (packet) {
            case ClientboundPacket.RegistrySync p -> {
                return available.withRegistry(p.registry());
            }
            case ClientboundPacket.MapState p -> {
                if (!p.bound() || !p.map().equals(available.map())) {
                    store.clear();
                    history.clear();
                }
                return available.withMap(p.bound(), p.map(), p.writable()).withCount(store.count());
            }
            case ClientboundPacket.SyncBegin p -> {
                if (current(available, p.map())) {
                    store.begin(p.revision());
                }
            }
            case ClientboundPacket.SyncBatch p -> {
                if (current(available, p.map())) {
                    store.stage(p.markers());
                }
            }
            case ClientboundPacket.SyncEnd p -> {
                if (current(available, p.map())) {
                    store.end();
                }
            }
            case ClientboundPacket.MarkerChanged p -> {
                if (current(available, p.map())) {
                    store.changed(p.revision(), p.marker());
                }
            }
            case ClientboundPacket.MarkerRemoved p -> {
                if (current(available, p.map())) {
                    store.removed(p.revision(), p.type(), p.id());
                }
            }
            case ClientboundPacket.EditRejected p -> {
                history.rejected(p.type(), p.id());
                out.chat("§cBackstage editor: " + p.reason());
            }
            default -> {
            }
        }
        return available.withCount(store.count());
    }

    private static boolean current(EditorState.Available available, String map) {
        return available.bound() && available.map().equals(map);
    }

    public synchronized List<String> describe() {
        List<String> lines = new ArrayList<>();
        switch (state) {
            case EditorState.None s -> lines.add("§7Editor: §fnone§7 (this server has no Backstage editor)");
            case EditorState.Handshaking s -> lines.add("§7Editor: §fconnecting...");
            case EditorState.Denied s -> lines.add("§7Editor: §cdenied§7 (" + reason(s.reason()) + ", server protocol "
                    + s.serverProtocol() + ", mod protocol " + EditorProtocol.VERSION + ")");
            case EditorState.Available s -> {
                lines.add("§7Editor: §aavailable§7 on §f" + s.serverName());
                lines.add(s.bound()
                        ? "§7Map: §f" + s.map() + (s.writable() ? "§7 (writable)" : "§e (read-only)")
                        : "§7Map: §fno editable map here");
                lines.add("§7Markers: §f" + s.markerCount() + "§7 (points §f" + store.count(MarkerType.POINT)
                        + "§7, groups §f" + store.count(MarkerType.GROUP) + "§7, regions §f"
                        + store.count(MarkerType.REGION) + "§7), revision §f" + store.revision());
                Registry registry = s.registry();
                lines.add("§7Registry: §f" + registry.types().size() + "§7 types, §f" + registry.kinds().size()
                        + "§7 kinds, §f" + registry.lists().size() + "§7 lists");
            }
        }
        return lines;
    }

    private static String reason(DeniedReason reason) {
        return switch (reason) {
            case NO_PERMISSION -> "no permission";
            case CLIENT_OUTDATED -> "mod outdated";
            case SERVER_OUTDATED -> "server outdated";
            case REVOKED -> "revoked";
        };
    }
}
