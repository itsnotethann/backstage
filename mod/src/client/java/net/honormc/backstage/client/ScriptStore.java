package net.honormc.backstage.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.honormc.backstage.Backstage;

public final class ScriptStore {
    private static final List<String> BUNDLED = List.of(
            "API.md", "tower.lua", "tree.lua", "terrain.lua", "overlay.lua", "dome.lua", "house.lua");

    private final Path dir;

    public ScriptStore() {
        this.dir = FabricLoader.getInstance().getGameDir().resolve("backstage").resolve("scripts");
    }

    public void init() {
        try {
            Path game = FabricLoader.getInstance().getGameDir();
            Path legacy = game.resolve("honorbrush");
            if (Files.isDirectory(legacy) && !Files.exists(game.resolve("backstage"))) {
                Files.move(legacy, game.resolve("backstage"));
            }
            Files.createDirectories(dir);
            for (String name : BUNDLED) {
                Path target = dir.resolve(name);
                if (Files.exists(target)) {
                    continue;
                }
                try (InputStream in = ScriptStore.class.getResourceAsStream("/assets/backstage/scripts/" + name)) {
                    if (in != null) {
                        Files.copy(in, target);
                    }
                }
            }
            try (InputStream in = ScriptStore.class.getResourceAsStream("/assets/backstage/scripts/API.md")) {
                if (in != null) {
                    Files.write(dir.resolve("API.md"), in.readAllBytes());
                }
            }
        } catch (IOException e) {
            Backstage.LOG.error("Could not set up {}", dir, e);
        }
        Backstage.LOG.info("Backstage scripts folder: {}", dir.toAbsolutePath());
    }

    public Path dir() {
        return dir;
    }

    public List<String> list() {
        if (!Files.isDirectory(dir)) {
            return List.of();
        }
        try (Stream<Path> files = Files.walk(dir, 4)) {
            return files.filter(p -> p.getFileName().toString().endsWith(".lua"))
                    .map(p -> dir.relativize(p).toString().replace('\\', '/'))
                    .map(n -> n.substring(0, n.length() - 4))
                    .sorted()
                    .toList();
        } catch (IOException e) {
            return List.of();
        }
    }

    public String read(String name) throws IOException {
        Path file = dir.resolve(name + ".lua").normalize();
        if (!file.startsWith(dir.normalize())) {
            throw new IOException("script path escapes the scripts folder");
        }
        if (!Files.isRegularFile(file)) {
            throw new IOException("no script '" + name + "' (looked for " + file.getFileName() + ")");
        }
        return Files.readString(file, StandardCharsets.UTF_8);
    }
}
