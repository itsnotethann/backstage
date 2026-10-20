package net.honormc.backstage.client.lua;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.Heightmap;

public interface WorldView {
    BlockState blockState(BlockPos pos);

    int surfaceY(int x, int z);

    boolean inBuildHeight(int y);

    static WorldView of(Level level) {
        return new WorldView() {
            @Override
            public BlockState blockState(BlockPos pos) {
                return level.getBlockState(pos);
            }

            @Override
            public int surfaceY(int x, int z) {
                return level.getHeight(Heightmap.Types.WORLD_SURFACE, x, z) - 1;
            }

            @Override
            public boolean inBuildHeight(int y) {
                return level.isInsideBuildHeight(y);
            }
        };
    }
}
