package net.honormc.backstage.client.editor;

public final class EditorScale {
    private static float ui = 1;

    private EditorScale() {
    }

    public static float ui() {
        return ui;
    }

    public static void ui(float scale) {
        ui = scale;
    }

    public static float of(int guiScale) {
        return Math.clamp(guiScale / 3f, 0.75f, 2.5f);
    }
}
