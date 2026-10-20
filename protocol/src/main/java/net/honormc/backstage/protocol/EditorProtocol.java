package net.honormc.backstage.protocol;

import java.util.regex.Pattern;

public final class EditorProtocol {
    public static final String CHANNEL = "backstage:editor";
    public static final int VERSION = 1;

    public static final int MAX_SERVERBOUND_BODY = 32_000;
    public static final int MAX_CLIENTBOUND_BODY = 1_000_000;
    public static final int MAX_STRING_BYTES = 32_767;
    public static final int MAX_LIST = 65_536;
    public static final int MAX_TAGS = 64;
    public static final int MAX_TAG_BYTES = 64;
    public static final int MAX_PROPS = 64;
    public static final int MAX_PROP_DEPTH = 4;
    public static final int MAX_GROUP_BLOCKS = 3_500;
    public static final int MIN_POLYGON_VERTICES = 3;
    public static final int MAX_POLYGON_VERTICES = 256;

    public static final Pattern ID = Pattern.compile("[a-z0-9_.-]{1,64}");

    private EditorProtocol() {
    }

    public static boolean isValidId(String id) {
        return id != null && ID.matcher(id).matches();
    }
}
