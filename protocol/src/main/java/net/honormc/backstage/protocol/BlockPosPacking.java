package net.honormc.backstage.protocol;

public final class BlockPosPacking {
    private static final int XZ_BITS = 26;
    private static final int Y_BITS = 12;
    private static final long XZ_MASK = (1L << XZ_BITS) - 1;
    private static final long Y_MASK = (1L << Y_BITS) - 1;
    private static final int X_SHIFT = Y_BITS + XZ_BITS;
    private static final int Z_SHIFT = Y_BITS;

    private BlockPosPacking() {
    }

    public static long pack(int x, int y, int z) {
        return ((x & XZ_MASK) << X_SHIFT) | ((z & XZ_MASK) << Z_SHIFT) | (y & Y_MASK);
    }

    public static int x(long packed) {
        return (int) (packed << (64 - X_SHIFT - XZ_BITS) >> (64 - XZ_BITS));
    }

    public static int y(long packed) {
        return (int) (packed << (64 - Y_BITS) >> (64 - Y_BITS));
    }

    public static int z(long packed) {
        return (int) (packed << (64 - Z_SHIFT - XZ_BITS) >> (64 - XZ_BITS));
    }
}
