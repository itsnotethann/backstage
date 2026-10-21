package net.honormc.backstage.client.editor.ui;

import java.util.ArrayList;
import java.util.List;

public final class Toasts {
    public static final long LIFETIME_MS = 4_000;
    public static final int MAX = 5;

    public enum Kind {
        OK,
        INFO,
        ERROR
    }

    public record Toast(long id, Kind kind, String title, String message, long created) {
    }

    private final List<Toast> toasts = new ArrayList<>();
    private long next;

    public synchronized void push(Kind kind, String title, String message) {
        if (!toasts.isEmpty()) {
            Toast last = toasts.getLast();
            if (last.title().equals(title) && last.message().equals(message)) {
                toasts.removeLast();
            }
        }
        toasts.add(new Toast(next++, kind, title, message == null ? "" : message, System.currentTimeMillis()));
        while (toasts.size() > MAX) {
            toasts.removeFirst();
        }
    }

    public synchronized List<Toast> live(long now) {
        toasts.removeIf(t -> now - t.created() > LIFETIME_MS + 400);
        return List.copyOf(toasts);
    }

    public synchronized void dismiss(long id) {
        toasts.removeIf(t -> t.id() == id);
    }
}
