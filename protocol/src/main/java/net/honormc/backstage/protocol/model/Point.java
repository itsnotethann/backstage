package net.honormc.backstage.protocol.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Point(String id, double x, double y, double z, float yaw, float pitch, List<String> tags,
                    Map<String, Prop> props) implements Marker {
    public Point {
        Objects.requireNonNull(id);
        tags = List.copyOf(tags);
        props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
    }

    @Override
    public MarkerType type() {
        return MarkerType.POINT;
    }
}
