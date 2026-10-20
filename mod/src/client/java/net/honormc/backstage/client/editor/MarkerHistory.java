package net.honormc.backstage.client.editor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Optional;
import net.honormc.backstage.protocol.ServerboundPacket;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;

public final class MarkerHistory {
    public static final int LIMIT = 50;

    public sealed interface Op {
        MarkerType type();

        String id();

        Op inverse();

        ServerboundPacket packet(String map);
    }

    public record Change(Marker before, Marker after) implements Op {
        public Change {
            if (before == null && after == null) {
                throw new IllegalArgumentException("a change needs a before or an after");
            }
            if (before != null && after != null && (before.type() != after.type() || !before.id().equals(after.id()))) {
                throw new IllegalArgumentException("a change keeps its type and id; use a rename");
            }
        }

        @Override
        public MarkerType type() {
            return after != null ? after.type() : before.type();
        }

        @Override
        public String id() {
            return after != null ? after.id() : before.id();
        }

        @Override
        public Op inverse() {
            return new Change(after, before);
        }

        @Override
        public ServerboundPacket packet(String map) {
            return after == null
                    ? new ServerboundPacket.RemoveMarker(map, before.type(), before.id())
                    : new ServerboundPacket.UpsertMarker(map, after);
        }
    }

    public record Rename(MarkerType type, String from, String to) implements Op {
        @Override
        public String id() {
            return from;
        }

        @Override
        public Op inverse() {
            return new Rename(type, to, from);
        }

        @Override
        public ServerboundPacket packet(String map) {
            return new ServerboundPacket.RenameMarker(map, type, from, to);
        }
    }

    private enum Source {
        RECORD,
        UNDO,
        REDO
    }

    private record Last(Source source, Op sent, List<Op> clearedRedo) {
    }

    private final Deque<Op> undo = new ArrayDeque<>();
    private final Deque<Op> redo = new ArrayDeque<>();
    private Last last;

    public void record(Op op) {
        List<Op> cleared = new ArrayList<>(redo);
        redo.clear();
        undo.push(op);
        while (undo.size() > LIMIT) {
            undo.removeLast();
        }
        last = new Last(Source.RECORD, op, cleared);
    }

    public Optional<Op> undo() {
        Op op = undo.poll();
        if (op == null) {
            return Optional.empty();
        }
        redo.push(op);
        Op inverse = op.inverse();
        last = new Last(Source.UNDO, inverse, List.of());
        return Optional.of(inverse);
    }

    public Optional<Op> redo() {
        Op op = redo.poll();
        if (op == null) {
            return Optional.empty();
        }
        undo.push(op);
        last = new Last(Source.REDO, op, List.of());
        return Optional.of(op);
    }

    public boolean rejected(MarkerType type, String id) {
        if (last == null || last.sent().type() != type || !last.sent().id().equals(id)) {
            return false;
        }
        revert();
        return true;
    }

    public void revert() {
        if (last == null) {
            return;
        }
        switch (last.source()) {
            case RECORD -> {
                undo.poll();
                for (int i = last.clearedRedo().size() - 1; i >= 0; i--) {
                    redo.push(last.clearedRedo().get(i));
                }
            }
            case UNDO -> undo.push(redo.pop());
            case REDO -> redo.push(undo.pop());
        }
        last = null;
    }

    public int undoSize() {
        return undo.size();
    }

    public int redoSize() {
        return redo.size();
    }

    public void clear() {
        undo.clear();
        redo.clear();
        last = null;
    }
}
