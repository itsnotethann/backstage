package net.honormc.backstage.client.editor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Prop;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;

public final class MarkerEdits {
    private MarkerEdits() {
    }

    public static Marker withTags(Marker marker, List<String> tags) {
        return switch (marker) {
            case Point p -> new Point(p.id(), p.x(), p.y(), p.z(), p.yaw(), p.pitch(), tags, p.props());
            case Group g -> new Group(g.id(), g.blocks(), tags, g.props());
            case Region r -> new Region(r.id(), r.shape(), tags, r.props());
        };
    }

    public static Marker withProps(Marker marker, Map<String, Prop> props) {
        return switch (marker) {
            case Point p -> new Point(p.id(), p.x(), p.y(), p.z(), p.yaw(), p.pitch(), p.tags(), props);
            case Group g -> new Group(g.id(), g.blocks(), g.tags(), props);
            case Region r -> new Region(r.id(), r.shape(), r.tags(), props);
        };
    }

    public static Marker withId(Marker marker, String id) {
        return switch (marker) {
            case Point p -> new Point(id, p.x(), p.y(), p.z(), p.yaw(), p.pitch(), p.tags(), p.props());
            case Group g -> new Group(id, g.blocks(), g.tags(), g.props());
            case Region r -> new Region(id, r.shape(), r.tags(), r.props());
        };
    }

    public static Marker addTag(Marker marker, String tag) {
        if (marker.tags().contains(tag)) {
            return marker;
        }
        List<String> tags = new ArrayList<>(marker.tags());
        tags.add(tag);
        return withTags(marker, tags);
    }

    public static Marker removeTag(Marker marker, String tag) {
        List<String> tags = new ArrayList<>(marker.tags());
        tags.remove(tag);
        return withTags(marker, tags);
    }

    public static Marker setProp(Marker marker, String key, Prop value) {
        Map<String, Prop> props = new LinkedHashMap<>(marker.props());
        props.put(key, value);
        return withProps(marker, props);
    }

    public static Marker removeProp(Marker marker, String key) {
        Map<String, Prop> props = new LinkedHashMap<>(marker.props());
        props.remove(key);
        return withProps(marker, props);
    }

    public static Point moved(Point point, double x, double y, double z, float yaw) {
        return new Point(point.id(), x, y, z, yaw, point.pitch(), point.tags(), point.props());
    }

    public static Group withBlocks(Group group, long[] blocks) {
        return new Group(group.id(), blocks, group.tags(), group.props());
    }

    public static Region withShape(Region region, Shape shape) {
        return new Region(region.id(), shape, region.tags(), region.props());
    }

    public static Shape withHeight(Shape shape, int minY, int maxY) {
        return switch (shape) {
            case Shape.Box b -> new Shape.Box(b.minX(), minY, b.minZ(), b.maxX(), maxY, b.maxZ());
            case Shape.Polygon p -> new Shape.Polygon(p.xz(), minY, maxY);
        };
    }
}
