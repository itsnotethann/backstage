package net.honormc.backstage.client.editor.render;

import static org.lwjgl.opengl.GL33C.*;

import java.util.HashMap;
import java.util.Map;

public final class GlProgram {
    private final int id;
    private final Map<String, Integer> uniforms = new HashMap<>();

    public GlProgram(String vertex, String fragment, String... attributes) {
        int vs = compile(GL_VERTEX_SHADER, vertex);
        int fs = compile(GL_FRAGMENT_SHADER, fragment);
        id = glCreateProgram();
        glAttachShader(id, vs);
        glAttachShader(id, fs);
        for (int i = 0; i < attributes.length; i++) {
            glBindAttribLocation(id, i, attributes[i]);
        }
        glBindFragDataLocation(id, 0, "fragColor");
        glLinkProgram(id);
        glDeleteShader(vs);
        glDeleteShader(fs);
        if (glGetProgrami(id, GL_LINK_STATUS) == GL_FALSE) {
            String log = glGetProgramInfoLog(id);
            glDeleteProgram(id);
            throw new IllegalStateException("Backstage shader link failed: " + log);
        }
    }

    private static int compile(int type, String source) {
        int shader = glCreateShader(type);
        glShaderSource(shader, source);
        glCompileShader(shader);
        if (glGetShaderi(shader, GL_COMPILE_STATUS) == GL_FALSE) {
            String log = glGetShaderInfoLog(shader);
            glDeleteShader(shader);
            throw new IllegalStateException("Backstage shader compile failed: " + log);
        }
        return shader;
    }

    public void use() {
        glUseProgram(id);
    }

    public int uniform(String name) {
        return uniforms.computeIfAbsent(name, n -> glGetUniformLocation(id, n));
    }

    public void delete() {
        glDeleteProgram(id);
    }
}
