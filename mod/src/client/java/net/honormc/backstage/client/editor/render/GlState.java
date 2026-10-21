package net.honormc.backstage.client.editor.render;

import static org.lwjgl.opengl.GL33C.*;

public final class GlState {
    private final int program;
    private final int vertexArray;
    private final int arrayBuffer;
    private final int drawFramebuffer;
    private final int readFramebuffer;
    private final int activeTexture;
    private final int texture0;
    private final int[] viewport = new int[4];
    private final boolean blend;
    private final int blendSrcRgb;
    private final int blendDstRgb;
    private final int blendSrcAlpha;
    private final int blendDstAlpha;
    private final int blendEquationRgb;
    private final int blendEquationAlpha;
    private final boolean depthTest;
    private final int depthFunc;
    private final boolean depthMask;
    private final boolean cull;
    private final boolean scissor;
    private final boolean[] colorMask = new boolean[4];

    private GlState() {
        program = glGetInteger(GL_CURRENT_PROGRAM);
        vertexArray = glGetInteger(GL_VERTEX_ARRAY_BINDING);
        arrayBuffer = glGetInteger(GL_ARRAY_BUFFER_BINDING);
        drawFramebuffer = glGetInteger(GL_DRAW_FRAMEBUFFER_BINDING);
        readFramebuffer = glGetInteger(GL_READ_FRAMEBUFFER_BINDING);
        activeTexture = glGetInteger(GL_ACTIVE_TEXTURE);
        glActiveTexture(GL_TEXTURE0);
        texture0 = glGetInteger(GL_TEXTURE_BINDING_2D);
        glGetIntegerv(GL_VIEWPORT, viewport);
        blend = glIsEnabled(GL_BLEND);
        blendSrcRgb = glGetInteger(GL_BLEND_SRC_RGB);
        blendDstRgb = glGetInteger(GL_BLEND_DST_RGB);
        blendSrcAlpha = glGetInteger(GL_BLEND_SRC_ALPHA);
        blendDstAlpha = glGetInteger(GL_BLEND_DST_ALPHA);
        blendEquationRgb = glGetInteger(GL_BLEND_EQUATION_RGB);
        blendEquationAlpha = glGetInteger(GL_BLEND_EQUATION_ALPHA);
        depthTest = glIsEnabled(GL_DEPTH_TEST);
        depthFunc = glGetInteger(GL_DEPTH_FUNC);
        depthMask = glGetBoolean(GL_DEPTH_WRITEMASK);
        cull = glIsEnabled(GL_CULL_FACE);
        scissor = glIsEnabled(GL_SCISSOR_TEST);
        int[] mask = new int[4];
        glGetIntegerv(GL_COLOR_WRITEMASK, mask);
        for (int i = 0; i < 4; i++) {
            colorMask[i] = mask[i] != 0;
        }
    }

    public static GlState save() {
        return new GlState();
    }

    public void restore() {
        glUseProgram(program);
        glBindVertexArray(vertexArray);
        glBindBuffer(GL_ARRAY_BUFFER, arrayBuffer);
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, drawFramebuffer);
        glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
        glActiveTexture(GL_TEXTURE0);
        glBindTexture(GL_TEXTURE_2D, texture0);
        glActiveTexture(activeTexture);
        glViewport(viewport[0], viewport[1], viewport[2], viewport[3]);
        toggle(GL_BLEND, blend);
        glBlendFuncSeparate(blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha);
        glBlendEquationSeparate(blendEquationRgb, blendEquationAlpha);
        toggle(GL_DEPTH_TEST, depthTest);
        glDepthFunc(depthFunc);
        glDepthMask(depthMask);
        toggle(GL_CULL_FACE, cull);
        toggle(GL_SCISSOR_TEST, scissor);
        glColorMask(colorMask[0], colorMask[1], colorMask[2], colorMask[3]);
    }

    private static void toggle(int cap, boolean on) {
        if (on) {
            glEnable(cap);
        } else {
            glDisable(cap);
        }
    }
}
