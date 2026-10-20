package net.honormc.backstage.minestom;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.TreeMap;
import net.honormc.backstage.protocol.EditorProtocol;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Region;

public final class MapMarkers {
    interface Listener {
        Listener NONE = new Listener() {
            @Override
            public void changed(MapMarkers markers, long revision, Marker marker) {
            }

            @Override
            public void removed(MapMarkers markers, long revision, MarkerType type, String id) {
            }
        };

        void changed(MapMarkers markers, long revision, Marker marker);

        void removed(MapMarkers markers, long revision, MarkerType type, String id);
    }

    private final String map;
    private final boolean writable;
    private final Map<MarkerType, TreeMap<String, Marker>> byType = new EnumMap<>(MarkerType.class);
    private long revision;
    private volatile Listener listener = Listener.NONE;

    MapMarkers(MarkerSnapshot snapshot, boolean writable) {
        this.map = snapshot.map();
        this.writable = writable;
        this.revision = snapshot.revision();
        for (MarkerType type : MarkerType.values()) {
            byType.put(type, new TreeMap<>());
        }
        for (Marker marker : snapshot.all()) {
            byType.get(marker.type()).put(marker.id(), marker);
        }
    }

    void listener(Listener listener) {
        this.listener = listener;
    }

    public String map() {
        return map;
    }

    public boolean writable() {
        return writable;
    }

    public synchronized long revision() {
        return revision;
    }

    public synchronized List<Point> points() {
        return byType.get(MarkerType.POINT).values().stream().map(Point.class::cast).toList();
    }

    public synchronized List<Group> groups() {
        return byType.get(MarkerType.GROUP).values().stream().map(Group.class::cast).toList();
    }

    public synchronized List<Region> regions() {
        return byType.get(MarkerType.REGION).values().stream().map(Region.class::cast).toList();
    }

    public synchronized Optional<Marker> get(MarkerType type, String id) {
        return Optional.ofNullable(byType.get(type).get(id));
    }

    public synchronized MarkerSnapshot snapshot() {
        return new MarkerSnapshot(map, revision, points(), groups(), regions());
    }

    public synchronized long upsert(Marker marker) {
        checkWritable();
        checkId(marker.id());
        byType.get(marker.type()).put(marker.id(), marker);
        revision++;
        listener.changed(this, revision, marker);
        return revision;
    }

    public synchronized boolean remove(MarkerType type, String id) {
        checkWritable();
        if (byType.get(type).remove(id) == null) {
            return false;
        }
        revision++;
        listener.removed(this, revision, type, id);
        return true;
    }

    public synchronized boolean rename(MarkerType type, String from, String to) {
        checkWritable();
        checkId(to);
        TreeMap<String, Marker> markers = byType.get(type);
        if (!markers.containsKey(from) || markers.containsKey(to)) {
            return false;
        }
        Marker renamed = withId(markers.remove(from), to);
        markers.put(to, renamed);
        revision++;
        listener.removed(this, revision, type, from);
        listener.changed(this, revision, renamed);
        return true;
    }

    private void checkWritable() {
        if (!writable) {
            throw new IllegalStateException("markers of map '" + map + "' are read-only");
        }
    }

    private static void checkId(String id) {
        if (!EditorProtocol.isValidId(id)) {
            throw new IllegalArgumentException("invalid marker id '" + id + "'");
        }
    }

    private static Marker withId(Marker marker, String id) {
        return switch (marker) {
            case Point p -> new Point(id, p.x(), p.y(), p.z(), p.yaw(), p.pitch(), p.tags(), p.props());
            case Group g -> new Group(id, g.blocks(), g.tags(), g.props());
            case Region r -> new Region(id, r.shape(), r.tags(), r.props());
        };
    }
}
