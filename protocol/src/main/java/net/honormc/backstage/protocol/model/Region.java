package net.honormc.backstage.protocol.model;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Region(String id, Shape shape, List<String> tags, Map<String, Prop> props) implements Marker {
    public Region {
        Objects.requireNonNull(id);
        Objects.requireNonNull(shape);
        tags = List.copyOf(tags);
        props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
    }

    @Override
    public MarkerType type() {
        return MarkerType.REGION;
    }
}
