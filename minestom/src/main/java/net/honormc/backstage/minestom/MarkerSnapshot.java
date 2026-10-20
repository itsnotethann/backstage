package net.honormc.backstage.minestom;

import java.util.ArrayList;
import java.util.List;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Region;

public record MarkerSnapshot(String map, long revision, List<Point> points, List<Group> groups, List<Region> regions) {
    public MarkerSnapshot {
        points = List.copyOf(points);
        groups = List.copyOf(groups);
        regions = List.copyOf(regions);
    }

    public static MarkerSnapshot empty(String map) {
        return new MarkerSnapshot(map, 0, List.of(), List.of(), List.of());
    }

    public List<Marker> all() {
        List<Marker> all = new ArrayList<>(size());
        all.addAll(points);
        all.addAll(groups);
        all.addAll(regions);
        return all;
    }

    public int size() {
        return points.size() + groups.size() + regions.size();
    }
}
