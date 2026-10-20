package net.honormc.backstage.protocol.model;

import java.util.Objects;

public record ListEntry(String value, String label) {
    public ListEntry {
        Objects.requireNonNull(value);
        Objects.requireNonNull(label);
    }
}
