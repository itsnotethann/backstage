package net.honormc.backstage.protocol.model;

import java.util.List;
import java.util.Map;

public sealed interface Marker permits Point, Group, Region {
    String id();

    List<String> tags();

    Map<String, Prop> props();

    MarkerType type();
}
