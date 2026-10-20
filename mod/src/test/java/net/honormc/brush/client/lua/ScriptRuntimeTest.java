package net.honormc.brush.client.lua;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.server.Bootstrap;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StairBlock;
import net.minecraft.world.level.block.state.BlockState;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.luaj.vm2.LuaError;

class ScriptRuntimeTest {
    static final class FlatWorld implements WorldView {
        final Map<Long, BlockState> blocks = new HashMap<>();

        @Override
        public BlockState blockState(BlockPos pos) {
            BlockState s = blocks.get(pos.asLong());
            if (s != null) return s;
            return pos.getY() <= 63 ? Blocks.STONE.defaultBlockState() : Blocks.AIR.defaultBlockState();
        }

        @Override
        public int surfaceY(int x, int z) {
            return 63;
        }

        @Override
        public boolean inBuildHeight(int y) {
            return y >= -64 && y < 320;
        }
    }

    static final BlockPos HIT = new BlockPos(0, 63, 0);
    static final List<String> lines = new ArrayList<>();

    @BeforeAll
    static void boot() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    static ScriptRuntime runtime() {
        return new ScriptRuntime(new FlatWorld(), BuiltInRegistries.BLOCK, lines::add);
    }

    static Edit run(String source, Direction facing, Map<String, String> overrides) {
        return runtime().run(new ScriptRuntime.Request("test", source, HIT, HIT.above(), facing, true, 5, 42L, overrides));
    }

    static String bundled(String name) throws Exception {
        try (InputStream in = ScriptRuntimeTest.class.getResourceAsStream("/assets/honorbrush/scripts/" + name)) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    @Test
    void everyBundledScriptRunsInEveryDirection() throws Exception {
        for (String name : List.of("tower.lua", "tree.lua", "terrain.lua", "overlay.lua", "dome.lua", "house.lua")) {
            String source = bundled(name);
            for (Direction facing : List.of(Direction.NORTH, Direction.EAST, Direction.SOUTH, Direction.WEST)) {
                long t = System.nanoTime();
                Edit edit = run(source, facing, Map.of());
                assertFalse(edit.isEmpty(), name + " facing " + facing + " placed nothing");
                if (facing == Direction.NORTH) {
                    System.out.printf("%-12s %7d blocks  %4d ms  %s..%s%n", name, edit.size(),
                            (System.nanoTime() - t) / 1_000_000, edit.min().toShortString(), edit.max().toShortString());
                }
            }
        }
    }

    @Test
    void buildFrameRotatesPositionsAndStates() {
        String src = "function build() set(0, 0, 1, 'oak_stairs[facing=north]') set(1, 0, 0, 'stone') end";
        BlockPos o = HIT.above();

        Edit north = run(src, Direction.NORTH, Map.of());
        assertEquals(Direction.NORTH, north.changes().get(o.north().asLong()).getValue(StairBlock.FACING));
        assertTrue(north.changes().containsKey(o.east().asLong()), "+x is right (east) facing north");

        Edit east = run(src, Direction.EAST, Map.of());
        assertEquals(Direction.EAST, east.changes().get(o.east().asLong()).getValue(StairBlock.FACING));
        assertTrue(east.changes().containsKey(o.south().asLong()), "+x is right (south) facing east");

        Edit south = run(src, Direction.SOUTH, Map.of());
        assertEquals(Direction.SOUTH, south.changes().get(o.south().asLong()).getValue(StairBlock.FACING));

        Edit west = run(src, Direction.WEST, Map.of());
        assertEquals(Direction.WEST, west.changes().get(o.west().asLong()).getValue(StairBlock.FACING));
        assertTrue(west.changes().containsKey(o.north().asLong()), "+x is right (north) facing west");
    }

    @Test
    void getSeesOwnWritesInScriptFrame() {
        String src = """
                function build()
                  set(0, 0, 1, 'oak_stairs[facing=north]')
                  local s = get(0, 0, 1)
                  if not string.find(s, 'facing=north') then error('got ' .. s) end
                  if blockAt(0, -1, 0) ~= 'minecraft:stone' then error('ground is ' .. blockAt(0, -1, 0)) end
                  if highest(3, 3) ~= -1 then error('highest ' .. highest(3, 3)) end
                end""";
        run(src, Direction.EAST, Map.of());
    }

    @Test
    void paintUsesWorldAxesAndReturnValues() {
        String src = "params = { radius = 2 } function paint(x, y, z) if y == 0 then return 'gold_block' end end";
        Edit edit = run(src, Direction.EAST, Map.of());
        assertTrue(edit.changes().containsKey(HIT.east(2).asLong()));
        assertFalse(edit.changes().containsKey(HIT.above().asLong()));
        assertEquals(21, edit.size(), "radius-2 disc of a sphere");
    }

    @Test
    void sandboxHidesDangerousGlobals() {
        String src = """
                function build()
                  for _, n in ipairs({'io', 'os', 'require', 'load', 'loadfile', 'dofile', 'debug', 'luajava'}) do
                    if _G[n] ~= nil then error(n .. ' is reachable') end
                  end
                  set(0, 0, 0, 'stone')
                end""";
        run(src, Direction.NORTH, Map.of());
    }

    @Test
    void infiniteLoopTimesOut() {
        ScriptRuntime rt = runtime().timeout(300);
        LuaError e = assertThrows(LuaError.class, () -> rt.run(new ScriptRuntime.Request(
                "loop", "function build() while true do end end", HIT, HIT.above(), Direction.NORTH, true, 5, 1L, Map.of())));
        assertTrue(e.getMessage().contains("longer than"), e.getMessage());
    }

    @Test
    void overridesAreCoercedToTheDefaultsType() {
        String src = """
                params = { height = 3, solid = true, block = 'stone' }
                function build()
                  if type(params.height) ~= 'number' then error('height not a number') end
                  if params.solid ~= false then error('solid not false') end
                  fill(0, 0, 0, 0, params.height - 1, 0, params.block)
                end""";
        Edit edit = run(src, Direction.NORTH, Map.of("height", "7", "solid", "false", "block", "diamond_block"));
        assertEquals(7, edit.size());
        assertEquals(Blocks.DIAMOND_BLOCK.defaultBlockState(), edit.changes().get(HIT.above().asLong()));
    }

    @Test
    void badBlockIsAReadableError() {
        LuaError e = assertThrows(LuaError.class, () -> run("function build() set(0,0,0,'not_a_block') end", Direction.NORTH, Map.of()));
        assertTrue(e.getMessage().contains("not_a_block"), e.getMessage());
    }

    @Test
    void describeReadsHeaderWithoutBuilding() throws Exception {
        ScriptRuntime.Info info = runtime().describe("house", bundled("house.lua"));
        assertEquals("build", info.mode());
        assertTrue(info.params().containsKey("width"));
        assertFalse(info.description().isEmpty());
    }
}
