package net.honormc.backstage.client.editor.ui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import java.util.List;
import net.honormc.backstage.client.editor.EditorState;
import net.honormc.backstage.client.editor.Theme;

final class TopBar {
    private static final String[] MENUS = {"File", "Edit", "View", "Help"};

    private final EditorUi ui;
    private String openMenu;
    private float menuX;
    private float menuY;

    TopBar(EditorUi ui) {
        this.ui = ui;
    }

    boolean closeMenus() {
        if (openMenu != null) {
            openMenu = null;
            return true;
        }
        return false;
    }

    void draw() {
        Kit kit = ui.kit;
        float m = ui.s(Theme.MARGIN);
        float h = ui.s(44);
        float w = ui.screenW() - m * 2;
        ui.begin("##topbar", m, m, w, h, 0, 0, -1, 0);
        ImDrawList dl = ImGui.getWindowDrawList();
        float x = ImGui.getWindowPosX();
        float y = ImGui.getWindowPosY();
        float cy = y + h / 2;

        float logo = ui.s(22);
        float lx = x + ui.s(12);
        float ly = cy - logo / 2;
        for (int i = 3; i >= 1; i--) {
            dl.addRectFilled(lx - i * ui.s(3), ly - i * ui.s(3), lx + logo + i * ui.s(3), ly + logo + i * ui.s(3),
                    Kit.col(Theme.SPOT, 0.05f), ui.s(6) + i * ui.s(3));
        }
        dl.addRectFilledMultiColor(lx, ly, lx + logo, ly + logo, Kit.col(Theme.SPOT, 1), Kit.col(Theme.LOGO_MID, 1),
                Kit.col(Theme.POINT, 1), Kit.col(Theme.SPOT_LIGHT, 1));
        dl.addRect(lx - 1, ly - 1, lx + logo + 1, ly + logo + 1, Kit.col(Theme.GLASS, 1), ui.s(7), 0, ui.s(2.5f));
        float bx = lx + logo + ui.s(8);
        kit.text(dl, ui.fonts.bold, 13.5f, bx, cy - kit.size(13.5f) / 2f - ui.s(1), Theme.TEXT, 1, "Backstage");
        float cursor = bx + kit.width(ui.fonts.bold, 13.5f, "Backstage") + ui.s(16);

        for (String menu : MENUS) {
            float mw = kit.width(ui.fonts.body, 13, menu) + ui.s(20);
            float mh = ui.s(30);
            float my = cy - mh / 2;
            ImGui.setCursorScreenPos(cursor, my);
            boolean clicked = ImGui.invisibleButton("##menu" + menu, mw, mh);
            boolean hovered = ImGui.isItemHovered();
            if (clicked) {
                openMenu = menu.equals(openMenu) ? null : menu;
                menuX = cursor;
                menuY = y + h + ui.s(6);
            } else if (hovered && openMenu != null && !openMenu.equals(menu)) {
                openMenu = menu;
                menuX = cursor;
                menuY = y + h + ui.s(6);
            }
            boolean on = menu.equals(openMenu);
            float hv = ui.anim.hover(ImGui.getID("##menu" + menu), hovered || on);
            if (hv > 0) {
                dl.addRectFilled(cursor, my, cursor + mw, my + mh, Kit.col(Theme.WHITE, 0.06f * hv), ui.s(Theme.RADIUS_SM));
            }
            kit.text(dl, ui.fonts.body, 13, cursor + ui.s(10), cy - kit.size(13) / 2f, Theme.mix(Theme.TEXT_2, Theme.TEXT, hv), 1,
                    menu);
            cursor += mw + ui.s(2);
        }

        float searchW = ui.s(260);
        float searchH = ui.s(30);
        float sx = x + w - ui.s(8) - searchW;
        float sy = cy - searchH / 2;
        ImGui.setCursorScreenPos(sx, sy);
        if (ImGui.invisibleButton("##search", searchW, searchH)) {
            ui.palette.show();
        }
        float shv = ui.anim.hover(ImGui.getID("##search"), ImGui.isItemHovered());
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.TextInput);
        }
        dl.addRectFilled(sx, sy, sx + searchW, sy + searchH, Kit.col(Theme.WHITE, 0.04f + 0.03f * shv), ui.s(Theme.RADIUS_SM));
        dl.addRect(sx, sy, sx + searchW, sy + searchH, Kit.col(Theme.EDGE, Theme.EDGE_ALPHA + 0.05f * shv), ui.s(Theme.RADIUS_SM),
                0, 1);
        kit.text(dl, ui.fonts.iconsSmall, 15, sx + ui.s(10), cy - kit.size(15) / 2f, Theme.TEXT_3, 1, Icons.SEARCH);
        kit.text(dl, ui.fonts.body, 13, sx + ui.s(32), cy - kit.size(13) / 2f, Theme.TEXT_3, 1, "Search markers, tools, actions");
        List<String> caps = List.of("Ctrl", "K");
        float kw = kit.keysWidth(caps);
        kit.keys(dl, sx + searchW - ui.s(8) - kw, cy - (kit.size(11) + ui.s(5)) / 2, caps);

        centrePill(dl, kit, cursor, sx, cy);
        ui.end();
        drawMenu();
    }

    private void centrePill(ImDrawList dl, Kit kit, float left, float right, float cy) {
        EditorState state = ui.mode.editor().state();
        String server = "Offline";
        String map = "-";
        int dot = Theme.DANGER;
        boolean writable = false;
        if (state instanceof EditorState.Available a) {
            server = a.serverName();
            map = a.bound() ? a.map() : "none";
            writable = a.writable();
            dot = a.bound() ? a.writable() ? Theme.OK : Theme.SPOT : Theme.SPOT;
        }
        String rev = "rev " + ui.mode.editor().store().revision();
        float gap = ui.s(10);
        float wServer = kit.width(ui.fonts.semibold, 13, server);
        float wMapLabel = kit.width(ui.fonts.body, 13, "map ");
        float wMap = kit.width(ui.fonts.semibold, 13, map);
        float wRev = kit.width(ui.fonts.mono, 12, rev);
        float wLock = writable ? 0 : kit.width(ui.fonts.iconsSmall, 13, Icons.LOCK) + ui.s(6);
        float total = ui.s(12) + ui.s(8) + gap + wServer + gap + 1 + gap + wMapLabel + wMap + wLock + gap + 1 + gap + wRev + ui.s(12);
        float px = (left + right) / 2 - total / 2;
        px = Math.max(left + ui.s(8), Math.min(px, right - total - ui.s(8)));
        float ph = ui.s(28);
        float py = cy - ph / 2;
        dl.addRectFilled(px, py, px + total, py + ph, Kit.col(Theme.WHITE, 0.04f), ph / 2);
        dl.addRect(px, py, px + total, py + ph, Kit.col(Theme.EDGE, Theme.EDGE_ALPHA), ph / 2, 0, 1);
        float c = px + ui.s(12);
        float pulse = 0.75f + 0.25f * (float) Math.sin(ui.anim.time() * 2.4);
        kit.dot(dl, c + ui.s(4), cy, dot, 4 * pulse + 0.4f, true);
        c += ui.s(8) + gap;
        kit.text(dl, ui.fonts.semibold, 13, c, cy - kit.size(13) / 2f, Theme.TEXT, 1, server);
        c += wServer + gap;
        dl.addLine(c, cy - ui.s(7), c, cy + ui.s(7), Kit.col(Theme.EDGE, Theme.EDGE_HI_ALPHA), 1);
        c += 1 + gap;
        kit.text(dl, ui.fonts.body, 13, c, cy - kit.size(13) / 2f, Theme.TEXT_2, 1, "map ");
        c += wMapLabel;
        kit.text(dl, ui.fonts.semibold, 13, c, cy - kit.size(13) / 2f, Theme.TEXT, 1, map);
        c += wMap;
        if (!writable) {
            kit.text(dl, ui.fonts.iconsSmall, 13, c + ui.s(6), cy - kit.size(13) / 2f, Theme.SPOT, 1, Icons.LOCK);
            c += wLock;
        }
        c += gap;
        dl.addLine(c, cy - ui.s(7), c, cy + ui.s(7), Kit.col(Theme.EDGE, Theme.EDGE_HI_ALPHA), 1);
        c += 1 + gap;
        kit.text(dl, ui.fonts.mono, 12, c, cy - kit.size(12) / 2f, Theme.TEXT_3, 1, rev);
    }

    private void drawMenu() {
        if (openMenu == null) {
            return;
        }
        Kit kit = ui.kit;
        List<Commands.Command> items = Commands.all(ui).stream().filter(c -> c.menu().equals(openMenu)).toList();
        boolean view = openMenu.equals("View");
        float w = ui.s(250);
        float rowH = ui.s(30);
        float pad = ui.s(6);
        float extra = view ? ui.s(52) : 0;
        float h = pad * 2 + rowH * items.size() + extra;
        ImGui.setNextWindowPos(menuX, menuY);
        ImGui.setNextWindowSize(w, h);
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, 1);
        ImGui.begin("##menuwindow", EditorUi.PANEL_FLAGS & ~ImGuiWindowFlags.NoFocusOnAppearing);
        ImDrawList dl = ImGui.getWindowDrawList();
        float x = ImGui.getWindowPosX();
        float y = ImGui.getWindowPosY();
        dl.pushClipRectFullScreen();
        kit.panel(ImGui.getBackgroundDrawList(), dl, x, y, x + w, y + h, 1);
        dl.popClipRect();
        float cy = y + pad;
        for (Commands.Command item : items) {
            ImGui.setCursorScreenPos(x + pad, cy);
            boolean enabled = item.on();
            boolean clicked = ImGui.invisibleButton("##mi" + item.title(), w - pad * 2, rowH) && enabled;
            float hv = ui.anim.hover(ImGui.getID("##mi" + item.title()), ImGui.isItemHovered() && enabled);
            if (hv > 0) {
                dl.addRectFilled(x + pad, cy, x + w - pad, cy + rowH, Kit.col(Theme.WHITE, 0.07f * hv), ui.s(7));
            }
            float alpha = enabled ? 1 : 0.4f;
            int iconColour = item.ticked() ? Theme.SPOT : Theme.TEXT_3;
            kit.text(dl, ui.fonts.iconsSmall, 15, x + pad + ui.s(8), cy + (rowH - kit.size(15)) / 2, iconColour, alpha, item.icon());
            kit.text(dl, ui.fonts.body, 13, x + pad + ui.s(32), cy + (rowH - kit.size(13)) / 2, Theme.mix(Theme.TEXT_2, Theme.TEXT, hv),
                    alpha, item.title());
            if (item.checked() != null) {
                float sw = ui.s(26);
                float sh = ui.s(15);
                float tx = x + w - pad - ui.s(8) - sw;
                float ty = cy + (rowH - sh) / 2;
                float t = ui.anim.approach(ImGui.getID("##tick" + item.title()), item.ticked() ? 1 : 0, Anim.PANEL_MS);
                dl.addRectFilled(tx, ty, tx + sw, ty + sh, Kit.col(Theme.mix(Theme.TRACK, Theme.SPOT, t), 1), sh / 2);
                dl.addCircleFilled(tx + sh / 2 + (sw - sh) * t, ty + sh / 2, sh / 2 - ui.s(2), Kit.col(Theme.WHITE, 1));
            } else if (!item.caps().isEmpty()) {
                float kw = kit.keysWidth(item.caps());
                kit.keys(dl, x + w - pad - ui.s(8) - kw, cy + (rowH - kit.size(11) - ui.s(5)) / 2, item.caps());
            }
            if (clicked) {
                item.run().run();
                if (item.checked() == null) {
                    openMenu = null;
                }
            }
            cy += rowH;
        }
        if (view) {
            cy += ui.s(4);
            dl.addLine(x + pad, cy, x + w - pad, cy, Kit.col(Theme.EDGE, Theme.EDGE_HI_ALPHA), 1);
            cy += ui.s(10);
            kit.text(dl, ui.fonts.body, 12, x + pad + ui.s(8), cy + ui.s(4), Theme.TEXT_2, 1, "Render radius");
            float[] value = {ui.mode.layout().renderRadius};
            float sliderX = x + pad + ui.s(100);
            float sliderW = w - pad * 2 - ui.s(150);
            if (kit.slider("##radius", value, 16, 512, sliderX, cy + ui.s(11), sliderW, Theme.SPOT, Theme.SPOT_LIGHT)) {
                ui.mode.layout().renderRadius = (int) value[0];
            }
            kit.text(dl, ui.fonts.mono, 12, x + w - pad - ui.s(40), cy + ui.s(4), Theme.TEXT, 1,
                    Integer.toString(ui.mode.layout().renderRadius));
        }
        boolean hoveredMenu = ImGui.isWindowHovered();
        ImGui.end();
        ImGui.popStyleVar();
        if (ImGui.isMouseClicked(0) && !hoveredMenu && ImGui.getMousePosY() > menuY - ui.s(4)) {
            openMenu = null;
        }
    }
}
