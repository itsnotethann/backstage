package net.honormc.backstage.client.editor.ui;

import imgui.ImGui;
import imgui.ImGuiStyle;
import imgui.flag.ImGuiCol;
import net.honormc.backstage.client.editor.Theme;

final class Style {
    private Style() {
    }

    static void apply(float s) {
        ImGuiStyle style = ImGui.getStyle();
        style.setWindowPadding(0, 0);
        style.setWindowRounding(Theme.RADIUS * s);
        style.setWindowBorderSize(0);
        style.setChildRounding(Theme.RADIUS_SM * s);
        style.setPopupRounding(10 * s);
        style.setPopupBorderSize(1);
        style.setFramePadding(10 * s, 6 * s);
        style.setFrameRounding(7 * s);
        style.setFrameBorderSize(1);
        style.setItemSpacing(8 * s, 6 * s);
        style.setIndentSpacing(14 * s);
        style.setScrollbarSize(6 * s);
        style.setScrollbarRounding(6 * s);
        style.setGrabRounding(6 * s);
        style.setTabRounding(6 * s);
        style.setAntiAliasedLines(true);
        set(style, ImGuiCol.Text, Theme.TEXT, 1);
        set(style, ImGuiCol.TextDisabled, Theme.TEXT_3, 1);
        set(style, ImGuiCol.WindowBg, Theme.GLASS, 0);
        set(style, ImGuiCol.ChildBg, Theme.GLASS, 0);
        set(style, ImGuiCol.PopupBg, Theme.GLASS_STRONG, 0.96f);
        set(style, ImGuiCol.Border, Theme.EDGE, Theme.EDGE_HI_ALPHA);
        set(style, ImGuiCol.BorderShadow, 0, 0);
        set(style, ImGuiCol.FrameBg, Theme.SHADOW, 0.25f);
        set(style, ImGuiCol.FrameBgHovered, Theme.SHADOW, 0.32f);
        set(style, ImGuiCol.FrameBgActive, Theme.SHADOW, 0.38f);
        set(style, ImGuiCol.ScrollbarBg, 0, 0);
        set(style, ImGuiCol.ScrollbarGrab, Theme.WHITE, 0.10f);
        set(style, ImGuiCol.ScrollbarGrabHovered, Theme.WHITE, 0.18f);
        set(style, ImGuiCol.ScrollbarGrabActive, Theme.SPOT, 0.6f);
        set(style, ImGuiCol.CheckMark, Theme.SPOT, 1);
        set(style, ImGuiCol.SliderGrab, Theme.WHITE, 1);
        set(style, ImGuiCol.SliderGrabActive, Theme.SPOT, 1);
        set(style, ImGuiCol.Button, Theme.WHITE, 0.06f);
        set(style, ImGuiCol.ButtonHovered, Theme.WHITE, 0.10f);
        set(style, ImGuiCol.ButtonActive, Theme.WHITE, 0.14f);
        set(style, ImGuiCol.Header, Theme.SPOT, 0.16f);
        set(style, ImGuiCol.HeaderHovered, Theme.WHITE, 0.06f);
        set(style, ImGuiCol.HeaderActive, Theme.SPOT, 0.22f);
        set(style, ImGuiCol.Separator, Theme.EDGE, Theme.EDGE_HI_ALPHA);
        set(style, ImGuiCol.SeparatorHovered, Theme.SPOT, 0.5f);
        set(style, ImGuiCol.SeparatorActive, Theme.SPOT, 0.8f);
        set(style, ImGuiCol.TextSelectedBg, Theme.SPOT, 0.30f);
        set(style, ImGuiCol.InputTextCursor, Theme.SPOT, 1);
        set(style, ImGuiCol.NavCursor, Theme.SPOT, 0.6f);
        set(style, ImGuiCol.ModalWindowDimBg, Theme.SHADOW, 0.35f);
        set(style, ImGuiCol.ResizeGrip, 0, 0);
        set(style, ImGuiCol.ResizeGripHovered, Theme.SPOT, 0.4f);
        set(style, ImGuiCol.ResizeGripActive, Theme.SPOT, 0.7f);
    }

    private static void set(ImGuiStyle style, int col, int rgb, float alpha) {
        style.setColor(col, (rgb >> 16 & 0xFF) / 255f, (rgb >> 8 & 0xFF) / 255f, (rgb & 0xFF) / 255f, alpha);
    }
}
