package net.honormc.backstage.protocol.model;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

public record Group(String id, long[] blocks, List<String> tags, Map<String, Prop> props) implements Marker {
    public Group {
        Objects.requireNonNull(id);
        blocks = blocks.clone();
        tags = List.copyOf(tags);
        props = Collections.unmodifiableMap(new LinkedHashMap<>(props));
    }

    @Override
    public long[] blocks() {
        return blocks.clone();
    }

    @Override
    public MarkerType type() {
        return MarkerType.GROUP;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof Group g && id.equals(g.id) && Arrays.equals(blocks, g.blocks) && tags.equals(g.tags)
                && props.equals(g.props);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, Arrays.hashCode(blocks), tags, props);
    }

    @Override
    public String toString() {
        return "Group[id=" + id + ", blocks=" + Arrays.toString(blocks) + ", tags=" + tags + ", props=" + props + "]";
    }
}
