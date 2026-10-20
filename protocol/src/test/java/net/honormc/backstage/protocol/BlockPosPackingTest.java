package net.honormc.backstage.protocol;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class BlockPosPackingTest {
    private static void packs(int x, int y, int z, long expected) {
        long packed = BlockPosPacking.pack(x, y, z);
        assertEquals(expected, packed);
        assertEquals(x, BlockPosPacking.x(packed));
        assertEquals(y, BlockPosPacking.y(packed));
        assertEquals(z, BlockPosPacking.z(packed));
    }

    @Test
    void matchesMinecraftPacking() {
        packs(0, 0, 0, 0L);
        packs(-1, -1, -1, -1L);
        packs(-1, 0, 0, 0xFFFFFFC000000000L);
        packs(0, 0, -1, 0x0000003FFFFFF000L);
        packs(0, -64, 0, 0x0000000000000FC0L);
        packs(-30_000_000, -64, 29_999_999, 0x8D8F201C9C37FFC0L);
        packs(123, -45, -678, 0x00001EFFFFD5AFD3L);
    }

    @Test
    void roundTripsWorldRange() {
        int[] xz = {-33_554_432, -30_000_000, -1, 0, 1, 30_000_000, 33_554_431};
        int[] ys = {-2048, -64, -1, 0, 1, 319, 2047};
        for (int x : xz) {
            for (int y : ys) {
                for (int z : xz) {
                    long packed = BlockPosPacking.pack(x, y, z);
                    assertEquals(x, BlockPosPacking.x(packed));
                    assertEquals(y, BlockPosPacking.y(packed));
                    assertEquals(z, BlockPosPacking.z(packed));
                }
            }
        }
    }
}
