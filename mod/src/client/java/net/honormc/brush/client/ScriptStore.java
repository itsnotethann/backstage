package net.honormc.brush.client;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;
import net.fabricmc.loader.api.FabricLoader;
import net.honormc.brush.HonorBrush;

/**
 * The scripts folder: {@code <game dir>/honorbrush/scripts}. Scripts are read
 * fresh from disk on every run, so editing a file (by hand or by a tool) takes
 * effect on the next click with no reload step.
 */
public final class ScriptStore {

    /** Shipped in the jar and copied out on first launch; never overwritten after. */
    private static final List<String> BUNDLED = List.of(
            "API.md", "tower.lua", "tree.lua", "terrain.lua", "overlay.lua", "dome.lua", "house.lua");

    private final Path dir;

    public ScriptStore() {
        this.dir = FabricLoader.getInstance().getGameDir().resolve("honorbrush").resolve("scripts");
    }

    public void init() {
        try {
            Files.createDirectories(dir);
            for (String name : BUNDLED) {
                Path target = dir.resolve(name);
                if (Files.exists(target)) {
                    continue;
                }
                try (InputStream in = ScriptStore.class.getResourceAsStream("/assets/honorbrush/scripts/" + name)) {
                    if (in != null) {
                        Files.copy(in, target);
                    }
                }
            }
            // API.md is documentation, so keep it current with the jar even if it exists.
            try (InputStream in = ScriptStore.class.getResourceAsStream("/assets/honorbrush/scripts/API.md")) {
                if (in != null) {
                    Files.write(dir.resolve("API.md"), in.readAllBytes());
                }
            }
        } catch (IOException e) {
            HonorBrush.LOG.error("Could not set up {}", dir, e);
        }
        HonorBrush.LOG.info("HonorBrush scripts folder: {}", dir.toAbsolutePath());
    }

    public Path dir() {
        return dir;
    }

    /** Script names without {@code .lua}, subfolders joined with '/'. */
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
