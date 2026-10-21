package net.honormc.backstage.client.editor;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.honormc.backstage.protocol.BlockPosPacking;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Prop;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;
import net.minecraft.core.BlockPos;

public final class MarkerText {
    private MarkerText() {
    }

    public static String type(MarkerType type) {
        return type.name().toLowerCase(Locale.ROOT);
    }

    public static String xyz(double x, double y, double z) {
        return String.format(Locale.ROOT, "%.2f %.2f %.2f", x, y, z);
    }

    public static String xyz(BlockPos pos) {
        return pos.getX() + " " + pos.getY() + " " + pos.getZ();
    }

    public static String shape(Shape shape) {
        return switch (shape) {
            case Shape.Box b -> "box " + b.minX() + " " + b.minY() + " " + b.minZ() + " to " + b.maxX() + " "
                    + b.maxY() + " " + b.maxZ();
            case Shape.Polygon p -> "polygon of " + p.vertices() + " vertices, Y " + p.minY() + " to " + p.maxY();
        };
    }

    public static String summary(Marker marker) {
        String detail = switch (marker) {
            case Point p -> " at " + xyz(p.x(), p.y(), p.z());
            case Group g -> " (" + g.blocks().length + " blocks)";
            case Region r -> " (" + shape(r.shape()) + ")";
        };
        return type(marker.type()) + " §f" + marker.id() + "§7" + detail;
    }

    public static String op(MarkerHistory.Op op) {
        return switch (op) {
            case MarkerHistory.Change c when c.before() == null -> "create " + type(c.type()) + " " + c.id();
            case MarkerHistory.Change c when c.after() == null -> "delete " + type(c.type()) + " " + c.id();
            case MarkerHistory.Change c -> "change " + type(c.type()) + " " + c.id();
            case MarkerHistory.Rename r -> "rename " + type(r.type()) + " " + r.from() + " to " + r.to();
        };
    }

    public static double[] anchor(Marker marker) {
        return switch (marker) {
            case Point p -> new double[]{p.x(), p.y(), p.z()};
            case Group g -> {
                long[] blocks = g.blocks();
                if (blocks.length == 0) {
                    yield null;
                }
                yield new double[]{BlockPosPacking.x(blocks[0]) + 0.5, BlockPosPacking.y(blocks[0]) + 1,
                        BlockPosPacking.z(blocks[0]) + 0.5};
            }
            case Region r -> MarkerPick.centre(r.shape());
        };
    }

    public static double[] centre(Marker marker) {
        return switch (marker) {
            case Point p -> new double[]{p.x(), p.y() + 0.15, p.z()};
            case Group g -> {
                long[] blocks = g.blocks();
                if (blocks.length == 0) {
                    yield null;
                }
                double sx = 0;
                double sy = 0;
                double sz = 0;
                for (long block : blocks) {
                    sx += BlockPosPacking.x(block) + 0.5;
                    sy += BlockPosPacking.y(block) + 0.5;
                    sz += BlockPosPacking.z(block) + 0.5;
                }
                yield new double[]{sx / blocks.length, sy / blocks.length, sz / blocks.length};
            }
            case Region r -> MarkerPick.centre(r.shape());
        };
    }

    public static List<String> details(Marker marker) {
        List<String> lines = new ArrayList<>();
        lines.add("§6" + type(marker.type()) + " §f" + marker.id());
        switch (marker) {
            case Point p -> lines.add("§7Position: §f" + xyz(p.x(), p.y(), p.z()) + "§7, yaw §f"
                    + String.format(Locale.ROOT, "%.1f", p.yaw()) + "§7, pitch §f" + String.format(Locale.ROOT, "%.1f", p.pitch()));
            case Group g -> {
                long[] blocks = g.blocks();
                lines.add("§7Blocks: §f" + blocks.length);
                if (blocks.length > 0) {
                    int minX = Integer.MAX_VALUE;
                    int minY = Integer.MAX_VALUE;
                    int minZ = Integer.MAX_VALUE;
                    int maxX = Integer.MIN_VALUE;
                    int maxY = Integer.MIN_VALUE;
                    int maxZ = Integer.MIN_VALUE;
                    for (long block : blocks) {
                        minX = Math.min(minX, BlockPosPacking.x(block));
                        minY = Math.min(minY, BlockPosPacking.y(block));
                        minZ = Math.min(minZ, BlockPosPacking.z(block));
                        maxX = Math.max(maxX, BlockPosPacking.x(block));
                        maxY = Math.max(maxY, BlockPosPacking.y(block));
                        maxZ = Math.max(maxZ, BlockPosPacking.z(block));
                    }
                    lines.add("§7Bounds: §f" + minX + " " + minY + " " + minZ + "§7 to §f" + maxX + " " + maxY + " " + maxZ);
                }
            }
            case Region r -> lines.add("§7Shape: §f" + shape(r.shape()));
        }
        lines.add("§7Tags: §f" + (marker.tags().isEmpty() ? "none" : String.join(", ", marker.tags())));
        if (marker.props().isEmpty()) {
            lines.add("§7Props: §fnone");
        } else {
            lines.add("§7Props (" + marker.props().size() + "):");
            for (Map.Entry<String, Prop> entry : marker.props().entrySet()) {
                lines.add("§7  " + entry.getKey() + " = §f" + PropParser.describe(entry.getValue()));
            }
        }
        return lines;
    }
}
