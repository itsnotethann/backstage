package net.honormc.backstage.client.editor.ui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import net.honormc.backstage.client.editor.EditorTools;
import net.honormc.backstage.client.editor.EditorTools.Selection;
import net.honormc.backstage.client.editor.Outliner;
import net.honormc.backstage.client.editor.Theme;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;

final class OutlinerPanel {
    private static final int MAX_CHIPS = 8;

    private final EditorUi ui;
    private final ImString search = new ImString(128);
    private final Set<String> tagFilter = new LinkedHashSet<>();
    private final EnumSet<MarkerType> collapsed = EnumSet.noneOf(MarkerType.class);
    private boolean focus;
    private boolean dragging;

    OutlinerPanel(EditorUi ui) {
        this.ui = ui;
    }

    void focusSearch() {
        focus = true;
    }

    boolean cancel() {
        if (!search.get().isEmpty() || !tagFilter.isEmpty()) {
            search.set("");
            tagFilter.clear();
            return true;
        }
        return false;
    }

    void draw() {
        Kit kit = ui.kit;
        float x = ui.dockX();
        float top = ui.dockTop();
        float total = ui.dockBottom() - top;
        float h = (total - ui.s(10)) * ui.mode.layout().outlinerShare;
        float w = ui.s(ui.mode.layout().dockWidth);
        ui.begin("##outliner", x, top, w, h, 3, 1, 0, 0);
        ImDrawList dl = ImGui.getWindowDrawList();
        float wx = ImGui.getWindowPosX();
        float wy = ImGui.getWindowPosY();
        float pad = ui.s(12);
        float right = wx + w - pad;
        float c = wy + pad;
        List<Marker> all = new ArrayList<>(ui.mode.editor().store().all());
        if (ui.tools.draft() != null && ui.mode.editor().store().get(MarkerType.GROUP, ui.tools.draft().id()) == null) {
            all.add(ui.tools.current(new Selection(MarkerType.GROUP, ui.tools.draft().id())));
        }
        kit.text(dl, ui.fonts.semibold, 13, wx + pad, c, Theme.TEXT, 1, "Outliner");
        String count = all.size() + (all.size() == 1 ? " marker" : " markers");
        kit.text(dl, ui.fonts.mono, 11.5f, right - kit.width(ui.fonts.mono, 11.5f, count), c + ui.s(1), Theme.TEXT_3, 1, count);
        c += kit.size(13) + ui.s(10);

        float fieldW = w - pad * 2;
        kit.field("##search", search, "Search id or tag   Ctrl+F", wx + pad, c, fieldW, ImGuiInputTextFlags.None, focus);
        focus = false;
        c += kit.fieldHeight() + ui.s(8);

        Map<String, Integer> tagCounts = new HashMap<>();
        for (Marker marker : all) {
            for (String tag : marker.tags()) {
                tagCounts.merge(tag, 1, Integer::sum);
            }
        }
        tagFilter.retainAll(tagCounts.keySet());
        List<String> tags = tagCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Integer>comparingByValue().reversed().thenComparing(Map.Entry.comparingByKey()))
                .map(Map.Entry::getKey).limit(MAX_CHIPS).toList();
        List<String> chips = new ArrayList<>(tagFilter);
        for (String tag : tags) {
            if (!chips.contains(tag)) {
                chips.add(tag);
            }
        }
        if (!chips.isEmpty()) {
            float cx = wx + pad;
            float chipH = kit.chipHeight();
            for (String tag : chips) {
                boolean on = tagFilter.contains(tag);
                float cw = kit.chipWidth("#" + tag, on);
                if (cx + cw > right) {
                    cx = wx + pad;
                    c += chipH + ui.s(6);
                }
                int result = kit.chip("##tag" + tag, "#" + tag, on, on, cx, c);
                if (result == Kit.CHIP_CLICK || result == Kit.CHIP_CLOSE) {
                    if (on) {
                        tagFilter.remove(tag);
                    } else {
                        tagFilter.add(tag);
                    }
                }
                cx += cw + ui.s(6);
            }
            c += chipH + ui.s(10);
        }

        float listH = wy + h - c - ui.s(8);
        ImGui.setCursorScreenPos(wx + ui.s(6), c);
        ImGui.beginChild("##tree", w - ui.s(12), Math.max(ui.s(20), listH), false, ImGuiWindowFlags.NoBackground);
        tree(all);
        ImGui.endChild();
        splitter(wx, wy + h, w);
        ui.end();
    }

    private void tree(List<Marker> all) {
        Kit kit = ui.kit;
        ImDrawList dl = ImGui.getWindowDrawList();
        float wx = ImGui.getWindowPosX();
        float w = ImGui.getWindowWidth();
        float rowH = ui.s(28);
        String query = search.get();
        if (all.isEmpty()) {
            float y = ImGui.getCursorScreenPosY() + ui.s(6);
            dl.addText(ui.fonts.small, kit.size(11.5f), wx + ui.s(6), y, Kit.col(Theme.TEXT_3, 1),
                    "No markers yet. Press 2 and click to place a point.", null, w - ui.s(12));
            ImGui.dummy(w, ui.s(40));
            return;
        }
        boolean any = false;
        for (MarkerType type : MarkerType.values()) {
            List<Marker> markers = all.stream().filter(m -> m.type() == type)
                    .filter(m -> Outliner.matches(m, query, tagFilter))
                    .sorted(Comparator.comparing(Marker::id)).toList();
            if (markers.isEmpty()) {
                continue;
            }
            any = true;
            float y = ImGui.getCursorScreenPosY();
            ImGui.invisibleButton("##grp" + type, w, ui.s(26));
            if (ImGui.isItemClicked()) {
                if (!collapsed.remove(type)) {
                    collapsed.add(type);
                }
            }
            boolean closed = collapsed.contains(type);
            float hv = ui.anim.hover(ImGui.getID("##grp" + type), ImGui.isItemHovered());
            float chevron = ui.anim.approach(ImGui.getID("##chev" + type), closed ? 0 : 1, Anim.PANEL_MS);
            kit.text(dl, ui.fonts.iconsSmall, 12, wx + ui.s(4), y + ui.s(6), Theme.TEXT_3, 0.6f + 0.4f * hv,
                    chevron > 0.5f ? Icons.CHEVRON_DOWN : Icons.CHEVRON_RIGHT);
            kit.swatch(dl, wx + ui.s(20), y + ui.s(8), Theme.typeColour(type), 10);
            String label = switch (type) {
                case POINT -> "Points";
                case GROUP -> "Groups";
                case REGION -> "Regions";
            };
            kit.spaced(dl, ui.fonts.header, 11, wx + ui.s(36), y + ui.s(6), Theme.mix(Theme.TEXT_3, Theme.TEXT_2, hv), 1,
                    label.toUpperCase(Locale.ROOT), 0.4f);
            String count = Integer.toString(markers.size());
            kit.text(dl, ui.fonts.mono, 11.5f, wx + w - ui.s(10) - kit.width(ui.fonts.mono, 11.5f, count), y + ui.s(6), Theme.TEXT_3,
                    1, count);
            if (closed) {
                continue;
            }
            float viewTop = ImGui.getWindowPosY();
            float viewBottom = viewTop + ImGui.getWindowHeight();
            for (Marker marker : markers) {
                float ry = ImGui.getCursorScreenPosY();
                if (ry + rowH < viewTop - rowH || ry > viewBottom + rowH) {
                    ImGui.dummy(w, rowH);
                    continue;
                }
                row(dl, marker, wx, ry, w, rowH);
            }
        }
        if (!any) {
            float y = ImGui.getCursorScreenPosY() + ui.s(6);
            dl.addText(ui.fonts.small, kit.size(11.5f), wx + ui.s(6), y, Kit.col(Theme.TEXT_3, 1),
                    "Nothing matches. Clear the search or the tag filters.", null, w - ui.s(12));
            ImGui.dummy(w, ui.s(40));
        }
    }

    private void row(ImDrawList dl, Marker marker, float x, float y, float w, float h) {
        Kit kit = ui.kit;
        EditorTools tools = ui.tools;
        Selection key = new Selection(marker.type(), marker.id());
        boolean selected = tools.selected(key);
        boolean hidden = tools.hidden(key);
        String id = "##row" + marker.type() + marker.id();
        ImGui.setCursorScreenPos(x, y);
        ImGui.invisibleButton(id, w - ui.s(30), h);
        boolean hovered = ImGui.isItemHovered();
        boolean clicked = ImGui.isItemClicked();
        boolean doubled = hovered && ImGui.isMouseDoubleClicked(0);
        float hv = ui.anim.hover(ImGui.getID(id), hovered);
        float sel = ui.anim.approach(ImGui.getID(id + "#sel"), selected ? 1 : 0, Anim.HOVER_MS);
        float r = ui.s(Theme.RADIUS_SM);
        if (hv > 0) {
            dl.addRectFilled(x, y, x + w, y + h, Kit.col(Theme.WHITE, 0.05f * hv), r);
        }
        if (sel > 0) {
            dl.addRectFilledMultiColor(x, y, x + w, y + h, Kit.col(Theme.SPOT, 0.18f * sel), Kit.col(Theme.SPOT, 0.04f * sel),
                    Kit.col(Theme.SPOT, 0.04f * sel), Kit.col(Theme.SPOT, 0.18f * sel));
            dl.addRectFilled(x, y + ui.s(3), x + ui.s(2), y + h - ui.s(3), Kit.col(Theme.SPOT, sel), ui.s(1));
        }
        boolean editing = tools.draft() != null && marker.type() == MarkerType.GROUP && tools.draft().id().equals(marker.id());
        kit.swatch(dl, x + ui.s(10), y + (h - ui.s(10)) / 2, Theme.typeColour(marker.type()), 10);
        int text = selected ? Theme.TEXT : Theme.mix(Theme.TEXT_2, Theme.TEXT, hv);
        kit.text(dl, ui.fonts.body, 13, x + ui.s(29), y + (h - kit.size(13)) / 2, text, hidden ? 0.45f : 1, marker.id());
        String meta = meta(marker);
        float eyeX = x + w - ui.s(24);
        float mw = kit.width(ui.fonts.mono, 11, meta);
        kit.text(dl, ui.fonts.mono, 11, eyeX - ui.s(8) - mw, y + (h - kit.size(11)) / 2, editing ? Theme.SPOT : Theme.TEXT_3,
                hidden ? 0.5f : 1, editing ? "editing" : meta);
        if (kit.iconButton(id + "#eye", hidden ? Icons.EYE_OFF : Icons.EYE, eyeX - ui.s(2), y + ui.s(3), ui.s(22), h - ui.s(6),
                false, hidden ? "Show" : "Hide", List.of())) {
            tools.hide(key, !hidden);
        }
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        if (doubled) {
            ui.mode.flyTo(marker);
        } else if (clicked) {
            if (ImGui.getIO().getKeyCtrl()) {
                if (selected) {
                    tools.deselect(key);
                } else {
                    tools.select(key, true);
                }
            } else {
                tools.select(key, ImGui.getIO().getKeyShift());
            }
            if (tools.tool() == EditorTools.Tool.GROUP && marker instanceof Group) {
                tools.groupEdit(marker.id());
            }
        }
        ImGui.setCursorScreenPos(x, y + h);
        ImGui.dummy(w, 0);
    }

    static String meta(Marker marker) {
        return switch (marker) {
            case Point p -> Math.round(p.x()) + " " + Math.round(p.y()) + " " + Math.round(p.z());
            case Group g -> Integer.toString(g.blocks().length);
            case Region r -> switch (r.shape()) {
                case Shape.Box b -> "box · " + (b.maxX() - b.minX() + 1) + "×" + (b.maxZ() - b.minZ() + 1);
                case Shape.Polygon p -> "poly · " + p.vertices();
            };
        };
    }

    private void splitter(float x, float y, float w) {
        float h = ui.s(8);
        ImGui.setCursorScreenPos(x + ui.s(20), y - h);
        ImGui.invisibleButton("##split", w - ui.s(40), h);
        boolean hovered = ImGui.isItemHovered();
        if (ImGui.isItemActivated()) {
            dragging = true;
        }
        if (dragging && ImGui.isMouseDown(0)) {
            float top = ui.dockTop();
            float total = ui.dockBottom() - top - ui.s(10);
            ui.mode.layout().outlinerShare = Math.clamp((ImGui.getMousePosY() - top) / total, 0.2f, 0.8f);
        } else {
            dragging = false;
        }
        if (hovered || dragging) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeNS);
            ImGui.getForegroundDrawList().addLine(x + w * 0.35f, y + ui.s(5), x + w * 0.65f, y + ui.s(5),
                    Kit.col(Theme.SPOT, 0.6f), ui.s(2));
        }
    }
}
