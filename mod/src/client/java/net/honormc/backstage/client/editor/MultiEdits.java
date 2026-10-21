package net.honormc.backstage.client.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.honormc.backstage.protocol.BlockPosPacking;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;

public final class MultiEdits {
    private static final Pattern NUMBERED = Pattern.compile("(.+)_\\d+");

    private MultiEdits() {
    }

    public static List<String> sharedTags(Collection<? extends Marker> markers) {
        if (markers.isEmpty()) {
            return List.of();
        }
        Set<String> shared = null;
        for (Marker marker : markers) {
            if (shared == null) {
                shared = new LinkedHashSet<>(marker.tags());
            } else {
                shared.retainAll(marker.tags());
            }
        }
        return List.copyOf(shared);
    }

    public static List<MarkerHistory.Change> addTag(Collection<? extends Marker> markers, String tag) {
        List<MarkerHistory.Change> changes = new ArrayList<>();
        for (Marker marker : markers) {
            if (!marker.tags().contains(tag)) {
                changes.add(new MarkerHistory.Change(marker, MarkerEdits.addTag(marker, tag)));
            }
        }
        return changes;
    }

    public static List<MarkerHistory.Change> removeTag(Collection<? extends Marker> markers, String tag) {
        List<MarkerHistory.Change> changes = new ArrayList<>();
        for (Marker marker : markers) {
            if (marker.tags().contains(tag)) {
                changes.add(new MarkerHistory.Change(marker, MarkerEdits.removeTag(marker, tag)));
            }
        }
        return changes;
    }

    public static List<Marker> duplicate(Collection<? extends Marker> markers, Collection<String> taken) {
        Set<String> used = new HashSet<>(taken);
        List<Marker> copies = new ArrayList<>();
        for (Marker marker : markers) {
            Matcher matcher = NUMBERED.matcher(marker.id());
            String base = matcher.matches() ? matcher.group(1) : marker.id();
            String id = MarkerIds.next(base, used);
            used.add(id);
            copies.add(MarkerEdits.withId(marker, id));
        }
        return copies;
    }

    public static Marker translated(Marker marker, double dx, double dy, double dz) {
        return switch (marker) {
            case Point p -> MarkerEdits.moved(p, p.x() + dx, p.y() + dy, p.z() + dz, p.yaw());
            case Group g -> {
                int ix = (int) Math.rint(dx);
                int iy = (int) Math.rint(dy);
                int iz = (int) Math.rint(dz);
                long[] blocks = g.blocks().clone();
                for (int i = 0; i < blocks.length; i++) {
                    blocks[i] = BlockPosPacking.pack(BlockPosPacking.x(blocks[i]) + ix, BlockPosPacking.y(blocks[i]) + iy,
                            BlockPosPacking.z(blocks[i]) + iz);
                }
                yield MarkerEdits.withBlocks(g, blocks);
            }
            case Region r -> MarkerEdits.withShape(r, translated(r.shape(), (int) Math.rint(dx), (int) Math.rint(dy),
                    (int) Math.rint(dz)));
        };
    }

    public static Shape translated(Shape shape, int dx, int dy, int dz) {
        return switch (shape) {
            case Shape.Box b -> new Shape.Box(b.minX() + dx, b.minY() + dy, b.minZ() + dz, b.maxX() + dx, b.maxY() + dy,
                    b.maxZ() + dz);
            case Shape.Polygon p -> {
                int[] xz = p.xz().clone();
                for (int i = 0; i < xz.length; i += 2) {
                    xz[i] += dx;
                    xz[i + 1] += dz;
                }
                yield new Shape.Polygon(xz, p.minY() + dy, p.maxY() + dy);
            }
        };
    }
}
