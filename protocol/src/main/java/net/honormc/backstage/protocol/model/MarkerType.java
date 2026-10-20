package net.honormc.backstage.protocol.model;

import java.util.Optional;

public enum MarkerType {
    POINT(0),
    GROUP(1),
    REGION(2);

    private final int id;

    MarkerType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static Optional<MarkerType> byId(int id) {
        for (MarkerType type : values()) {
            if (type.id == id) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
