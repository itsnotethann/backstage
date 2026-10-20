package net.honormc.backstage.client.editor;

import java.util.Collection;

public final class MarkerIds {
    private MarkerIds() {
    }

    public static String next(String prefix, Collection<String> taken) {
        for (int n = 1; ; n++) {
            String id = prefix + "_" + n;
            if (!taken.contains(id)) {
                return id;
            }
        }
    }
}
