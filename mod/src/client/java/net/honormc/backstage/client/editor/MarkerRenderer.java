package net.honormc.backstage.client.editor;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import net.honormc.backstage.Backstage;
import net.honormc.backstage.client.editor.EditorTools.Selection;
import net.honormc.backstage.protocol.BlockPosPacking;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MarkerRenderer {
    public static final int DEFAULT_RADIUS = 96;
    public static final int GROUP_CAP = 1024;
    public static final int TOTAL_GROUP_CAP = 4096;

    static final int POINT_COLOUR = 0x55FF55;
    static final int GROUP_COLOUR = 0xFFAA00;
    static final int REGION_COLOUR = 0x55FFFF;
    static final int DRAFT_COLOUR = 0xFF55FF;
    static final int SELECTED_COLOUR = 0xFFFF55;

    private static final int EVERY_TICKS = 5;
    private static final double MOVE_EVALUATE = 4;
    private static final double MOVE_REBUILD = 16;
    private static final float EDGE = 0.06f;
    private static final float POINT_SIZE = 0.3f;
    private static final int FIRST_ID = -1_000_000;

    public enum Overlay {
        POINTS,
        GROUPS,
        REGIONS,
        LABELS
    }

    private record Source(Object marker, boolean selected, boolean labels) {
    }

    private record Built(Source source, List<Entity> entities, Vec3 anchor, boolean clipped, int blocks) {
    }

    private record DraftSource(String id, long version) {
    }

    private final Map<Selection, Built> built = new HashMap<>();
    private final Map<Overlay, Boolean> overlays = new LinkedHashMap<>();
    private int radius = DEFAULT_RADIUS;
    private ClientLevel level;
    private String map;
    private long seenStore = -1;
    private long seenTools = -1;
    private Vec3 evaluatedAt;
    private int nextId = FIRST_ID;
    private int ticks;
    private boolean capLogged;
    private boolean dirty;

    public MarkerRenderer() {
        for (Overlay overlay : Overlay.values()) {
            overlays.put(overlay, true);
        }
    }

    public boolean shown(Overlay overlay) {
        return overlays.get(overlay);
    }

    public void show(Overlay overlay, boolean on) {
        overlays.put(overlay, on);
        dirty = true;
    }

    public int radius() {
        return radius;
    }

    public void radius(int radius) {
        this.radius = radius;
        dirty = true;
    }

    public int entityCount() {
        int count = 0;
        for (Built entry : built.values()) {
            count += entry.entities().size();
        }
        return count;
    }

    public void tick(Minecraft mc, EditorClient editor, EditorTools tools) {
        if (++ticks % EVERY_TICKS != 0) {
            return;
        }
        String current = editor.state() instanceof EditorState.Available a && a.bound() ? a.map() : null;
        if (mc.level == null || mc.player == null || current == null) {
            clear();
            level = null;
            map = null;
            return;
        }
        if (mc.level != level || !current.equals(map)) {
            clear();
            level = mc.level;
            map = current;
        }
        Vec3 eye = mc.player.position();
        MarkerStore store = editor.store();
        boolean moved = evaluatedAt == null || evaluatedAt.distanceToSqr(eye) > MOVE_EVALUATE * MOVE_EVALUATE;
        if (!dirty && !moved && store.version() == seenStore && tools.revision() == seenTools) {
            return;
        }
        dirty = false;
        seenStore = store.version();
        seenTools = tools.revision();
        evaluatedAt = eye;
        evaluate(store, tools, eye);
    }

    public void clear() {
        for (Built entry : built.values()) {
            discard(entry);
        }
        built.clear();
        seenStore = -1;
        seenTools = -1;
        evaluatedAt = null;
        capLogged = false;
    }

    private void evaluate(MarkerStore store, EditorTools tools, Vec3 eye) {
        double r2 = (double) radius * radius;
        Selection selected = tools.selected();
        Selection forced = tools.forced();
        boolean labels = shown(Overlay.LABELS);
        Map<Selection, Source> wanted = new LinkedHashMap<>();
        GroupDraft draft = tools.draft();

        for (Point point : store.all(MarkerType.POINT, Point.class)) {
            Selection key = new Selection(MarkerType.POINT, point.id());
            boolean force = key.equals(forced);
            if (force || shown(Overlay.POINTS) && eye.distanceToSqr(point.x(), point.y(), point.z()) <= r2) {
                wanted.put(key, new Source(point, key.equals(selected) || force, labels));
            }
        }
        for (Region region : store.all(MarkerType.REGION, Region.class)) {
            Selection key = new Selection(MarkerType.REGION, region.id());
            boolean force = key.equals(forced);
            if (force || shown(Overlay.REGIONS) && MarkerPick.distanceSquared(region.shape(), eye.x, eye.y, eye.z) <= r2) {
                wanted.put(key, new Source(region, key.equals(selected) || force, labels));
            }
        }
        List<Map.Entry<Selection, Source>> groups = new ArrayList<>();
        for (Group group : store.all(MarkerType.GROUP, Group.class)) {
            Selection key = new Selection(MarkerType.GROUP, group.id());
            if (draft != null && draft.id().equals(group.id())) {
                continue;
            }
            boolean force = key.equals(forced);
            if (force || shown(Overlay.GROUPS) && nearest(group.blocks(), eye) <= r2) {
                groups.add(Map.entry(key, new Source(group, key.equals(selected) || force, labels)));
            }
        }
        if (draft != null) {
            groups.add(Map.entry(new Selection(MarkerType.GROUP, draft.id()),
                    new Source(new DraftSource(draft.id(), tools.draftVersion()), true, labels)));
        }

        Iterator<Map.Entry<Selection, Built>> it = built.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Selection, Built> entry = it.next();
            Source want = wanted.get(entry.getKey());
            if (want == null) {
                want = groups.stream().filter(g -> g.getKey().equals(entry.getKey())).map(Map.Entry::getValue)
                        .findFirst().orElse(null);
            }
            Built have = entry.getValue();
            boolean stale = want == null || !want.equals(have.source())
                    || have.clipped() && have.anchor().distanceToSqr(eye) > MOVE_REBUILD * MOVE_REBUILD;
            if (stale) {
                discard(have);
                it.remove();
            }
        }

        for (Map.Entry<Selection, Source> entry : wanted.entrySet()) {
            if (!built.containsKey(entry.getKey())) {
                List<Entity> entities = new ArrayList<>();
                switch ((Marker) entry.getValue().marker()) {
                    case Point point -> point(entities, point, entry.getValue());
                    case Region region -> region(entities, region, entry.getValue());
                    case Group group -> {
                    }
                }
                built.put(entry.getKey(), spawn(entry.getValue(), entities, eye, false, 0));
            }
        }

        int used = 0;
        for (Map.Entry<Selection, Source> entry : groups) {
            Built have = built.get(entry.getKey());
            if (have != null) {
                used += have.blocks();
            }
        }
        groups.sort(Comparator.comparingDouble(entry -> nearest(blocksOf(entry.getValue(), draft), eye)));
        for (Map.Entry<Selection, Source> entry : groups) {
            if (built.containsKey(entry.getKey())) {
                continue;
            }
            int budget = Math.min(GROUP_CAP, TOTAL_GROUP_CAP - used);
            long[] blocks = blocksOf(entry.getValue(), draft);
            List<Entity> entities = new ArrayList<>();
            int drawn = group(entities, entry.getKey().id(), blocks, entry.getValue(), draft != null
                    && entry.getValue().marker() instanceof DraftSource, eye, r2, budget);
            used += drawn;
            built.put(entry.getKey(), spawn(entry.getValue(), entities, eye, drawn < blocks.length, drawn));
        }
    }

    private static long[] blocksOf(Source source, GroupDraft draft) {
        return source.marker() instanceof Group group ? group.blocks() : draft.blocks();
    }

    private static double nearest(long[] blocks, Vec3 eye) {
        double best = Double.MAX_VALUE;
        for (long block : blocks) {
            best = Math.min(best, eye.distanceToSqr(BlockPosPacking.x(block) + 0.5, BlockPosPacking.y(block) + 0.5,
                    BlockPosPacking.z(block) + 0.5));
        }
        return best;
    }

    private Built spawn(Source source, List<Entity> entities, Vec3 eye, boolean clipped, int blocks) {
        for (Entity entity : entities) {
            entity.setId(nextId--);
            level.addEntity(entity);
        }
        return new Built(source, entities, eye, clipped, blocks);
    }

    private void discard(Built entry) {
        if (level == null) {
            return;
        }
        for (Entity entity : entry.entities()) {
            level.removeEntity(entity.getId(), Entity.RemovalReason.DISCARDED);
        }
    }

    private void point(List<Entity> out, Point point, Source source) {
        int colour = source.selected() ? SELECTED_COLOUR : POINT_COLOUR;
        BlockState state = (source.selected() ? Blocks.CONCRETE.yellow() : Blocks.CONCRETE.lime()).defaultBlockState();
        Quaternionf facing = new Quaternionf().rotationY((float) Math.toRadians(-point.yaw()));
        float half = POINT_SIZE / 2;
        out.add(MarkerDisplays.block(level, state, colour, point.x(), point.y(), point.z(), facing,
                new Vector3f(-half, 0, -half), new Vector3f(POINT_SIZE), 1));
        out.add(MarkerDisplays.block(level, state, colour, point.x(), point.y(), point.z(), facing,
                new Vector3f(-0.05f, 0.1f, half), new Vector3f(0.1f, 0.1f, 0.25f), 1));
        if (source.labels()) {
            out.add(MarkerDisplays.label(level, label(point, colour), point.x(), point.y() + 0.75, point.z()));
        }
    }

    private void region(List<Entity> out, Region region, Source source) {
        int colour = source.selected() ? SELECTED_COLOUR : REGION_COLOUR;
        BlockState state = (source.selected() ? Blocks.CONCRETE.yellow() : Blocks.CONCRETE.lightBlue()).defaultBlockState();
        switch (region.shape()) {
            case Shape.Box b -> {
                double x0 = b.minX();
                double y0 = b.minY();
                double z0 = b.minZ();
                double x1 = b.maxX() + 1;
                double y1 = b.maxY() + 1;
                double z1 = b.maxZ() + 1;
                double[][] corners = {{x0, z0}, {x1, z0}, {x1, z1}, {x0, z1}};
                for (int i = 0; i < 4; i++) {
                    double[] a = corners[i];
                    double[] c = corners[(i + 1) % 4];
                    edge(out, state, colour, a[0], y0, a[1], c[0], y0, c[1]);
                    edge(out, state, colour, a[0], y1, a[1], c[0], y1, c[1]);
                    edge(out, state, colour, a[0], y0, a[1], a[0], y1, a[1]);
                }
            }
            case Shape.Polygon p -> {
                int[] xz = p.xz();
                int n = p.vertices();
                double y0 = p.minY();
                double y1 = p.maxY() + 1;
                for (int i = 0; i < n; i++) {
                    int j = (i + 1) % n;
                    double ax = xz[2 * i] + 0.5;
                    double az = xz[2 * i + 1] + 0.5;
                    double bx = xz[2 * j] + 0.5;
                    double bz = xz[2 * j + 1] + 0.5;
                    edge(out, state, colour, ax, y0, az, bx, y0, bz);
                    edge(out, state, colour, ax, y1, az, bx, y1, bz);
                    edge(out, state, colour, ax, y0, az, ax, y1, az);
                }
            }
        }
        if (source.labels()) {
            double[] centre = MarkerPick.centre(region.shape());
            out.add(MarkerDisplays.label(level, label(region, colour), centre[0], centre[1], centre[2]));
        }
    }

    private void edge(List<Entity> out, BlockState state, int colour, double ax, double ay, double az, double bx,
                      double by, double bz) {
        float half = EDGE / 2;
        if (ax == bx && az == bz) {
            float length = (float) Math.abs(by - ay);
            out.add(MarkerDisplays.block(level, state, colour, ax, Math.min(ay, by), az, new Quaternionf(),
                    new Vector3f(-half, 0, -half), new Vector3f(EDGE, length, EDGE), length + 2));
            return;
        }
        double dx = bx - ax;
        double dz = bz - az;
        float length = (float) Math.sqrt(dx * dx + dz * dz);
        Quaternionf along = new Quaternionf().rotationY((float) Math.atan2(dx, dz));
        out.add(MarkerDisplays.block(level, state, colour, ax, ay, az, along, new Vector3f(-half, -half, 0),
                new Vector3f(EDGE, EDGE, length + EDGE / 2), 2 * length + 2));
    }

    private int group(List<Entity> out, String id, long[] blocks, Source source, boolean isDraft, Vec3 eye, double r2,
                      int budget) {
        int colour = isDraft ? DRAFT_COLOUR : source.selected() ? SELECTED_COLOUR : GROUP_COLOUR;
        int drawn = 0;
        Long first = null;
        for (long block : blocks) {
            int x = BlockPosPacking.x(block);
            int y = BlockPosPacking.y(block);
            int z = BlockPosPacking.z(block);
            if (eye.distanceToSqr(x + 0.5, y + 0.5, z + 0.5) > r2 && !source.selected()) {
                continue;
            }
            if (drawn >= budget) {
                if (!capLogged) {
                    capLogged = true;
                    Backstage.LOG.info("Backstage editor: group display cap reached ({} per group, {} in total); "
                            + "extra blocks are not drawn", GROUP_CAP, TOTAL_GROUP_CAP);
                }
                break;
            }
            BlockState state = level.getBlockState(new BlockPos(x, y, z));
            if (state.isAir()) {
                state = Blocks.STAINED_GLASS.white().defaultBlockState();
            }
            out.add(MarkerDisplays.block(level, state, colour, x, y, z, new Quaternionf(),
                    new Vector3f(-0.001f), new Vector3f(1.002f), 3));
            if (first == null) {
                first = block;
            }
            drawn++;
        }
        if (source.labels() && first != null) {
            MutableComponent text = Component.literal(id).withColor(colour)
                    .append(Component.literal(" (" + blocks.length + ")").withStyle(ChatFormatting.GRAY));
            out.add(MarkerDisplays.label(level, text, BlockPosPacking.x(first) + 0.5, BlockPosPacking.y(first) + 1.4,
                    BlockPosPacking.z(first) + 0.5));
        }
        return drawn;
    }

    private static Component label(Marker marker, int colour) {
        MutableComponent text = Component.literal(marker.id()).withColor(colour);
        if (!marker.tags().isEmpty()) {
            text.append(Component.literal("\n" + String.join(", ", marker.tags())).withStyle(ChatFormatting.GRAY));
        }
        return text;
    }
}
