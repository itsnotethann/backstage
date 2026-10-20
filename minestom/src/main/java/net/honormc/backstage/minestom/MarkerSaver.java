package net.honormc.backstage.minestom;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

final class MarkerSaver {
    static final long DEBOUNCE_MILLIS = 2_000;
    private static final System.Logger LOGGER = System.getLogger("backstage");

    private final ScheduledExecutorService executor =
            Executors.newSingleThreadScheduledExecutor(Thread.ofVirtual().name("backstage-save").factory());
    private final Map<MapMarkers, ScheduledFuture<?>> pending = new ConcurrentHashMap<>();

    void schedule(MapMarkers markers, MarkerFile file) {
        pending.compute(markers, (key, previous) -> {
            if (previous != null) {
                previous.cancel(false);
            }
            return executor.schedule(() -> save(markers, file), DEBOUNCE_MILLIS, TimeUnit.MILLISECONDS);
        });
    }

    void flush(MapMarkers markers, MarkerFile file) {
        ScheduledFuture<?> previous = pending.remove(markers);
        if (previous != null) {
            previous.cancel(false);
        }
        save(markers, file);
    }

    void shutdown() {
        executor.shutdown();
        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static void save(MapMarkers markers, MarkerFile file) {
        try {
            file.save(markers);
        } catch (IOException | RuntimeException e) {
            LOGGER.log(System.Logger.Level.ERROR, "saving markers of map " + markers.map() + " to " + file.path() + " failed", e);
        }
    }
}
