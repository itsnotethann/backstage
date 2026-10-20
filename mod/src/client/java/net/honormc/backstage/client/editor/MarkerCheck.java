package net.honormc.backstage.client.editor;

import java.nio.charset.StandardCharsets;
import net.honormc.backstage.protocol.EditorCodec;
import net.honormc.backstage.protocol.EditorProtocol;
import net.honormc.backstage.protocol.ProtocolException;
import net.honormc.backstage.protocol.ServerboundPacket;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Prop;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;

public final class MarkerCheck {
    private MarkerCheck() {
    }

    public static String id(String id) {
        return EditorProtocol.isValidId(id) ? null
                : "'" + id + "' is not a valid id: use 1 to 64 of a-z, 0-9, _ . and -.";
    }

    public static String tag(String tag) {
        if (!EditorProtocol.isValidId(tag) || tag.getBytes(StandardCharsets.UTF_8).length > EditorProtocol.MAX_TAG_BYTES) {
            return "'" + tag + "' is not a valid tag: use 1 to " + EditorProtocol.MAX_TAG_BYTES + " of a-z, 0-9, _ . and -.";
        }
        return null;
    }

    public static String marker(Marker marker) {
        String problem = id(marker.id());
        if (problem != null) {
            return problem;
        }
        if (marker.tags().size() > EditorProtocol.MAX_TAGS) {
            return "A marker has at most " + EditorProtocol.MAX_TAGS + " tags.";
        }
        for (String tag : marker.tags()) {
            if (tag.getBytes(StandardCharsets.UTF_8).length > EditorProtocol.MAX_TAG_BYTES) {
                return "Tag '" + tag + "' is longer than " + EditorProtocol.MAX_TAG_BYTES + " bytes.";
            }
        }
        if (marker.props().size() > EditorProtocol.MAX_PROPS) {
            return "A marker has at most " + EditorProtocol.MAX_PROPS + " props.";
        }
        for (var entry : marker.props().entrySet()) {
            if (depth(entry.getValue()) > EditorProtocol.MAX_PROP_DEPTH) {
                return "Prop '" + entry.getKey() + "' nests lists deeper than " + EditorProtocol.MAX_PROP_DEPTH + ".";
            }
        }
        problem = switch (marker) {
            case Point p -> Double.isFinite(p.x()) && Double.isFinite(p.y()) && Double.isFinite(p.z())
                    && Float.isFinite(p.yaw()) && Float.isFinite(p.pitch()) ? null : "Point position is not finite.";
            case Group g -> g.blocks().length > EditorProtocol.MAX_GROUP_BLOCKS
                    ? "A group has at most " + EditorProtocol.MAX_GROUP_BLOCKS + " blocks, this one has "
                    + g.blocks().length + "." : null;
            case Region r -> shape(r.shape());
        };
        return problem;
    }

    public static String shape(Shape shape) {
        return switch (shape) {
            case Shape.Box b -> b.minX() > b.maxX() || b.minY() > b.maxY() || b.minZ() > b.maxZ()
                    ? "Box min is above max." : null;
            case Shape.Polygon p -> {
                if (p.vertices() < EditorProtocol.MIN_POLYGON_VERTICES || p.vertices() > EditorProtocol.MAX_POLYGON_VERTICES) {
                    yield "A polygon has " + EditorProtocol.MIN_POLYGON_VERTICES + " to "
                            + EditorProtocol.MAX_POLYGON_VERTICES + " vertices, this one has " + p.vertices() + ".";
                }
                yield p.minY() > p.maxY() ? "Polygon minY is above maxY." : null;
            }
        };
    }

    public static String packet(ServerboundPacket packet) {
        if (packet instanceof ServerboundPacket.UpsertMarker upsert) {
            String problem = marker(upsert.marker());
            if (problem != null) {
                return problem;
            }
        }
        if (packet instanceof ServerboundPacket.RenameMarker rename) {
            String problem = id(rename.to());
            if (problem != null) {
                return problem;
            }
        }
        try {
            EditorCodec.encode(packet);
        } catch (ProtocolException e) {
            return "That edit is too large to send: " + e.getMessage() + ".";
        }
        return null;
    }

    private static int depth(Prop prop) {
        if (!(prop instanceof Prop.ListProp list)) {
            return 0;
        }
        int deepest = 0;
        for (Prop value : list.values()) {
            deepest = Math.max(deepest, depth(value));
        }
        return deepest + 1;
    }
}
