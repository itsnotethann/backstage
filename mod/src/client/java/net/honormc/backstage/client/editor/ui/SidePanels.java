package net.honormc.backstage.client.editor.ui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import java.util.List;
import java.util.Locale;
import net.honormc.backstage.client.editor.EditorShortcuts;
import net.honormc.backstage.client.editor.MarkerHistory;
import net.honormc.backstage.client.editor.MarkerText;
import net.honormc.backstage.client.editor.Theme;

final class SidePanels {
    private record Fixed(String label, List<String> caps) {
    }

    boolean shortcutsOpen;
    private long shortcutsAt;
    private boolean wasOpen;

    private final EditorUi ui;

    SidePanels(EditorUi ui) {
        this.ui = ui;
    }

    void draw() {
        if (ui.mode.layout().historyOpen) {
            history();
        }
        if (shortcutsOpen && !wasOpen) {
            shortcutsAt = System.currentTimeMillis();
        }
        wasOpen = shortcutsOpen;
        if (shortcutsOpen) {
            shortcuts();
        }
    }

    private void history() {
        Kit kit = ui.kit;
        List<MarkerHistory.Op> undo = ui.mode.editor().history().undoSteps();
        List<MarkerHistory.Op> redo = ui.mode.editor().history().redoSteps();
        float w = ui.s(260);
        float rowH = ui.s(26);
        int rows = undo.size() + redo.size() + 1;
        float h = Math.min(ui.s(360), ui.s(48) + rowH * rows + ui.s(8));
        float x = ui.dockX() - ui.s(Theme.GAP) - w;
        float y = ui.dockBottom() - h;
        ui.begin("##history", x, y, w, h, 0, 1, 0, 0);
        ImDrawList dl = ImGui.getWindowDrawList();
        float wx = ImGui.getWindowPosX();
        float wy = ImGui.getWindowPosY();
        float pad = ui.s(12);
        kit.text(dl, ui.fonts.semibold, 13, wx + pad, wy + pad, Theme.TEXT, 1, "History");
        if (kit.iconButton("##historyclose", Icons.X, wx + w - pad - ui.s(20), wy + pad - ui.s(3), ui.s(20), ui.s(20), false,
                "Close", ui.caps(EditorShortcuts.Action.HISTORY))) {
            ui.mode.layout().historyOpen = false;
        }
        float top = wy + pad + kit.size(13) + ui.s(10);
        ImGui.setCursorScreenPos(wx + ui.s(6), top);
        ImGui.beginChild("##historylist", w - ui.s(12), wy + h - top - ui.s(8), false, ImGuiWindowFlags.NoBackground);
        ImDrawList ll = ImGui.getWindowDrawList();
        float lw = ImGui.getWindowWidth();
        float lx = ImGui.getWindowPosX();
        Integer jump = null;
        if (ImGui.invisibleButton("##hstart", lw, rowH)) {
            jump = -undo.size();
        }
        row(ll, lx, ImGui.getItemRectMinY(), lw, rowH, "Start of session", Theme.TEXT_3, undo.isEmpty(), "##hstart");
        for (int i = undo.size() - 1; i >= 0; i--) {
            int steps = -i;
            String id = "##hu" + i;
            if (ImGui.invisibleButton(id, lw, rowH) && steps != 0) {
                jump = steps;
            }
            row(ll, lx, ImGui.getItemRectMinY(), lw, rowH, MarkerText.op(undo.get(i)), Theme.TEXT_2, i == 0, id);
        }
        for (int i = 0; i < redo.size(); i++) {
            String id = "##hr" + i;
            if (ImGui.invisibleButton(id, lw, rowH)) {
                jump = i + 1;
            }
            row(ll, lx, ImGui.getItemRectMinY(), lw, rowH, MarkerText.op(redo.get(i)), Theme.TEXT_3, false, id);
        }
        ImGui.endChild();
        ui.end();
        if (jump != null && jump != 0) {
            ui.tools.jump(jump);
        }
    }

    private void row(ImDrawList dl, float x, float y, float w, float h, String text, int colour, boolean current, String id) {
        Kit kit = ui.kit;
        float hv = ui.anim.hover(ImGui.getID(id), ImGui.isItemHovered());
        if (hv > 0) {
            dl.addRectFilled(x, y, x + w, y + h, Kit.col(Theme.WHITE, 0.05f * hv), ui.s(6));
        }
        if (current) {
            dl.addRectFilledMultiColor(x, y, x + w, y + h, Kit.col(Theme.SPOT, 0.16f), Kit.col(Theme.SPOT, 0.03f),
                    Kit.col(Theme.SPOT, 0.03f), Kit.col(Theme.SPOT, 0.16f));
            dl.addRectFilled(x, y + ui.s(5), x + ui.s(2), y + h - ui.s(5), Kit.col(Theme.SPOT, 1), 1);
        }
        kit.dot(dl, x + ui.s(12), y + h / 2, current ? Theme.SPOT : colour, 2.5f, false);
        kit.text(dl, ui.fonts.small, 11.5f, x + ui.s(24), y + (h - kit.size(11.5f)) / 2, current ? Theme.TEXT : colour, 1, text);
    }

    private void shortcuts() {
        Kit kit = ui.kit;
        List<EditorShortcuts.Binding> bindings = ui.mode.shortcuts().bindings();
        List<EditorShortcuts.Conflict> conflicts = ui.mode.conflicts();
        float t = Anim.easeOut((System.currentTimeMillis() - shortcutsAt) / Anim.PANEL_MS);
        float rowH = ui.s(26);
        float w = Math.min(ui.s(560), ui.screenW() - ui.s(40));
        int half = (bindings.size() + 4) / 2;
        float h = ui.s(60) + rowH * half + ui.s(16) + (conflicts.isEmpty() ? 0 : ui.s(22) * (conflicts.size() + 1));
        float x = (ui.screenW() - w) / 2;
        float y = (ui.screenH() - h) / 2 - (1 - t) * ui.s(12);
        ImGui.setNextWindowPos(x, y);
        ImGui.setNextWindowSize(w, h);
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, t);
        ImGui.begin("##shortcuts", EditorUi.PANEL_FLAGS & ~ImGuiWindowFlags.NoFocusOnAppearing);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.pushClipRectFullScreen();
        kit.shadow(dl, x, y, x + w, y + h, ui.s(14), t * 1.3f);
        kit.glass(dl, x, y, x + w, y + h, ui.s(14), t);
        dl.popClipRect();
        float pad = ui.s(16);
        kit.text(dl, ui.fonts.title, 15, x + pad, y + pad, Theme.TEXT, 1, "Keyboard shortcuts");
        if (kit.iconButton("##shortcutsclose", Icons.X, x + w - pad - ui.s(22), y + pad - ui.s(3), ui.s(22), ui.s(22), false, "Close",
                List.of("Esc"))) {
            shortcutsOpen = false;
        }
        float c = y + pad + ui.s(32);
        float colW = (w - pad * 3) / 2;
        String toggle = ui.mode.toggleKeyName();
        List<Fixed> fixed = List.of(new Fixed("Toggle editor", List.of(toggle)), new Fixed("Look around", List.of("Hold RMB")),
                new Fixed("Fly", List.of("W", "A", "S", "D")), new Fixed("Fly speed", List.of("Scroll")));
        int index = 0;
        for (Fixed row : fixed) {
            entry(dl, x + pad + (index / half) * (colW + pad), c + (index % half) * rowH, colW, row.label(), row.caps(), false);
            index++;
        }
        for (EditorShortcuts.Binding binding : bindings) {
            if (binding.action() == EditorShortcuts.Action.REDO_ALT) {
                continue;
            }
            boolean conflicted = conflicts.stream().anyMatch(con -> con.action() == binding.action());
            entry(dl, x + pad + (index / half) * (colW + pad), c + (index % half) * rowH, colW, binding.action().label(),
                    binding.key().caps(), conflicted);
            index++;
        }
        c += half * rowH + ui.s(10);
        if (!conflicts.isEmpty()) {
            kit.header(dl, x + pad, c, "Conflicts");
            c += ui.s(20);
            for (EditorShortcuts.Conflict conflict : conflicts) {
                kit.text(dl, ui.fonts.small, 11.5f, x + pad, c, Theme.DANGER, 1, conflict.key().label() + " is also " + conflict.with()
                        + "; " + conflict.action().label().toLowerCase(Locale.ROOT) + " is ignored. Rebind in Controls.");
                c += ui.s(22);
            }
        }
        ImGui.end();
        ImGui.popStyleVar();
    }

    private void entry(ImDrawList dl, float x, float y, float w, String label, List<String> caps, boolean conflicted) {
        Kit kit = ui.kit;
        kit.text(dl, ui.fonts.body, 13, x, y + ui.s(4), conflicted ? Theme.DANGER : Theme.TEXT_2, 1, label);
        float kw = kit.keysWidth(caps);
        kit.keys(dl, x + w - kw, y + ui.s(3), caps);
        dl.addLine(x, y + ui.s(25), x + w, y + ui.s(25), Kit.col(Theme.EDGE, Theme.EDGE_ALPHA), 1);
    }
}
