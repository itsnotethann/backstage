package net.honormc.backstage.client.editor.ui;

import static org.lwjgl.opengl.GL33C.*;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.flag.ImGuiConfigFlags;
import imgui.gl3.ImGuiImplGl3;
import imgui.glfw.ImGuiImplGlfw;
import net.honormc.backstage.client.editor.EditorScale;
import net.honormc.backstage.client.editor.render.GlState;
import org.lwjgl.glfw.GLFW;
import org.lwjgl.glfw.GLFWMonitorCallback;

public final class ImGuiLayer {
    private final ImGuiImplGlfw glfw = new ImGuiImplGlfw();
    private final ImGuiImplGl3 gl3 = new ImGuiImplGl3();
    private final Fonts fonts = new Fonts();
    private final long window;
    private int framebuffer;
    private boolean started;

    public ImGuiLayer(long window) {
        this.window = window;
    }

    public Fonts fonts() {
        return fonts;
    }

    private void start() {
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.setLogFilename(null);
        io.addConfigFlags(ImGuiConfigFlags.NoMouseCursorChange);
        GLFWMonitorCallback monitors = GLFW.glfwSetMonitorCallback(null);
        glfw.init(window, false);
        GLFW.glfwSetMonitorCallback(monitors);
        GlState saved = GlState.save();
        try {
            gl3.init("#version 150");
        } finally {
            saved.restore();
        }
        Style.apply(1);
        started = true;
    }

    public void key(int key, int scancode, int action, int mods) {
        if (started) {
            glfw.keyCallback(window, key, scancode, action, mods);
        }
    }

    public void character(int codepoint) {
        if (started) {
            glfw.charCallback(window, codepoint);
        }
    }

    public void mouseButton(int button, int action, int mods) {
        if (started) {
            glfw.mouseButtonCallback(window, button, action, mods);
        }
    }

    public void scroll(double x, double y) {
        if (started) {
            glfw.scrollCallback(window, x, y);
        }
    }

    public boolean wantsMouse() {
        return started && ImGui.getIO().getWantCaptureMouse();
    }

    public boolean wantsText() {
        return started && ImGui.getIO().getWantTextInput();
    }

    public void frame(RenderTarget target, Runnable ui) {
        if (!(target.getColorTexture() instanceof GlTexture color)) {
            return;
        }
        if (!started) {
            start();
        }
        float scale = EditorScale.ui();
        if (fonts.scale != scale) {
            GlState saved = GlState.save();
            try {
                gl3.destroyFontsTexture();
                fonts.build(ImGui.getIO().getFonts(), scale);
                gl3.createFontsTexture();
            } finally {
                saved.restore();
            }
            Style.apply(scale);
        }
        glfw.newFrame();
        gl3.newFrame();
        ImGui.newFrame();
        ImGui.pushFont(fonts.body, Fonts.px(13, scale));
        try {
            ui.run();
        } finally {
            ImGui.popFont();
            ImGui.render();
        }
        GlState saved = GlState.save();
        try {
            if (framebuffer == 0) {
                framebuffer = glGenFramebuffers();
            }
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color.glId(), 0);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D, 0, 0);
            glViewport(0, 0, target.width, target.height);
            gl3.renderDrawData(ImGui.getDrawData());
        } finally {
            saved.restore();
        }
    }
}
