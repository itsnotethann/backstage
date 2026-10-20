package net.honormc.backstage.client.editor;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;

public final class Outliner {
    private Outliner() {
    }

    public static MarkerType type(String plural) {
        return switch (plural) {
            case "points" -> MarkerType.POINT;
            case "groups" -> MarkerType.GROUP;
            case "regions" -> MarkerType.REGION;
            default -> null;
        };
    }

    public static List<Marker> filter(Collection<Marker> markers, MarkerType type, String tag) {
        return markers.stream()
                .filter(marker -> type == null || marker.type() == type)
                .filter(marker -> tag == null || marker.tags().contains(tag))
                .sorted(Comparator.comparingInt((Marker marker) -> marker.type().id()).thenComparing(Marker::id))
                .toList();
    }
}
