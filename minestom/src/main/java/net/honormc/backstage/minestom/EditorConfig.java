package net.honormc.backstage.minestom;

import java.io.IOException;
import java.io.Reader;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.yaml.snakeyaml.LoaderOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.SafeConstructor;

public record EditorConfig(Set<UUID> editors, String serverName) {
    public static final Path DEFAULT_PATH = Path.of("config", "editor", "config.yml");

    static final String DEFAULT_FILE = """
            server-name: "Build"
            editors:
              - "00000000-0000-0000-0000-000000000000"
            """;

    public EditorConfig {
        editors = Set.copyOf(editors);
        if (serverName == null) {
            serverName = "Build";
        }
    }

    public static EditorConfig load(Path file) {
        try {
            if (Files.notExists(file)) {
                Path parent = file.toAbsolutePath().getParent();
                if (parent != null) {
                    Files.createDirectories(parent);
                }
                Files.writeString(file, DEFAULT_FILE, StandardCharsets.UTF_8);
            }
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                Object root = new Yaml(new SafeConstructor(new LoaderOptions())).load(reader);
                return parse(root, file);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static EditorConfig parse(Object root, Path file) {
        if (root == null) {
            return new EditorConfig(Set.of(), "Build");
        }
        if (!(root instanceof Map<?, ?> map)) {
            throw new IllegalArgumentException(file + ": expected a mapping at the top level");
        }
        Object name = map.get("server-name");
        Set<UUID> editors = new LinkedHashSet<>();
        Object list = map.get("editors");
        if (list instanceof List<?> entries) {
            for (Object entry : entries) {
                try {
                    editors.add(UUID.fromString(String.valueOf(entry).trim()));
                } catch (IllegalArgumentException e) {
                    throw new IllegalArgumentException(file + ": '" + entry + "' in editors is not a UUID");
                }
            }
        } else if (list != null) {
            throw new IllegalArgumentException(file + ": editors must be a list of UUIDs");
        }
        return new EditorConfig(editors, name == null ? "Build" : String.valueOf(name));
    }
}
