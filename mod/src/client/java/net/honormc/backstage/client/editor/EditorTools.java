package net.honormc.backstage.client.editor;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.honormc.backstage.client.BrushSession;
import net.honormc.backstage.client.editor.GizmoMath.Axis;
import net.honormc.backstage.protocol.BlockPosPacking;
import net.honormc.backstage.protocol.EditorProtocol;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

public final class EditorTools {
    public static final long DEBOUNCE_MS = 500;
    public static final int MAX_BOX_VOLUME = 1_000_000;
    public static final float HANDLE_RADIUS = 9;
    public static final float GIZMO_PIXELS = 90;
    public static final float YAW_STEP = 45;
    public static final long MISSING_MS = 2_000;

    public enum Tool {
        SELECT("Select", "select"),
        POINT("Point", "point"),
        GROUP("Group brush", "group"),
        BOX("Box", "box"),
        REGION("Region polygon", "region"),
        MOVE("Move", "move"),
        BRUSH("Brush", "brush");

        private final String title;
        private final String key;

        Tool(String title, String key) {
            this.title = title;
            this.key = key;
        }

        public String title() {
            return title;
        }

        public String key() {
            return key;
        }

        public static Tool of(String key) {
            for (Tool tool : values()) {
                if (tool.key.equals(key)) {
                    return tool;
                }
            }
            return SELECT;
        }
    }

    public enum BrushMode {
        PAINT,
        ERASE,
        FILL
    }

    public enum HandleKind {
        GIZMO,
        GIZMO_CENTRE,
        BOX_FACE,
        POLY_VERTEX,
        POLY_TOP,
        POLY_BOTTOM,
        YAW
    }

    public record Selection(MarkerType type, String id) {
    }

    public record Handle(HandleKind kind, double x, double y, double z, Axis axis, int index, int colour) {
    }

    public record Cursor(MarkerPick.Ray ray, BlockHitResult hit, float x, float y, Marquee.View view, boolean ctrl,
                         boolean shift, boolean alt) {
        public BlockPos block() {
            return hit == null ? null : hit.getBlockPos();
        }
    }

    public interface Notifier {
        void ok(String title, String message);

        void error(String message);
    }

    private record Drag(Handle handle, MarkerPick.Ray start, List<Marker> before, Shape.Box box, int[] vertex) {
    }

    private final EditorClient editor;
    private final BrushSession brushes;
    private final Notifier notifier;
    private Tool tool = Tool.SELECT;
    private final LinkedHashSet<Selection> selection = new LinkedHashSet<>();
    private final Set<Selection> hidden = new HashSet<>();
    private final Set<String> reserved = new HashSet<>();
    private final Map<Selection, Long> missingSince = new HashMap<>();
    private Marker hovered;
    private Cursor cursor;
    private long revision;
    private String map;

    private GroupDraft draft;
    private Group draftBase;
    private long draftVersion;
    private boolean draftDirty;
    private long draftChangedAt;
    private Block paintBlock;
    private Long lastPainted;
    private boolean fullWarned;
    private boolean painting;

    private Shape.Box boxDraft;
    private BlockPos boxStart;
    private final List<int[]> polygon = new ArrayList<>();
    private Integer polyMinY;
    private Integer polyMaxY;

    private Drag drag;
    private double[] moveDelta = {0, 0, 0};
    private Shape liveShape;
    private Float liveYaw;
    private float[] marqueeStart;
    private Marquee.Rect marquee;
    private boolean leftDown;

    public int brushRadius = 2;
    public BrushMode brushMode = BrushMode.PAINT;
    public boolean sameBlock;
    public boolean boxAsGroup;

    public EditorTools(EditorClient editor, BrushSession brushes, Notifier notifier) {
        this.editor = editor;
        this.brushes = brushes;
        this.notifier = notifier;
    }

    public EditorClient editor() {
        return editor;
    }

    public BrushSession brushes() {
        return brushes;
    }

    public Tool tool() {
        return tool;
    }

    public void tool(Tool next) {
        if (tool == next) {
            return;
        }
        cancelGesture();
        if (next != Tool.BOX) {
            boxDraft = null;
            boxStart = null;
        }
        if (next != Tool.REGION) {
            polygon.clear();
        }
        tool = next;
        changed();
    }

    public long revision() {
        return revision;
    }

    public List<Selection> selection() {
        return List.copyOf(selection);
    }

    public Selection primary() {
        Selection last = null;
        for (Selection s : selection) {
            last = s;
        }
        return last;
    }

    public boolean selected(Selection s) {
        return selection.contains(s);
    }

    public List<Marker> selectedMarkers() {
        List<Marker> markers = new ArrayList<>();
        for (Selection s : selection) {
            Marker marker = current(s);
            if (marker != null) {
                markers.add(marker);
            }
        }
        return markers;
    }

    public void select(Selection s, boolean add) {
        if (!add) {
            selection.clear();
        }
        if (s != null) {
            selection.remove(s);
            selection.add(s);
        }
        changed();
    }

    public void deselect(Selection s) {
        selection.remove(s);
        changed();
    }

    public void selectAll(Collection<Selection> all, boolean add) {
        if (!add) {
            selection.clear();
        }
        selection.addAll(all);
        changed();
    }

    public void selectNone() {
        selection.clear();
        changed();
    }

    public boolean hidden(Selection s) {
        return hidden.contains(s);
    }

    public void hide(Selection s, boolean hide) {
        if (hide) {
            hidden.add(s);
            selection.remove(s);
        } else {
            hidden.remove(s);
        }
        changed();
    }

    public Marker hovered() {
        return hovered;
    }

    public Cursor cursor() {
        return cursor;
    }

    public GroupDraft draft() {
        return draft;
    }

    public long draftVersion() {
        return draftVersion;
    }

    public Shape.Box boxDraft() {
        return boxDraft;
    }

    public List<int[]> polygonDraft() {
        return polygon;
    }

    public Integer polyMinY() {
        return polyMinY;
    }

    public Integer polyMaxY() {
        return polyMaxY;
    }

    public void polyHeights(Integer minY, Integer maxY) {
        polyMinY = minY;
        polyMaxY = maxY;
    }

    public Marquee.Rect marquee() {
        return marquee;
    }

    public double[] moveDelta() {
        return drag != null && (drag.handle().kind() == HandleKind.GIZMO || drag.handle().kind() == HandleKind.GIZMO_CENTRE)
                ? moveDelta : null;
    }

    public Shape liveShape() {
        return drag != null ? liveShape : null;
    }

    public Float liveYaw() {
        return drag != null ? liveYaw : null;
    }

    public Handle dragging() {
        return drag == null ? null : drag.handle();
    }

    public boolean busy() {
        return drag != null || marquee != null || painting || boxStart != null;
    }

    public Marker current(Selection s) {
        if (s == null) {
            return null;
        }
        if (draft != null && s.type() == MarkerType.GROUP && s.id().equals(draft.id())) {
            return draftBase != null ? MarkerEdits.withBlocks(draftBase, draft.blocks())
                    : new Group(draft.id(), draft.blocks(), List.of(), Map.of());
        }
        return editor.store().get(s.type(), s.id());
    }

    public void tick() {
        String current = editor.state() instanceof EditorState.Available a && a.bound() ? a.map() : null;
        if (current == null ? map != null : !current.equals(map)) {
            resetSession();
            map = current;
        }
        if (draftDirty && System.currentTimeMillis() - draftChangedAt >= DEBOUNCE_MS) {
            flush();
        }
        long now = System.currentTimeMillis();
        for (Selection s : selection) {
            if (current(s) == null) {
                missingSince.putIfAbsent(s, now);
            } else {
                missingSince.remove(s);
            }
        }
        selection.removeIf(s -> now - missingSince.getOrDefault(s, now) > MISSING_MS);
        missingSince.keySet().retainAll(selection);
    }

    public void hover(Cursor next) {
        cursor = next;
        hovered = drag == null ? pick(next) : null;
        if (leftDown) {
            drag(next);
        }
    }

    public boolean mouseDown(Cursor at) {
        cursor = at;
        leftDown = true;
        Handle handle = handleAt(at);
        if (handle != null) {
            beginDrag(handle, at);
            return true;
        }
        String problem = editor.cannotEdit();
        if (problem != null && tool != Tool.SELECT && tool != Tool.BRUSH) {
            notifier.error(problem);
            leftDown = false;
            return true;
        }
        switch (tool) {
            case SELECT -> selectDown(at);
            case POINT -> pointDown(at);
            case GROUP -> groupDown(at);
            case BOX -> boxDown(at);
            case REGION -> regionDown(at);
            case MOVE -> selectDown(at);
            case BRUSH -> brushDown(at);
        }
        return true;
    }

    public void mouseUp(Cursor at) {
        cursor = at;
        leftDown = false;
        if (drag != null) {
            endDrag(at);
        }
        if (marquee != null) {
            endMarquee(at);
        }
        marqueeStart = null;
        if (painting) {
            painting = false;
            lastPainted = null;
            fullWarned = false;
            paintBlock = null;
        }
        if (boxStart != null) {
            boxStart = null;
            changed();
        }
    }

    public void cancelGesture() {
        drag = null;
        marquee = null;
        marqueeStart = null;
        painting = false;
        leftDown = false;
        liveShape = null;
        liveYaw = null;
        moveDelta = new double[]{0, 0, 0};
    }

    public boolean escape() {
        if (drag != null || marquee != null) {
            cancelGesture();
            return true;
        }
        if (boxDraft != null) {
            boxDraft = null;
            boxStart = null;
            changed();
            return true;
        }
        if (!polygon.isEmpty()) {
            polygon.clear();
            changed();
            return true;
        }
        return false;
    }

    private void drag(Cursor at) {
        if (drag != null) {
            updateDrag(at);
            return;
        }
        if (marqueeStart != null) {
            if (marquee != null || Math.abs(at.x() - marqueeStart[0]) + Math.abs(at.y() - marqueeStart[1]) > 4) {
                marquee = Marquee.Rect.between(marqueeStart[0], marqueeStart[1], at.x(), at.y());
            }
            return;
        }
        if (painting && tool == Tool.GROUP) {
            paint(at);
            return;
        }
        if (boxStart != null && at.block() != null) {
            BlockPos end = at.block();
            boxDraft = BlockSelection.bounds(boxStart.getX(), boxStart.getY(), boxStart.getZ(), end.getX(), end.getY(), end.getZ());
            changed();
        }
    }

    private Marker pick(Cursor at) {
        if (at == null || at.ray() == null) {
            return null;
        }
        MarkerStore store = editor.store();
        Long block = at.block() == null ? null : BlockPosPacking.pack(at.block().getX(), at.block().getY(), at.block().getZ());
        List<Point> points = new ArrayList<>(store.all(MarkerType.POINT, Point.class));
        points.removeIf(p -> hidden.contains(new Selection(MarkerType.POINT, p.id())));
        List<Group> groups = new ArrayList<>(store.all(MarkerType.GROUP, Group.class));
        groups.removeIf(g -> hidden.contains(new Selection(MarkerType.GROUP, g.id())));
        if (draft != null) {
            groups.removeIf(g -> g.id().equals(draft.id()));
            groups.add(new Group(draft.id(), draft.blocks(), List.of(), Map.of()));
        }
        List<Region> regions = new ArrayList<>(store.all(MarkerType.REGION, Region.class));
        regions.removeIf(r -> hidden.contains(new Selection(MarkerType.REGION, r.id())));
        MarkerPick.Ray ray = at.ray();
        Point point = MarkerPick.point(ray, points);
        if (point != null) {
            return point;
        }
        if (block != null) {
            Group group = MarkerPick.group(block, groups);
            if (group != null) {
                return group;
            }
        }
        for (double t = 0; t <= ray.reach(); t += MarkerPick.RAY_STEP) {
            double x = ray.x() + ray.dx() * t;
            double y = ray.y() + ray.dy() * t;
            double z = ray.z() + ray.dz() * t;
            if (at.hit() != null && at.hit().getLocation().distanceToSqr(ray.x(), ray.y(), ray.z()) < t * t) {
                break;
            }
            for (Region region : regions) {
                if (MarkerPick.contains(region.shape(), x, y, z) && !MarkerPick.contains(region.shape(), ray.x(), ray.y(), ray.z())) {
                    return region;
                }
            }
        }
        return null;
    }

    private static Selection key(Marker marker) {
        return new Selection(marker.type(), marker.id());
    }

    private void selectDown(Cursor at) {
        Marker marker = hovered != null ? hovered : pick(at);
        if (marker == null) {
            marqueeStart = new float[]{at.x(), at.y()};
            return;
        }
        Selection s = key(marker);
        if (at.ctrl()) {
            deselect(s);
        } else {
            select(s, at.shift());
        }
    }

    private void endMarquee(Cursor at) {
        Marquee.Rect rect = marquee;
        marquee = null;
        if (rect == null || at.view() == null) {
            return;
        }
        List<Marker> candidates = new ArrayList<>();
        for (Marker marker : editor.store().all()) {
            if (!hidden.contains(key(marker))) {
                candidates.add(marker);
            }
        }
        List<Marker> inside = Marquee.select(candidates, MarkerText::centre, at.view(), rect);
        List<Selection> picked = inside.stream().map(EditorTools::key).toList();
        if (at.ctrl()) {
            picked.forEach(selection::remove);
            changed();
            return;
        }
        selectAll(picked, at.shift());
    }

    private void pointDown(Cursor at) {
        Marker under = hovered != null ? hovered : pick(at);
        if (under instanceof Point point) {
            select(key(point), at.shift());
            return;
        }
        if (at.hit() == null) {
            notifier.error("Point the cursor at a block to place a point.");
            return;
        }
        Vec3 spot = faceCentre(at.hit());
        float yaw = GizmoMath.snapYaw(cameraYaw(), YAW_STEP);
        Set<String> taken = new HashSet<>(editor.store().markers(MarkerType.POINT).keySet());
        taken.addAll(reserved);
        String id = MarkerIds.next("point", taken);
        Point point = new Point(id, spot.x, spot.y, spot.z, yaw, 0, List.of(), Map.of());
        if (edit(new MarkerHistory.Change(null, point))) {
            reserved.add(id);
            select(new Selection(MarkerType.POINT, id), false);
            notifier.ok("Placed " + id, MarkerText.xyz(spot.x, spot.y, spot.z));
        }
    }

    public Vec3 ghostPoint() {
        if (tool != Tool.POINT || cursor == null || cursor.hit() == null || hovered instanceof Point || busy()) {
            return null;
        }
        return faceCentre(cursor.hit());
    }

    private static float cameraYaw() {
        Minecraft mc = Minecraft.getInstance();
        return mc.player == null ? 0 : Mth.wrapDegrees(mc.player.getYRot());
    }

    public void snapYaw() {
        List<MarkerHistory.Op> ops = new ArrayList<>();
        for (Marker marker : selectedMarkers()) {
            if (marker instanceof Point p) {
                float yaw = GizmoMath.snapYaw(p.yaw(), YAW_STEP);
                if (yaw != p.yaw()) {
                    ops.add(new MarkerHistory.Change(p, MarkerEdits.moved(p, p.x(), p.y(), p.z(), yaw)));
                }
            }
        }
        if (ops.isEmpty()) {
            notifier.error("Select a point to snap its yaw.");
            return;
        }
        editAll(ops);
    }

    public void rotate(float degrees) {
        List<MarkerHistory.Op> ops = new ArrayList<>();
        for (Marker marker : selectedMarkers()) {
            if (marker instanceof Point p) {
                float yaw = Mth.wrapDegrees(p.yaw() + degrees);
                ops.add(new MarkerHistory.Change(p, MarkerEdits.moved(p, p.x(), p.y(), p.z(), yaw)));
            }
        }
        editAll(ops);
    }

    public boolean groupNew(String id) {
        String problem = MarkerCheck.id(id);
        if (problem == null && (editor.store().get(MarkerType.GROUP, id) != null || draft != null && draft.id().equals(id))) {
            problem = "A group named " + id + " already exists.";
        }
        if (problem == null) {
            problem = editor.cannotEdit();
        }
        if (problem != null) {
            notifier.error(problem);
            return false;
        }
        openGroup(id, null);
        return true;
    }

    public boolean groupEdit(String id) {
        if (draft != null && draft.id().equals(id)) {
            return true;
        }
        if (!(editor.store().get(MarkerType.GROUP, id) instanceof Group group)) {
            notifier.error("No group named " + id + ".");
            return false;
        }
        String problem = editor.cannotEdit();
        if (problem != null) {
            notifier.error(problem);
            return false;
        }
        openGroup(id, group);
        return true;
    }

    private void openGroup(String id, Group base) {
        closeGroup();
        draft = new GroupDraft(id, base == null ? new long[0] : base.blocks());
        draftBase = base;
        draftDirty = false;
        select(new Selection(MarkerType.GROUP, id), false);
        draftChanged();
    }

    public void closeGroup() {
        if (draft == null) {
            return;
        }
        flush();
        draft = null;
        draftBase = null;
        draftChanged();
    }

    private void groupDown(Cursor at) {
        if (draft == null) {
            if (hovered instanceof Group group) {
                groupEdit(group.id());
                return;
            }
            notifier.error("Pick a group in the Outliner or press + Group first.");
            return;
        }
        if (at.hit() == null) {
            return;
        }
        BlockPos pos = at.block();
        if (at.alt() || brushMode == BrushMode.FILL) {
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            Block block = level.getBlockState(pos).getBlock();
            BlockSelection.Result result = BlockSelection.flood(BlockPosPacking.pack(pos.getX(), pos.getY(), pos.getZ()),
                    packed -> level.getBlockState(BlockPos.of(packed)).is(block),
                    EditorProtocol.MAX_GROUP_BLOCKS - draft.size(), BlockSelection.FILL_RADIUS);
            int added = draft.addAll(result.blocks());
            draftChanged();
            markDirty();
            notifier.ok("Filled " + added + " blocks", draft.id() + " · " + draft.size() + " / " + EditorProtocol.MAX_GROUP_BLOCKS);
            if (draft.full() || result.capped()) {
                notifier.error("Group " + draft.id() + " is capped at " + EditorProtocol.MAX_GROUP_BLOCKS + " blocks.");
            }
            return;
        }
        painting = true;
        Level level = Minecraft.getInstance().level;
        paintBlock = level == null ? null : level.getBlockState(pos).getBlock();
        lastPainted = null;
        paint(at);
    }

    private boolean erasing(Cursor at) {
        return brushMode == BrushMode.ERASE ^ at.ctrl();
    }

    private void paint(Cursor at) {
        if (draft == null || at.hit() == null) {
            return;
        }
        BlockPos centre = at.block();
        long packed = BlockPosPacking.pack(centre.getX(), centre.getY(), centre.getZ());
        if (lastPainted != null && lastPainted == packed) {
            return;
        }
        lastPainted = packed;
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        boolean erase = erasing(at);
        boolean changed = false;
        int r = brushRadius;
        for (int dy = -r; dy <= r; dy++) {
            for (int dz = -r; dz <= r; dz++) {
                for (int dx = -r; dx <= r; dx++) {
                    if (dx * dx + dy * dy + dz * dz > r * r + r) {
                        continue;
                    }
                    BlockPos pos = centre.offset(dx, dy, dz);
                    long key = BlockPosPacking.pack(pos.getX(), pos.getY(), pos.getZ());
                    if (erase) {
                        changed |= draft.remove(key);
                        continue;
                    }
                    BlockState state = level.getBlockState(pos);
                    if (state.isAir() || sameBlock && paintBlock != null && !state.is(paintBlock)) {
                        continue;
                    }
                    GroupDraft.Outcome outcome = draft.add(key);
                    if (outcome == GroupDraft.Outcome.ADDED) {
                        changed = true;
                    } else if (outcome == GroupDraft.Outcome.FULL && !fullWarned) {
                        fullWarned = true;
                        notifier.error("Group " + draft.id() + " is full: " + EditorProtocol.MAX_GROUP_BLOCKS + " blocks at most.");
                    }
                }
            }
        }
        if (changed) {
            draftChanged();
            markDirty();
        }
    }

    public void brushRadius(int delta) {
        brushRadius = Math.clamp(brushRadius + delta, 0, EditorLayout.MAX_BRUSH);
        changed();
    }

    private void markDirty() {
        draftDirty = true;
        draftChangedAt = System.currentTimeMillis();
    }

    private void flush() {
        if (draft == null || !draftDirty) {
            return;
        }
        draftDirty = false;
        Group after = draftBase == null
                ? new Group(draft.id(), draft.blocks(), List.of(), Map.of())
                : MarkerEdits.withBlocks(draftBase, draft.blocks());
        if (draftBase != null && Arrays.equals(draftBase.blocks(), after.blocks())) {
            return;
        }
        String problem = editor.edit(new MarkerHistory.Change(draftBase, after));
        if (problem != null) {
            notifier.error(problem);
            return;
        }
        draftBase = after;
    }

    private void boxDown(Cursor at) {
        if (at.hit() == null) {
            return;
        }
        boxStart = at.block();
        boxDraft = BlockSelection.bounds(boxStart.getX(), boxStart.getY(), boxStart.getZ(), boxStart.getX(),
                boxStart.getY(), boxStart.getZ());
        changed();
    }

    private Region selectedRegion(boolean polygonShape) {
        Selection s = primary();
        if (s == null || s.type() != MarkerType.REGION || selection.size() != 1) {
            return null;
        }
        Marker marker = current(s);
        if (marker instanceof Region region && region.shape() instanceof Shape.Polygon == polygonShape) {
            return region;
        }
        return null;
    }

    public void confirm() {
        if (tool == Tool.BOX && boxDraft != null) {
            confirmBox();
        } else if (tool == Tool.REGION && polygon.size() >= EditorProtocol.MIN_POLYGON_VERTICES) {
            closePolygon();
        }
    }

    private void confirmBox() {
        Shape.Box box = boxDraft;
        if (boxAsGroup) {
            long volume = (long) (box.maxX() - box.minX() + 1) * (box.maxY() - box.minY() + 1) * (box.maxZ() - box.minZ() + 1);
            if (volume > MAX_BOX_VOLUME) {
                notifier.error("That box is " + volume + " blocks; a group box scans at most " + MAX_BOX_VOLUME + ".");
                return;
            }
            Level level = Minecraft.getInstance().level;
            if (level == null) {
                return;
            }
            BlockSelection.Result result = BlockSelection.box(box, packed -> !level.getBlockState(BlockPos.of(packed)).isAir(),
                    EditorProtocol.MAX_GROUP_BLOCKS);
            Set<String> taken = new HashSet<>(editor.store().markers(MarkerType.GROUP).keySet());
            taken.addAll(reserved);
            String id = MarkerIds.next("group", taken);
            Group group = new Group(id, result.blocks(), List.of(), Map.of());
            if (edit(new MarkerHistory.Change(null, group))) {
                reserved.add(id);
                boxDraft = null;
                select(key(group), false);
                notifier.ok("Created " + id, result.blocks().length + " blocks" + (result.capped() ? " (capped)" : ""));
            }
            return;
        }
        if (createRegion(box)) {
            boxDraft = null;
        }
    }

    private void regionDown(Cursor at) {
        if (at.hit() == null) {
            return;
        }
        BlockPos pos = at.block();
        if (polygon.size() >= EditorProtocol.MIN_POLYGON_VERTICES && at.view() != null) {
            int[] first = polygon.getFirst();
            float[] screen = at.view().project(first[0] + 0.5, first[1] + 1, first[2] + 0.5);
            if (closes(pos.getX(), pos.getZ()) || screen != null
                    && Math.hypot(screen[0] - at.x(), screen[1] - at.y()) <= HANDLE_RADIUS * 1.5) {
                closePolygon();
                return;
            }
        }
        if (!polygon.isEmpty()) {
            int[] last = polygon.getLast();
            if (last[0] == pos.getX() && last[2] == pos.getZ()) {
                return;
            }
        }
        if (polygon.size() >= EditorProtocol.MAX_POLYGON_VERTICES) {
            notifier.error("A polygon has at most " + EditorProtocol.MAX_POLYGON_VERTICES + " vertices.");
            return;
        }
        polygon.add(new int[]{pos.getX(), pos.getY(), pos.getZ()});
        changed();
    }

    public boolean closes(int x, int z) {
        PolygonBuilder builder = new PolygonBuilder();
        for (int[] v : polygon) {
            builder.add(v[0], v[1], v[2]);
        }
        return builder.closesAt(x, z);
    }

    private void closePolygon() {
        PolygonBuilder builder = new PolygonBuilder();
        for (int[] v : polygon) {
            String problem = builder.add(v[0], v[1], v[2]);
            if (problem != null) {
                notifier.error(problem);
                return;
            }
        }
        Shape.Polygon shape;
        try {
            shape = builder.build(polyMinY, polyMaxY);
        } catch (IllegalArgumentException e) {
            notifier.error(e.getMessage());
            return;
        }
        if (createRegion(shape)) {
            polygon.clear();
        }
    }

    private boolean createRegion(Shape shape) {
        Set<String> taken = new HashSet<>(editor.store().markers(MarkerType.REGION).keySet());
        taken.addAll(reserved);
        String id = MarkerIds.next("region", taken);
        Region region = new Region(id, shape, List.of(), Map.of());
        if (!edit(new MarkerHistory.Change(null, region))) {
            return false;
        }
        reserved.add(id);
        select(key(region), false);
        notifier.ok("Created " + id, MarkerText.shape(shape));
        return true;
    }

    private void brushDown(Cursor at) {
        if (at.hit() == null) {
            notifier.error("Point the cursor at a block to run the brush.");
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        Direction facing = mc.player == null ? Direction.NORTH : mc.player.getDirection();
        brushes.run(at.block(), at.hit().getDirection(), facing, at.shift());
    }

    public List<Handle> handles() {
        List<Handle> handles = new ArrayList<>();
        Cursor at = cursor;
        if (at == null || at.view() == null) {
            return handles;
        }
        switch (tool) {
            case MOVE -> {
                double[] centre = selectionCentre();
                if (centre != null) {
                    double[] delta = moveDelta() != null ? moveDelta : new double[]{0, 0, 0};
                    double ox = centre[0] + delta[0];
                    double oy = centre[1] + delta[1];
                    double oz = centre[2] + delta[2];
                    double size = gizmoSize(at, ox, oy, oz);
                    for (Axis axis : Axis.values()) {
                        handles.add(new Handle(HandleKind.GIZMO, ox + axis.dx * size, oy + axis.dy * size, oz + axis.dz * size,
                                axis, 0, 0));
                    }
                    handles.add(new Handle(HandleKind.GIZMO_CENTRE, ox, oy, oz, null, 0, 0));
                }
            }
            case BOX -> {
                Shape.Box box = drag != null && liveShape instanceof Shape.Box live ? live : boxDraft;
                if (box == null) {
                    Region region = selectedRegion(false);
                    box = region == null ? null : (Shape.Box) region.shape();
                }
                if (box != null && boxStart == null) {
                    double cx = (box.minX() + box.maxX() + 1) / 2.0;
                    double cy = (box.minY() + box.maxY() + 1) / 2.0;
                    double cz = (box.minZ() + box.maxZ() + 1) / 2.0;
                    handles.add(new Handle(HandleKind.BOX_FACE, box.minX(), cy, cz, Axis.X, 0, 0));
                    handles.add(new Handle(HandleKind.BOX_FACE, box.maxX() + 1, cy, cz, Axis.X, 1, 0));
                    handles.add(new Handle(HandleKind.BOX_FACE, cx, box.minY(), cz, Axis.Y, 0, 0));
                    handles.add(new Handle(HandleKind.BOX_FACE, cx, box.maxY() + 1, cz, Axis.Y, 1, 0));
                    handles.add(new Handle(HandleKind.BOX_FACE, cx, cy, box.minZ(), Axis.Z, 0, 0));
                    handles.add(new Handle(HandleKind.BOX_FACE, cx, cy, box.maxZ() + 1, Axis.Z, 1, 0));
                }
            }
            case REGION -> {
                if (!polygon.isEmpty()) {
                    for (int i = 0; i < polygon.size(); i++) {
                        int[] v = polygon.get(i);
                        handles.add(new Handle(HandleKind.POLY_VERTEX, v[0] + 0.5, v[1] + 1, v[2] + 0.5, null, i, 0));
                    }
                } else {
                    Region region = selectedRegion(true);
                    if (region != null) {
                        Shape.Polygon p = (Shape.Polygon) (drag != null && liveShape != null ? liveShape : region.shape());
                        int[] xz = p.xz();
                        for (int i = 0; i < p.vertices(); i++) {
                            handles.add(new Handle(HandleKind.POLY_VERTEX, xz[2 * i] + 0.5, p.minY(), xz[2 * i + 1] + 0.5, null, i, 0));
                        }
                        double[] c = MarkerPick.centre(p);
                        handles.add(new Handle(HandleKind.POLY_TOP, c[0], p.maxY() + 1, c[2], Axis.Y, 1, 0));
                        handles.add(new Handle(HandleKind.POLY_BOTTOM, c[0], p.minY(), c[2], Axis.Y, 0, 0));
                    }
                }
            }
            case POINT -> {
                for (Marker marker : selectedMarkers()) {
                    if (marker instanceof Point p && selection.size() == 1) {
                        float yaw = liveYaw() != null ? liveYaw : p.yaw();
                        double rad = Math.toRadians(yaw);
                        handles.add(new Handle(HandleKind.YAW, p.x() - Math.sin(rad) * 1.2, p.y() + 0.15,
                                p.z() + Math.cos(rad) * 1.2, null, 0, 0));
                    }
                }
            }
            default -> {
            }
        }
        return handles;
    }

    public double gizmoSize(Cursor at, double x, double y, double z) {
        MarkerPick.Ray ray = at.ray();
        double dx = x - ray.x();
        double dy = y - ray.y();
        double dz = z - ray.z();
        double distance = Math.sqrt(dx * dx + dy * dy + dz * dz);
        Minecraft mc = Minecraft.getInstance();
        double fov = Math.toRadians(mc.options.fov().get());
        return GizmoMath.worldSize(distance, fov, at.view().height(), GIZMO_PIXELS * EditorScale.ui());
    }

    public double[] selectionCentre() {
        List<Marker> markers = selectedMarkers();
        if (markers.isEmpty()) {
            return null;
        }
        double sx = 0;
        double sy = 0;
        double sz = 0;
        int n = 0;
        for (Marker marker : markers) {
            double[] c = MarkerText.centre(marker);
            if (c != null) {
                sx += c[0];
                sy += c[1];
                sz += c[2];
                n++;
            }
        }
        return n == 0 ? null : new double[]{sx / n, sy / n, sz / n};
    }

    public Handle handleAt(Cursor at) {
        if (at.view() == null) {
            return null;
        }
        List<Handle> handles = handles();
        float radius = HANDLE_RADIUS * EditorScale.ui();
        Handle best = null;
        double bestDistance = radius;
        float[] origin = null;
        float[][] tips = new float[3][];
        for (Handle handle : handles) {
            float[] screen = at.view().project(handle.x(), handle.y(), handle.z());
            if (handle.kind() == HandleKind.GIZMO) {
                tips[handle.axis().ordinal()] = screen;
                continue;
            }
            if (handle.kind() == HandleKind.GIZMO_CENTRE) {
                origin = screen;
            }
            if (screen == null) {
                continue;
            }
            double distance = Math.hypot(screen[0] - at.x(), screen[1] - at.y());
            if (distance <= bestDistance) {
                best = handle;
                bestDistance = distance;
            }
        }
        if (best != null) {
            return best;
        }
        Axis axis = GizmoMath.hovered(at.x(), at.y(), origin, tips, radius * 0.8f);
        if (axis != null) {
            for (Handle handle : handles) {
                if (handle.kind() == HandleKind.GIZMO && handle.axis() == axis) {
                    return handle;
                }
            }
        }
        return null;
    }

    private void beginDrag(Handle handle, Cursor at) {
        List<Marker> before = selectedMarkers();
        Shape.Box box = null;
        int[] vertex = null;
        if (handle.kind() == HandleKind.BOX_FACE) {
            Region region = boxDraft == null ? selectedRegion(false) : null;
            box = boxDraft != null ? boxDraft : region == null ? null : (Shape.Box) region.shape();
            before = region == null ? List.of() : List.of(region);
        } else if (handle.kind() == HandleKind.POLY_VERTEX && !polygon.isEmpty()) {
            vertex = polygon.get(handle.index()).clone();
            before = List.of();
        } else if (handle.kind() == HandleKind.POLY_VERTEX || handle.kind() == HandleKind.POLY_TOP
                || handle.kind() == HandleKind.POLY_BOTTOM) {
            Region region = selectedRegion(true);
            before = region == null ? List.of() : List.of(region);
        }
        drag = new Drag(handle, at.ray(), before, box, vertex);
        moveDelta = new double[]{0, 0, 0};
        liveShape = null;
        liveYaw = null;
    }

    private void updateDrag(Cursor at) {
        Handle handle = drag.handle();
        switch (handle.kind()) {
            case GIZMO -> {
                double[] centre = selectionCentre();
                if (centre != null) {
                    moveDelta = GizmoMath.dragDelta(drag.start(), at.ray(), centre[0], centre[1], centre[2], handle.axis(),
                            at.ctrl() && allPoints());
                }
            }
            case GIZMO_CENTRE -> {
                double[] centre = selectionCentre();
                if (centre != null) {
                    double[] from = GizmoMath.planeHit(drag.start(), centre[1]);
                    double[] to = GizmoMath.planeHit(at.ray(), centre[1]);
                    if (from != null && to != null) {
                        boolean free = at.ctrl() && allPoints();
                        moveDelta = new double[]{GizmoMath.snap(to[0] - from[0], free), 0, GizmoMath.snap(to[2] - from[2], free)};
                    }
                }
            }
            case BOX_FACE -> {
                if (drag.box() == null) {
                    return;
                }
                double[] d = GizmoMath.dragDelta(drag.start(), at.ray(), handle.x(), handle.y(), handle.z(), handle.axis(), false);
                int delta = (int) (d[0] + d[1] + d[2]);
                Shape.Box b = drag.box();
                int[] min = {b.minX(), b.minY(), b.minZ()};
                int[] max = {b.maxX(), b.maxY(), b.maxZ()};
                int axis = handle.axis().ordinal();
                if (handle.index() == 0) {
                    min[axis] = Math.min(min[axis] + delta, max[axis]);
                } else {
                    max[axis] = Math.max(max[axis] + delta, min[axis]);
                }
                Shape.Box live = new Shape.Box(min[0], min[1], min[2], max[0], max[1], max[2]);
                if (boxDraft != null) {
                    boxDraft = live;
                    changed();
                } else {
                    liveShape = live;
                }
            }
            case POLY_VERTEX -> {
                double planeY = handle.y();
                double[] hit = at.block() != null ? new double[]{at.block().getX() + 0.5, planeY, at.block().getZ() + 0.5}
                        : GizmoMath.planeHit(at.ray(), planeY);
                if (hit == null) {
                    return;
                }
                int x = (int) Math.floor(hit[0]);
                int z = (int) Math.floor(hit[2]);
                if (drag.vertex() != null) {
                    int[] v = polygon.get(handle.index());
                    v[0] = x;
                    v[2] = z;
                    changed();
                } else if (!drag.before().isEmpty() && drag.before().getFirst() instanceof Region region
                        && region.shape() instanceof Shape.Polygon p) {
                    int[] xz = p.xz().clone();
                    xz[2 * handle.index()] = x;
                    xz[2 * handle.index() + 1] = z;
                    liveShape = new Shape.Polygon(xz, p.minY(), p.maxY());
                }
            }
            case POLY_TOP, POLY_BOTTOM -> {
                if (drag.before().isEmpty() || !(drag.before().getFirst() instanceof Region region)
                        || !(region.shape() instanceof Shape.Polygon p)) {
                    return;
                }
                double[] d = GizmoMath.dragDelta(drag.start(), at.ray(), handle.x(), handle.y(), handle.z(), Axis.Y, false);
                int delta = (int) d[1];
                int minY = p.minY();
                int maxY = p.maxY();
                if (handle.kind() == HandleKind.POLY_TOP) {
                    maxY = Math.max(minY, maxY + delta);
                } else {
                    minY = Math.min(maxY, minY + delta);
                }
                liveShape = new Shape.Polygon(p.xz(), minY, maxY);
            }
            case YAW -> {
                Marker marker = selectedMarkers().isEmpty() ? null : selectedMarkers().getFirst();
                if (marker instanceof Point p) {
                    double[] hit = GizmoMath.planeHit(at.ray(), p.y() + 0.15);
                    if (hit != null) {
                        float yaw = GizmoMath.yawTowards(p.x(), p.z(), hit[0], hit[2]);
                        liveYaw = at.shift() ? GizmoMath.snapYaw(yaw, YAW_STEP) : Math.round(yaw);
                    }
                }
            }
        }
    }

    private boolean allPoints() {
        for (Selection s : selection) {
            if (s.type() != MarkerType.POINT) {
                return false;
            }
        }
        return true;
    }

    private void endDrag(Cursor at) {
        Drag done = drag;
        drag = null;
        Handle handle = done.handle();
        switch (handle.kind()) {
            case GIZMO, GIZMO_CENTRE -> {
                double[] delta = moveDelta;
                moveDelta = new double[]{0, 0, 0};
                if (delta[0] == 0 && delta[1] == 0 && delta[2] == 0) {
                    return;
                }
                List<MarkerHistory.Op> ops = new ArrayList<>();
                for (Marker marker : done.before()) {
                    flushIfDraft(marker);
                    Marker base = current(key(marker));
                    if (base != null) {
                        ops.add(new MarkerHistory.Change(base, MultiEdits.translated(base, delta[0], delta[1], delta[2])));
                    }
                }
                if (editAll(ops)) {
                    syncDraftFromStore(ops);
                }
            }
            case BOX_FACE, POLY_VERTEX, POLY_TOP, POLY_BOTTOM -> {
                Shape shape = liveShape;
                liveShape = null;
                if (shape != null && !done.before().isEmpty() && done.before().getFirst() instanceof Region region
                        && !shape.equals(region.shape())) {
                    String problem = MarkerCheck.shape(shape);
                    if (problem == null && shape instanceof Shape.Polygon p) {
                        problem = polygonProblem(p);
                    }
                    if (problem != null) {
                        notifier.error(problem);
                        return;
                    }
                    edit(new MarkerHistory.Change(region, MarkerEdits.withShape(region, shape)));
                }
            }
            case YAW -> {
                Float yaw = liveYaw;
                liveYaw = null;
                Marker marker = done.before().isEmpty() ? null : done.before().getFirst();
                if (yaw != null && marker instanceof Point p && yaw != p.yaw()) {
                    edit(new MarkerHistory.Change(p, MarkerEdits.moved(p, p.x(), p.y(), p.z(), yaw)));
                }
            }
        }
    }

    private static String polygonProblem(Shape.Polygon p) {
        PolygonBuilder builder = new PolygonBuilder();
        int[] xz = p.xz();
        for (int i = 0; i < xz.length; i += 2) {
            String problem = builder.add(xz[i], p.minY(), xz[i + 1]);
            if (problem != null) {
                return problem;
            }
        }
        try {
            builder.build(p.minY(), p.maxY());
        } catch (IllegalArgumentException e) {
            return e.getMessage();
        }
        return null;
    }

    private void flushIfDraft(Marker marker) {
        if (draft != null && marker instanceof Group group && group.id().equals(draft.id())) {
            flush();
        }
    }

    private void syncDraftFromStore(List<MarkerHistory.Op> ops) {
        for (MarkerHistory.Op op : ops) {
            if (draft != null && op instanceof MarkerHistory.Change c && c.after() instanceof Group group
                    && group.id().equals(draft.id())) {
                draft.reset(group.blocks());
                draftBase = group;
                draftChanged();
            }
        }
    }

    public boolean edit(MarkerHistory.Op op) {
        String problem = editor.edit(op);
        if (problem != null) {
            notifier.error(problem);
            return false;
        }
        if (op instanceof MarkerHistory.Change c && draft != null && c.type() == MarkerType.GROUP && c.id().equals(draft.id())) {
            if (c.after() instanceof Group group) {
                draftBase = group;
                if (!Arrays.equals(group.blocks(), draft.blocks())) {
                    draft.reset(group.blocks());
                    draftChanged();
                }
            } else {
                draft = null;
                draftBase = null;
                draftDirty = false;
                draftChanged();
            }
        }
        changed();
        return true;
    }

    public boolean editAll(List<? extends MarkerHistory.Op> ops) {
        boolean all = true;
        for (MarkerHistory.Op op : ops) {
            all &= edit(op);
        }
        return all && !ops.isEmpty();
    }

    public boolean change(Marker before, Marker after) {
        if (before.equals(after)) {
            return false;
        }
        flushIfDraft(before);
        Marker base = current(key(before));
        return edit(new MarkerHistory.Change(base == null ? before : base, after));
    }

    public boolean rename(Marker marker, String to) {
        if (marker.id().equals(to)) {
            return false;
        }
        String problem = MarkerCheck.id(to);
        if (problem == null && editor.store().get(marker.type(), to) != null) {
            problem = "A " + MarkerText.type(marker.type()) + " named " + to + " already exists.";
        }
        if (problem != null) {
            notifier.error(problem);
            return false;
        }
        flushIfDraft(marker);
        if (!edit(new MarkerHistory.Rename(marker.type(), marker.id(), to))) {
            return false;
        }
        renamed(marker.type(), marker.id(), to);
        return true;
    }

    public void delete() {
        List<Marker> markers = selectedMarkers();
        if (markers.isEmpty()) {
            notifier.error("Nothing selected.");
            return;
        }
        String problem = editor.cannotEdit();
        if (problem != null) {
            notifier.error(problem);
            return;
        }
        List<MarkerHistory.Op> ops = new ArrayList<>();
        for (Marker marker : markers) {
            if (draft != null && marker.type() == MarkerType.GROUP && marker.id().equals(draft.id())) {
                draft = null;
                draftBase = null;
                draftDirty = false;
                draftChanged();
                marker = editor.store().get(MarkerType.GROUP, marker.id());
                if (marker == null) {
                    continue;
                }
            }
            ops.add(new MarkerHistory.Change(marker, null));
        }
        if (editAll(ops)) {
            notifier.ok("Deleted " + ops.size() + (ops.size() == 1 ? " marker" : " markers"),
                    ops.size() == 1 ? ops.getFirst().id() : "");
            selectNone();
        }
    }

    public void duplicate() {
        List<Marker> markers = selectedMarkers();
        if (markers.isEmpty()) {
            notifier.error("Nothing selected to duplicate.");
            return;
        }
        String problem = editor.cannotEdit();
        if (problem != null) {
            notifier.error(problem);
            return;
        }
        Set<String> taken = new HashSet<>(reserved);
        for (Marker marker : editor.store().all()) {
            taken.add(marker.id());
        }
        List<Marker> copies = MultiEdits.duplicate(markers, taken);
        List<MarkerHistory.Op> ops = new ArrayList<>();
        for (Marker copy : copies) {
            ops.add(new MarkerHistory.Change(null, copy));
            reserved.add(copy.id());
        }
        if (editAll(ops)) {
            selectAll(copies.stream().map(EditorTools::key).toList(), false);
            tool(Tool.MOVE);
            notifier.ok("Duplicated " + copies.size() + (copies.size() == 1 ? " marker" : " markers"),
                    "Drag the gizmo to move the copies.");
        }
    }

    public void undo(boolean redo) {
        if (draft != null) {
            flush();
        }
        MarkerHistory.Op op = redo ? editor.redo() : editor.undo();
        if (op == null) {
            notifier.error(editor.lastStepProblem());
            return;
        }
        applied(op);
        notifier.ok(redo ? "Redo" : "Undo", MarkerText.op(op));
    }

    public void jump(int steps) {
        if (draft != null) {
            flush();
        }
        boolean redo = steps > 0;
        int done = 0;
        for (int i = 0; i < Math.abs(steps); i++) {
            MarkerHistory.Op op = redo ? editor.redo() : editor.undo();
            if (op == null) {
                notifier.error(editor.lastStepProblem());
                break;
            }
            applied(op);
            done++;
        }
        if (done > 0) {
            notifier.ok(redo ? "Redid " + done + " steps" : "Undid " + done + " steps", "");
        }
    }

    private void applied(MarkerHistory.Op op) {
        switch (op) {
            case MarkerHistory.Change change -> {
                if (draft != null && change.type() == MarkerType.GROUP && change.id().equals(draft.id())) {
                    if (change.after() instanceof Group group) {
                        draft.reset(group.blocks());
                        draftBase = group;
                    } else {
                        draft = null;
                        draftBase = null;
                    }
                    draftDirty = false;
                    draftChanged();
                }
            }
            case MarkerHistory.Rename rename -> renamed(rename.type(), rename.from(), rename.to());
        }
        changed();
    }

    private void renamed(MarkerType type, String from, String to) {
        List<Selection> next = new ArrayList<>();
        for (Selection s : selection) {
            next.add(s.type() == type && s.id().equals(from) ? new Selection(type, to) : s);
        }
        selection.clear();
        selection.addAll(next);
        if (hidden.remove(new Selection(type, from))) {
            hidden.add(new Selection(type, to));
        }
        if (draft != null && type == MarkerType.GROUP && draft.id().equals(from)) {
            draft.id(to);
            if (draftBase != null) {
                draftBase = new Group(to, draftBase.blocks(), draftBase.tags(), draftBase.props());
            }
            draftChanged();
        }
        changed();
    }

    private void resetSession() {
        draft = null;
        draftBase = null;
        draftDirty = false;
        selection.clear();
        hidden.clear();
        reserved.clear();
        boxDraft = null;
        boxStart = null;
        polygon.clear();
        cancelGesture();
        draftChanged();
    }

    public static Vec3 faceCentre(BlockHitResult hit) {
        Direction face = hit.getDirection();
        return Vec3.atCenterOf(hit.getBlockPos()).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
    }

    private void changed() {
        revision++;
    }

    private void draftChanged() {
        draftVersion++;
        revision++;
    }
}
