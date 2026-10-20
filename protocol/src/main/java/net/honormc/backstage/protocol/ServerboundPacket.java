package net.honormc.backstage.protocol;

import java.util.Objects;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;

public sealed interface ServerboundPacket {
    record Hello(int protocol, String modVersion) implements ServerboundPacket {
        public Hello {
            Objects.requireNonNull(modVersion);
        }
    }

    record Resync() implements ServerboundPacket {
    }

    record UpsertMarker(String map, Marker marker) implements ServerboundPacket {
        public UpsertMarker {
            Objects.requireNonNull(map);
            Objects.requireNonNull(marker);
        }
    }

    record RemoveMarker(String map, MarkerType type, String id) implements ServerboundPacket {
        public RemoveMarker {
            Objects.requireNonNull(map);
            Objects.requireNonNull(type);
            Objects.requireNonNull(id);
        }
    }

    record RenameMarker(String map, MarkerType type, String from, String to) implements ServerboundPacket {
        public RenameMarker {
            Objects.requireNonNull(map);
            Objects.requireNonNull(type);
            Objects.requireNonNull(from);
            Objects.requireNonNull(to);
        }
    }
}
