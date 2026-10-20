package net.honormc.backstage.protocol.model;

import java.util.Arrays;
import java.util.Objects;

public sealed interface Shape {
    record Box(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) implements Shape {
    }

    record Polygon(int[] xz, int minY, int maxY) implements Shape {
        public Polygon {
            if (xz.length % 2 != 0) {
                throw new IllegalArgumentException("polygon xz must hold x,z pairs");
            }
            xz = xz.clone();
        }

        public int vertices() {
            return xz.length / 2;
        }

        @Override
        public int[] xz() {
            return xz.clone();
        }

        @Override
        public boolean equals(Object o) {
            return o instanceof Polygon p && Arrays.equals(xz, p.xz) && minY == p.minY && maxY == p.maxY;
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(xz), minY, maxY);
        }

        @Override
        public String toString() {
            return "Polygon[xz=" + Arrays.toString(xz) + ", minY=" + minY + ", maxY=" + maxY + "]";
        }
    }
}
