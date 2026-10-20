package net.honormc.backstage.minestom;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import net.kyori.adventure.nbt.BinaryTagIO;
import net.kyori.adventure.nbt.CompoundBinaryTag;

final class MarkerFile {
    private static final System.Logger LOGGER = System.getLogger("backstage");

    private final Path file;
    private final Path tmp;
    private final Path bak;
    private boolean backedUp;
    private long savedRevision;

    MarkerFile(Path file) {
        this.file = file;
        this.tmp = file.resolveSibling(file.getFileName() + ".tmp");
        this.bak = file.resolveSibling(file.getFileName() + ".bak");
    }

    static MarkerFile forPolar(Path polar) {
        return new MarkerFile(MarkerSidecar.pathFor(polar));
    }

    Path path() {
        return file;
    }

    synchronized MapMarkers load(String map) throws IOException {
        if (Files.notExists(file)) {
            savedRevision = 0;
            return new MapMarkers(MarkerSnapshot.empty(map), true);
        }
        CompoundBinaryTag root = BinaryTagIO.unlimitedReader().read(file, BinaryTagIO.Compression.GZIP);
        String format = root.getString("format", "");
        if (!MarkerSidecar.FORMAT.equals(format)) {
            throw new IOException(file + " is not a backstage markers file (format '" + format + "')");
        }
        int version = root.getInt("version", 0);
        if (version > MarkerSidecar.VERSION) {
            LOGGER.log(System.Logger.Level.ERROR, "{0} is markers version {1}, newer than {2}; map {3} binds empty and read-only",
                    file, version, MarkerSidecar.VERSION, map);
            return new MapMarkers(MarkerSnapshot.empty(map), false);
        }
        MarkerSnapshot snapshot = switch (version) {
            case 1 -> MarkerSidecar.readV1(root, map);
            default -> throw new IOException(file + " has unsupported markers version " + version);
        };
        savedRevision = snapshot.revision();
        return new MapMarkers(snapshot, true);
    }

    synchronized boolean save(MapMarkers markers) throws IOException {
        if (!markers.writable()) {
            return false;
        }
        MarkerSnapshot snapshot = markers.snapshot();
        if (snapshot.revision() == savedRevision) {
            return false;
        }
        Path parent = file.toAbsolutePath().getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
        if (!backedUp) {
            if (Files.exists(file)) {
                Files.copy(file, bak, StandardCopyOption.REPLACE_EXISTING);
            }
            backedUp = true;
        }
        BinaryTagIO.writer().write(MarkerSidecar.write(snapshot), tmp, BinaryTagIO.Compression.GZIP);
        Files.move(tmp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
        savedRevision = snapshot.revision();
        return true;
    }
}
