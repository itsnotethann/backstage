package net.honormc.backstage.minestom;

import java.util.UUID;
import java.util.function.Supplier;
import net.minestom.server.entity.Player;

@FunctionalInterface
public interface EditorPermission {
    int OP_LEVEL = 4;

    boolean mayEdit(Player player);

    static EditorPermission defaults(Supplier<EditorConfig> config) {
        return player -> allows(player.getPermissionLevel(), player.getUuid(), config.get());
    }

    static boolean allows(int permissionLevel, UUID uuid, EditorConfig config) {
        return permissionLevel >= OP_LEVEL || config.editors().contains(uuid);
    }
}
