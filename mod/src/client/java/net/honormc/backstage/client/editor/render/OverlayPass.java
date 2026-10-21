package net.honormc.backstage.client.editor.render;

import static org.lwjgl.opengl.GL33C.*;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import org.joml.Matrix4fc;
import org.lwjgl.system.MemoryStack;

public final class OverlayPass {
    private static final String VERTEX = """
            #version 150
            in vec3 aPos;
            in vec3 aOther;
            in float aSide;
            in float aDist;
            uniform mat4 uProj;
            uniform mat4 uView;
            uniform vec3 uOrigin;
            uniform vec2 uViewport;
            uniform float uWidth;
            uniform float uBias;
            out float vDist;
            void main() {
                vDist = aDist;
                vec4 a = uProj * uView * vec4(aPos + uOrigin, 1.0);
                if (aSide != 0.0) {
                    vec4 b = uProj * uView * vec4(aOther + uOrigin, 1.0);
                    if (a.w < 0.01 && b.w < 0.01) {
                        gl_Position = vec4(2.0, 2.0, 2.0, 1.0);
                        return;
                    }
                    if (a.w < 0.01) {
                        a = mix(a, b, (0.01 - a.w) / (b.w - a.w));
                    }
                    if (b.w < 0.01) {
                        b = mix(b, a, (0.01 - b.w) / (a.w - b.w));
                    }
                    vec2 sa = a.xy / a.w * uViewport;
                    vec2 sb = b.xy / b.w * uViewport;
                    vec2 dir = sb - sa;
                    float len = length(dir);
                    dir = len > 0.0001 ? dir / len : vec2(1.0, 0.0);
                    vec2 n = vec2(-dir.y, dir.x);
                    a.xy += n * aSide * uWidth / uViewport * a.w;
                    a.z += uBias * a.w;
                }
                gl_Position = a;
            }
            """;
    private static final String FRAGMENT = """
            #version 150
            in float vDist;
            uniform vec4 uColor;
            uniform float uDash;
            uniform float uTime;
            out vec4 fragColor;
            void main() {
                if (uDash > 0.5 && vDist >= 0.0 && fract(vDist * 2.0 - uTime * 1.6) > 0.55) {
                    discard;
                }
                fragColor = uColor;
            }
            """;

    private GlProgram program;
    private int vertexArray;
    private int framebuffer;
    private boolean failed;
    private GlState saved;
    private boolean depthAvailable;
    private float viewportW;
    private float viewportH;

    public boolean begin(RenderTarget target, Matrix4fc projection, Matrix4fc view, float time) {
        if (failed || !(target.getColorTexture() instanceof GlTexture color)) {
            return false;
        }
        saved = GlState.save();
        try {
            if (program == null) {
                program = new GlProgram(VERTEX, FRAGMENT, "aPos", "aOther", "aSide", "aDist");
                vertexArray = glGenVertexArrays();
                framebuffer = glGenFramebuffers();
            }
        } catch (RuntimeException e) {
            failed = true;
            saved.restore();
            saved = null;
            throw e;
        }
        glBindFramebuffer(GL_FRAMEBUFFER, framebuffer);
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color.glId(), 0);
        depthAvailable = target.getDepthTexture() instanceof GlTexture;
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_DEPTH_ATTACHMENT, GL_TEXTURE_2D,
                target.getDepthTexture() instanceof GlTexture depth ? depth.glId() : 0, 0);
        viewportW = target.width;
        viewportH = target.height;
        glViewport(0, 0, target.width, target.height);
        glDisable(GL_SCISSOR_TEST);
        glDisable(GL_CULL_FACE);
        glColorMask(true, true, true, true);
        glDepthMask(false);
        glDepthFunc(GL_GEQUAL);
        program.use();
        glBindVertexArray(vertexArray);
        for (int i = 0; i < 4; i++) {
            glEnableVertexAttribArray(i);
        }
        try (MemoryStack stack = MemoryStack.stackPush()) {
            glUniformMatrix4fv(program.uniform("uProj"), false, projection.get(stack.mallocFloat(16)));
            glUniformMatrix4fv(program.uniform("uView"), false, view.get(stack.mallocFloat(16)));
        }
        glUniform2f(program.uniform("uViewport"), viewportW, viewportH);
        glUniform1f(program.uniform("uTime"), time);
        return true;
    }

    public void end() {
        for (int i = 0; i < 4; i++) {
            glDisableVertexAttribArray(i);
        }
        if (saved != null) {
            saved.restore();
            saved = null;
        }
    }

    public void fill(Mesh mesh, double cameraX, double cameraY, double cameraZ, int argb, float alpha, boolean depth,
                     double offsetX, double offsetY, double offsetZ) {
        if (mesh.empty()) {
            return;
        }
        mesh.bind();
        if (mesh.fillCount() == 0) {
            return;
        }
        setup(mesh, cameraX, cameraY, cameraZ, offsetX, offsetY, offsetZ, argb, alpha, depth, false, 0, false);
        glDrawArrays(GL_TRIANGLES, 0, mesh.fillCount());
    }

    public void lines(Mesh mesh, double cameraX, double cameraY, double cameraZ, int argb, float alpha, float width,
                      boolean depth, boolean additive, boolean dashed, double offsetX, double offsetY, double offsetZ) {
        if (mesh.empty()) {
            return;
        }
        mesh.bind();
        if (mesh.lineCount() == 0) {
            return;
        }
        setup(mesh, cameraX, cameraY, cameraZ, offsetX, offsetY, offsetZ, argb, alpha, depth, additive, width, dashed);
        glDrawArrays(GL_TRIANGLES, mesh.fillCount(), mesh.lineCount());
    }

    private void setup(Mesh mesh, double cameraX, double cameraY, double cameraZ, double offsetX, double offsetY,
                       double offsetZ, int argb, float alpha, boolean depth, boolean additive, float width, boolean dashed) {
        glUniform3f(program.uniform("uOrigin"), (float) (mesh.originX + offsetX - cameraX),
                (float) (mesh.originY + offsetY - cameraY), (float) (mesh.originZ + offsetZ - cameraZ));
        glUniform4f(program.uniform("uColor"), (argb >> 16 & 0xFF) / 255f, (argb >> 8 & 0xFF) / 255f, (argb & 0xFF) / 255f,
                alpha);
        glUniform1f(program.uniform("uWidth"), width);
        glUniform1f(program.uniform("uBias"), width > 0 ? 0.0004f : 0);
        glUniform1f(program.uniform("uDash"), dashed ? 1 : 0);
        if (depth && depthAvailable) {
            glEnable(GL_DEPTH_TEST);
        } else {
            glDisable(GL_DEPTH_TEST);
        }
        glEnable(GL_BLEND);
        glBlendEquation(GL_FUNC_ADD);
        if (additive) {
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE, GL_ZERO, GL_ONE);
        } else {
            glBlendFuncSeparate(GL_SRC_ALPHA, GL_ONE_MINUS_SRC_ALPHA, GL_ZERO, GL_ONE);
        }
    }
}
