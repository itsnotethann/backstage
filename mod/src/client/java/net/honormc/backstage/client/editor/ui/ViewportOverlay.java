package net.honormc.backstage.client.editor.ui;

import imgui.ImDrawList;
import imgui.ImGui;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import net.honormc.backstage.client.editor.EditorTools;
import net.honormc.backstage.client.editor.GroupOutline;
import net.honormc.backstage.client.editor.Marquee;
import net.honormc.backstage.client.editor.MarkerPick;
import net.honormc.backstage.client.editor.Theme;
import net.honormc.backstage.client.editor.render.WorldOverlay;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Region;
import net.minecraft.world.phys.Vec3;

final class ViewportOverlay {
    private record Label(Marker marker, float x, float y, double distance, boolean selected, boolean hovered) {
    }

    private final EditorUi ui;

    ViewportOverlay(EditorUi ui) {
        this.ui = ui;
    }

    void draw() {
        Marquee.View view = ui.mode.view();
        if (view == null) {
            return;
        }
        ImDrawList dl = ImGui.getBackgroundDrawList();
        if (ui.mode.layout().labels) {
            labels(dl, view);
        }
        handles(dl, view);
        Marquee.Rect rect = ui.tools.marquee();
        if (rect != null) {
            dl.addRectFilled(rect.x0(), rect.y0(), rect.x1(), rect.y1(), Kit.col(Theme.SPOT, 0.08f), ui.s(3));
            ui.kit.dashedRect(dl, rect.x0(), rect.y0(), rect.x1(), rect.y1(), Theme.SPOT, 0.9f, ui.anim.time());
        }
    }

    private void labels(ImDrawList dl, Marquee.View view) {
        double radius = ui.mode.layout().renderRadius;
        Vec3 eye = new Vec3(view.cameraX(), view.cameraY(), view.cameraZ());
        List<Label> labels = new ArrayList<>();
        for (WorldOverlay.Visible visible : ui.mode.overlay().visible()) {
            double[] anchor = anchor(visible.marker());
            if (anchor == null) {
                continue;
            }
            double distance = eye.distanceTo(new Vec3(anchor[0], anchor[1], anchor[2]));
            if (distance > radius && !visible.selected()) {
                continue;
            }
            float[] screen = view.project(anchor[0], anchor[1], anchor[2]);
            if (screen == null || screen[0] < -100 || screen[1] < -100 || screen[0] > view.width() + 100
                    || screen[1] > view.height() + 100) {
                continue;
            }
            labels.add(new Label(visible.marker(), screen[0], screen[1], distance, visible.selected(), visible.hovered()));
        }
        labels.sort(Comparator.comparingDouble(Label::distance).reversed());
        for (Label label : labels) {
            float fade = (float) (label.selected() ? 1 : 1 - Math.clamp((label.distance() - radius * 0.55) / (radius * 0.45), 0, 1));
            if (fade <= 0.02f) {
                continue;
            }
            pill(dl, label, fade);
        }
    }

    private static double[] anchor(Marker marker) {
        return switch (marker) {
            case Point p -> new double[]{p.x(), p.y() + 0.95, p.z()};
            case Group g -> {
                if (g.blocks().length == 0) {
                    yield null;
                }
                GroupOutline.Shape b = GroupOutline.bounds(g.blocks());
                yield new double[]{(b.minX() + b.maxX() + 1) / 2.0, b.maxY() + 1.6, (b.minZ() + b.maxZ() + 1) / 2.0};
            }
            case Region r -> MarkerPick.centre(r.shape());
        };
    }

    private void pill(ImDrawList dl, Label label, float alpha) {
        Kit kit = ui.kit;
        Marker marker = label.marker();
        String id = marker.id();
        String extra = switch (marker) {
            case Group g -> g.blocks().length + " blocks";
            default -> marker.tags().isEmpty() ? "" : "#" + String.join(" #", marker.tags().subList(0, Math.min(2,
                    marker.tags().size())));
        };
        float idW = kit.width(ui.fonts.semibold, 11.5f, id);
        float exW = extra.isEmpty() ? 0 : kit.width(ui.fonts.small, 11.5f, extra) + ui.s(6);
        float w = ui.s(9) + ui.s(7) + ui.s(6) + idW + exW + ui.s(9);
        float h = kit.size(11.5f) + ui.s(9);
        float x = label.x() - w / 2;
        float y = label.y() - h;
        float r = h / 2;
        float lift = ui.anim.approach(ImGui.getID("##label" + marker.type() + id), label.hovered() || label.selected() ? 1 : 0,
                Anim.HOVER_MS);
        y -= lift * ui.s(3);
        kit.shadow(dl, x, y, x + w, y + h, r, 0.5f * alpha);
        if (kit.blur != 0) {
            dl.addImageRounded(kit.blur, x, y, x + w, y + h, x / kit.screenW, 1 - y / kit.screenH, (x + w) / kit.screenW,
                    1 - (y + h) / kit.screenH, Kit.col(Theme.WHITE, alpha), r);
        }
        dl.addRectFilled(x, y, x + w, y + h, Kit.col(Theme.GLASS_STRONG, Theme.GLASS_STRONG_ALPHA * alpha), r);
        int border = label.selected() ? Theme.SPOT : Theme.EDGE;
        float borderAlpha = label.selected() ? 0.7f : Theme.EDGE_HI_ALPHA + 0.1f * lift;
        dl.addRect(x, y, x + w, y + h, Kit.col(border, borderAlpha * alpha), r, 0, 1);
        int colour = Theme.typeColour(marker.type());
        dl.addCircleFilled(x + ui.s(9) + ui.s(3.5f), y + h / 2, ui.s(3.5f), Kit.col(colour, alpha));
        float tx = x + ui.s(9) + ui.s(7) + ui.s(6);
        kit.text(dl, ui.fonts.semibold, 11.5f, tx, y + ui.s(4.5f), Theme.TEXT, alpha, id);
        if (!extra.isEmpty()) {
            kit.text(dl, ui.fonts.small, 11.5f, tx + idW + ui.s(6), y + ui.s(4.5f), Theme.TEXT_3, alpha, extra);
        }
    }

    private void handles(ImDrawList dl, Marquee.View view) {
        EditorTools tools = ui.tools;
        List<EditorTools.Handle> handles = tools.handles();
        if (handles.isEmpty()) {
            return;
        }
        EditorTools.Cursor cursor = tools.cursor();
        EditorTools.Handle hover = tools.dragging() != null ? tools.dragging()
                : cursor == null || ui.mode.looking() ? null : tools.handleAt(cursor);
        float[] origin = null;
        for (EditorTools.Handle handle : handles) {
            if (handle.kind() == EditorTools.HandleKind.GIZMO_CENTRE) {
                origin = view.project(handle.x(), handle.y(), handle.z());
            }
        }
        if (origin != null) {
            gizmo(dl, view, handles, origin, hover);
        }
        for (EditorTools.Handle handle : handles) {
            if (handle.kind() == EditorTools.HandleKind.GIZMO || handle.kind() == EditorTools.HandleKind.GIZMO_CENTRE) {
                continue;
            }
            float[] p = view.project(handle.x(), handle.y(), handle.z());
            if (p == null) {
                continue;
            }
            boolean on = handle.equals(hover);
            float grow = ui.anim.approach(ImGui.getID("##handle" + handle.kind() + handle.index() + handle.axis()), on ? 1 : 0,
                    Anim.HOVER_MS);
            switch (handle.kind()) {
                case BOX_FACE -> {
                    int colour = Theme.axis(handle.axis());
                    float r = ui.s(6 + 2 * grow);
                    dl.addCircleFilled(p[0], p[1], r + ui.s(5), Kit.col(colour, 0.18f + 0.2f * grow));
                    dl.addCircleFilled(p[0], p[1], r, Kit.col(Theme.WHITE, 1));
                    dl.addCircle(p[0], p[1], r, Kit.col(colour, 1), 0, ui.s(2));
                }
                case POLY_VERTEX -> {
                    boolean first = handle.index() == 0 && tools.polygonDraft().size() >= 3;
                    float r = ui.s((first ? 7 : 5.5f) + 2 * grow);
                    dl.addCircleFilled(p[0], p[1], r + ui.s(5), Kit.col(Theme.REGION, 0.2f + 0.2f * grow));
                    dl.addCircleFilled(p[0], p[1], r, Kit.col(first ? Theme.SPOT : Theme.WHITE, 1));
                    dl.addCircle(p[0], p[1], r, Kit.col(Theme.REGION, 1), 0, ui.s(2));
                }
                case POLY_TOP, POLY_BOTTOM -> {
                    float r = ui.s(7 + 2 * grow);
                    float dir = handle.kind() == EditorTools.HandleKind.POLY_TOP ? -1 : 1;
                    dl.addCircleFilled(p[0], p[1], r + ui.s(5), Kit.col(Theme.AXIS_Y, 0.18f + 0.2f * grow));
                    dl.addTriangleFilled(p[0], p[1] + dir * r, p[0] - r, p[1] - dir * r * 0.6f, p[0] + r, p[1] - dir * r * 0.6f,
                            Kit.col(Theme.AXIS_Y, 1));
                }
                case YAW -> {
                    float r = ui.s(6 + 2 * grow);
                    dl.addCircleFilled(p[0], p[1], r + ui.s(5), Kit.col(Theme.POINT, 0.2f + 0.2f * grow));
                    dl.addCircleFilled(p[0], p[1], r, Kit.col(Theme.POINT, 1));
                    dl.addCircle(p[0], p[1], r, Kit.col(Theme.WHITE, 0.9f), 0, ui.s(1.5f));
                }
                default -> {
                }
            }
        }
    }

    private void gizmo(ImDrawList dl, Marquee.View view, List<EditorTools.Handle> handles, float[] origin,
                       EditorTools.Handle hover) {
        for (EditorTools.Handle handle : handles) {
            if (handle.kind() != EditorTools.HandleKind.GIZMO) {
                continue;
            }
            float[] tip = view.project(handle.x(), handle.y(), handle.z());
            if (tip == null) {
                continue;
            }
            boolean on = handle.equals(hover);
            float glow = ui.anim.approach(ImGui.getID("##axis" + handle.axis()), on ? 1 : 0, Anim.HOVER_MS);
            int colour = on ? Theme.mix(Theme.axis(handle.axis()), Theme.WHITE, 0.25f) : Theme.axis(handle.axis());
            float dx = tip[0] - origin[0];
            float dy = tip[1] - origin[1];
            float length = (float) Math.hypot(dx, dy);
            if (length < 1) {
                continue;
            }
            float ux = dx / length;
            float uy = dy / length;
            float head = ui.s(14);
            float bx = tip[0] - ux * head;
            float by = tip[1] - uy * head;
            float thick = ui.s(3 + glow);
            dl.addLine(origin[0], origin[1], bx, by, Kit.col(colour, 0.25f * (0.5f + glow)), thick + ui.s(6));
            dl.addLine(origin[0], origin[1], bx, by, Kit.col(colour, 1), thick);
            float wing = ui.s(7 + glow);
            dl.addTriangleFilled(tip[0] + ux * ui.s(2), tip[1] + uy * ui.s(2), bx - uy * wing, by + ux * wing, bx + uy * wing,
                    by - ux * wing, Kit.col(colour, 1));
            if (on) {
                ui.kit.text(dl, ui.fonts.badge, 10.5f, tip[0] + ux * ui.s(8) + ui.s(4), tip[1] + uy * ui.s(8) - ui.s(6), colour, 1,
                        handle.axis().name());
            }
        }
        boolean centre = hover != null && hover.kind() == EditorTools.HandleKind.GIZMO_CENTRE;
        float grow = ui.anim.approach(ImGui.getID("##gizmocentre"), centre ? 1 : 0, Anim.HOVER_MS);
        dl.addCircleFilled(origin[0], origin[1], ui.s(10 + 3 * grow), Kit.col(Theme.WHITE, 0.12f + 0.15f * grow));
        dl.addCircleFilled(origin[0], origin[1], ui.s(6 + grow), Kit.col(Theme.WHITE, 1));
        double[] delta = ui.tools.moveDelta();
        if (delta != null && (delta[0] != 0 || delta[1] != 0 || delta[2] != 0)) {
            String text = String.format(Locale.ROOT, "%+.2f  %+.2f  %+.2f", delta[0], delta[1], delta[2]);
            float w = ui.kit.width(ui.fonts.mono, 12, text) + ui.s(16);
            float x = origin[0] + ui.s(16);
            float y = origin[1] + ui.s(14);
            dl.addRectFilled(x, y, x + w, y + ui.s(22), Kit.col(Theme.GLASS_STRONG, 0.9f), ui.s(11));
            dl.addRect(x, y, x + w, y + ui.s(22), Kit.col(Theme.SPOT, 0.5f), ui.s(11), 0, 1);
            ui.kit.text(dl, ui.fonts.mono, 12, x + ui.s(8), y + ui.s(4), Theme.TEXT, 1, text);
        }
    }
}
