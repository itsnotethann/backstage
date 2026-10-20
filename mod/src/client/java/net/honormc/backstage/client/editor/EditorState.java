package net.honormc.backstage.client.editor;

import java.util.List;
import net.honormc.backstage.protocol.DeniedReason;
import net.honormc.backstage.protocol.model.Registry;

public sealed interface EditorState {
    Registry EMPTY_REGISTRY = new Registry(List.of(), List.of(), List.of());

    record None() implements EditorState {
    }

    record Handshaking() implements EditorState {
    }

    record Available(String serverName, String map, boolean bound, boolean writable, int markerCount,
                     Registry registry) implements EditorState {
        Available withMap(boolean bound, String map, boolean writable) {
            return new Available(serverName, map, bound, writable, markerCount, registry);
        }

        Available withCount(int markerCount) {
            return new Available(serverName, map, bound, writable, markerCount, registry);
        }

        Available withRegistry(Registry registry) {
            return new Available(serverName, map, bound, writable, markerCount, registry);
        }
    }

    record Denied(DeniedReason reason, int serverProtocol) implements EditorState {
    }
}
