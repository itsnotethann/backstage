package net.honormc.backstage.client.editor.ui;

import imgui.ImFont;
import imgui.ImFontAtlas;
import imgui.ImFontConfig;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;

public final class Fonts {
    static final short[] TEXT_RANGES = {
            0x0020, 0x00FF, 0x2010, 0x2027, 0x2030, 0x203A, 0x2190, 0x2193, 0x21E7, 0x21E7, 0x2212, 0x2212,
            0x2318, 0x2318, 0x2325, 0x2325, 0x25CF, 0x25CF, 0};

    private static byte[] regularData;
    private static byte[] semiboldData;
    private static byte[] boldData;
    private static byte[] monoData;
    private static byte[] monoSemiData;
    private static byte[] iconData;

    public ImFont body;
    public ImFont semibold;
    public ImFont small;
    public ImFont header;
    public ImFont badge;
    public ImFont bold;
    public ImFont mono;
    public ImFont monoSmall;
    public ImFont icons;
    public ImFont iconsSmall;
    public ImFont title;
    public float scale;

    public void build(ImFontAtlas atlas, float scale) {
        this.scale = scale;
        if (regularData == null) {
            regularData = read("Inter-Regular.ttf");
            semiboldData = read("Inter-SemiBold.ttf");
            boldData = read("Inter-Bold.ttf");
            monoData = read("JetBrainsMono-Regular.ttf");
            monoSemiData = read("JetBrainsMono-SemiBold.ttf");
            iconData = read("tabler-icons.ttf");
        }
        atlas.clear();
        body = withIcons(atlas, regularData, 13, scale);
        semibold = withIcons(atlas, semiboldData, 13, scale);
        small = withIcons(atlas, regularData, 11.5f, scale);
        header = add(atlas, semiboldData, 11, scale, TEXT_RANGES);
        badge = add(atlas, boldData, 10.5f, scale, TEXT_RANGES);
        bold = add(atlas, boldData, 13.5f, scale, TEXT_RANGES);
        title = add(atlas, semiboldData, 15, scale, TEXT_RANGES);
        mono = add(atlas, monoData, 12, scale, TEXT_RANGES);
        monoSmall = add(atlas, monoSemiData, 10, scale, TEXT_RANGES);
        icons = add(atlas, iconData, 19, scale, Icons.RANGES);
        iconsSmall = add(atlas, iconData, 15, scale, Icons.RANGES);
        atlas.build();
    }

    public static int px(float size, float scale) {
        return Math.max(6, Math.round(size * scale));
    }

    private static ImFont add(ImFontAtlas atlas, byte[] data, float size, float scale, short[] ranges) {
        ImFontConfig config = new ImFontConfig();
        config.setOversampleH(3);
        config.setOversampleV(2);
        config.setRasterizerDensity(2);
        config.setFontDataOwnedByAtlas(false);
        config.setGlyphRanges(ranges);
        ImFont font = atlas.addFontFromMemoryTTF(data, px(size, scale), config, ranges);
        config.destroy();
        return font;
    }

    private static ImFont withIcons(ImFontAtlas atlas, byte[] data, float size, float scale) {
        ImFont font = add(atlas, data, size, scale, TEXT_RANGES);
        ImFontConfig config = new ImFontConfig();
        config.setMergeMode(true);
        config.setOversampleH(3);
        config.setOversampleV(2);
        config.setRasterizerDensity(2);
        config.setFontDataOwnedByAtlas(false);
        config.setGlyphOffset(0, 2 * scale);
        config.setGlyphRanges(Icons.RANGES);
        atlas.addFontFromMemoryTTF(iconData, px(size + 1, scale), config, Icons.RANGES);
        config.destroy();
        return font;
    }

    private static byte[] read(String name) {
        try (InputStream in = Fonts.class.getResourceAsStream("/assets/backstage/fonts/" + name)) {
            if (in == null) {
                throw new IOException("missing font " + name);
            }
            return in.readAllBytes();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
