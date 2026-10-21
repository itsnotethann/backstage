package net.honormc.backstage.client.editor.ui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiHoveredFlags;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Consumer;
import net.honormc.backstage.client.editor.EditorTools;
import net.honormc.backstage.client.editor.EditorTools.Selection;
import net.honormc.backstage.client.editor.GroupOutline;
import net.honormc.backstage.client.editor.MarkerCheck;
import net.honormc.backstage.client.editor.MarkerEdits;
import net.honormc.backstage.client.editor.MarkerHistory;
import net.honormc.backstage.client.editor.MultiEdits;
import net.honormc.backstage.client.editor.PropParser;
import net.honormc.backstage.client.editor.Theme;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Prop;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;
import net.minecraft.core.registries.BuiltInRegistries;

final class InspectorPanel {
    private static final long CONFIRM_MS = 3_000;
    private static final String[] TYPES = {"str", "num", "bool", "block", "item"};

    private final EditorUi ui;
    private final ImString id = new ImString(80);
    private Selection idFor;
    private final ImString tagInput = new ImString(80);
    private boolean addingTag;
    private boolean focusTag;
    private final ImString propKey = new ImString(80);
    private boolean addingProp;
    private boolean focusProp;
    private final Map<String, ImString> propText = new HashMap<>();
    private Selection propsFor;
    private long confirmAt;
    private String typeMenu;
    private String blockMenu;
    private final ImString blockSearch = new ImString(80);
    private List<String> blocks;

    InspectorPanel(EditorUi ui) {
        this.ui = ui;
    }

    boolean cancel() {
        if (typeMenu != null || blockMenu != null || addingTag || addingProp || confirmAt != 0) {
            typeMenu = null;
            blockMenu = null;
            addingTag = false;
            addingProp = false;
            confirmAt = 0;
            return true;
        }
        return false;
    }

    void draw() {
        Kit kit = ui.kit;
        float x = ui.dockX();
        float top = ui.dockTop();
        float total = ui.dockBottom() - top;
        float outlinerH = (total - ui.s(10)) * ui.mode.layout().outlinerShare;
        float y = top + outlinerH + ui.s(10);
        float h = ui.dockBottom() - y;
        float w = ui.s(ui.mode.layout().dockWidth);
        ui.begin("##inspector", x, y, w, h, 4, 1, 0, 0);
        ImDrawList dl = ImGui.getWindowDrawList();
        float wx = ImGui.getWindowPosX();
        float wy = ImGui.getWindowPosY();
        float pad = ui.s(12);
        float c = wy + pad;
        kit.text(dl, ui.fonts.semibold, 13, wx + pad, c, Theme.TEXT, 1, "Inspector");
        float kw = kit.kbdWidth("F");
        kit.kbd(dl, wx + w - pad - kw, c - ui.s(1), "F");
        c += kit.size(13) + ui.s(10);
        ImGui.setCursorScreenPos(wx + pad, c);
        ImGui.beginChild("##inspectorbody", w - pad * 2 + ui.s(6), wy + h - c - ui.s(8), false, ImGuiWindowFlags.NoBackground);
        List<Marker> markers = ui.tools.selectedMarkers();
        if (markers.isEmpty()) {
            empty();
        } else if (markers.size() == 1) {
            single(markers.getFirst());
        } else {
            multi(markers);
        }
        ImGui.endChild();
        ui.end();
    }

    private void empty() {
        Kit kit = ui.kit;
        ImDrawList dl = ImGui.getWindowDrawList();
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY() + ui.s(4);
        dl.addText(ui.fonts.small, kit.size(11.5f), x, y, Kit.col(Theme.TEXT_3, 1),
                "Nothing selected. Click a marker in the world or the Outliner.", null, ImGui.getWindowWidth() - ui.s(12));
        ImGui.dummy(10, ui.s(40));
    }

    private float width() {
        return ImGui.getWindowWidth() - ui.s(8);
    }

    private void single(Marker marker) {
        Kit kit = ui.kit;
        EditorTools tools = ui.tools;
        ImDrawList dl = ImGui.getWindowDrawList();
        float x = ImGui.getCursorScreenPosX();
        float c = ImGui.getCursorScreenPosY();
        float w = width();
        Selection key = new Selection(marker.type(), marker.id());
        if (!key.equals(idFor) || !ImGui.isAnyItemActive() && !id.get().equals(marker.id())) {
            id.set(marker.id());
            idFor = key;
        }
        if (!key.equals(propsFor)) {
            propsFor = key;
            propText.clear();
            confirmAt = 0;
            addingProp = false;
            addingTag = false;
        }
        float fh = kit.fieldHeight() + ui.s(4);
        dl.addRectFilled(x, c, x + w, c + fh, Kit.col(Theme.SHADOW, 0.25f), ui.s(Theme.RADIUS_SM));
        dl.addRect(x, c, x + w, c + fh, Kit.col(Theme.EDGE, Theme.EDGE_HI_ALPHA), ui.s(Theme.RADIUS_SM), 0, 1);
        String badge = marker.type().name();
        float bw = kit.width(ui.fonts.badge, 10.5f, badge) + ui.s(14);
        float bh = kit.size(10.5f) + ui.s(6);
        float by = c + (fh - bh) / 2;
        dl.addRectFilled(x + ui.s(10), by, x + ui.s(10) + bw, by + bh, Kit.col(Theme.typeColour(marker.type()), 1), ui.s(5));
        kit.text(dl, ui.fonts.badge, 10.5f, x + ui.s(17), by + ui.s(3), Theme.BADGE_INK, 1, badge);
        String meta = OutlinerPanel.meta(marker) + (marker instanceof Group ? " blocks" : "");
        float mw = kit.width(ui.fonts.mono, 11.5f, meta);
        kit.text(dl, ui.fonts.mono, 11.5f, x + w - ui.s(10) - mw, c + (fh - kit.size(11.5f)) / 2, Theme.TEXT_3, 1, meta);
        float fx = x + ui.s(10) + bw + ui.s(2);
        ImGui.pushFont(ui.fonts.semibold, kit.size(13));
        boolean renamed = kit.field("##id", id, "id", fx, c + ui.s(2), x + w - ui.s(14) - mw - fx,
                ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.CharsNoBlank, false);
        ImGui.popFont();
        if (renamed) {
            tools.rename(marker, id.get().trim());
        }
        if (ImGui.isItemHovered()) {
            kit.tooltip("Edit and press Enter to rename", List.of("Enter"));
        }
        c += fh + ui.s(14);
        c = geometry(dl, marker, x, c, w);
        c = tags(dl, marker, x, c, w);
        c = props(dl, marker, x, c, w);
        c += ui.s(8);
        float bhh = kit.buttonHeight();
        boolean confirming = confirmAt != 0 && System.currentTimeMillis() - confirmAt < CONFIRM_MS;
        if (!confirming) {
            confirmAt = 0;
        }
        String label = confirming ? "Confirm delete" : "Delete";
        float dw = kit.buttonWidth(label, ui.s(80));
        if (kit.button("##delete", label, Kit.ButtonKind.DANGER, x + w - dw, c, dw)) {
            if (confirming) {
                confirmAt = 0;
                tools.delete();
            } else {
                confirmAt = System.currentTimeMillis();
            }
        }
        if (marker instanceof Group && tools.tool() != EditorTools.Tool.GROUP) {
            if (kit.button("##editblocks", "Edit blocks", Kit.ButtonKind.PLAIN, x, c, 0)) {
                tools.tool(EditorTools.Tool.GROUP);
                tools.groupEdit(marker.id());
            }
        }
        ImGui.setCursorScreenPos(x, c + bhh + ui.s(8));
        ImGui.dummy(w, 1);
    }

    private float geometry(ImDrawList dl, Marker marker, float x, float c, float w) {
        Kit kit = ui.kit;
        EditorTools tools = ui.tools;
        float gap = ui.s(6);
        float cell = (w - gap * 2) / 3;
        switch (marker) {
            case Point p -> {
                kit.header(dl, x, c, "Position");
                c += kit.size(11) + ui.s(6);
                Double nx = kit.number("##px", "X", Theme.AXIS_X, p.x(), 0.25, 2, x, c, cell);
                Double ny = kit.number("##py", "Y", Theme.AXIS_Y, p.y(), 0.25, 2, x + cell + gap, c, cell);
                Double nz = kit.number("##pz", "Z", Theme.AXIS_Z, p.z(), 0.25, 2, x + (cell + gap) * 2, c, cell);
                c += kit.numberHeight() + ui.s(8);
                Double yaw = kit.number("##pyaw", "YAW", Theme.POINT, p.yaw(), 5, 1, x, c, cell * 1.5f + gap / 2);
                Double pitch = kit.number("##ppitch", "PITCH", Theme.TEXT_2, p.pitch(), 5, 1, x + cell * 1.5f + gap * 1.5f, c,
                        cell * 1.5f + gap / 2);
                c += kit.numberHeight() + ui.s(14);
                if (nx != null || ny != null || nz != null || yaw != null || pitch != null) {
                    tools.change(p, new Point(p.id(), nx != null ? nx : p.x(), ny != null ? ny : p.y(), nz != null ? nz : p.z(),
                            yaw != null ? yaw.floatValue() : p.yaw(), pitch != null ? (float) Math.clamp(pitch, -90, 90) : p.pitch(),
                            p.tags(), p.props()));
                }
            }
            case Group g -> {
                kit.header(dl, x, c, "Bounds");
                c += kit.size(11) + ui.s(6);
                GroupOutline.Shape b = GroupOutline.bounds(g.blocks());
                if (g.blocks().length > 0) {
                    bound(dl, "X", Theme.AXIS_X, b.minX(), b.maxX(), x, c, cell);
                    bound(dl, "Y", Theme.AXIS_Y, b.minY(), b.maxY(), x + cell + gap, c, cell);
                    bound(dl, "Z", Theme.AXIS_Z, b.minZ(), b.maxZ(), x + (cell + gap) * 2, c, cell);
                    c += kit.numberHeight() + ui.s(14);
                } else {
                    kit.text(dl, ui.fonts.small, 11.5f, x, c, Theme.TEXT_3, 1, "No blocks yet. Paint some with the group brush.");
                    c += ui.s(24);
                }
            }
            case Region r -> {
                switch (r.shape()) {
                    case Shape.Box b -> {
                        kit.header(dl, x, c, "Min");
                        c += kit.size(11) + ui.s(6);
                        Double x0 = kit.number("##bx0", "X", Theme.AXIS_X, b.minX(), 1, 0, x, c, cell);
                        Double y0 = kit.number("##by0", "Y", Theme.AXIS_Y, b.minY(), 1, 0, x + cell + gap, c, cell);
                        Double z0 = kit.number("##bz0", "Z", Theme.AXIS_Z, b.minZ(), 1, 0, x + (cell + gap) * 2, c, cell);
                        c += kit.numberHeight() + ui.s(10);
                        kit.header(dl, x, c, "Max");
                        c += kit.size(11) + ui.s(6);
                        Double x1 = kit.number("##bx1", "X", Theme.AXIS_X, b.maxX(), 1, 0, x, c, cell);
                        Double y1 = kit.number("##by1", "Y", Theme.AXIS_Y, b.maxY(), 1, 0, x + cell + gap, c, cell);
                        Double z1 = kit.number("##bz1", "Z", Theme.AXIS_Z, b.maxZ(), 1, 0, x + (cell + gap) * 2, c, cell);
                        c += kit.numberHeight() + ui.s(14);
                        if (x0 != null || y0 != null || z0 != null || x1 != null || y1 != null || z1 != null) {
                            int ax = (int) Math.rint(x0 != null ? x0 : b.minX());
                            int ay = (int) Math.rint(y0 != null ? y0 : b.minY());
                            int az = (int) Math.rint(z0 != null ? z0 : b.minZ());
                            int bx = (int) Math.rint(x1 != null ? x1 : b.maxX());
                            int bY = (int) Math.rint(y1 != null ? y1 : b.maxY());
                            int bz = (int) Math.rint(z1 != null ? z1 : b.maxZ());
                            Shape.Box next = new Shape.Box(Math.min(ax, bx), Math.min(ay, bY), Math.min(az, bz), Math.max(ax, bx),
                                    Math.max(ay, bY), Math.max(az, bz));
                            tools.change(r, MarkerEdits.withShape(r, next));
                        }
                    }
                    case Shape.Polygon p -> {
                        kit.header(dl, x, c, "Polygon · " + p.vertices() + " vertices");
                        c += kit.size(11) + ui.s(6);
                        float half = (w - gap) / 2;
                        Double minY = kit.number("##pminy", "MIN Y", Theme.AXIS_Y, p.minY(), 1, 0, x, c, half);
                        Double maxY = kit.number("##pmaxy", "MAX Y", Theme.AXIS_Y, p.maxY(), 1, 0, x + half + gap, c, half);
                        c += kit.numberHeight() + ui.s(14);
                        if (minY != null || maxY != null) {
                            int lo = (int) Math.rint(minY != null ? minY : p.minY());
                            int hi = (int) Math.rint(maxY != null ? maxY : p.maxY());
                            tools.change(r, MarkerEdits.withShape(r, MarkerEdits.withHeight(p, Math.min(lo, hi), Math.max(lo, hi))));
                        }
                    }
                }
            }
        }
        return c;
    }

    private void bound(ImDrawList dl, String axis, int colour, int min, int max, float x, float y, float w) {
        Kit kit = ui.kit;
        float h = kit.numberHeight();
        dl.addRectFilled(x, y, x + w, y + h, Kit.col(Theme.WHITE, 0.04f), ui.s(7));
        dl.addRect(x, y, x + w, y + h, Kit.col(Theme.EDGE, Theme.EDGE_ALPHA), ui.s(7), 0, 1);
        kit.text(dl, ui.fonts.badge, 10.5f, x + ui.s(8), y + (h - kit.size(10.5f)) / 2, colour, 1, axis);
        kit.text(dl, ui.fonts.mono, 12, x + ui.s(22), y + (h - kit.size(12)) / 2, Theme.TEXT, 1, min + " → " + max);
    }

    private float tags(ImDrawList dl, Marker marker, float x, float c, float w) {
        Kit kit = ui.kit;
        kit.header(dl, x, c, "Tags");
        c += kit.size(11) + ui.s(8);
        float cx = x;
        float chipH = kit.chipHeight();
        for (String tag : marker.tags()) {
            float cw = kit.chipWidth(tag, true);
            if (cx + cw > x + w) {
                cx = x;
                c += chipH + ui.s(6);
            }
            if (kit.chip("##t" + tag, tag, false, true, cx, c) == Kit.CHIP_CLOSE) {
                ui.tools.change(marker, MarkerEdits.removeTag(marker, tag));
            }
            cx += cw + ui.s(6);
        }
        c = tagAdder(cx, c, x, w, chipH, tag -> {
            String problem = MarkerCheck.tag(tag);
            if (problem != null) {
                ui.mode.toasts().push(Toasts.Kind.ERROR, problem, "");
                return;
            }
            ui.tools.change(marker, MarkerEdits.addTag(marker, tag));
        });
        return c + chipH + ui.s(14);
    }

    private float tagAdder(float cx, float c, float x, float w, float chipH, Consumer<String> add) {
        Kit kit = ui.kit;
        if (!addingTag) {
            float cw = kit.chipWidth("+ add", false);
            if (cx + cw > x + w) {
                cx = x;
                c += chipH + ui.s(6);
            }
            if (kit.chip("##addtag", "+ add", false, false, cx, c) == Kit.CHIP_CLICK) {
                addingTag = true;
                focusTag = true;
                tagInput.set("");
            }
            return c;
        }
        float fw = ui.s(140);
        if (cx + fw > x + w) {
            cx = x;
            c += chipH + ui.s(6);
        }
        boolean entered = kit.field("##tagin", tagInput, "tag, Enter", cx, c - ui.s(3), fw,
                ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.CharsNoBlank, focusTag);
        focusTag = false;
        boolean active = ImGui.isItemActive();
        float fieldBottom = c - ui.s(3) + kit.fieldHeight();
        String typed = tagInput.get().trim().toLowerCase(Locale.ROOT);
        if (entered && !typed.isEmpty()) {
            add.accept(typed);
            tagInput.set("");
            focusTag = true;
        } else if (!active && !entered && ImGui.isItemDeactivated()) {
            addingTag = false;
        }
        if (active || addingTag) {
            List<String> suggestions = new ArrayList<>();
            TreeSet<String> known = new TreeSet<>();
            for (Marker m : ui.mode.editor().store().all()) {
                known.addAll(m.tags());
            }
            for (String tag : known) {
                if (!typed.isEmpty() && tag.startsWith(typed) && !tag.equals(typed) && suggestions.size() < 6) {
                    suggestions.add(tag);
                }
            }
            if (!suggestions.isEmpty() && active) {
                ImGui.setNextWindowPos(cx, fieldBottom + ui.s(4));
                ImGui.beginTooltip();
                ImDrawList tl = ImGui.getWindowDrawList();
                for (String suggestion : suggestions) {
                    float sy = ImGui.getCursorScreenPosY();
                    float sx = ImGui.getCursorScreenPosX();
                    ImGui.dummy(fw, ui.s(22));
                    kit.text(tl, ui.fonts.body, 12, sx + ui.s(8), sy + ui.s(4), Theme.TEXT_2, 1, typed);
                    kit.text(tl, ui.fonts.body, 12, sx + ui.s(8) + kit.width(ui.fonts.body, 12, typed), sy + ui.s(4), Theme.TEXT_3, 1,
                            suggestion.substring(typed.length()));
                }
                kit.text(tl, ui.fonts.small, 11, ImGui.getCursorScreenPosX() + ui.s(8), ImGui.getCursorScreenPosY(), Theme.TEXT_3, 1,
                        "Tab completes");
                ImGui.dummy(fw, ui.s(16));
                ImGui.endTooltip();
                if (ImGui.isKeyPressed(ImGuiKey.Tab)) {
                    tagInput.set(suggestions.getFirst());
                    focusTag = true;
                }
            }
        }
        return c;
    }

    private float props(ImDrawList dl, Marker marker, float x, float c, float w) {
        Kit kit = ui.kit;
        kit.header(dl, x, c, "Props");
        c += kit.size(11) + ui.s(6);
        float rowH = kit.fieldHeight() + ui.s(6);
        float keyW = w * 0.32f;
        float typeW = ui.s(44);
        float editorX = x + keyW + typeW + ui.s(8);
        float editorW = x + w - ui.s(26) - editorX;
        if (marker.props().isEmpty() && !addingProp) {
            kit.text(dl, ui.fonts.small, 11.5f, x, c + ui.s(2), Theme.TEXT_3, 1, "No props. Add typed values your scripts can read.");
            c += ui.s(22);
        }
        for (Map.Entry<String, Prop> entry : marker.props().entrySet()) {
            String key = entry.getKey();
            Prop prop = entry.getValue();
            dl.addLine(x, c, x + w, c, Kit.col(Theme.EDGE, Theme.EDGE_ALPHA), 1);
            float ry = c + ui.s(3);
            kit.text(dl, ui.fonts.mono, 12, x + ui.s(2), ry + (kit.fieldHeight() - kit.size(12)) / 2, Theme.TEXT, 1, ellipsize(key, keyW));
            String type = typeOf(prop);
            if (typeButton("##ty" + key, type, x + keyW, ry + ui.s(4))) {
                typeMenu = typeMenu != null && typeMenu.equals(key) ? null : key;
            }
            if (key.equals(typeMenu)) {
                String chosen = typeMenuPopup(x + keyW, ry + kit.fieldHeight());
                if (chosen != null) {
                    typeMenu = null;
                    if (!chosen.equals(type)) {
                        ui.tools.change(marker, MarkerEdits.setProp(marker, key, convert(prop, chosen)));
                        propText.remove(key);
                    }
                }
            }
            Prop next = valueEditor(key, prop, editorX, ry, editorW);
            if (next != null && !next.equals(prop)) {
                ui.tools.change(marker, MarkerEdits.setProp(marker, key, next));
            }
            if (kit.iconButton("##rm" + key, Icons.X, x + w - ui.s(22), ry + ui.s(2), ui.s(22), kit.fieldHeight() - ui.s(4), false,
                    "Remove prop", List.of())) {
                ui.tools.change(marker, MarkerEdits.removeProp(marker, key));
                propText.remove(key);
            }
            c += rowH;
        }
        c += ui.s(8);
        if (addingProp) {
            boolean entered = kit.field("##newprop", propKey, "prop key, Enter", x, c, w * 0.6f,
                    ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.CharsNoBlank, focusProp);
            focusProp = false;
            if (entered) {
                String key = propKey.get().trim();
                String problem = key.isEmpty() ? "A prop needs a key." : marker.props().containsKey(key) ? "Prop " + key
                        + " already exists." : null;
                if (problem != null) {
                    ui.mode.toasts().push(Toasts.Kind.ERROR, problem, "");
                } else {
                    ui.tools.change(marker, MarkerEdits.setProp(marker, key, new Prop.Str("")));
                    addingProp = false;
                }
            } else if (ImGui.isItemDeactivated()) {
                addingProp = false;
            }
            c += kit.fieldHeight() + ui.s(6);
        } else if (kit.button("##addprop", "+ Prop", Kit.ButtonKind.PLAIN, x, c, 0)) {
            addingProp = true;
            focusProp = true;
            propKey.set("");
        }
        return c;
    }

    private String ellipsize(String text, float width) {
        Kit kit = ui.kit;
        if (kit.width(ui.fonts.mono, 12, text) <= width - ui.s(6)) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && kit.width(ui.fonts.mono, 12, cut + "…") > width - ui.s(6)) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }

    private boolean typeButton(String id, String type, float x, float y) {
        Kit kit = ui.kit;
        ImDrawList dl = ImGui.getWindowDrawList();
        float w = kit.width(ui.fonts.badge, 10.5f, type) + ui.s(12);
        float h = kit.size(10.5f) + ui.s(4);
        ImGui.setCursorScreenPos(x, y);
        boolean clicked = ImGui.invisibleButton(id, w, h);
        float hv = ui.anim.hover(ImGui.getID(id), ImGui.isItemHovered());
        dl.addRectFilled(x, y, x + w, y + h, Kit.col(Theme.REGION, 0.16f + 0.1f * hv), ui.s(4));
        kit.text(dl, ui.fonts.badge, 10.5f, x + ui.s(6), y + ui.s(2), Theme.REGION, 1, type);
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
            kit.tooltip("Change type", List.of());
        }
        return clicked;
    }

    private String typeMenuPopup(float x, float y) {
        Kit kit = ui.kit;
        ImGui.setNextWindowPos(x, y);
        ImGui.beginTooltip();
        String chosen = null;
        ImDrawList dl = ImGui.getWindowDrawList();
        for (String type : TYPES) {
            float sx = ImGui.getCursorScreenPosX();
            float sy = ImGui.getCursorScreenPosY();
            ImGui.dummy(ui.s(80), ui.s(22));
            boolean hovered = ImGui.isMouseHoveringRect(sx, sy, sx + ui.s(80), sy + ui.s(22));
            if (hovered) {
                dl.addRectFilled(sx, sy, sx + ui.s(80), sy + ui.s(22), Kit.col(Theme.WHITE, 0.07f), ui.s(5));
                if (ImGui.isMouseClicked(0)) {
                    chosen = type;
                }
            }
            kit.text(dl, ui.fonts.mono, 12, sx + ui.s(8), sy + ui.s(4), Theme.TEXT, 1, type);
        }
        ImGui.endTooltip();
        return chosen;
    }

    private Prop valueEditor(String key, Prop prop, float x, float y, float w) {
        Kit kit = ui.kit;
        switch (prop) {
            case Prop.Bool b -> {
                boolean next = kit.toggle("##v" + key, b.value(), x, y + (kit.fieldHeight() - ui.s(18)) / 2, Theme.SPOT);
                return next != b.value() ? new Prop.Bool(next) : null;
            }
            case Prop.Num n -> {
                double step = n.value() == Math.rint(n.value()) ? 1 : 0.1;
                Double next = kit.number("##v" + key, null, 0, n.value(), step, step == 1 ? 0 : 2, x, y + ui.s(1), w);
                return next != null ? new Prop.Num(next) : null;
            }
            case Prop.BlockProp b -> {
                String label = b.state().startsWith("minecraft:") ? b.state().substring(10) : b.state();
                if (kit.button("##v" + key, ellipsize(label, w), Kit.ButtonKind.PLAIN, x, y + ui.s(1), w)) {
                    blockMenu = key.equals(blockMenu) ? null : key;
                    blockSearch.set("");
                }
                if (key.equals(blockMenu)) {
                    String picked = blockPicker(x, y + kit.fieldHeight() + ui.s(4), w);
                    if (picked != null) {
                        blockMenu = null;
                        return new Prop.BlockProp(picked);
                    }
                }
                return null;
            }
            case Prop.ListProp l -> {
                kit.text(ImGui.getWindowDrawList(), ui.fonts.small, 11.5f, x, y + ui.s(6), Theme.TEXT_3, 1,
                        "list of " + l.values().size());
                return null;
            }
            default -> {
                String text = switch (prop) {
                    case Prop.Str s -> s.value();
                    case Prop.ItemProp i -> i.snbt();
                    default -> "";
                };
                ImString buffer = propText.computeIfAbsent(key, k -> new ImString(text, 4096));
                if (!ImGui.isAnyItemActive() && !buffer.get().equals(text)) {
                    buffer.set(text);
                }
                boolean entered = kit.field("##v" + key, buffer, prop instanceof Prop.ItemProp ? "{id:\"minecraft:stone\"}" : "",
                        x, y + ui.s(1), w, ImGuiInputTextFlags.EnterReturnsTrue, false);
                if (entered || ImGui.isItemDeactivatedAfterEdit()) {
                    try {
                        return PropParser.parse(typeOf(prop), buffer.get());
                    } catch (IllegalArgumentException e) {
                        ui.mode.toasts().push(Toasts.Kind.ERROR, e.getMessage(), "");
                        buffer.set(text);
                    }
                }
                return null;
            }
        }
    }

    private String blockPicker(float x, float y, float w) {
        Kit kit = ui.kit;
        if (blocks == null) {
            blocks = BuiltInRegistries.BLOCK.keySet().stream().map(Object::toString)
                    .map(s -> s.startsWith("minecraft:") ? s.substring(10) : s).sorted().toList();
        }
        float pw = Math.max(w, ui.s(200));
        ImGui.setNextWindowPos(x + w - pw, y);
        ImGui.setNextWindowSize(pw, ui.s(260));
        ImGui.begin("##blockpicker", EditorUi.PANEL_FLAGS & ~ImGuiWindowFlags.NoFocusOnAppearing);
        ImDrawList dl = ImGui.getWindowDrawList();
        float wx = ImGui.getWindowPosX();
        float wy = ImGui.getWindowPosY();
        dl.pushClipRectFullScreen();
        kit.panel(ImGui.getBackgroundDrawList(), dl, wx, wy, wx + pw, wy + ui.s(260), 1);
        dl.popClipRect();
        kit.field("##blocksearch", blockSearch, "Search blocks", wx + ui.s(8), wy + ui.s(8), pw - ui.s(16), ImGuiInputTextFlags.None,
                ImGui.isWindowAppearing());
        String query = blockSearch.get().trim().toLowerCase(Locale.ROOT);
        float c = wy + ui.s(8) + kit.fieldHeight() + ui.s(6);
        ImGui.setCursorScreenPos(wx + ui.s(6), c);
        ImGui.beginChild("##blocklist", pw - ui.s(12), wy + ui.s(254) - c, false, ImGuiWindowFlags.NoBackground);
        String picked = null;
        int shown = 0;
        for (String block : blocks) {
            if (!query.isEmpty() && !block.contains(query)) {
                continue;
            }
            if (++shown > 200) {
                break;
            }
            float sx = ImGui.getCursorScreenPosX();
            float sy = ImGui.getCursorScreenPosY();
            if (ImGui.invisibleButton("##b" + block, pw - ui.s(20), ui.s(24))) {
                picked = "minecraft:" + block;
            }
            float hv = ui.anim.hover(ImGui.getID("##b" + block), ImGui.isItemHovered());
            ImDrawList ll = ImGui.getWindowDrawList();
            if (hv > 0) {
                ll.addRectFilled(sx, sy, sx + pw - ui.s(20), sy + ui.s(24), Kit.col(Theme.WHITE, 0.06f * hv), ui.s(6));
            }
            kit.text(ll, ui.fonts.mono, 12, sx + ui.s(8), sy + ui.s(5), Theme.TEXT_2, 1, block);
        }
        ImGui.endChild();
        boolean hovered = ImGui.isWindowHovered(ImGuiHoveredFlags.ChildWindows);
        ImGui.end();
        if (ImGui.isMouseClicked(0) && !hovered && picked == null) {
            blockMenu = null;
        }
        return picked;
    }

    private static String typeOf(Prop prop) {
        return switch (prop) {
            case Prop.Str s -> "str";
            case Prop.Num n -> "num";
            case Prop.Bool b -> "bool";
            case Prop.BlockProp b -> "block";
            case Prop.ItemProp i -> "item";
            case Prop.ListProp l -> "list";
        };
    }

    private static Prop convert(Prop prop, String type) {
        String text = switch (prop) {
            case Prop.Str s -> s.value();
            case Prop.Num n -> PropParser.describe(n).substring(4);
            case Prop.Bool b -> Boolean.toString(b.value());
            case Prop.BlockProp b -> b.state();
            case Prop.ItemProp i -> i.snbt();
            case Prop.ListProp l -> "";
        };
        try {
            return PropParser.parse(type, text);
        } catch (IllegalArgumentException e) {
            return switch (type) {
                case "num" -> new Prop.Num(0);
                case "bool" -> new Prop.Bool(false);
                case "block" -> new Prop.BlockProp("minecraft:stone");
                case "item" -> new Prop.ItemProp("{id:\"minecraft:stone\",count:1}");
                default -> new Prop.Str(text);
            };
        }
    }

    private void multi(List<Marker> markers) {
        Kit kit = ui.kit;
        ImDrawList dl = ImGui.getWindowDrawList();
        float x = ImGui.getCursorScreenPosX();
        float c = ImGui.getCursorScreenPosY();
        float w = width();
        int points = 0;
        int groups = 0;
        int regions = 0;
        for (Marker marker : markers) {
            switch (marker.type()) {
                case POINT -> points++;
                case GROUP -> groups++;
                case REGION -> regions++;
            }
        }
        kit.text(dl, ui.fonts.title, 15, x, c, Theme.TEXT, 1, markers.size() + " selected");
        c += kit.size(15) + ui.s(6);
        float cx = x;
        for (MarkerType type : MarkerType.values()) {
            int n = type == MarkerType.POINT ? points : type == MarkerType.GROUP ? groups : regions;
            if (n == 0) {
                continue;
            }
            kit.swatch(dl, cx, c + ui.s(3), Theme.typeColour(type), 10);
            String label = n + " " + type.name().toLowerCase(Locale.ROOT) + (n == 1 ? "" : "s");
            kit.text(dl, ui.fonts.small, 11.5f, cx + ui.s(15), c, Theme.TEXT_2, 1, label);
            cx += ui.s(15) + kit.width(ui.fonts.small, 11.5f, label) + ui.s(14);
        }
        c += ui.s(26);
        kit.header(dl, x, c, "Shared tags");
        c += kit.size(11) + ui.s(8);
        float chipH = kit.chipHeight();
        cx = x;
        for (String tag : MultiEdits.sharedTags(markers)) {
            float cw = kit.chipWidth(tag, true);
            if (cx + cw > x + w) {
                cx = x;
                c += chipH + ui.s(6);
            }
            if (kit.chip("##mt" + tag, tag, true, true, cx, c) == Kit.CHIP_CLOSE) {
                ui.tools.editAll(MultiEdits.removeTag(markers, tag));
            }
            cx += cw + ui.s(6);
        }
        c = tagAdder(cx, c, x, w, chipH, tag -> {
            String problem = MarkerCheck.tag(tag);
            if (problem != null) {
                ui.mode.toasts().push(Toasts.Kind.ERROR, problem, "");
                return;
            }
            List<MarkerHistory.Change> changes = MultiEdits.addTag(markers, tag);
            if (ui.tools.editAll(changes)) {
                ui.mode.toasts().push(Toasts.Kind.OK, "Tagged " + changes.size() + " markers", "#" + tag);
            }
        });
        c += chipH + ui.s(16);
        boolean confirming = confirmAt != 0 && System.currentTimeMillis() - confirmAt < CONFIRM_MS;
        if (!confirming) {
            confirmAt = 0;
        }
        String label = confirming ? "Confirm delete " + markers.size() : "Delete " + markers.size();
        float dw = kit.buttonWidth(label, ui.s(80));
        if (kit.button("##mdelete", label, Kit.ButtonKind.DANGER, x + w - dw, c, dw)) {
            if (confirming) {
                confirmAt = 0;
                ui.tools.delete();
            } else {
                confirmAt = System.currentTimeMillis();
            }
        }
        if (kit.button("##mdup", "Duplicate", Kit.ButtonKind.PLAIN, x, c, 0)) {
            ui.tools.duplicate();
        }
        ImGui.setCursorScreenPos(x, c + kit.buttonHeight() + ui.s(8));
        ImGui.dummy(w, 1);
    }
}
