package net.honormc.backstage.protocol.model;

import java.util.Optional;

public enum FieldType {
    POINT_REF(0),
    GROUP_REF(1),
    REGION_REF(2),
    NUMBER(3),
    STRING(4),
    BOOL(5),
    BLOCK(6),
    ITEM(7),
    CHOICE(8);

    private final int id;

    FieldType(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static Optional<FieldType> byId(int id) {
        for (FieldType type : values()) {
            if (type.id == id) {
                return Optional.of(type);
            }
        }
        return Optional.empty();
    }
}
