package net.honormc.backstage.client.editor.ui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.honormc.backstage.client.editor.EditorShortcuts.Action;
import net.honormc.backstage.client.editor.EditorState;
import net.honormc.backstage.client.editor.EditorTools;
import net.honormc.backstage.client.editor.MarkerStore;
import net.honormc.backstage.client.editor.Theme;
import net.honormc.backstage.protocol.model.MarkerType;

final class StatusPanel {
    private record Part(String text, List<String> caps, boolean bold) {
        static Part text(String text) {
            return new Part(text, null, false);
        }

        static Part bold(String text) {
            return new Part(text, null, true);
        }

        static Part keys(String... caps) {
            return new Part(null, List.of(caps), false);
        }
    }

    private final EditorUi ui;

    StatusPanel(EditorUi ui) {
        this.ui = ui;
    }

    void draw() {
        status();
        hint();
        toasts();
    }

    private void status() {
        Kit kit = ui.kit;
        EditorState state = ui.mode.editor().state();
        MarkerStore store = ui.mode.editor().store();
        String server = "offline";
        String map = "";
        String access = "";
        if (state instanceof EditorState.Available a) {
            server = a.serverName();
            map = a.bound() ? a.map() : "no map";
            access = a.writable() ? "writable" : "read-only";
        }
        String rejected = ui.mode.rejected();
        float m = ui.s(Theme.MARGIN);
        float h = ui.s(34);
        float w = ui.measured("status", ui.s(420));
        float y = ui.screenH() - m - h;
        ui.begin("##status", m, y, w, h, 5, 0, 1, 0);
        ImDrawList dl = ImGui.getWindowDrawList();
        float wx = ImGui.getWindowPosX();
        float wy = ImGui.getWindowPosY();
        float cy = wy + h / 2;
        float c = wx + ui.s(12);
        boolean connected = state instanceof EditorState.Available;
        kit.dot(dl, c + ui.s(3), cy, connected ? Theme.OK : Theme.DANGER, 3.5f, true);
        c += ui.s(12);
        c = segment(dl, c, cy, server, Theme.TEXT_2);
        if (!map.isEmpty()) {
            c = segment(dl, c, cy, "· " + map, Theme.TEXT_2);
            c = segment(dl, c, cy, "· " + access, access.equals("writable") ? Theme.TEXT_3 : Theme.SPOT);
        }
        c += ui.s(6);
        c = number(dl, c, cy, store.count(), " markers");
        String split = store.count(MarkerType.POINT) + "·" + store.count(MarkerType.GROUP) + "·" + store.count(MarkerType.REGION);
        c = segment(dl, c, cy, split, Theme.TEXT_3);
        c = number(dl, c, cy, store.revision(), "", "rev ");
        c = number(dl, c, cy, ui.tools.selection().size(), "", "sel ");
        c = number(dl, c, cy, ui.mode.fps(), " fps");
        if (rejected != null) {
            float age = (System.currentTimeMillis() % 100_000) / 1000f;
            float alpha = 0.85f + 0.15f * (float) Math.sin(age * 6);
            c += ui.s(4);
            kit.text(dl, ui.fonts.iconsSmall, 13, c, cy - kit.size(13) / 2f, Theme.DANGER, alpha, Icons.ALERT);
            c += ui.s(18);
            String text = rejected.length() > 80 ? rejected.substring(0, 80) + "…" : rejected;
            kit.text(dl, ui.fonts.mono, 11.5f, c, cy - kit.size(11.5f) / 2f, Theme.DANGER, alpha, text);
            c += kit.width(ui.fonts.mono, 11.5f, text) + ui.s(14);
        }
        ui.measure("status", c - wx + ui.s(4));
        ui.end();
    }

    private float segment(ImDrawList dl, float c, float cy, String text, int colour) {
        Kit kit = ui.kit;
        kit.text(dl, ui.fonts.mono, 11.5f, c, cy - kit.size(11.5f) / 2f, colour, 1, text);
        return c + kit.width(ui.fonts.mono, 11.5f, text) + ui.s(10);
    }

    private float number(ImDrawList dl, float c, float cy, long value, String suffix) {
        return number(dl, c, cy, value, suffix, "");
    }

    private float number(ImDrawList dl, float c, float cy, long value, String suffix, String prefix) {
        Kit kit = ui.kit;
        float y = cy - kit.size(11.5f) / 2f;
        if (!prefix.isEmpty()) {
            kit.text(dl, ui.fonts.mono, 11.5f, c, y, Theme.TEXT_3, 1, prefix);
            c += kit.width(ui.fonts.mono, 11.5f, prefix);
        }
        String n = String.format(Locale.ROOT, "%,d", value);
        kit.text(dl, ui.fonts.mono, 11.5f, c, y, Theme.TEXT_2, 1, n);
        c += kit.width(ui.fonts.mono, 11.5f, n);
        if (!suffix.isEmpty()) {
            kit.text(dl, ui.fonts.mono, 11.5f, c, y, Theme.TEXT_3, 1, suffix);
            c += kit.width(ui.fonts.mono, 11.5f, suffix);
        }
        return c + ui.s(14);
    }

    private List<Part> parts() {
        EditorTools tools = ui.tools;
        List<Part> parts = new ArrayList<>();
        if (System.currentTimeMillis() - ui.flySpeedAt() < 1500) {
            parts.add(Part.bold("Fly speed"));
            parts.add(Part.text(String.format(Locale.ROOT, "%.1f×", ui.mode.layout().flySpeed / 0.05f)));
            parts.add(Part.text("· scroll to change"));
            return parts;
        }
        parts.add(Part.bold(tools.tool().title()));
        switch (tools.tool()) {
            case SELECT -> {
                parts.add(Part.text("Click to pick ·"));
                parts.add(Part.keys("Shift"));
                parts.add(Part.text("add ·"));
                parts.add(Part.keys("Ctrl"));
                parts.add(Part.text("remove · drag for marquee"));
            }
            case POINT -> {
                parts.add(Part.text("Click to place a point · drag the ring to turn ·"));
                parts.add(Part.keys("R"));
                parts.add(Part.text("snap yaw"));
            }
            case GROUP -> {
                if (tools.draft() == null) {
                    parts.add(Part.text("Pick a group in the Outliner or press + Group"));
                } else {
                    parts.add(Part.text("Drag to paint ·"));
                    parts.add(Part.keys("Ctrl"));
                    parts.add(Part.text("erase ·"));
                    parts.add(Part.keys("Alt"));
                    parts.add(Part.text("fill ·"));
                    parts.add(Part.keys("[", "]"));
                    parts.add(Part.text("radius"));
                }
            }
            case BOX -> {
                if (tools.boxDraft() == null) {
                    parts.add(Part.text("Drag from block to block to make a box"));
                } else {
                    parts.add(Part.text("Drag a face to resize ·"));
                    parts.add(Part.keys("Enter"));
                    parts.add(Part.text("confirm ·"));
                    parts.add(Part.keys("Esc"));
                    parts.add(Part.text("cancel"));
                }
            }
            case REGION -> {
                if (tools.polygonDraft().size() < 3) {
                    parts.add(Part.text("Click blocks to add vertices"));
                } else {
                    parts.add(Part.text("Click the first vertex or"));
                    parts.add(Part.keys("Enter"));
                    parts.add(Part.text("to close"));
                }
            }
            case MOVE -> {
                parts.add(Part.text(tools.selection().isEmpty() ? "Select markers, then drag an arrow" : "Drag an arrow to move ·"));
                if (!tools.selection().isEmpty()) {
                    parts.add(Part.keys("Ctrl"));
                    parts.add(Part.text("free movement on points"));
                }
            }
            case BRUSH -> {
                parts.add(Part.text("Click to run ·"));
                parts.add(Part.keys("Shift"));
                parts.add(Part.text("click to preview"));
            }
        }
        if (!ui.tools.selection().isEmpty() && tools.tool() != EditorTools.Tool.GROUP) {
            parts.add(Part.text("·"));
            parts.add(new Part(null, ui.caps(Action.FOCUS), false));
            parts.add(Part.text("fly to"));
        }
        return parts;
    }

    private void hint() {
        Kit kit = ui.kit;
        List<Part> parts = parts();
        float h = ui.s(36);
        float w = ui.measured("hint", ui.s(380));
        float shown = ui.anim.approach(ImGui.getID("##hintw"), w, Anim.PANEL_MS);
        float x = (ui.screenW() - shown) / 2;
        float y = ui.screenH() - ui.s(18) - h;
        float left = ui.s(Theme.MARGIN) + ui.measured("status", 0) + ui.s(Theme.GAP);
        float right = ui.dockX() - ui.s(Theme.GAP);
        if (x < left && left + shown < right) {
            x = left + (right - left - shown) / 2;
        }
        ImGui.setNextWindowPos(x, y);
        ImGui.setNextWindowSize(shown, h);
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, ui.entrance(6));
        ImGui.begin("##hint", EditorUi.PANEL_FLAGS | ImGuiWindowFlags.NoInputs);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.pushClipRectFullScreen();
        kit.shadow(ImGui.getBackgroundDrawList(), x, y, x + shown, y + h, h / 2, 1);
        kit.glass(dl, x, y, x + shown, y + h, h / 2, 1);
        dl.popClipRect();
        float cy = y + h / 2;
        float c = x + ui.s(14);
        for (Part part : parts) {
            if (part.caps() != null) {
                c += kit.keys(dl, c, cy - (kit.size(11) + ui.s(5)) / 2, part.caps()) + ui.s(6);
            } else if (part.bold()) {
                kit.text(dl, ui.fonts.semibold, 13, c, cy - kit.size(13) / 2f, Theme.TEXT, 1, part.text());
                c += kit.width(ui.fonts.semibold, 13, part.text()) + ui.s(10);
            } else {
                kit.text(dl, ui.fonts.body, 13, c, cy - kit.size(13) / 2f, Theme.TEXT_2, 1, part.text());
                c += kit.width(ui.fonts.body, 13, part.text()) + ui.s(6);
            }
        }
        ui.measure("hint", c - x + ui.s(8));
        ImGui.end();
        ImGui.popStyleVar();
    }

    private void toasts() {
        Kit kit = ui.kit;
        long now = System.currentTimeMillis();
        List<Toasts.Toast> live = ui.mode.toasts().live(now);
        float right = ui.dockX() - ui.s(Theme.GAP);
        float y = ui.dockTop();
        float w = ui.s(300);
        for (int i = live.size() - 1; i >= 0; i--) {
            Toasts.Toast toast = live.get(i);
            long age = now - toast.created();
            float in = Anim.easeOut(age / 260f);
            float out = age > Toasts.LIFETIME_MS ? 1 - Math.clamp((age - Toasts.LIFETIME_MS) / 300f, 0, 1) : 1;
            float alpha = in * out;
            boolean hasMessage = !toast.message().isEmpty();
            float h = hasMessage ? ui.s(52) : ui.s(38);
            float slot = ui.anim.approach(ImGui.getID("##toasty" + toast.id()), y, Anim.PANEL_MS);
            float x = right - w + (1 - in) * ui.s(24) + (1 - out) * ui.s(16);
            ImGui.setNextWindowPos(x, slot);
            ImGui.setNextWindowSize(w, h);
            ImGui.pushStyleVar(ImGuiStyleVar.Alpha, Math.max(alpha, 0.001f));
            ImGui.begin("##toast" + toast.id(), EditorUi.PANEL_FLAGS);
            ImDrawList dl = ImGui.getWindowDrawList();
            dl.pushClipRectFullScreen();
            kit.panel(ImGui.getBackgroundDrawList(), dl, x, slot, x + w, slot + h, alpha);
            int colour = switch (toast.kind()) {
                case OK -> Theme.OK;
                case INFO -> Theme.SPOT;
                case ERROR -> Theme.DANGER;
            };
            dl.addRectFilled(x, slot + ui.s(8), x + ui.s(3), slot + h - ui.s(8), Kit.col(colour, alpha), ui.s(2));
            kit.dot(dl, x + ui.s(18), slot + ui.s(19), colour, 4, true);
            float tw = w - ui.s(58);
            String title = clip(toast.title(), tw, true);
            kit.text(dl, ui.fonts.semibold, 13, x + ui.s(32), slot + ui.s(11), Theme.TEXT, 1, title);
            if (hasMessage) {
                kit.text(dl, ui.fonts.small, 11.5f, x + ui.s(32), slot + ui.s(29), Theme.TEXT_3, 1, clip(toast.message(), tw, false));
            }
            if (kit.iconButton("##dismiss" + toast.id(), Icons.X, x + w - ui.s(28), slot + ui.s(8), ui.s(20), ui.s(20), false, null,
                    null)) {
                ui.mode.toasts().dismiss(toast.id());
            }
            dl.popClipRect();
            ImGui.end();
            ImGui.popStyleVar();
            y += h + ui.s(8);
        }
    }

    private String clip(String text, float width, boolean title) {
        Kit kit = ui.kit;
        Fonts f = ui.fonts;
        float size = title ? 13 : 11.5f;
        if (kit.width(title ? f.semibold : f.small, size, text) <= width) {
            return text;
        }
        String cut = text;
        while (cut.length() > 1 && kit.width(title ? f.semibold : f.small, size, cut + "…") > width) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "…";
    }
}
