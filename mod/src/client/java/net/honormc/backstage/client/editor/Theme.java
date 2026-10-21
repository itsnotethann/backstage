package net.honormc.backstage.client.editor;

import net.honormc.backstage.protocol.model.MarkerType;

public final class Theme {
    public static final int STAGE = 0x07080C;
    public static final int GLASS = 0x10121A;
    public static final float GLASS_ALPHA = 0.62f;
    public static final float GLASS_SOLID_ALPHA = 0.86f;
    public static final int GLASS_STRONG = 0x0E1017;
    public static final float GLASS_STRONG_ALPHA = 0.78f;
    public static final int EDGE = 0xFFFFFF;
    public static final float EDGE_ALPHA = 0.07f;
    public static final float EDGE_HI_ALPHA = 0.14f;
    public static final int TEXT = 0xECEEF4;
    public static final int TEXT_2 = 0xA3A8B8;
    public static final int TEXT_3 = 0x6B7083;
    public static final int SPOT = 0xFFB547;
    public static final int SPOT_LIGHT = 0xFFC76B;
    public static final int SPOT_INK = 0x1A1206;
    public static final int POINT = 0xFF6FB5;
    public static final int GROUP = 0x4FE0D6;
    public static final int GROUP_LIGHT = 0x8FF5EC;
    public static final int REGION = 0x9D8CFF;
    public static final int DANGER = 0xFF5D6C;
    public static final int OK = 0x57E39B;
    public static final int AXIS_X = 0xFF5D6C;
    public static final int AXIS_Y = 0x57E39B;
    public static final int AXIS_Z = 0x5AA8FF;
    public static final int LOGO_MID = 0xFF7A59;
    public static final int SHADOW = 0x000000;
    public static final int TRACK = 0x2A2E3A;
    public static final int WHITE = 0xFFFFFF;
    public static final int BADGE_INK = 0x10201F;

    public static final float RADIUS = 12;
    public static final float RADIUS_SM = 8;
    public static final float MARGIN = 14;
    public static final float GAP = 10;

    private Theme() {
    }

    public static int typeColour(MarkerType type) {
        return switch (type) {
            case POINT -> POINT;
            case GROUP -> GROUP;
            case REGION -> REGION;
        };
    }

    public static int axis(GizmoMath.Axis axis) {
        return switch (axis) {
            case X -> AXIS_X;
            case Y -> AXIS_Y;
            case Z -> AXIS_Z;
        };
    }

    public static int mix(int a, int b, float t) {
        int r = Math.round((a >> 16 & 0xFF) + ((b >> 16 & 0xFF) - (a >> 16 & 0xFF)) * t);
        int g = Math.round((a >> 8 & 0xFF) + ((b >> 8 & 0xFF) - (a >> 8 & 0xFF)) * t);
        int bl = Math.round((a & 0xFF) + ((b & 0xFF) - (a & 0xFF)) * t);
        return r << 16 | g << 8 | bl;
    }

    public static int abgr(int rgb, float alpha) {
        int a = Math.clamp(Math.round(alpha * 255), 0, 255);
        return a << 24 | (rgb & 0xFF) << 16 | (rgb >> 8 & 0xFF) << 8 | rgb >> 16 & 0xFF;
    }
}
