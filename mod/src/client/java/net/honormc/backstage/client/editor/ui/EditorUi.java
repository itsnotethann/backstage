package net.honormc.backstage.client.editor.ui;

import imgui.ImDrawList;
import imgui.ImGui;
import imgui.flag.ImGuiStyleVar;
import imgui.flag.ImGuiWindowFlags;
import it.unimi.dsi.fastutil.objects.Object2FloatOpenHashMap;
import java.util.List;
import net.honormc.backstage.client.editor.EditorMode;
import net.honormc.backstage.client.editor.EditorScale;
import net.honormc.backstage.client.editor.EditorShortcuts;
import net.honormc.backstage.client.editor.EditorTools;
import net.honormc.backstage.client.editor.Theme;

public final class EditorUi {
    static final int PANEL_FLAGS = ImGuiWindowFlags.NoDecoration | ImGuiWindowFlags.NoMove | ImGuiWindowFlags.NoSavedSettings
            | ImGuiWindowFlags.NoBackground | ImGuiWindowFlags.NoScrollWithMouse | ImGuiWindowFlags.NoFocusOnAppearing
            | ImGuiWindowFlags.NoNav;

    final EditorMode mode;
    final EditorTools tools;
    final Fonts fonts;
    final Anim anim = new Anim();
    final Kit kit;
    final TopBar topBar;
    final ToolRail rail;
    final OutlinerPanel outliner;
    final InspectorPanel inspector;
    final StatusPanel status;
    final CommandPalette palette;
    final SidePanels side;
    final ViewportOverlay viewport;
    private final Object2FloatOpenHashMap<String> measured = new Object2FloatOpenHashMap<>();
    private long lastFrame;
    private long flySpeedAt;
    float panelAlpha = 1;

    public EditorUi(EditorMode mode, Fonts fonts) {
        this.mode = mode;
        this.tools = mode.tools();
        this.fonts = fonts;
        this.kit = new Kit(fonts, anim);
        this.topBar = new TopBar(this);
        this.rail = new ToolRail(this);
        this.outliner = new OutlinerPanel(this);
        this.inspector = new InspectorPanel(this);
        this.status = new StatusPanel(this);
        this.palette = new CommandPalette(this);
        this.side = new SidePanels(this);
        this.viewport = new ViewportOverlay(this);
    }

    public void opened() {
        palette.close();
        side.shortcutsOpen = false;
        lastFrame = 0;
    }

    public boolean capturesKeys() {
        return palette.open();
    }

    public boolean escape() {
        if (palette.open()) {
            palette.close();
            return true;
        }
        if (side.shortcutsOpen) {
            side.shortcutsOpen = false;
            return true;
        }
        if (topBar.closeMenus() || inspector.cancel() || outliner.cancel()) {
            return true;
        }
        return false;
    }

    public void focusSearch() {
        outliner.focusSearch();
    }

    public void openPalette() {
        palette.show();
    }

    public void requestDelete() {
        tools.delete();
    }

    public void flySpeedChanged() {
        flySpeedAt = System.currentTimeMillis();
    }

    long flySpeedAt() {
        return flySpeedAt;
    }

    public void draw(int blurTexture) {
        long now = System.nanoTime();
        anim.frame(lastFrame == 0 ? 1 / 60f : (now - lastFrame) / 1e9f);
        lastFrame = now;
        kit.s = EditorScale.ui();
        kit.blur = blurTexture;
        kit.screenW = ImGui.getIO().getDisplaySizeX();
        kit.screenH = ImGui.getIO().getDisplaySizeY();
        viewport.draw();
        topBar.draw();
        rail.draw();
        outliner.draw();
        inspector.draw();
        status.draw();
        side.draw();
        palette.draw();
    }

    float s(float v) {
        return kit.s(v);
    }

    float screenW() {
        return kit.screenW;
    }

    float screenH() {
        return kit.screenH;
    }

    float entrance(int order) {
        float ms = (System.currentTimeMillis() - mode.openedAt()) - order * Anim.STAGGER_MS;
        return Anim.easeOut(ms / Anim.PANEL_MS);
    }

    float measured(String key, float fallback) {
        return measured.containsKey(key) ? measured.getFloat(key) : fallback;
    }

    void measure(String key, float value) {
        measured.put(key, value);
    }

    float dockX() {
        return screenW() - s(Theme.MARGIN) - s(mode.layout().dockWidth);
    }

    float dockTop() {
        return s(Theme.MARGIN) + s(44) + s(Theme.GAP);
    }

    float dockBottom() {
        return screenH() - s(Theme.MARGIN) - s(36) - s(Theme.GAP);
    }

    boolean begin(String name, float x, float y, float w, float h, int order, float slideX, float slideY, int extraFlags) {
        float t = entrance(order);
        float alpha = t * panelAlpha;
        float px = x + slideX * (1 - t) * s(16);
        float py = y + slideY * (1 - t) * s(16);
        ImGui.setNextWindowPos(px, py);
        ImGui.setNextWindowSize(w, h);
        ImGui.pushStyleVar(ImGuiStyleVar.Alpha, Math.max(alpha, 0.001f));
        boolean open = ImGui.begin(name, PANEL_FLAGS | extraFlags);
        ImDrawList dl = ImGui.getWindowDrawList();
        dl.pushClipRectFullScreen();
        kit.panel(ImGui.getBackgroundDrawList(), dl, px, py, px + w, py + h, alpha);
        dl.popClipRect();
        return open;
    }

    void end() {
        ImGui.end();
        ImGui.popStyleVar();
    }

    List<String> caps(EditorShortcuts.Action action) {
        EditorShortcuts.Key key = mode.shortcuts().key(action);
        return key == null ? List.of() : key.caps();
    }
}
