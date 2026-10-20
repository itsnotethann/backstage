package net.honormc.backstage.protocol;

import java.util.List;
import java.util.Objects;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Registry;

public sealed interface ClientboundPacket {
    record Welcome(int protocol, String serverName) implements ClientboundPacket {
        public Welcome {
            Objects.requireNonNull(serverName);
        }
    }

    record Denied(DeniedReason reason, int serverProtocol) implements ClientboundPacket {
        public Denied {
            Objects.requireNonNull(reason);
        }
    }

    record RegistrySync(Registry registry) implements ClientboundPacket {
        public RegistrySync {
            Objects.requireNonNull(registry);
        }
    }

    record MapState(boolean bound, String map, boolean writable) implements ClientboundPacket {
        public MapState {
            Objects.requireNonNull(map);
        }
    }

    record SyncBegin(String map, long revision, int total) implements ClientboundPacket {
        public SyncBegin {
            Objects.requireNonNull(map);
        }
    }

    record SyncBatch(String map, List<Marker> markers) implements ClientboundPacket {
        public SyncBatch {
            Objects.requireNonNull(map);
            markers = List.copyOf(markers);
        }
    }

    record SyncEnd(String map) implements ClientboundPacket {
        public SyncEnd {
            Objects.requireNonNull(map);
        }
    }

    record MarkerChanged(String map, long revision, Marker marker) implements ClientboundPacket {
        public MarkerChanged {
            Objects.requireNonNull(map);
            Objects.requireNonNull(marker);
        }
    }

    record MarkerRemoved(String map, long revision, MarkerType type, String id) implements ClientboundPacket {
        public MarkerRemoved {
            Objects.requireNonNull(map);
            Objects.requireNonNull(type);
            Objects.requireNonNull(id);
        }
    }

    record EditRejected(String map, MarkerType type, String id, String reason) implements ClientboundPacket {
        public EditRejected {
            Objects.requireNonNull(map);
            Objects.requireNonNull(type);
            Objects.requireNonNull(id);
            Objects.requireNonNull(reason);
        }
    }
}
