package net.honormc.backstage.client.editor;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonParseException;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;
import net.honormc.backstage.Backstage;

public final class EditorLayout {
    public static final float MIN_DOCK = 240;
    public static final float MAX_DOCK = 520;
    public static final int MIN_RADIUS = 16;
    public static final int MAX_RADIUS = 512;
    public static final int MAX_BRUSH = 8;
    public static final float MIN_FLY = 0.01f;
    public static final float MAX_FLY = 0.25f;

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    public float dockWidth = 300;
    public float outlinerShare = 0.52f;
    public boolean optionsOpen = true;
    public boolean historyOpen;
    public boolean points = true;
    public boolean groups = true;
    public boolean regions = true;
    public boolean labels = true;
    public int renderRadius = 128;
    public String tool = "select";
    public int brushRadius = 2;
    public String brushMode = "paint";
    public boolean sameBlock;
    public boolean boxAsGroup;
    public float flySpeed = 0.05f;

    public static EditorLayout fromJson(String json) {
        EditorLayout layout;
        try {
            layout = GSON.fromJson(json, EditorLayout.class);
        } catch (JsonParseException e) {
            return new EditorLayout();
        }
        return layout == null ? new EditorLayout() : layout.clamped();
    }

    public String toJson() {
        return GSON.toJson(clamped());
    }

    public static EditorLayout read(Path file) {
        if (!Files.isRegularFile(file)) {
            return new EditorLayout();
        }
        try {
            return fromJson(Files.readString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            Backstage.LOG.warn("Backstage editor: could not read {}: {}", file, e.getMessage());
            return new EditorLayout();
        }
    }

    public void write(Path file) {
        try {
            Files.createDirectories(file.getParent());
            Files.writeString(file, toJson(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            Backstage.LOG.warn("Backstage editor: could not write {}: {}", file, e.getMessage());
        }
    }

    public EditorLayout clamped() {
        dockWidth = Float.isFinite(dockWidth) ? Math.clamp(dockWidth, MIN_DOCK, MAX_DOCK) : 300;
        outlinerShare = Float.isFinite(outlinerShare) ? Math.clamp(outlinerShare, 0.2f, 0.8f) : 0.52f;
        renderRadius = Math.clamp(renderRadius, MIN_RADIUS, MAX_RADIUS);
        brushRadius = Math.clamp(brushRadius, 0, MAX_BRUSH);
        flySpeed = Float.isFinite(flySpeed) ? Math.clamp(flySpeed, MIN_FLY, MAX_FLY) : 0.05f;
        if (tool == null) {
            tool = "select";
        }
        if (!"paint".equals(brushMode) && !"erase".equals(brushMode) && !"fill".equals(brushMode)) {
            brushMode = "paint";
        }
        return this;
    }

    @Override
    public boolean equals(Object o) {
        return o instanceof EditorLayout l && Float.compare(dockWidth, l.dockWidth) == 0
                && Float.compare(outlinerShare, l.outlinerShare) == 0 && optionsOpen == l.optionsOpen
                && historyOpen == l.historyOpen && points == l.points && groups == l.groups && regions == l.regions
                && labels == l.labels && renderRadius == l.renderRadius && Objects.equals(tool, l.tool)
                && brushRadius == l.brushRadius && Objects.equals(brushMode, l.brushMode) && sameBlock == l.sameBlock
                && boxAsGroup == l.boxAsGroup && Float.compare(flySpeed, l.flySpeed) == 0;
    }

    @Override
    public int hashCode() {
        return Objects.hash(dockWidth, outlinerShare, optionsOpen, historyOpen, points, groups, regions, labels,
                renderRadius, tool, brushRadius, brushMode, sameBlock, boxAsGroup, flySpeed);
    }
}
