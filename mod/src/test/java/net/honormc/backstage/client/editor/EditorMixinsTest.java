package net.honormc.backstage.client.editor;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class EditorMixinsTest {
    private static List<String> injected(String className) throws ClassNotFoundException {
        Class<?> target = Class.forName(className, false, EditorMixinsTest.class.getClassLoader());
        return Arrays.stream(target.getDeclaredMethods()).map(Method::getName).filter(n -> n.contains("backstage$"))
                .map(n -> n.substring(n.indexOf("backstage$"))).sorted().toList();
    }

    @Test
    void inputAndFrameHooksApplyToTheGameClasses() throws Exception {
        assertEquals(List.of("backstage$button", "backstage$grab", "backstage$scroll"), injected("net.minecraft.client.MouseHandler"));
        assertEquals(List.of("backstage$character", "backstage$key"), injected("net.minecraft.client.KeyboardHandler"));
        assertEquals(List.of("backstage$frame"), injected("net.minecraft.client.Minecraft"));
    }
}
