package net.honormc.backstage.client.editor;

import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.Map;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;

public final class MarkerStore {
    private final Map<MarkerType, Map<String, Marker>> current = empty();
    private Map<MarkerType, Map<String, Marker>> staged;
    private long revision;
    private long stagedRevision;

    private static Map<MarkerType, Map<String, Marker>> empty() {
        Map<MarkerType, Map<String, Marker>> markers = new EnumMap<>(MarkerType.class);
        for (MarkerType type : MarkerType.values()) {
            markers.put(type, new LinkedHashMap<>());
        }
        return markers;
    }

    void begin(long revision) {
        staged = empty();
        stagedRevision = revision;
    }

    void stage(Iterable<Marker> markers) {
        if (staged == null) {
            return;
        }
        for (Marker marker : markers) {
            staged.get(marker.type()).put(marker.id(), marker);
        }
    }

    boolean end() {
        if (staged == null) {
            return false;
        }
        for (MarkerType type : MarkerType.values()) {
            current.get(type).clear();
            current.get(type).putAll(staged.get(type));
        }
        revision = stagedRevision;
        staged = null;
        return true;
    }

    void changed(long revision, Marker marker) {
        current.get(marker.type()).put(marker.id(), marker);
        if (staged != null) {
            staged.get(marker.type()).put(marker.id(), marker);
        }
        this.revision = revision;
    }

    void removed(long revision, MarkerType type, String id) {
        current.get(type).remove(id);
        if (staged != null) {
            staged.get(type).remove(id);
        }
        this.revision = revision;
    }

    void clear() {
        for (Map<String, Marker> markers : current.values()) {
            markers.clear();
        }
        staged = null;
        revision = 0;
    }

    public int count() {
        int count = 0;
        for (Map<String, Marker> markers : current.values()) {
            count += markers.size();
        }
        return count;
    }

    public int count(MarkerType type) {
        return current.get(type).size();
    }

    public Map<String, Marker> markers(MarkerType type) {
        return Map.copyOf(current.get(type));
    }

    public long revision() {
        return revision;
    }
}
