package net.honormc.backstage.protocol.model;

import java.util.List;
import java.util.Objects;

public record ListDef(String name, List<ListEntry> entries) {
    public ListDef {
        Objects.requireNonNull(name);
        entries = List.copyOf(entries);
    }
}
