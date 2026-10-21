package net.honormc.backstage.client.editor.render;

import static org.lwjgl.opengl.GL33C.*;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import net.honormc.backstage.Backstage;

public final class FrameBlur {
    private static final String VERTEX = """
            #version 150
            out vec2 vUv;
            void main() {
                vec2 p = vec2((gl_VertexID << 1) & 2, gl_VertexID & 2);
                vUv = p;
                gl_Position = vec4(p * 2.0 - 1.0, 0.0, 1.0);
            }
            """;
    private static final String FRAGMENT = """
            #version 150
            in vec2 vUv;
            uniform sampler2D uSource;
            uniform vec2 uStep;
            out vec4 fragColor;
            void main() {
                vec3 c = texture(uSource, vUv).rgb * 0.2270270270;
                c += texture(uSource, vUv + uStep * 1.3846153846).rgb * 0.3162162162;
                c += texture(uSource, vUv - uStep * 1.3846153846).rgb * 0.3162162162;
                c += texture(uSource, vUv + uStep * 3.2307692308).rgb * 0.0702702703;
                c += texture(uSource, vUv - uStep * 3.2307692308).rgb * 0.0702702703;
                fragColor = vec4(c, 1.0);
            }
            """;
    private static final int PASSES = 2;

    private GlProgram program;
    private int vertexArray;
    private int readFramebuffer;
    private final int[] textures = new int[3];
    private final int[] framebuffers = new int[3];
    private int width;
    private int height;
    private boolean failed;
    private boolean ready;

    public boolean failed() {
        return failed;
    }

    public int texture() {
        return ready ? textures[1] : 0;
    }

    public void update(RenderTarget target) {
        ready = false;
        if (failed || !(target.getColorTexture() instanceof GlTexture color)) {
            return;
        }
        GlState saved = GlState.save();
        try {
            if (program == null) {
                program = new GlProgram(VERTEX, FRAGMENT);
                vertexArray = glGenVertexArrays();
                readFramebuffer = glGenFramebuffers();
                glGenTextures(textures);
                glGenFramebuffers(framebuffers);
            }
            int w = Math.max(1, target.width / 4);
            int h = Math.max(1, target.height / 4);
            if (w != width || h != height) {
                resize(w, h);
            }
            glDisable(GL_SCISSOR_TEST);
            glDisable(GL_BLEND);
            glDisable(GL_DEPTH_TEST);
            glDisable(GL_CULL_FACE);
            glColorMask(true, true, true, true);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, readFramebuffer);
            glFramebufferTexture2D(GL_READ_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, color.glId(), 0);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffers[0]);
            glBlitFramebuffer(0, 0, target.width, target.height, 0, 0, width * 2, height * 2, GL_COLOR_BUFFER_BIT, GL_LINEAR);
            glBindFramebuffer(GL_READ_FRAMEBUFFER, framebuffers[0]);
            glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffers[1]);
            glBlitFramebuffer(0, 0, width * 2, height * 2, 0, 0, width, height, GL_COLOR_BUFFER_BIT, GL_LINEAR);
            program.use();
            glUniform1i(program.uniform("uSource"), 0);
            glBindVertexArray(vertexArray);
            glActiveTexture(GL_TEXTURE0);
            glViewport(0, 0, width, height);
            for (int i = 0; i < PASSES; i++) {
                float spread = 1 + i;
                pass(textures[1], framebuffers[2], spread / width, 0);
                pass(textures[2], framebuffers[1], 0, spread / height);
            }
            ready = true;
        } catch (RuntimeException e) {
            failed = true;
            Backstage.LOG.warn("Backstage editor: frame blur disabled: {}", e.getMessage());
        } finally {
            saved.restore();
        }
    }

    private void pass(int source, int framebuffer, float stepX, float stepY) {
        glBindFramebuffer(GL_DRAW_FRAMEBUFFER, framebuffer);
        glBindTexture(GL_TEXTURE_2D, source);
        glUniform2f(program.uniform("uStep"), stepX, stepY);
        glDrawArrays(GL_TRIANGLES, 0, 3);
    }

    private void resize(int w, int h) {
        width = w;
        height = h;
        int[][] sizes = {{w * 2, h * 2}, {w, h}, {w, h}};
        for (int i = 0; i < 3; i++) {
            glBindTexture(GL_TEXTURE_2D, textures[i]);
            glTexImage2D(GL_TEXTURE_2D, 0, GL_RGBA8, sizes[i][0], sizes[i][1], 0, GL_RGBA, GL_UNSIGNED_BYTE, 0L);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE);
            glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE);
            glBindFramebuffer(GL_FRAMEBUFFER, framebuffers[i]);
            glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, textures[i], 0);
        }
    }
}
