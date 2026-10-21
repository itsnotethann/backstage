package net.honormc.backstage.client.editor.ui;

import it.unimi.dsi.fastutil.ints.Int2FloatOpenHashMap;

public final class Anim {
    public static final float HOVER_MS = 120;
    public static final float PANEL_MS = 180;
    public static final float STAGGER_MS = 30;

    private final Int2FloatOpenHashMap values = new Int2FloatOpenHashMap();
    private float dt = 1 / 60f;
    private double time;

    public void frame(float dt) {
        this.dt = Math.clamp(dt, 0, 0.1f);
        time += this.dt;
    }

    public float dt() {
        return dt;
    }

    public double time() {
        return time;
    }

    public float approach(int id, float target, float ms) {
        float value = values.containsKey(id) ? values.get(id) : target;
        float k = 1 - (float) Math.exp(-dt / (ms / 1000f / 3f));
        value += (target - value) * k;
        if (Math.abs(target - value) < 0.0005f) {
            value = target;
        }
        values.put(id, value);
        return value;
    }

    public float hover(int id, boolean on) {
        return approach(id, on ? 1 : 0, HOVER_MS);
    }

    public float get(int id, float fallback) {
        return values.containsKey(id) ? values.get(id) : fallback;
    }

    public void set(int id, float value) {
        values.put(id, value);
    }

    public static float easeOut(float t) {
        float c = Math.clamp(t, 0, 1);
        return 1 - (1 - c) * (1 - c) * (1 - c);
    }

    public static float easeInOut(float t) {
        float c = Math.clamp(t, 0, 1);
        return c < 0.5f ? 4 * c * c * c : 1 - (float) Math.pow(-2 * c + 2, 3) / 2;
    }
}
