package net.honormc.brush.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import net.honormc.brush.client.lua.Edit;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.world.level.block.state.BlockState;

/**
 * Shows the pending preview with particles: one per changed block for small
 * edits (green = placing, red = clearing to air), and a bounding-box outline
 * for big ones. Particles need no render hooks, which keeps this working on
 * whatever renderer the client (Lunar included) runs.
 */
final class PreviewRenderer {

    private static final int EVERY_TICKS = 10;
    /** Above this many blocks, draw just the outline. */
    private static final int PER_BLOCK_LIMIT = 1500;

    private static final DustParticleOptions PLACE = new DustParticleOptions(0x55FF55, 1.0f);
    private static final DustParticleOptions CLEAR = new DustParticleOptions(0xFF5555, 1.0f);
    private static final DustParticleOptions EDGE = new DustParticleOptions(0x55FFFF, 1.5f);

    private static int ticks;

    private PreviewRenderer() {}

    static void tick(Minecraft mc, BrushSession session) {
        if (mc.level == null || ++ticks % EVERY_TICKS != 0) {
            return;
        }
        Edit edit = session.preview();
        if (edit == null) {
            return;
        }
        if (edit.size() <= PER_BLOCK_LIMIT) {
            for (Long2ObjectMap.Entry<BlockState> e : edit.changes().long2ObjectEntrySet()) {
                long pos = e.getLongKey();
                DustParticleOptions dust = e.getValue().isAir() ? CLEAR : PLACE;
                mc.level.addParticle(dust, true, true,
                        BlockPos.getX(pos) + 0.5, BlockPos.getY(pos) + 0.5, BlockPos.getZ(pos) + 0.5, 0, 0, 0);
            }
        }
        outline(mc, edit.min(), edit.max());
    }

    private static void outline(Minecraft mc, BlockPos min, BlockPos max) {
        double x0 = min.getX(), y0 = min.getY(), z0 = min.getZ();
        double x1 = max.getX() + 1, y1 = max.getY() + 1, z1 = max.getZ() + 1;
        double[][] corners = {{x0, z0}, {x1, z0}, {x0, z1}, {x1, z1}};
        for (double[] c : corners) {
            edge(mc, c[0], y0, c[1], c[0], y1, c[1]);
        }
        for (double y : new double[] {y0, y1}) {
            edge(mc, x0, y, z0, x1, y, z0);
            edge(mc, x0, y, z1, x1, y, z1);
            edge(mc, x0, y, z0, x0, y, z1);
            edge(mc, x1, y, z0, x1, y, z1);
        }
    }

    private static void edge(Minecraft mc, double ax, double ay, double az, double bx, double by, double bz) {
        double length = Math.sqrt((bx - ax) * (bx - ax) + (by - ay) * (by - ay) + (bz - az) * (bz - az));
        // Half-block spacing, but never more than 200 particles per edge on huge boxes.
        int steps = (int) Math.min(200, Math.max(1, Math.ceil(length * 2)));
        for (int i = 0; i <= steps; i++) {
            double t = (double) i / steps;
            mc.level.addParticle(EDGE, true, true,
                    ax + (bx - ax) * t, ay + (by - ay) * t, az + (bz - az) * t, 0, 0, 0);
        }
    }
}
