package net.honormc.backstage.client.editor.ui;

import imgui.ImDrawList;
import imgui.ImFont;
import imgui.ImGui;
import imgui.flag.ImDrawFlags;
import imgui.flag.ImGuiCol;
import imgui.flag.ImGuiInputTextFlags;
import imgui.flag.ImGuiKey;
import imgui.flag.ImGuiMouseCursor;
import imgui.flag.ImGuiStyleVar;
import imgui.type.ImString;
import it.unimi.dsi.fastutil.ints.Int2ObjectOpenHashMap;
import java.util.List;
import java.util.Locale;
import net.honormc.backstage.client.editor.Theme;

public final class Kit {
    public static final int CHIP_NONE = 0;
    public static final int CHIP_CLICK = 1;
    public static final int CHIP_CLOSE = 2;

    public enum ButtonKind {
        PLAIN,
        PRIMARY,
        DANGER
    }

    final Fonts fonts;
    final Anim anim;
    float s = 1;
    int blur;
    float screenW;
    float screenH;
    private final Int2ObjectOpenHashMap<ImString> editing = new Int2ObjectOpenHashMap<>();
    private final Int2ObjectOpenHashMap<double[]> dragStart = new Int2ObjectOpenHashMap<>();

    Kit(Fonts fonts, Anim anim) {
        this.fonts = fonts;
        this.anim = anim;
    }

    public float s(float v) {
        return v * s;
    }

    public static int col(int rgb, float alpha) {
        return Theme.abgr(rgb, alpha);
    }

    public int size(float base) {
        return Fonts.px(base, s);
    }

    public float width(ImFont font, float base, String text) {
        return font.calcTextSizeAX(size(base), Float.MAX_VALUE, 0, text);
    }

    public void text(ImDrawList dl, ImFont font, float base, float x, float y, int rgb, float alpha, String text) {
        dl.addText(font, size(base), x, y, col(rgb, alpha), text);
    }

    public float spaced(ImDrawList dl, ImFont font, float base, float x, float y, int rgb, float alpha, String text,
                        float spacing) {
        float cursor = x;
        for (int i = 0; i < text.length(); i++) {
            String c = text.substring(i, i + 1);
            dl.addText(font, size(base), cursor, y, col(rgb, alpha), c);
            cursor += width(font, base, c) + s(spacing);
        }
        return cursor - x;
    }

    public float spacedWidth(ImFont font, float base, String text, float spacing) {
        float w = 0;
        for (int i = 0; i < text.length(); i++) {
            w += width(font, base, text.substring(i, i + 1)) + s(spacing);
        }
        return w;
    }

    public void header(ImDrawList dl, float x, float y, String text) {
        spaced(dl, fonts.header, 11, x, y, Theme.TEXT_3, 1, text.toUpperCase(Locale.ROOT), 0.6f);
    }

    public void shadow(ImDrawList dl, float x0, float y0, float x1, float y1, float radius, float alpha) {
        int layers = 10;
        for (int i = layers; i >= 1; i--) {
            float t = i / (float) layers;
            float grow = s(22) * t;
            float a = 0.045f * (1 - t) * (1 - t) * alpha * 2.2f;
            dl.addRectFilled(x0 - grow, y0 - grow + s(10) * t, x1 + grow, y1 + grow + s(10) * t, col(Theme.SHADOW, a),
                    radius + grow);
        }
    }

    public void glass(ImDrawList dl, float x0, float y0, float x1, float y1, float radius, float alpha) {
        if (blur != 0) {
            dl.addImageRounded(blur, x0, y0, x1, y1, x0 / screenW, 1 - y0 / screenH, x1 / screenW, 1 - y1 / screenH,
                    col(Theme.WHITE, alpha), radius);
            dl.addRectFilled(x0, y0, x1, y1, col(Theme.GLASS, Theme.GLASS_ALPHA * alpha), radius);
        } else {
            dl.addRectFilled(x0, y0, x1, y1, col(Theme.GLASS, Theme.GLASS_SOLID_ALPHA * alpha), radius);
        }
        dl.addRectFilledMultiColor(x0 + radius, y0 + 1, x1 - radius, y0 + s(1) + 1, col(Theme.WHITE, 0.04f * alpha),
                col(Theme.WHITE, 0.04f * alpha), col(Theme.WHITE, 0), col(Theme.WHITE, 0));
        dl.addRect(x0, y0, x1, y1, col(Theme.EDGE, Theme.EDGE_ALPHA * alpha), radius, ImDrawFlags.None, 1);
    }

    public void panel(ImDrawList background, ImDrawList dl, float x0, float y0, float x1, float y1, float alpha) {
        shadow(background, x0, y0, x1, y1, s(Theme.RADIUS), alpha);
        glass(dl, x0, y0, x1, y1, s(Theme.RADIUS), alpha);
    }

    public float kbd(ImDrawList dl, float x, float y, String label) {
        float w = width(fonts.mono, 11, label) + s(12);
        float h = size(11) + s(5);
        dl.addRectFilled(x, y, x + w, y + h, col(Theme.WHITE, 0.06f), s(5));
        dl.addRect(x, y, x + w, y + h, col(Theme.EDGE, Theme.EDGE_HI_ALPHA), s(5), ImDrawFlags.None, 1);
        dl.addLine(x + s(3), y + h - 1, x + w - s(3), y + h - 1, col(Theme.SHADOW, 0.35f), 1);
        text(dl, fonts.mono, 11, x + s(6), y + s(2), Theme.TEXT_2, 1, label);
        return w;
    }

    public float kbdWidth(String label) {
        return width(fonts.mono, 11, label) + s(12);
    }

    public float keys(ImDrawList dl, float x, float y, List<String> caps) {
        float cursor = x;
        for (String cap : caps) {
            cursor += kbd(dl, cursor, y, cap) + s(3);
        }
        return cursor - x - s(3);
    }

    public float keysWidth(List<String> caps) {
        float w = 0;
        for (String cap : caps) {
            w += kbdWidth(cap) + s(3);
        }
        return caps.isEmpty() ? 0 : w - s(3);
    }

    public void tooltip(String text, List<String> caps) {
        ImGui.beginTooltip();
        ImDrawList dl = ImGui.getWindowDrawList();
        float pad = s(9);
        float tw = width(fonts.body, 13, text);
        float kw = caps == null || caps.isEmpty() ? 0 : keysWidth(caps) + s(10);
        float h = size(13) + pad * 2 - s(2);
        float x = ImGui.getCursorScreenPosX();
        float y = ImGui.getCursorScreenPosY();
        ImGui.dummy(tw + kw + pad * 2, h);
        text(dl, fonts.body, 13, x + pad, y + pad - s(1), Theme.TEXT, 1, text);
        if (kw > 0) {
            keys(dl, x + pad + tw + s(10), y + (h - size(11) - s(5)) / 2, caps);
        }
        ImGui.endTooltip();
    }

    public boolean iconButton(String id, String icon, float x, float y, float w, float h, boolean active, String tip,
                              List<String> caps) {
        ImGui.setCursorScreenPos(x, y);
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        int key = ImGui.getID(id);
        float hv = anim.hover(key, hovered || ImGui.isItemActive());
        ImDrawList dl = ImGui.getWindowDrawList();
        if (!active && hv > 0) {
            dl.addRectFilled(x, y, x + w, y + h, col(Theme.WHITE, 0.06f * hv), s(8));
        }
        int rgb = active ? Theme.SPOT_INK : Theme.mix(Theme.TEXT_2, Theme.TEXT, hv);
        float iw = width(fonts.iconsSmall, 15, icon);
        text(dl, fonts.iconsSmall, 15, x + (w - iw) / 2, y + (h - size(15)) / 2, rgb, 1, icon);
        if (tip != null && hovered) {
            tooltip(tip, caps);
        }
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        return clicked;
    }

    public boolean button(String id, String label, ButtonKind kind, float x, float y, float minWidth) {
        float w = Math.max(minWidth, width(fonts.semibold, 12, label) + s(24));
        float h = size(12) + s(14);
        ImGui.setCursorScreenPos(x, y);
        boolean clicked = ImGui.invisibleButton(id, w, h);
        float hv = anim.hover(ImGui.getID(id), ImGui.isItemHovered());
        float press = anim.approach(ImGui.getID(id + "#press"), ImGui.isItemActive() ? 1 : 0, Anim.HOVER_MS);
        ImDrawList dl = ImGui.getWindowDrawList();
        float r = s(Theme.RADIUS_SM);
        switch (kind) {
            case PRIMARY -> {
                dl.addRectFilled(x, y + s(3), x + w, y + h + s(4), col(Theme.SPOT, 0.18f + 0.12f * hv), r + s(3));
                dl.addRectFilledMultiColor(x, y, x + w, y + h, col(Theme.SPOT_LIGHT, 1), col(Theme.SPOT_LIGHT, 1),
                        col(Theme.SPOT, 1), col(Theme.SPOT, 1));
                dl.addRect(x, y, x + w, y + h, col(Theme.SPOT, 1), r, ImDrawFlags.None, 2);
                dl.addRectFilled(x, y, x + w, y + h, col(Theme.WHITE, 0.08f * hv - 0.06f * press), r);
                float tw = width(fonts.semibold, 12, label);
                text(dl, fonts.semibold, 12, x + (w - tw) / 2, y + s(7), Theme.SPOT_INK, 1, label);
            }
            case DANGER -> {
                dl.addRectFilled(x, y, x + w, y + h, col(Theme.DANGER, 0.08f + 0.08f * hv + 0.06f * press), r);
                dl.addRect(x, y, x + w, y + h, col(Theme.DANGER, 0.3f + 0.2f * hv), r, ImDrawFlags.None, 1);
                float tw = width(fonts.semibold, 12, label);
                text(dl, fonts.semibold, 12, x + (w - tw) / 2, y + s(7), Theme.DANGER, 1, label);
            }
            case PLAIN -> {
                dl.addRectFilled(x, y, x + w, y + h, col(Theme.WHITE, 0.06f + 0.05f * hv + 0.04f * press), r);
                dl.addRect(x, y, x + w, y + h, col(Theme.EDGE, Theme.EDGE_HI_ALPHA), r, ImDrawFlags.None, 1);
                float tw = width(fonts.semibold, 12, label);
                text(dl, fonts.semibold, 12, x + (w - tw) / 2, y + s(7), Theme.mix(Theme.TEXT_2, Theme.TEXT, hv), 1, label);
            }
        }
        return clicked;
    }

    public float buttonWidth(String label, float minWidth) {
        return Math.max(minWidth, width(fonts.semibold, 12, label) + s(24));
    }

    public float buttonHeight() {
        return size(12) + s(14);
    }

    public int chip(String id, String label, boolean on, boolean closable, float x, float y) {
        float h = size(11.5f) + s(8);
        float xw = closable ? width(fonts.iconsSmall, 12, Icons.X) + s(5) : 0;
        float w = width(fonts.small, 11.5f, label) + s(18) + xw;
        ImGui.setCursorScreenPos(x, y);
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        float hv = anim.hover(ImGui.getID(id), hovered);
        ImDrawList dl = ImGui.getWindowDrawList();
        float r = h / 2;
        if (on) {
            dl.addRectFilled(x, y, x + w, y + h, col(Theme.SPOT, 0.14f + 0.06f * hv), r);
            dl.addRect(x, y, x + w, y + h, col(Theme.SPOT, 0.45f), r, ImDrawFlags.None, 1);
        } else {
            dl.addRectFilled(x, y, x + w, y + h, col(Theme.WHITE, 0.06f + 0.04f * hv), r);
            dl.addRect(x, y, x + w, y + h, col(Theme.EDGE, Theme.EDGE_ALPHA + 0.05f * hv), r, ImDrawFlags.None, 1);
        }
        int rgb = on ? Theme.SPOT : Theme.mix(Theme.TEXT_2, Theme.TEXT, hv);
        text(dl, fonts.small, 11.5f, x + s(9), y + s(4), rgb, 1, label);
        if (closable) {
            float cx = x + w - s(9) - xw + s(5);
            boolean overX = ImGui.isMouseHoveringRect(cx - s(3), y, x + w, y + h);
            text(dl, fonts.iconsSmall, 12, cx, y + (h - size(12)) / 2, rgb, overX ? 1 : 0.6f, Icons.X);
            if (clicked && overX) {
                return CHIP_CLOSE;
            }
        }
        if (hovered) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        return clicked ? CHIP_CLICK : CHIP_NONE;
    }

    public float chipWidth(String label, boolean closable) {
        float xw = closable ? width(fonts.iconsSmall, 12, Icons.X) + s(5) : 0;
        return width(fonts.small, 11.5f, label) + s(18) + xw;
    }

    public float chipHeight() {
        return size(11.5f) + s(8);
    }

    public boolean slider(String id, float[] value, float min, float max, float x, float y, float w, int from, int to) {
        float h = s(18);
        ImGui.setCursorScreenPos(x, y - h / 2);
        ImGui.invisibleButton(id, w, h);
        boolean active = ImGui.isItemActive();
        boolean hovered = ImGui.isItemHovered();
        boolean changed = false;
        if (active) {
            float t = Math.clamp((ImGui.getMousePosX() - x) / w, 0, 1);
            float next = Math.round(min + (max - min) * t);
            if (next != value[0]) {
                value[0] = next;
                changed = true;
            }
        }
        float t = (value[0] - min) / (max - min);
        int key = ImGui.getID(id);
        float shown = anim.approach(key, t, Anim.HOVER_MS);
        float hv = anim.hover(ImGui.getID(id + "#h"), hovered || active);
        ImDrawList dl = ImGui.getWindowDrawList();
        float th = s(4);
        dl.addRectFilled(x, y - th / 2, x + w, y + th / 2, col(Theme.WHITE, 0.1f), th);
        float fx = x + w * shown;
        dl.addRectFilledMultiColor(x, y - th / 2, Math.max(x + 1, fx), y + th / 2, col(from, 1), col(to, 1), col(to, 1),
                col(from, 1));
        float knob = s(7);
        dl.addCircleFilled(fx, y, knob + s(4) * (0.6f + 0.4f * hv), col(from, 0.25f));
        dl.addCircleFilled(fx, y, knob, col(Theme.WHITE, 1));
        if (hovered || active) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        return changed;
    }

    public int segmented(String id, String[] options, int selected, float x, float y) {
        float pad = s(3);
        float h = size(12) + s(8);
        float[] widths = new float[options.length];
        float total = pad;
        for (int i = 0; i < options.length; i++) {
            widths[i] = width(fonts.body, 12, options[i]) + s(20);
            total += widths[i] + pad;
        }
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.addRectFilled(x, y, x + total, y + h + pad * 2, col(Theme.WHITE, 0.05f), s(9));
        int key = ImGui.getID(id);
        float targetX = x + pad;
        for (int i = 0; i < selected && i < options.length; i++) {
            targetX += widths[i] + pad;
        }
        float pillX = anim.approach(key, targetX - x, Anim.PANEL_MS) + x;
        float pillW = anim.approach(key + 1, widths[Math.clamp(selected, 0, options.length - 1)], Anim.PANEL_MS);
        dl.addRectFilled(pillX, y + pad, pillX + pillW, y + pad + h, col(Theme.WHITE, 0.12f), s(6));
        int result = selected;
        float cursor = x + pad;
        for (int i = 0; i < options.length; i++) {
            ImGui.setCursorScreenPos(cursor, y + pad);
            if (ImGui.invisibleButton(id + "#" + i, widths[i], h)) {
                result = i;
            }
            float hv = anim.hover(ImGui.getID(id + "#" + i), ImGui.isItemHovered());
            int rgb = i == selected ? Theme.TEXT : Theme.mix(Theme.TEXT_2, Theme.TEXT, hv * 0.6f);
            float tw = width(fonts.body, 12, options[i]);
            text(dl, fonts.body, 12, cursor + (widths[i] - tw) / 2, y + pad + s(4), rgb, 1, options[i]);
            cursor += widths[i] + pad;
        }
        return result;
    }

    public float segmentedWidth(String[] options) {
        float total = s(3);
        for (String option : options) {
            total += width(fonts.body, 12, option) + s(20) + s(3);
        }
        return total;
    }

    public boolean toggle(String id, boolean on, float x, float y, int colour) {
        float w = s(30);
        float h = s(18);
        ImGui.setCursorScreenPos(x, y);
        boolean clicked = ImGui.invisibleButton(id, w, h);
        boolean next = clicked != on;
        float t = anim.approach(ImGui.getID(id), next ? 1 : 0, Anim.PANEL_MS);
        ImDrawList dl = ImGui.getWindowDrawList();
        int track = Theme.mix(Theme.TRACK, colour, t);
        dl.addRectFilled(x, y, x + w, y + h, col(track, 1), h / 2);
        float knob = h / 2 - s(2);
        float kx = x + s(2) + knob + (w - s(4) - knob * 2) * t;
        dl.addCircleFilled(kx, y + h / 2, knob, col(Theme.WHITE, 1));
        if (ImGui.isItemHovered()) {
            ImGui.setMouseCursor(ImGuiMouseCursor.Hand);
        }
        return next;
    }

    public void meter(float x, float y, float w, float fraction, int from, int to) {
        ImDrawList dl = ImGui.getWindowDrawList();
        float h = s(6);
        dl.addRectFilled(x, y, x + w, y + h, col(Theme.WHITE, 0.08f), h);
        float f = Math.clamp(fraction, 0, 1);
        if (f > 0) {
            int end = f > 0.9f ? Theme.DANGER : to;
            dl.addRectFilledMultiColor(x, y, x + Math.max(h, w * f), y + h, col(from, 1), col(end, 1), col(end, 1),
                    col(from, 1));
        }
    }

    public Double number(String id, String axis, int axisColour, double value, double speed, int decimals, float x, float y,
                         float w) {
        float h = size(12) + s(12);
        int key = ImGui.getID(id);
        ImDrawList dl = ImGui.getWindowDrawList();
        ImString buffer = editing.get(key);
        if (buffer != null) {
            ImGui.setCursorScreenPos(x, y);
            ImGui.setNextItemWidth(w);
            ImGui.pushFont(fonts.mono, size(12));
            if (ImGui.isWindowFocused() && !ImGui.isAnyItemActive()) {
                ImGui.setKeyboardFocusHere();
            }
            boolean entered = ImGui.inputText("##" + id, buffer,
                    ImGuiInputTextFlags.EnterReturnsTrue | ImGuiInputTextFlags.AutoSelectAll | ImGuiInputTextFlags.CharsScientific);
            boolean done = entered || ImGui.isItemDeactivated();
            ImGui.popFont();
            if (ImGui.isKeyPressed(ImGuiKey.Escape)) {
                editing.remove(key);
                return null;
            }
            if (done) {
                editing.remove(key);
                try {
                    double parsed = Double.parseDouble(buffer.get().trim());
                    return Double.isFinite(parsed) ? parsed : null;
                } catch (NumberFormatException e) {
                    return null;
                }
            }
            return null;
        }
        ImGui.setCursorScreenPos(x, y);
        ImGui.invisibleButton(id, w, h);
        boolean hovered = ImGui.isItemHovered();
        boolean active = ImGui.isItemActive();
        float hv = anim.hover(key, hovered || active);
        double shown = value;
        Double result = null;
        if (ImGui.isItemActivated()) {
            dragStart.put(key, new double[]{value, ImGui.getMousePosX()});
        }
        double[] start = dragStart.get(key);
        if (active && start != null) {
            double dx = ImGui.getMousePosX() - start[1];
            shown = start[0] + Math.round(dx / s(4)) * speed;
        }
        if (ImGui.isItemDeactivated() && start != null) {
            dragStart.remove(key);
            double dx = ImGui.getMousePosX() - start[1];
            if (Math.abs(dx) < s(3)) {
                editing.put(key, new ImString(format(value, decimals), 64));
            } else {
                double next = start[0] + Math.round(dx / s(4)) * speed;
                if (next != start[0]) {
                    result = next;
                }
            }
        }
        dl.addRectFilled(x, y, x + w, y + h, col(Theme.WHITE, 0.04f + 0.03f * hv), s(7));
        dl.addRect(x, y, x + w, y + h, col(active ? Theme.SPOT : Theme.EDGE, active ? 0.5f : Theme.EDGE_ALPHA + 0.06f * hv),
                s(7), ImDrawFlags.None, 1);
        float tx = x + s(8);
        if (axis != null) {
            text(dl, fonts.badge, 10.5f, tx, y + (h - size(10.5f)) / 2, axisColour, 1, axis);
            tx += width(fonts.badge, 10.5f, axis) + s(6);
        }
        text(dl, fonts.mono, 12, tx, y + (h - size(12)) / 2, Theme.TEXT, 1, format(shown, decimals));
        if (hovered || active) {
            ImGui.setMouseCursor(ImGuiMouseCursor.ResizeEW);
        }
        return result;
    }

    public float numberHeight() {
        return size(12) + s(12);
    }

    public static String format(double value, int decimals) {
        if (decimals == 0) {
            return Long.toString(Math.round(value));
        }
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    public boolean field(String id, ImString buffer, String hint, float x, float y, float w, int flags, boolean focus) {
        float h = size(13) + s(12);
        ImDrawList dl = ImGui.getWindowDrawList();
        ImGui.setCursorScreenPos(x, y);
        ImGui.pushStyleVar(ImGuiStyleVar.FramePadding, s(10), s(6));
        ImGui.pushStyleVar(ImGuiStyleVar.FrameBorderSize, 0);
        ImGui.pushStyleColor(ImGuiCol.FrameBg, 0);
        ImGui.pushStyleColor(ImGuiCol.FrameBgHovered, 0);
        ImGui.pushStyleColor(ImGuiCol.FrameBgActive, 0);
        dl.channelsSplit(2);
        dl.channelsSetCurrent(1);
        ImGui.setNextItemWidth(w);
        if (focus) {
            ImGui.setKeyboardFocusHere();
        }
        boolean result = ImGui.inputTextWithHint("##" + id, hint, buffer, flags);
        boolean active = ImGui.isItemActive();
        float hv = anim.hover(ImGui.getID(id), ImGui.isItemHovered() || active);
        dl.channelsSetCurrent(0);
        dl.addRectFilled(x, y, x + w, y + h, col(Theme.SHADOW, 0.25f), s(Theme.RADIUS_SM));
        dl.addRect(x, y, x + w, y + h, col(active ? Theme.SPOT : Theme.EDGE, active ? 0.55f : Theme.EDGE_HI_ALPHA + 0.06f * hv),
                s(Theme.RADIUS_SM), ImDrawFlags.None, 1);
        dl.channelsMerge();
        ImGui.popStyleColor(3);
        ImGui.popStyleVar(2);
        return result;
    }

    public float fieldHeight() {
        return size(13) + s(12);
    }

    public void swatch(ImDrawList dl, float x, float y, int rgb, float size) {
        dl.addRectFilled(x, y, x + s(size), y + s(size), col(rgb, 1), s(3));
    }

    public void dot(ImDrawList dl, float x, float y, int rgb, float radius, boolean glow) {
        if (glow) {
            for (int i = 3; i >= 1; i--) {
                dl.addCircleFilled(x, y, s(radius) + s(2.5f) * i, col(rgb, 0.08f));
            }
        }
        dl.addCircleFilled(x, y, s(radius), col(rgb, 1));
    }

    public void dashedRect(ImDrawList dl, float x0, float y0, float x1, float y1, int rgb, float alpha, double time) {
        float dash = s(6);
        float gap = s(5);
        float period = dash + gap;
        float offset = (float) (time * s(22) % period);
        float[][] edges = {{x0, y0, x1, y0}, {x1, y0, x1, y1}, {x1, y1, x0, y1}, {x0, y1, x0, y0}};
        for (float[] e : edges) {
            float length = (float) Math.hypot(e[2] - e[0], e[3] - e[1]);
            if (length < 1) {
                continue;
            }
            float ux = (e[2] - e[0]) / length;
            float uy = (e[3] - e[1]) / length;
            for (float d = -offset; d < length; d += period) {
                float a = Math.max(0, d);
                float b = Math.min(length, d + dash);
                if (b > a) {
                    dl.addLine(e[0] + ux * a, e[1] + uy * a, e[0] + ux * b, e[1] + uy * b, col(rgb, alpha), s(1.5f));
                }
            }
        }
    }
}
