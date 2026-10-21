package net.honormc.backstage.client.editor;

import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
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

    public static boolean matches(Marker marker, String query, Set<String> tags) {
        if (!marker.tags().containsAll(tags)) {
            return false;
        }
        String q = query.trim().toLowerCase(Locale.ROOT);
        if (q.isEmpty()) {
            return true;
        }
        if (marker.id().contains(q)) {
            return true;
        }
        for (String tag : marker.tags()) {
            if (tag.contains(q)) {
                return true;
            }
        }
        return false;
    }
}
