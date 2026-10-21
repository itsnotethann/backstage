package net.honormc.backstage.client.editor.ui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiMouseCursor;
import imgui.type.ImString;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import net.honormc.backstage.client.BrushSession;
import net.honormc.backstage.client.editor.EditorLayout;
import net.honormc.backstage.client.editor.EditorShortcuts.Action;
import net.honormc.backstage.client.editor.EditorTools;
import net.honormc.backstage.client.editor.EditorTools.Tool;
import net.honormc.backstage.client.editor.Theme;
import net.honormc.backstage.client.lua.ScriptRuntime;
import net.honormc.backstage.protocol.EditorProtocol;
import net.minecraft.client.Minecraft;

final class ToolRail {
    record Slot(Tool tool, String icon, Action action, String number) {
    }

    static final List<Slot> SLOTS = List.of(
            new Slot(Tool.SELECT, Icons.POINTER, Action.TOOL_SELECT, "1"),
            new Slot(Tool.POINT, Icons.MAP_PIN, Action.TOOL_POINT, "2"),
            new Slot(Tool.GROUP, Icons.BRUSH, Action.TOOL_GROUP, "3"),
            new Slot(Tool.BOX, Icons.CUBE, Action.TOOL_BOX, "4"),
            new Slot(Tool.REGION, Icons.POLYGON, Action.TOOL_REGION, "5"),
            new Slot(Tool.MOVE, Icons.MOVE, Action.TOOL_MOVE, "6"),
            new Slot(Tool.BRUSH, Icons.SPARKLES, Action.TOOL_BRUSH, "7"));

    private final EditorUi ui;
    private final ImString newGroup = new ImString(80);
    private boolean naming;
    private boolean focusName;
    private final ImString minY = new ImString(16);
    private final ImString maxY = new ImString(16);
    private String scriptsFor;
    private List<String> scripts = List.of();
    private ScriptRuntime.Info info;
    private String infoFor;
    private long infoAt;
    private final Map<String, ImString> params = new HashMap<>();
    private boolean scriptMenu;

    ToolRail(EditorUi ui) {
        this.ui = ui;
    }

    void draw() {
        Kit kit = ui.kit;
        float pad = ui.s(7);
        float slot = ui.s(38);
        float gap = ui.s(4);
        float w = ui.s(52);
        float h = pad * 2 + SLOTS.size() * slot + (SLOTS.size() - 1) * gap + ui.s(9);
        float x = ui.s(Theme.MARGIN);
        float y = (ui.screenH() - h) / 2;
        ui.begin("##rail", x, y, w, h, 1, -1, 0, 0);
        ImDrawList dl = ImGui.getWindowDrawList();
        float wx = ImGui.getWindowPosX();
        float wy = ImGui.getWindowPosY();
        Tool active = ui.tools.tool();
        float targetY = 0;
        float cursor = wy + pad;
        for (int i = 0; i < SLOTS.size(); i++) {
            if (i == SLOTS.size() - 1) {
                cursor += ui.s(4);
                dl.addLine(wx + ui.s(13), cursor - gap / 2, wx + w - ui.s(13), cursor - gap / 2, Kit.col(Theme.EDGE, Theme.EDGE_HI_ALPHA), 1);
                cursor += ui.s(5);
            }
            if (SLOTS.get(i).tool() == active) {
                targetY = cursor - wy;
            }
            cursor += slot + gap;
        }
        int pillKey = ImGui.getID("##railpill");
        float pillY = wy + ui.anim.approach(pillKey, targetY, Anim.PANEL_MS);
        float px0 = wx + pad;
        float px1 = wx + w - pad;
        for (int i = 4; i >= 1; i--) {
            dl.addRectFilled(px0 - i * ui.s(2), pillY + ui.s(4) - i * ui.s(1.5f), px1 + i * ui.s(2), pillY + slot + ui.s(4) + i * ui.s(3),
                    Kit.col(Theme.SPOT, 0.07f), ui.s(9) + i * ui.s(2));
        }
        dl.addRectFilledMultiColor(px0, pillY, px1, pillY + slot, Kit.col(Theme.SPOT_LIGHT, 1), Kit.col(Theme.SPOT_LIGHT, 1),
                Kit.col(Theme.SPOT, 1), Kit.col(Theme.SPOT, 1));
        dl.addRect(px0, pillY, px1, pillY + slot, Kit.col(Theme.SPOT, 1), ui.s(9), 0, ui.s(2.5f));
        dl.addLine(px0 + ui.s(6), pillY + 1, px1 - ui.s(6), pillY + 1, Kit.col(Theme.WHITE, 0.5f), 1);
        cursor = wy + pad;
        for (int i = 0; i < SLOTS.size(); i++) {
            Slot s = SLOTS.get(i);
            if (i == SLOTS.size() - 1) {
                cursor += ui.s(9);
            }
            boolean on = s.tool() == active;
            boolean near = Math.abs(pillY - cursor) < slot * 0.5f;
            if (kit.iconButton("##tool" + i, "", px0, cursor, px1 - px0, slot, on, s.tool().title(), ui.caps(s.action()))) {
                ui.tools.tool(s.tool());
            }
            float hv = ui.anim.get(ImGui.getID("##tool" + i), 0);
            int rgb = near ? Theme.SPOT_INK : Theme.mix(Theme.TEXT_2, Theme.TEXT, hv);
            float iw = kit.width(ui.fonts.icons, 19, s.icon());
            kit.text(dl, ui.fonts.icons, 19, px0 + (px1 - px0 - iw) / 2, cursor + (slot - kit.size(19)) / 2, rgb, 1, s.icon());
            kit.text(dl, ui.fonts.monoSmall, 10, px1 - ui.s(9), cursor + slot - ui.s(13), near ? Theme.SPOT_INK : Theme.TEXT_2, 0.55f,
                    s.number());
            cursor += slot + gap;
        }
        ui.end();
        if (ui.mode.layout().optionsOpen) {
            options(x + w + ui.s(Theme.GAP), y);
        }
    }

    private void options(float x, float y) {
        Kit kit = ui.kit;
        EditorTools tools = ui.tools;
        float w = ui.s(230);
        float h = ui.measured("options", ui.s(120));
        float shown = ui.anim.approach(ImGui.getID("##optionsh"), h, Anim.PANEL_MS);
        ui.begin("##options", x, y, w, shown, 2, -1, 0, 0);
        ImDrawList dl = ImGui.getWindowDrawList();
        float wx = ImGui.getWindowPosX();
        float wy = ImGui.getWindowPosY();
        float pad = ui.s(12);
        float c = wy + pad;
        float right = wx + w - pad;
        dl.pushClipRect(wx, wy, wx + w, wy + shown, true);
        kit.header(dl, wx + pad, c, tools.tool().title());
        c += kit.size(11) + ui.s(10);
        switch (tools.tool()) {
            case GROUP -> c = group(dl, wx, c, w, pad, right);
            case BOX -> {
                c = label(dl, wx + pad, c, "Fill as");
                String[] options = {"Region", "Group"};
                float sw = kit.segmentedWidth(options);
                int next = kit.segmented("##boxas", options, tools.boxAsGroup ? 1 : 0, right - sw, c - ui.s(24));
                tools.boxAsGroup = next == 1;
                c += ui.s(4);
                c = hint(dl, wx + pad, c, w - pad * 2, tools.boxDraft() == null ? "Drag from block to block, then press Enter."
                        : "Drag a face handle to resize. Enter confirms.");
            }
            case REGION -> {
                c = label(dl, wx + pad, c, "Min Y");
                if (yField("##miny", minY, tools.polyMinY(), right - ui.s(80), c - ui.s(26), ui.s(80))) {
                    tools.polyHeights(parse(minY), tools.polyMaxY());
                }
                c = label(dl, wx + pad, c + ui.s(6), "Max Y");
                if (yField("##maxy", maxY, tools.polyMaxY(), right - ui.s(80), c - ui.s(26), ui.s(80))) {
                    tools.polyHeights(tools.polyMinY(), parse(maxY));
                }
                c = hint(dl, wx + pad, c + ui.s(4), w - pad * 2, "Empty uses the clicked blocks' heights. "
                        + tools.polygonDraft().size() + " vertices so far.");
            }
            case BRUSH -> c = brush(dl, wx, c, w, pad, right);
            case POINT -> c = hint(dl, wx + pad, c, w - pad * 2, "Click a block to place a point facing the way you look. "
                    + "Drag the ring to turn it; R snaps to 45°.");
            case MOVE -> c = hint(dl, wx + pad, c, w - pad * 2, tools.selection().isEmpty() ? "Select markers first."
                    : tools.selection().size() + " selected. Drag an arrow; hold Ctrl for free movement on points.");
            case SELECT -> c = hint(dl, wx + pad, c, w - pad * 2, "Click to pick, Shift-click to add, Ctrl-click to remove. "
                    + "Drag on empty space for a marquee.");
        }
        dl.popClipRect();
        ui.measure("options", c - wy + pad - ui.s(4));
        ui.end();
    }

    private float label(ImDrawList dl, float x, float y, String text) {
        Kit kit = ui.kit;
        kit.text(dl, ui.fonts.body, 13, x, y + ui.s(4), Theme.TEXT_2, 1, text);
        return y + ui.s(30);
    }

    private float hint(ImDrawList dl, float x, float y, float width, String text) {
        Kit kit = ui.kit;
        dl.addText(ui.fonts.small, kit.size(11.5f), x, y, Kit.col(Theme.TEXT_3, 1), text, null, width);
        float h = ui.fonts.small.calcTextSizeA(kit.size(11.5f), Float.MAX_VALUE, width, text).y;
        return y + h + ui.s(6);
    }

    private float group(ImDrawList dl, float wx, float c, float w, float pad, float right) {
        Kit kit = ui.kit;
        EditorTools tools = ui.tools;
        kit.text(dl, ui.fonts.body, 13, wx + pad, c + ui.s(3), Theme.TEXT_2, 1, "Editing");
        if (tools.draft() != null) {
            String id = tools.draft().id();
            float cw = kit.chipWidth(id, true);
            int result = kit.chip("##activegroup", id, true, true, right - cw, c);
            if (result == Kit.CHIP_CLOSE) {
                tools.closeGroup();
            }
        } else {
            kit.text(dl, ui.fonts.small, 11.5f, right - kit.width(ui.fonts.small, 11.5f, "none"), c + ui.s(4), Theme.TEXT_3, 1,
                    "none");
        }
        c += ui.s(30);
        if (naming) {
            boolean entered = kit.field("##newgroup", newGroup, "group id, Enter", wx + pad, c, w - pad * 2,
                    ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.CharsNoBlank, focusName);
            focusName = false;
            if (entered) {
                if (tools.groupNew(newGroup.get().trim())) {
                    naming = false;
                    newGroup.set("");
                }
            } else if (ImGui.isItemDeactivated() && !ImGui.isItemFocused()) {
                naming = false;
            }
            c += kit.fieldHeight() + ui.s(10);
        } else {
            if (kit.button("##addgroup", "+ Group", Kit.ButtonKind.PLAIN, wx + pad, c, w - pad * 2)) {
                naming = true;
                focusName = true;
            }
            c += kit.buttonHeight() + ui.s(10);
        }
        kit.text(dl, ui.fonts.body, 13, wx + pad, c + ui.s(2), Theme.TEXT_2, 1, "Radius");
        float[] radius = {tools.brushRadius};
        float sliderX = wx + pad + ui.s(62);
        float sliderW = w - pad * 2 - ui.s(62) - ui.s(26);
        if (kit.slider("##radius", radius, 0, EditorLayout.MAX_BRUSH, sliderX, c + ui.s(10), sliderW, Theme.GROUP, Theme.GROUP_LIGHT)) {
            tools.brushRadius = (int) radius[0];
        }
        String r = Integer.toString(tools.brushRadius);
        kit.text(dl, ui.fonts.mono, 12, right - kit.width(ui.fonts.mono, 12, r), c + ui.s(3), Theme.TEXT, 1, r);
        c += ui.s(30);
        kit.text(dl, ui.fonts.body, 13, wx + pad, c + ui.s(5), Theme.TEXT_2, 1, "Mode");
        String[] modes = {"Paint", "Erase", "Fill"};
        float sw = kit.segmentedWidth(modes);
        int mode = kit.segmented("##brushmode", modes, tools.brushMode.ordinal(), right - sw, c);
        tools.brushMode = EditorTools.BrushMode.values()[mode];
        c += ui.s(36);
        kit.text(dl, ui.fonts.body, 13, wx + pad, c + ui.s(1), Theme.TEXT_2, 1, "Same block only");
        tools.sameBlock = kit.toggle("##sameblock", tools.sameBlock, right - ui.s(30), c, Theme.GROUP);
        c += ui.s(30);
        int size = tools.draft() == null ? 0 : tools.draft().size();
        kit.text(dl, ui.fonts.small, 11.5f, wx + pad, c, Theme.TEXT_3, 1, "Blocks");
        String count = String.format(Locale.ROOT, "%,d / %,d", size, EditorProtocol.MAX_GROUP_BLOCKS);
        kit.text(dl, ui.fonts.mono, 12, right - kit.width(ui.fonts.mono, 12, count), c - ui.s(1), size >= EditorProtocol.MAX_GROUP_BLOCKS
                ? Theme.DANGER : Theme.TEXT_2, 1, count);
        c += kit.size(12) + ui.s(6);
        float fraction = ui.anim.approach(ImGui.getID("##meter"), size / (float) EditorProtocol.MAX_GROUP_BLOCKS, Anim.PANEL_MS);
        kit.meter(wx + pad, c, w - pad * 2, fraction, Theme.GROUP, Theme.OK);
        return c + ui.s(10);
    }

    private boolean yField(String id, ImString buffer, Integer value, float x, float y, float w) {
        String text = value == null ? "" : value.toString();
        if (!ImGui.isAnyItemActive() && !buffer.get().equals(text)) {
            buffer.set(text);
        }
        boolean entered = ui.kit.field(id, buffer, "auto", x, y, w, ImGuiInputTextFlags.CharsDecimal
                | ImGuiInputTextFlags.EnterReturnsTrue, false);
        return entered || ImGui.isItemDeactivatedAfterEdit();
    }

    private static Integer parse(ImString buffer) {
        String text = buffer.get().trim();
        if (text.isEmpty() || text.equals("-")) {
            return null;
        }
        try {
            return Integer.parseInt(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private float brush(ImDrawList dl, float wx, float c, float w, float pad, float right) {
        Kit kit = ui.kit;
        BrushSession brushes = ui.tools.brushes();
        long now = System.currentTimeMillis();
        if (scriptsFor == null || now - infoAt > 3000) {
            scripts = brushes.store().list();
            scriptsFor = "";
            infoAt = now;
            infoFor = null;
        }
        kit.text(dl, ui.fonts.body, 13, wx + pad, c + ui.s(6), Theme.TEXT_2, 1, "Script");
        String current = brushes.script() == null ? "Choose..." : brushes.script();
        float bw = w - pad * 2 - ui.s(60);
        if (kit.button("##script", current, Kit.ButtonKind.PLAIN, right - bw, c, bw)) {
            scriptMenu = !scriptMenu;
        }
        c += kit.buttonHeight() + ui.s(8);
        if (scriptMenu) {
            if (scripts.isEmpty()) {
                c = hint(dl, wx + pad, c, w - pad * 2, "No scripts. Put .lua files in " + brushes.store().dir().getFileName() + ".");
            }
            for (String name : scripts) {
                ImGui.setCursorScreenPos(wx + pad, c);
                boolean clicked = ImGui.invisibleButton("##sc" + name, w - pad * 2, ui.s(24));
                float hv = ui.anim.hover(ImGui.getID("##sc" + name), ImGui.isItemHovered());
                dl.addRectFilled(wx + pad, c, right, c + ui.s(24), Kit.col(Theme.WHITE, 0.03f + 0.06f * hv), ui.s(6));
                kit.text(dl, ui.fonts.body, 12, wx + pad + ui.s(8), c + ui.s(5), name.equals(brushes.script()) ? Theme.SPOT : Theme.TEXT,
                        1, name);
                if (ImGui.isItemHovered()) {
                    ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
                }
                if (clicked) {
                    brushes.script(name);
                    scriptMenu = false;
                    infoFor = null;
                    params.clear();
                }
                c += ui.s(26);
            }
            c += ui.s(6);
        }
        if (brushes.script() == null) {
            return hint(dl, wx + pad, c, w - pad * 2, "Pick a script, then click a block to run it. Shift-click previews.");
        }
        if (!brushes.script().equals(infoFor)) {
            infoFor = brushes.script();
            info = describe(brushes);
        }
        if (info == null) {
            return hint(dl, wx + pad, c, w - pad * 2, "This script could not be read.");
        }
        if (!info.description().isEmpty()) {
            c = hint(dl, wx + pad, c, w - pad * 2, info.description());
        }
        Map<String, String> overrides = brushes.overrides();
        for (Map.Entry<String, String> param : info.params().entrySet()) {
            String key = param.getKey();
            String value = overrides.getOrDefault(key, param.getValue());
            kit.text(dl, ui.fonts.mono, 12, wx + pad, c + ui.s(6), Theme.TEXT, 1, key);
            float fx = wx + pad + ui.s(86);
            float fw = right - fx;
            if (value.equals("true") || value.equals("false")) {
                boolean next = kit.toggle("##p" + key, value.equals("true"), right - ui.s(30), c + ui.s(3), Theme.SPOT);
                if (next != value.equals("true")) {
                    brushes.setParam(key, Boolean.toString(next));
                }
                c += ui.s(28);
            } else if (isNumber(value)) {
                Double next = kit.number("##p" + key, null, 0, Double.parseDouble(value), value.contains(".") ? 0.1 : 1,
                        value.contains(".") ? 2 : 0, fx, c, fw);
                if (next != null) {
                    String text = value.contains(".") ? Kit.format(next, 2) : Long.toString(Math.round(next));
                    brushes.setParam(key, text);
                }
                c += kit.numberHeight() + ui.s(6);
            } else {
                ImString buffer = params.computeIfAbsent(key, k -> new ImString(value, 256));
                if (kit.field("##p" + key, buffer, param.getValue(), fx, c, fw, ImGuiInputTextFlags.EnterReturnsTrue, false)
                        || ImGui.isItemDeactivatedAfterEdit()) {
                    brushes.setParam(key, buffer.get());
                }
                c += kit.fieldHeight() + ui.s(6);
            }
        }
        if (info.mode().equals("paint")) {
            kit.text(dl, ui.fonts.body, 13, wx + pad, c + ui.s(2), Theme.TEXT_2, 1, "Radius");
            float[] radius = {brushes.radius()};
            if (kit.slider("##luaradius", radius, 1, 32, wx + pad + ui.s(62), c + ui.s(10), w - pad * 2 - ui.s(90), Theme.SPOT,
                    Theme.SPOT_LIGHT)) {
                brushes.radius((int) radius[0]);
            }
            String r = Integer.toString(brushes.radius());
            kit.text(dl, ui.fonts.mono, 12, right - kit.width(ui.fonts.mono, 12, r), c + ui.s(3), Theme.TEXT, 1, r);
            c += ui.s(30);
        }
        kit.text(dl, ui.fonts.body, 13, wx + pad, c + ui.s(1), Theme.TEXT_2, 1, "Face me");
        boolean rotate = kit.toggle("##rotate", brushes.rotate(), right - ui.s(30), c, Theme.SPOT);
        brushes.rotate(rotate);
        c += ui.s(30);
        float half = (w - pad * 2 - ui.s(6)) / 2;
        if (kit.button("##apply", "Apply preview", Kit.ButtonKind.PRIMARY, wx + pad, c, half)) {
            brushes.applyPreview();
        }
        if (kit.button("##clear", "Clear", Kit.ButtonKind.PLAIN, wx + pad + half + ui.s(6), c, half)) {
            brushes.clearPreview();
        }
        return c + kit.buttonHeight() + ui.s(4);
    }

    private static boolean isNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private static ScriptRuntime.Info describe(BrushSession brushes) {
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.level == null) {
            return null;
        }
        try {
            String source = brushes.store().read(brushes.script());
            return new ScriptRuntime(mc.level, line -> {
            }).describe(brushes.script(), source);
        } catch (Exception e) {
            return null;
        }
    }
}
