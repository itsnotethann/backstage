package net.honormc.backstage.client.editor.ui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import imgui.type.ImString;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.honormc.backstage.client.editor.EditorTools.Selection;
import net.honormc.backstage.client.editor.Fuzzy;
import net.honormc.backstage.client.editor.Theme;
import net.honormc.backstage.protocol.model.Marker;

final class CommandPalette {
    private static final int VISIBLE = 9;

    private record Entry(String title, String subtitle, String icon, int colour, List<String> caps, int score, Runnable run) {
    }

    private final EditorUi ui;
    private final ImString query = new ImString(128);
    private boolean open;
    private long openedAt;
    private int cursor;
    private boolean focus;

    CommandPalette(EditorUi ui) {
        this.ui = ui;
    }

    boolean open() {
        return open;
    }

    void show() {
        open = true;
        openedAt = System.currentTimeMillis();
        query.set("");
        cursor = 0;
        focus = true;
    }

    void close() {
        open = false;
    }

    private List<Entry> entries() {
        String q = query.get();
        List<Entry> entries = new ArrayList<>();
        for (Marker marker : ui.mode.editor().store().all()) {
            String tags = marker.tags().isEmpty() ? "" : " #" + String.join(" #", marker.tags());
            int score = Math.max(Fuzzy.score(q, marker.id()), Fuzzy.score(q, marker.id() + tags) - 50);
            if (score == Fuzzy.NO_MATCH) {
                continue;
            }
            entries.add(new Entry(marker.id(), marker.type().name().toLowerCase(Locale.ROOT) + " · " + OutlinerPanel.meta(marker) + tags,
                    null, Theme.typeColour(marker.type()), List.of(), score, () -> {
                ui.tools.select(new Selection(marker.type(), marker.id()), false);
                ui.mode.flyTo(marker);
            }));
        }
        for (Commands.Command command : Commands.tools(ui)) {
            int score = Fuzzy.score(q, command.title());
            if (score != Fuzzy.NO_MATCH) {
                entries.add(new Entry(command.title(), "Tool", command.icon(), Theme.SPOT, command.caps(), score + 40,
                        command.run()));
            }
        }
        for (Commands.Command command : Commands.all(ui)) {
            int score = Fuzzy.score(q, command.menu() + " " + command.title());
            score = Math.max(score, Fuzzy.score(q, command.title()));
            if (score == Fuzzy.NO_MATCH || !command.on()) {
                continue;
            }
            String subtitle = command.checked() == null ? command.menu() : command.menu() + " · " + (command.ticked() ? "on" : "off");
            entries.add(new Entry(command.title(), subtitle, command.icon(), Theme.TEXT_2, command.caps(), score + 20, command.run()));
        }
        if (q.isBlank()) {
            entries.sort(Comparator.comparingInt((Entry e) -> e.icon() == null ? 1 : 0).thenComparing(Entry::title));
        } else {
            entries.sort(Comparator.comparingInt(Entry::score).reversed());
        }
        return entries;
    }

    void draw() {
        if (!open) {
            return;
        }
        Kit kit = ui.kit;
        float t = Anim.easeOut((System.currentTimeMillis() - openedAt) / Anim.PANEL_MS);
        ImGui.setNextWindowPos(0, 0);
        ImGui.setNextWindowSize(ui.screenW(), ui.screenH());
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 1);
        ImGui.begin("##palettedim", EditorUi.PANEL_FLAGS & ~ImGuiWindowFlags.NoFocusOnAppearing);
        ImGui.getWindowDrawList().addRectFilled(0, 0, ui.screenW(), ui.screenH(), Kit.col(Theme.SHADOW, 0.28f * t));
        boolean dimClicked = ImGui.isWindowHovered() && ImGui.isMouseClicked(0);
        ImGui.end();
        ImGui.popStyleVar();
        List<Entry> entries = entries();
        cursor = Math.clamp(cursor, 0, Math.max(0, entries.size() - 1));
        float w = Math.min(ui.s(580), ui.screenW() - ui.s(40));
        float rowH = ui.s(40);
        int rows = Math.min(VISIBLE, Math.max(1, entries.size()));
        float h = ui.s(12) + kit.fieldHeight() + ui.s(18) + rowH * rows + ui.s(34);
        float x = (ui.screenW() - w) / 2;
        float y = ui.screenH() * 0.16f - (1 - t) * ui.s(12);
        ImGui.setNextWindowPos(x, y);
        ImGui.setNextWindowSize(w, h);
        ImGui.setNextWindowFocus();
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, t);
        ImGui.begin("##palette", EditorUi.PANEL_FLAGS & ~ImGuiWindowFlags.NoFocusOnAppearing);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.pushClipRectFullScreen();
        kit.shadow(dl, x, y, x + w, y + h, ui.s(14), t * 1.4f);
        kit.glass(dl, x, y, x + w, y + h, ui.s(14), t);
        dl.addRect(x, y, x + w, y + h, Kit.col(Theme.SPOT, 0.18f * t), ui.s(14), 0, 1);
        dl.popClipRect();
        float pad = ui.s(12);
        float c = y + pad;
        kit.text(dl, ui.fonts.iconsSmall, 15, x + pad + ui.s(8), c + (kit.fieldHeight() - kit.size(15)) / 2, Theme.SPOT, 1, Icons.SEARCH);
        String before = query.get();
        kit.field("##palettequery", query, "Jump to a marker, pick a tool or run an action", x + pad + ui.s(26), c,
                w - pad * 2 - ui.s(26), ImGuiInputTextFlags.None, focus || !ImGui.isAnyItemActive());
        focus = false;
        if (!before.equals(query.get())) {
            cursor = 0;
        }
        c += kit.fieldHeight() + ui.s(10);
        dl.addLine(x + pad, c, x + w - pad, c, Kit.col(Theme.EDGE, Theme.EDGE_HI_ALPHA), 1);
        c += ui.s(8);
        if (ImGui.isKeyPressed(ImGuiKey.DownArrow)) {
            cursor = Math.min(entries.size() - 1, cursor + 1);
        }
        if (ImGui.isKeyPressed(ImGuiKey.UpArrow)) {
            cursor = Math.max(0, cursor - 1);
        }
        int first = Math.clamp(cursor - VISIBLE + 1, 0, Math.max(0, entries.size() - VISIBLE));
        first = Math.min(first, cursor);
        Entry chosen = null;
        if (entries.isEmpty()) {
            kit.text(dl, ui.fonts.body, 13, x + pad + ui.s(10), c + ui.s(10), Theme.TEXT_3, 1, "Nothing matches \"" + query.get() + "\".");
        }
        float selY = c + (cursor - first) * rowH;
        float pill = ui.anim.approach(ImGui.getID("##palettesel"), selY, Anim.HOVER_MS);
        if (!entries.isEmpty()) {
            dl.addRectFilledMultiColor(x + pad, pill, x + w - pad, pill + rowH, Kit.col(Theme.SPOT, 0.16f), Kit.col(Theme.SPOT, 0.03f),
                    Kit.col(Theme.SPOT, 0.03f), Kit.col(Theme.SPOT, 0.16f));
            dl.addRectFilled(x + pad, pill + ui.s(8), x + pad + ui.s(2), pill + rowH - ui.s(8), Kit.col(Theme.SPOT, 1), ui.s(1));
        }
        for (int i = first; i < Math.min(entries.size(), first + VISIBLE); i++) {
            Entry entry = entries.get(i);
            float ry = c + (i - first) * rowH;
            ImGui.setCursorScreenPos(x + pad, ry);
            if (ImGui.invisibleButton("##pe" + i, w - pad * 2, rowH)) {
                chosen = entry;
            }
            if (ImGui.isItemHovered() && ImGui.getIO().getMouseDeltaX() != 0) {
                cursor = i;
            }
            float ix = x + pad + ui.s(14);
            if (entry.icon() == null) {
                kit.swatch(dl, ix + ui.s(2), ry + (rowH - ui.s(10)) / 2, entry.colour(), 10);
            } else {
                kit.text(dl, ui.fonts.iconsSmall, 15, ix, ry + (rowH - kit.size(15)) / 2, entry.colour(), 1, entry.icon());
            }
            boolean sel = i == cursor;
            kit.text(dl, ui.fonts.body, 13, ix + ui.s(26), ry + ui.s(5), sel ? Theme.TEXT : Theme.TEXT_2, 1, entry.title());
            kit.text(dl, ui.fonts.small, 11, ix + ui.s(26), ry + ui.s(22), Theme.TEXT_3, 1, entry.subtitle());
            if (!entry.caps().isEmpty()) {
                float kw = kit.keysWidth(entry.caps());
                kit.keys(dl, x + w - pad - ui.s(10) - kw, ry + (rowH - kit.size(11) - ui.s(5)) / 2, entry.caps());
            }
        }
        float fy = y + h - ui.s(26);
        float fx = x + pad + ui.s(4);
        fx += kit.keys(dl, fx, fy, List.of("Up", "Down")) + ui.s(6);
        kit.text(dl, ui.fonts.small, 11, fx, fy + ui.s(2), Theme.TEXT_3, 1, "navigate");
        fx += kit.width(ui.fonts.small, 11, "navigate") + ui.s(14);
        fx += kit.kbd(dl, fx, fy, "Enter") + ui.s(6);
        kit.text(dl, ui.fonts.small, 11, fx, fy + ui.s(2), Theme.TEXT_3, 1, "run");
        fx += kit.width(ui.fonts.small, 11, "run") + ui.s(14);
        fx += kit.kbd(dl, fx, fy, "Esc") + ui.s(6);
        kit.text(dl, ui.fonts.small, 11, fx, fy + ui.s(2), Theme.TEXT_3, 1, "close");
        String count = entries.size() + " results";
        kit.text(dl, ui.fonts.mono, 11, x + w - pad - kit.width(ui.fonts.mono, 11, count), fy + ui.s(2), Theme.TEXT_3, 1, count);
        if (ImGui.isKeyPressed(ImGuiKey.Enter) && !entries.isEmpty()) {
            chosen = entries.get(cursor);
        }
        ImGui.end();
        ImGui.popStyleVar();
        if (chosen != null) {
            open = false;
            chosen.run().run();
        } else if (dimClicked) {
            open = false;
        }
    }
}
