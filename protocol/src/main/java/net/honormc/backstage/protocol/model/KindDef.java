package net.honormc.backstage.protocol.model;

import java.util.List;
import java.util.Objects;

public record KindDef(String name, String label, MarkerType anchor, List<FieldDef> fields) {
    public KindDef {
        Objects.requireNonNull(name);
        Objects.requireNonNull(label);
        Objects.requireNonNull(anchor);
        fields = List.copyOf(fields);
    }
}
