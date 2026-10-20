package net.honormc.backstage.client.editor;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;

public final class EditorTools {
    public static final double REACH = 256;
    public static final double INSPECT_REACH = 64;
    public static final long DEBOUNCE_MS = 500;
    public static final long HIGHLIGHT_MS = 10_000;
    public static final int MAX_BOX_VOLUME = 1_000_000;

    private static final int INSPECT_EVERY = 4;

    public enum Tool {
        SELECT,
        POINT,
        GROUP,
        REGION,
        OFF
    }

    public record Selection(MarkerType type, String id) {
    }

    private enum GroupMode {
        PAINT,
        BOX,
        FILL
    }

    private final EditorClient editor;
    private Tool tool = Tool.OFF;
    private Selection selected;
    private Selection forced;
    private long forcedUntil;
    private long revision;
    private String map;
    private boolean useWasDown;
    private int ticks;

    private String grabbed;
    private final Set<String> reserved = new HashSet<>();

    private GroupDraft draft;
    private Group draftBase;
    private long draftVersion;
    private boolean draftDirty;
    private long draftChangedAt;
    private GroupMode groupMode = GroupMode.PAINT;
    private BlockPos groupCorner;
    private Long lastPainted;
    private boolean fullWarned;
    private boolean paintLocked;

    private boolean regionPoly;
    private String regionId;
    private BlockPos regionCorner;
    private PolygonBuilder polygon;
    private Integer heightMin;
    private Integer heightMax;

    public EditorTools(EditorClient editor) {
        this.editor = editor;
    }

    public Tool tool() {
        return tool;
    }

    public Selection selected() {
        return selected;
    }

    public Selection forced() {
        return forced;
    }

    public GroupDraft draft() {
        return draft;
    }

    public long draftVersion() {
        return draftVersion;
    }

    public long revision() {
        return revision;
    }

    public void tool(Tool next) {
        if (tool == Tool.GROUP && next != Tool.GROUP) {
            closeGroup();
        }
        if (next != Tool.POINT) {
            grabbed = null;
        }
        if (next != Tool.REGION) {
            resetRegion();
        }
        tool = next;
        changed();
        info(next == Tool.OFF ? "§7Wand is back on Lua brushes." : "§7Tool: §f" + name(next) + "§7. " + hint());
    }

    public void tick(Minecraft mc, boolean holdingWand) {
        ticks++;
        String current = editor.state() instanceof EditorState.Available a && a.bound() ? a.map() : null;
        if (current == null ? map != null : !current.equals(map)) {
            resetSession();
            map = current;
        }
        if (forced != null && System.currentTimeMillis() > forcedUntil) {
            forced = null;
            changed();
        }
        if (draftDirty && System.currentTimeMillis() - draftChangedAt >= DEBOUNCE_MS) {
            flush();
        }
        boolean useDown = mc.options.keyUse.isDown();
        if (tool == Tool.GROUP && draft != null && groupMode == GroupMode.PAINT && useDown && !paintLocked
                && holdingWand && mc.gui.screen() == null && mc.player != null) {
            paint(mc.player);
        } else if (!useDown) {
            lastPainted = null;
            fullWarned = false;
            paintLocked = false;
        }
        useWasDown = useDown;
        if (holdingWand && tool != Tool.OFF && mc.player != null && ticks % INSPECT_EVERY == 0
                && editor.state() instanceof EditorState.Available) {
            mc.player.sendOverlayMessage(Component.literal(actionBar(mc.player)));
        }
    }

    public void click(Player player) {
        if (useWasDown) {
            return;
        }
        String problem = editor.cannotEdit();
        if (problem != null && tool != Tool.SELECT) {
            error(problem);
            return;
        }
        switch (tool) {
            case SELECT -> selectAtCrosshair(player);
            case POINT -> pointClick(player);
            case GROUP -> groupClick(player);
            case REGION -> regionClick(player);
            case OFF -> {
            }
        }
    }

    private void selectAtCrosshair(Player player) {
        Marker marker = pick(player, REACH);
        if (marker == null) {
            select(null, false);
            info("§7Nothing under the crosshair.");
            return;
        }
        select(new Selection(marker.type(), marker.id()), false);
        info("§7Selected " + MarkerText.summary(marker));
    }

    public void select(Selection selection, boolean highlight) {
        selected = selection;
        if (highlight && selection != null) {
            forced = selection;
            forcedUntil = System.currentTimeMillis() + HIGHLIGHT_MS;
        }
        changed();
    }

    public Marker current(Selection selection) {
        if (selection == null) {
            return null;
        }
        if (draft != null && selection.type() == MarkerType.GROUP && selection.id().equals(draft.id())) {
            flush();
            return draftBase;
        }
        return editor.store().get(selection.type(), selection.id());
    }

    public Marker selectedMarker() {
        return current(selected);
    }

    public Marker peekSelected() {
        if (selected == null) {
            return null;
        }
        if (draft != null && selected.type() == MarkerType.GROUP && selected.id().equals(draft.id())) {
            return draftBase;
        }
        return editor.store().get(selected.type(), selected.id());
    }

    private void pointClick(Player player) {
        if (player.isShiftKeyDown() && grabbed == null) {
            Point point = MarkerPick.point(ray(player, REACH), editor.store().all(MarkerType.POINT, Point.class));
            if (point == null) {
                error("Sneak + right-click a point to pick it up.");
                return;
            }
            grabbed = point.id();
            select(new Selection(MarkerType.POINT, point.id()), false);
            info("§7Moving §f" + point.id() + "§7: right-click a block to put it there.");
            return;
        }
        BlockHitResult hit = blockHit(player);
        if (hit == null) {
            return;
        }
        Vec3 spot = faceCentre(hit);
        float yaw = Mth.wrapDegrees(player.getYRot());
        if (grabbed != null) {
            Point before = (Point) editor.store().get(MarkerType.POINT, grabbed);
            String id = grabbed;
            grabbed = null;
            if (before == null) {
                error("Point " + id + " no longer exists.");
                return;
            }
            Point after = MarkerEdits.moved(before, spot.x, spot.y, spot.z, yaw);
            report(editor.edit(new MarkerHistory.Change(before, after)), "§7Moved §f" + id + "§7 to " + MarkerText.xyz(spot.x, spot.y, spot.z) + ".");
            return;
        }
        Set<String> taken = new HashSet<>(editor.store().markers(MarkerType.POINT).keySet());
        taken.addAll(reserved);
        String id = MarkerIds.next("point", taken);
        Point point = new Point(id, spot.x, spot.y, spot.z, yaw, 0, List.of(), Map.of());
        if (report(editor.edit(new MarkerHistory.Change(null, point)), "§7Placed §f" + id + "§7 at "
                + MarkerText.xyz(spot.x, spot.y, spot.z) + ".")) {
            reserved.add(id);
            select(new Selection(MarkerType.POINT, id), false);
        }
    }

    public boolean groupNew(String id) {
        String problem = MarkerCheck.id(id);
        if (problem != null) {
            error(problem);
            return false;
        }
        if (editor.store().get(MarkerType.GROUP, id) != null || draft != null && draft.id().equals(id)) {
            error("Group " + id + " already exists. /bs group edit " + id + " to change it.");
            return false;
        }
        openGroup(id, null);
        return true;
    }

    public boolean groupEdit(String id) {
        Marker marker = editor.store().get(MarkerType.GROUP, id);
        if (!(marker instanceof Group group)) {
            error("No group named " + id + ".");
            return false;
        }
        openGroup(id, group);
        return true;
    }

    private void openGroup(String id, Group base) {
        String problem = editor.cannotEdit();
        if (problem != null) {
            error(problem);
            return;
        }
        closeGroup();
        draft = new GroupDraft(id, base == null ? new long[0] : base.blocks());
        draftBase = base;
        draftDirty = false;
        groupMode = GroupMode.PAINT;
        groupCorner = null;
        selected = new Selection(MarkerType.GROUP, id);
        draftChanged();
        if (tool != Tool.GROUP) {
            tool = Tool.GROUP;
        }
        info("§7Editing group §f" + id + "§7 (" + draft.size() + " blocks). Hold right-click and drag to add, "
                + "sneak to remove.");
    }

    public void groupMode(boolean box) {
        if (draft == null) {
            error("No active group. /bs group new <id> or /bs group edit <id> first.");
            return;
        }
        if (tool != Tool.GROUP) {
            tool = Tool.GROUP;
        }
        groupMode = box ? GroupMode.BOX : GroupMode.FILL;
        groupCorner = null;
        changed();
        info(box ? "§7Box: right-click the first corner." : "§7Fill: right-click a block to add every connected block of that type.");
    }

    public void groupDone() {
        if (draft == null) {
            error("No active group.");
            return;
        }
        String id = draft.id();
        closeGroup();
        info("§7Closed group §f" + id + "§7.");
    }

    private void closeGroup() {
        if (draft == null) {
            return;
        }
        flush();
        draft = null;
        draftBase = null;
        groupCorner = null;
        groupMode = GroupMode.PAINT;
        draftChanged();
    }

    private void groupClick(Player player) {
        if (draft == null) {
            error("No active group. /bs group new <id> or /bs group edit <id> first.");
            return;
        }
        BlockHitResult hit = blockHit(player);
        if (hit == null) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        switch (groupMode) {
            case PAINT -> paintAt(pos, player.isShiftKeyDown());
            case BOX -> {
                if (groupCorner == null) {
                    groupCorner = pos;
                    paintLocked = true;
                    info("§7Box corner 1 at " + MarkerText.xyz(pos) + ". Right-click the opposite corner.");
                    return;
                }
                Shape.Box box = BlockSelection.bounds(groupCorner.getX(), groupCorner.getY(), groupCorner.getZ(),
                        pos.getX(), pos.getY(), pos.getZ());
                groupCorner = null;
                groupMode = GroupMode.PAINT;
                paintLocked = true;
                long volume = (long) (box.maxX() - box.minX() + 1) * (box.maxY() - box.minY() + 1) * (box.maxZ() - box.minZ() + 1);
                if (volume > MAX_BOX_VOLUME) {
                    error("That box is " + volume + " blocks; the most a group box scans is " + MAX_BOX_VOLUME + ".");
                    return;
                }
                BlockSelection.Result result = BlockSelection.box(box,
                        packed -> !player.level().getBlockState(BlockPos.of(packed)).isAir(),
                        EditorProtocol.MAX_GROUP_BLOCKS);
                addBlocks(result, "box");
            }
            case FILL -> {
                groupMode = GroupMode.PAINT;
                paintLocked = true;
                Block block = player.level().getBlockState(pos).getBlock();
                BlockSelection.Result result = BlockSelection.flood(BlockPosPacking.pack(pos.getX(), pos.getY(), pos.getZ()),
                        packed -> player.level().getBlockState(BlockPos.of(packed)).is(block),
                        EditorProtocol.MAX_GROUP_BLOCKS, BlockSelection.FILL_RADIUS);
                addBlocks(result, "fill");
            }
        }
    }

    private void addBlocks(BlockSelection.Result result, String what) {
        int added = draft.addAll(result.blocks());
        draftChanged();
        markDirty();
        String text = "§7" + capitalise(what) + " added §f" + added + "§7 blocks to §f" + draft.id() + "§7 (" + draft.size() + ").";
        info(text);
        if (draft.full() || result.capped()) {
            error("Group " + draft.id() + " is capped at " + EditorProtocol.MAX_GROUP_BLOCKS + " blocks; the rest were not added.");
        }
    }

    private void paint(Player player) {
        HitResult hit = player.pick(REACH, 1.0f, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            return;
        }
        BlockPos pos = block.getBlockPos();
        long packed = BlockPosPacking.pack(pos.getX(), pos.getY(), pos.getZ());
        if (lastPainted != null && lastPainted == packed) {
            return;
        }
        lastPainted = packed;
        paintAt(pos, player.isShiftKeyDown());
    }

    private void paintAt(BlockPos pos, boolean remove) {
        long packed = BlockPosPacking.pack(pos.getX(), pos.getY(), pos.getZ());
        if (remove) {
            if (draft.remove(packed)) {
                draftChanged();
                markDirty();
            }
            return;
        }
        switch (draft.add(packed)) {
            case ADDED -> {
                draftChanged();
                markDirty();
            }
            case FULL -> {
                if (!fullWarned) {
                    fullWarned = true;
                    error("Group " + draft.id() + " is full: " + EditorProtocol.MAX_GROUP_BLOCKS + " blocks at most.");
                }
            }
            case PRESENT -> {
            }
        }
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
        String problem = editor.edit(new MarkerHistory.Change(draftBase, after));
        if (problem != null) {
            error(problem);
            return;
        }
        draftBase = after;
    }

    public void regionBox(String id) {
        if (!checkRegionId(id)) {
            return;
        }
        resetRegion();
        regionId = id;
        tool = Tool.REGION;
        changed();
        info("§7Region box" + (id == null ? "" : " §f" + id) + "§7: right-click two corners.");
    }

    public void regionPoly(String id) {
        if (!checkRegionId(id)) {
            return;
        }
        resetRegion();
        regionId = id;
        regionPoly = true;
        polygon = new PolygonBuilder();
        tool = Tool.REGION;
        changed();
        info("§7Region polygon" + (id == null ? "" : " §f" + id) + "§7: right-click each vertex, then /bs region done.");
    }

    private boolean checkRegionId(String id) {
        if (id == null) {
            return true;
        }
        String problem = MarkerCheck.id(id);
        if (problem == null && editor.store().get(MarkerType.REGION, id) != null) {
            problem = "Region " + id + " already exists.";
        }
        if (problem != null) {
            error(problem);
            return false;
        }
        return true;
    }

    public void regionDone() {
        if (!regionPoly || polygon == null) {
            error("No polygon in progress. /bs region poly first.");
            return;
        }
        Shape.Polygon shape;
        try {
            shape = polygon.build(heightMin, heightMax);
        } catch (IllegalArgumentException e) {
            error(e.getMessage());
            return;
        }
        if (createRegion(shape)) {
            polygon = new PolygonBuilder();
            heightMin = null;
            heightMax = null;
        }
    }

    public void regionHeight(int minY, int maxY) {
        if (minY > maxY) {
            error("minY must not be above maxY.");
            return;
        }
        if (tool == Tool.REGION && (regionCorner != null || polygon != null && polygon.vertices() > 0)) {
            heightMin = minY;
            heightMax = maxY;
            info("§7The region being drawn will span Y §f" + minY + "§7 to §f" + maxY + "§7.");
            return;
        }
        if (selectedMarker() instanceof Region region) {
            Region after = MarkerEdits.withShape(region, MarkerEdits.withHeight(region.shape(), minY, maxY));
            report(editor.edit(new MarkerHistory.Change(region, after)), "§7Region §f" + region.id() + "§7 now spans Y §f"
                    + minY + "§7 to §f" + maxY + "§7.");
            return;
        }
        heightMin = minY;
        heightMax = maxY;
        info("§7The next region will span Y §f" + minY + "§7 to §f" + maxY + "§7.");
    }

    private void regionClick(Player player) {
        BlockHitResult hit = blockHit(player);
        if (hit == null) {
            return;
        }
        BlockPos pos = hit.getBlockPos();
        if (regionPoly) {
            if (polygon == null) {
                polygon = new PolygonBuilder();
            }
            String problem = polygon.add(pos.getX(), pos.getY(), pos.getZ());
            if (problem != null) {
                error(problem);
                return;
            }
            info("§7Vertex §f" + polygon.vertices() + "§7 at " + pos.getX() + " " + pos.getZ()
                    + (polygon.vertices() >= EditorProtocol.MIN_POLYGON_VERTICES ? ". /bs region done closes it." : "."));
            changed();
            return;
        }
        if (regionCorner == null) {
            regionCorner = pos;
            info("§7Region corner 1 at " + MarkerText.xyz(pos) + ". Right-click the opposite corner.");
            changed();
            return;
        }
        Shape.Box box = BlockSelection.bounds(regionCorner.getX(), regionCorner.getY(), regionCorner.getZ(),
                pos.getX(), pos.getY(), pos.getZ());
        if (heightMin != null) {
            box = (Shape.Box) MarkerEdits.withHeight(box, heightMin, heightMax);
        }
        regionCorner = null;
        if (createRegion(box)) {
            heightMin = null;
            heightMax = null;
        }
    }

    private boolean createRegion(Shape shape) {
        Set<String> taken = new HashSet<>(editor.store().markers(MarkerType.REGION).keySet());
        taken.addAll(reserved);
        String id = regionId != null ? regionId : MarkerIds.next("region", taken);
        Region region = new Region(id, shape, List.of(), Map.of());
        if (!report(editor.edit(new MarkerHistory.Change(null, region)), "§7Created region §f" + id + "§7 ("
                + MarkerText.shape(shape) + ").")) {
            return false;
        }
        reserved.add(id);
        regionId = null;
        select(new Selection(MarkerType.REGION, id), false);
        return true;
    }

    private void resetRegion() {
        regionPoly = false;
        regionId = null;
        regionCorner = null;
        polygon = null;
        heightMin = null;
        heightMax = null;
    }

    public boolean rename(String to) {
        Marker marker = requireSelected();
        if (marker == null) {
            return false;
        }
        String problem = MarkerCheck.id(to);
        if (problem == null && editor.store().get(marker.type(), to) != null) {
            problem = "A " + MarkerText.type(marker.type()) + " named " + to + " already exists.";
        }
        if (problem != null) {
            error(problem);
            return false;
        }
        if (!report(editor.edit(new MarkerHistory.Rename(marker.type(), marker.id(), to)), "§7Renamed §f"
                + marker.id() + "§7 to §f" + to + "§7.")) {
            return false;
        }
        renamed(marker.type(), marker.id(), to);
        return true;
    }

    public boolean delete() {
        Marker marker = requireSelected();
        if (marker == null) {
            return false;
        }
        if (draft != null && marker.type() == MarkerType.GROUP && marker.id().equals(draft.id())) {
            draft = null;
            draftBase = null;
            draftDirty = false;
            draftChanged();
        }
        if (!report(editor.edit(new MarkerHistory.Change(marker, null)), "§7Deleted " + MarkerText.summary(marker) + ".")) {
            return false;
        }
        select(null, false);
        return true;
    }

    public boolean change(Marker before, Marker after, String done) {
        if (before.equals(after)) {
            info("§7Nothing changed.");
            return false;
        }
        if (!report(editor.edit(new MarkerHistory.Change(before, after)), done)) {
            return false;
        }
        if (draft != null && after instanceof Group group && group.id().equals(draft.id())) {
            draftBase = group;
        }
        return true;
    }

    public Marker requireSelected() {
        String problem = editor.cannotEdit();
        if (problem != null) {
            error(problem);
            return null;
        }
        Marker marker = selectedMarker();
        if (marker == null) {
            error(selected == null ? "Nothing selected. Use the select tool or /bs list." : "The selected marker no longer exists.");
        }
        return marker;
    }

    public void undo(boolean redo) {
        if (draft != null) {
            flush();
        }
        MarkerHistory.Op op = redo ? editor.redo() : editor.undo();
        if (op == null) {
            error(editor.lastStepProblem());
            return;
        }
        info("§7" + (redo ? "Redid: " : "Undid: ") + MarkerText.op(op));
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
                    draftChanged();
                }
            }
            case MarkerHistory.Rename rename -> renamed(rename.type(), rename.from(), rename.to());
        }
    }

    private void renamed(MarkerType type, String from, String to) {
        if (selected != null && selected.type() == type && selected.id().equals(from)) {
            selected = new Selection(type, to);
        }
        if (type == MarkerType.POINT && from.equals(grabbed)) {
            grabbed = to;
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
        selected = null;
        forced = null;
        grabbed = null;
        reserved.clear();
        groupCorner = null;
        groupMode = GroupMode.PAINT;
        resetRegion();
        draftChanged();
    }

    private Marker pick(Player player, double reach) {
        MarkerStore store = editor.store();
        HitResult hit = player.pick(reach, 1.0f, false);
        Long block = hit instanceof BlockHitResult b && hit.getType() == HitResult.Type.BLOCK
                ? BlockPosPacking.pack(b.getBlockPos().getX(), b.getBlockPos().getY(), b.getBlockPos().getZ()) : null;
        List<Group> groups = new ArrayList<>(store.all(MarkerType.GROUP, Group.class));
        if (draft != null) {
            groups.removeIf(group -> group.id().equals(draft.id()));
            groups.add(new Group(draft.id(), draft.blocks(), List.of(), Map.of()));
        }
        Vec3 feet = player.position();
        return MarkerPick.pick(ray(player, reach), block, feet.x, feet.y, feet.z,
                store.all(MarkerType.POINT, Point.class), groups, store.all(MarkerType.REGION, Region.class));
    }

    private static MarkerPick.Ray ray(Player player, double reach) {
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getViewVector(1.0f);
        return new MarkerPick.Ray(eye.x, eye.y, eye.z, look.x, look.y, look.z, reach);
    }

    private static BlockHitResult blockHit(Player player) {
        HitResult hit = player.pick(REACH, 1.0f, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            error("Not looking at a block (within " + (int) REACH + ").");
            return null;
        }
        return block;
    }

    private static Vec3 faceCentre(BlockHitResult hit) {
        Direction face = hit.getDirection();
        return Vec3.atCenterOf(hit.getBlockPos()).add(face.getStepX() * 0.5, face.getStepY() * 0.5, face.getStepZ() * 0.5);
    }

    private String actionBar(Player player) {
        StringBuilder text = new StringBuilder("§6").append(name(tool)).append("§7");
        switch (tool) {
            case POINT -> {
                if (grabbed != null) {
                    text.append(" moving §f").append(grabbed).append("§7");
                }
            }
            case GROUP -> {
                if (draft == null) {
                    text.append(" (no active group)");
                } else {
                    text.append(" §f").append(draft.id()).append("§7 ").append(draft.size()).append("/")
                            .append(EditorProtocol.MAX_GROUP_BLOCKS);
                    if (groupMode == GroupMode.BOX) {
                        text.append(groupCorner == null ? " box: corner 1" : " box: corner 2");
                    } else if (groupMode == GroupMode.FILL) {
                        text.append(" fill: click a block");
                    }
                }
            }
            case REGION -> {
                if (regionPoly) {
                    text.append(" polygon, ").append(polygon == null ? 0 : polygon.vertices()).append(" vertices");
                } else {
                    text.append(regionCorner == null ? " box: corner 1" : " box: corner 2");
                }
            }
            default -> {
            }
        }
        Marker under = pick(player, INSPECT_REACH);
        if (under != null) {
            text.append(" §8| §f").append(MarkerText.type(under.type())).append(" §e").append(under.id());
            if (!under.tags().isEmpty()) {
                text.append(" §7[").append(String.join(", ", under.tags())).append("]");
            }
            text.append(" §7").append(under.props().size()).append(under.props().size() == 1 ? " prop" : " props");
        }
        return text.toString();
    }

    private String hint() {
        return switch (tool) {
            case SELECT -> "Right-click a point, group block or region.";
            case POINT -> "Right-click a block to place a point; sneak + right-click a point to move it.";
            case GROUP -> draft == null ? "/bs group new <id> or /bs group edit <id>." : "Drag to add, sneak-drag to remove.";
            case REGION -> "Right-click two corners, or /bs region poly.";
            case OFF -> "";
        };
    }

    private static String name(Tool tool) {
        return tool.name().toLowerCase(Locale.ROOT);
    }

    private static String capitalise(String text) {
        return Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }

    private void changed() {
        revision++;
    }

    private void draftChanged() {
        draftVersion++;
        revision++;
    }

    private static boolean report(String problem, String done) {
        if (problem != null) {
            error(problem);
            return false;
        }
        info(done);
        return true;
    }

    static void info(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }

    static void error(String text) {
        info("§c" + text);
    }
}
