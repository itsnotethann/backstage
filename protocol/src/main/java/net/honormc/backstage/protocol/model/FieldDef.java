package net.honormc.backstage.protocol.model;

import java.util.Objects;

public record FieldDef(String name, String label, FieldType type, boolean required, String listRef) {
    public FieldDef {
        Objects.requireNonNull(name);
        Objects.requireNonNull(label);
        Objects.requireNonNull(type);
        Objects.requireNonNull(listRef);
    }
}
