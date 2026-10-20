package net.honormc.backstage.protocol;

import java.util.Optional;

public enum DeniedReason {
    NO_PERMISSION(0),
    CLIENT_OUTDATED(1),
    SERVER_OUTDATED(2),
    REVOKED(3);

    private final int id;

    DeniedReason(int id) {
        this.id = id;
    }

    public int id() {
        return id;
    }

    public static Optional<DeniedReason> byId(int id) {
        for (DeniedReason reason : values()) {
            if (reason.id == id) {
                return Optional.of(reason);
            }
        }
        return Optional.empty();
    }
}
