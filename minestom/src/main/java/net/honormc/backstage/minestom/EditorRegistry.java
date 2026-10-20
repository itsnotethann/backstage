package net.honormc.backstage.minestom;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.honormc.backstage.protocol.EditorProtocol;
import net.honormc.backstage.protocol.model.KindDef;
import net.honormc.backstage.protocol.model.ListDef;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Registry;

public final class EditorRegistry {
    private final Set<MarkerType> types = EnumSet.allOf(MarkerType.class);
    private final Map<String, KindDef> kinds = new LinkedHashMap<>();
    private final Map<String, ListDef> lists = new LinkedHashMap<>();
    private final Runnable onChange;

    EditorRegistry(Runnable onChange) {
        this.onChange = onChange;
    }

    public EditorRegistry kind(KindDef kind) {
        if (!EditorProtocol.isValidId(kind.name())) {
            throw new IllegalArgumentException("invalid kind name '" + kind.name() + "'");
        }
        synchronized (this) {
            kinds.put(kind.name(), kind);
        }
        onChange.run();
        return this;
    }

    public EditorRegistry list(ListDef list) {
        if (!EditorProtocol.isValidId(list.name())) {
            throw new IllegalArgumentException("invalid list name '" + list.name() + "'");
        }
        synchronized (this) {
            lists.put(list.name(), list);
        }
        onChange.run();
        return this;
    }

    public EditorRegistry types(Set<MarkerType> narrowed) {
        synchronized (this) {
            types.clear();
            types.addAll(narrowed);
        }
        onChange.run();
        return this;
    }

    public synchronized Registry snapshot() {
        List<MarkerType> sorted = new ArrayList<>(types);
        sorted.sort(Comparator.comparingInt(MarkerType::id));
        return new Registry(sorted, List.copyOf(kinds.values()), List.copyOf(lists.values()));
    }
}
