package net.honormc.brush.client.lua;

import java.util.Map;
import java.util.function.Consumer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.level.block.state.BlockState;
import org.luaj.vm2.Globals;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.LuaError;
import org.luaj.vm2.LuaTable;
import org.luaj.vm2.LuaValue;
import org.luaj.vm2.Varargs;
import org.luaj.vm2.compiler.LuaC;
import org.luaj.vm2.lib.Bit32Lib;
import org.luaj.vm2.lib.DebugLib;
import org.luaj.vm2.lib.PackageLib;
import org.luaj.vm2.lib.StringLib;
import org.luaj.vm2.lib.TableLib;
import org.luaj.vm2.lib.VarArgFunction;
import org.luaj.vm2.lib.ZeroArgFunction;
import org.luaj.vm2.lib.jse.JseBaseLib;
import org.luaj.vm2.lib.jse.JseMathLib;

/**
 * Runs one script once and returns the blocks it wants changed. Nothing here
 * touches the world: reads go through a {@link WorldView}, writes go
 * into an {@link Edit} that the caller previews or sends to the server.
 *
 * <p>The sandbox has base, string, table, math and bit32, with no io, os,
 * require, load-from-file or luajava. A debug hook enforces the time limit and
 * is removed from the globals before the script runs.
 */
public final class ScriptRuntime {

    /** Hard cap on blocks in one edit, so a runaway loop cannot eat the heap. */
    public static final int MAX_BLOCKS = 4_000_000;
    public static final long TIMEOUT_MS = 30_000;
    private static final int MAX_PRINT_LINES = 30;

    /** What the player asked for: where, which way, and with what settings. */
    public record Request(
            String scriptName,
            String source,
            BlockPos origin,
            BlockPos buildOrigin,
            net.minecraft.core.Direction facing,
            boolean rotate,
            int radius,
            long seed,
            Map<String, String> overrides) {}

    private final WorldView world;
    private final BlockParser blocks;
    private final Consumer<String> output;

    private Frame frame;
    private Edit edit;
    private Noise noise;
    private long deadline;
    private int printed;
    private long timeoutMs = TIMEOUT_MS;

    public ScriptRuntime(Level level, Consumer<String> output) {
        this(WorldView.of(level), level.registryAccess().lookupOrThrow(Registries.BLOCK), output);
    }

    public ScriptRuntime(WorldView world, HolderLookup<Block> blockLookup, Consumer<String> output) {
        this.world = world;
        this.blocks = new BlockParser(blockLookup);
        this.output = output;
    }

    /** Overrides the run-time limit; tests use a short one. */
    public ScriptRuntime timeout(long ms) {
        this.timeoutMs = ms;
        return this;
    }

    public Edit run(Request request) {
        edit = new Edit();
        noise = new Noise(request.seed());
        printed = 0;
        deadline = System.currentTimeMillis() + timeoutMs;

        Globals g = sandbox();
        g.get("math").get("randomseed").call(LuaValue.valueOf(request.seed()));
        g.set("seed", LuaValue.valueOf(request.seed()));

        g.load(request.source(), "@" + request.scriptName()).call();

        LuaTable params = g.get("params").istable() ? (LuaTable) g.get("params") : new LuaTable();
        g.set("params", params);
        applyOverrides(params, request.overrides());

        LuaValue build = g.get("build");
        LuaValue paint = g.get("paint");
        if (build.isfunction()) {
            frame = request.rotate()
                    ? Frame.facing(request.buildOrigin(), request.facing())
                    : Frame.facing(request.buildOrigin(), net.minecraft.core.Direction.NORTH);
            build.call();
        } else if (paint.isfunction()) {
            frame = Frame.identity(request.origin());
            LuaValue r = params.get("radius");
            int radius = r.isnumber() ? r.toint() : request.radius();
            paint(paint, Math.max(0, Math.min(radius, 128)));
        } else {
            throw new LuaError("script defines neither build() nor paint()");
        }
        return edit;
    }

    /** What a script declares without running it: its {@code description} and {@code params} defaults. */
    public record Info(String description, String mode, java.util.SortedMap<String, String> params) {}

    /** Evaluates only the script's top level (definitions), never build/paint. */
    public Info describe(String name, String source) {
        edit = new Edit();
        noise = new Noise(0);
        frame = Frame.identity(BlockPos.ZERO);
        deadline = System.currentTimeMillis() + 2_000;
        Globals g = sandbox();
        g.load(source, "@" + name).call();
        java.util.SortedMap<String, String> params = new java.util.TreeMap<>();
        if (g.get("params").istable()) {
            LuaTable t = (LuaTable) g.get("params");
            for (LuaValue k = t.next(LuaValue.NIL).arg1(); !k.isnil(); k = t.next(k).arg1()) {
                params.put(k.tojstring(), t.get(k).tojstring());
            }
        }
        String mode = g.get("build").isfunction() ? "build" : g.get("paint").isfunction() ? "paint" : "none";
        LuaValue description = g.get("description");
        return new Info(description.isstring() ? description.tojstring() : "", mode, params);
    }

    /** Calls {@code paint(x, y, z, wx, wy, wz)} for every block in a sphere and sets what it returns. */
    private void paint(LuaValue fn, int radius) {
        BlockPos o = frame.origin();
        double r2 = (radius + 0.5) * (radius + 0.5);
        for (int x = -radius; x <= radius; x++) {
            for (int y = -radius; y <= radius; y++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + y * y + z * z > r2) {
                        continue;
                    }
                    LuaValue result = fn.invoke(LuaValue.varargsOf(new LuaValue[] {
                            LuaValue.valueOf(x), LuaValue.valueOf(y), LuaValue.valueOf(z),
                            LuaValue.valueOf(o.getX() + x), LuaValue.valueOf(o.getY() + y), LuaValue.valueOf(o.getZ() + z)
                    })).arg1();
                    if (!result.isnil()) {
                        set(x, y, z, result);
                    }
                }
            }
        }
    }

    // ---- world access, all in script coordinates ----

    private void set(int x, int y, int z, LuaValue block) {
        if (block.isnil()) {
            return;
        }
        BlockState state = frame.toWorld(blocks.parse(block.checkjstring()));
        long pos = frame.toWorld(x, y, z);
        if (!world.inBuildHeight(BlockPos.getY(pos))) {
            return;
        }
        edit.put(pos, state);
        if (edit.size() > MAX_BLOCKS) {
            throw new LuaError("edit is over " + MAX_BLOCKS + " blocks; aborting");
        }
    }

    private BlockState read(int x, int y, int z) {
        long pos = frame.toWorld(x, y, z);
        BlockState pending = edit.pending(pos);
        BlockState state = pending != null ? pending : world.blockState(BlockPos.of(pos));
        return frame.toScript(state);
    }

    private int highest(int x, int z) {
        int wx = frame.worldX(x, z), wz = frame.worldZ(x, z);
        int top = world.surfaceY(wx, wz);
        return top - frame.origin().getY();
    }

    private void fill(int x1, int y1, int z1, int x2, int y2, int z2, LuaValue block, boolean hollow) {
        int ax = Math.min(x1, x2), bx = Math.max(x1, x2);
        int ay = Math.min(y1, y2), by = Math.max(y1, y2);
        int az = Math.min(z1, z2), bz = Math.max(z1, z2);
        for (int x = ax; x <= bx; x++) {
            for (int y = ay; y <= by; y++) {
                for (int z = az; z <= bz; z++) {
                    if (hollow && x != ax && x != bx && y != ay && y != by && z != az && z != bz) {
                        continue;
                    }
                    set(x, y, z, block);
                }
            }
        }
    }

    private void sphere(int cx, int cy, int cz, double r, LuaValue block, boolean hollow) {
        int ir = (int) Math.ceil(r);
        double outer = (r + 0.5) * (r + 0.5), inner = (r - 0.5) * (r - 0.5);
        for (int x = -ir; x <= ir; x++) {
            for (int y = -ir; y <= ir; y++) {
                for (int z = -ir; z <= ir; z++) {
                    double d = x * x + y * y + z * z;
                    if (d <= outer && (!hollow || d >= inner)) {
                        set(cx + x, cy + y, cz + z, block);
                    }
                }
            }
        }
    }

    private void cylinder(int cx, int y0, int cz, double r, int height, LuaValue block, boolean hollow) {
        int ir = (int) Math.ceil(r);
        double outer = (r + 0.5) * (r + 0.5), inner = (r - 0.5) * (r - 0.5);
        for (int x = -ir; x <= ir; x++) {
            for (int z = -ir; z <= ir; z++) {
                double d = x * x + z * z;
                if (d <= outer && (!hollow || d >= inner)) {
                    for (int y = 0; y < height; y++) {
                        set(cx + x, y0 + y, cz + z, block);
                    }
                }
            }
        }
    }

    private void line(int x1, int y1, int z1, int x2, int y2, int z2, LuaValue block) {
        int steps = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        for (int i = 0; i <= steps; i++) {
            double t = steps == 0 ? 0 : (double) i / steps;
            set((int) Math.round(x1 + (x2 - x1) * t),
                    (int) Math.round(y1 + (y2 - y1) * t),
                    (int) Math.round(z1 + (z2 - z1) * t), block);
        }
    }

    // ---- sandbox ----

    private Globals sandbox() {
        Globals g = new Globals();
        g.load(new JseBaseLib());
        g.load(new PackageLib()); // the other libs register in package.loaded; hidden below
        g.load(new TableLib());
        g.load(new StringLib());
        g.load(new JseMathLib());
        g.load(new Bit32Lib());
        g.load(new DebugLib());
        LoadState.install(g);
        LuaC.install(g);

        // Time limit: a count hook every 10k instructions. Installed, then debug is hidden.
        g.get("debug").get("sethook").invoke(LuaValue.varargsOf(
                new ZeroArgFunction() {
                    @Override
                    public LuaValue call() {
                        checkTime();
                        return NONE;
                    }
                }, LuaValue.valueOf(""), LuaValue.valueOf(10_000)));
        g.set("debug", LuaValue.NIL);
        for (String unsafe : new String[] {"dofile", "loadfile", "load", "collectgarbage", "require", "package"}) {
            g.set(unsafe, LuaValue.NIL);
        }
        g.set("print", fn(a -> {
            StringBuilder line = new StringBuilder();
            for (int i = 1; i <= a.narg(); i++) {
                if (i > 1) line.append(' ');
                line.append(a.arg(i).tojstring());
            }
            if (printed++ < MAX_PRINT_LINES) {
                output.accept(line.toString());
            }
            return LuaValue.NONE;
        }));

        g.set("set", fn(a -> {
            set(i(a, 1), i(a, 2), i(a, 3), a.arg(4));
            return LuaValue.NONE;
        }));
        g.set("get", fn(a -> LuaValue.valueOf(BlockParser.serialize(read(i(a, 1), i(a, 2), i(a, 3))))));
        g.set("blockAt", fn(a -> LuaValue.valueOf(id(read(i(a, 1), i(a, 2), i(a, 3))))));
        g.set("isAir", fn(a -> LuaValue.valueOf(read(i(a, 1), i(a, 2), i(a, 3)).isAir())));
        g.set("isSolid", fn(a -> LuaValue.valueOf(read(i(a, 1), i(a, 2), i(a, 3)).isSolid())));
        g.set("highest", fn(a -> LuaValue.valueOf(highest(i(a, 1), i(a, 2)))));

        g.set("fill", fn(a -> {
            fill(i(a, 1), i(a, 2), i(a, 3), i(a, 4), i(a, 5), i(a, 6), a.arg(7), false);
            return LuaValue.NONE;
        }));
        g.set("box", fn(a -> {
            fill(i(a, 1), i(a, 2), i(a, 3), i(a, 4), i(a, 5), i(a, 6), a.arg(7), true);
            return LuaValue.NONE;
        }));
        g.set("sphere", fn(a -> {
            sphere(i(a, 1), i(a, 2), i(a, 3), a.checkdouble(4), a.arg(5), a.optboolean(6, false));
            return LuaValue.NONE;
        }));
        g.set("cylinder", fn(a -> {
            cylinder(i(a, 1), i(a, 2), i(a, 3), a.checkdouble(4), i(a, 5), a.arg(6), a.optboolean(7, false));
            return LuaValue.NONE;
        }));
        g.set("line", fn(a -> {
            line(i(a, 1), i(a, 2), i(a, 3), i(a, 4), i(a, 5), i(a, 6), a.arg(7));
            return LuaValue.NONE;
        }));

        g.set("noise", fn(a -> LuaValue.valueOf(noise.noise(a.checkdouble(1), a.optdouble(2, 0), a.optdouble(3, 0)))));
        g.set("fbm", fn(a -> LuaValue.valueOf(noise.fbm(a.checkdouble(1), a.checkdouble(2), a.checkdouble(3), a.optint(4, 4)))));
        g.set("rand", g.get("math").get("random"));
        g.set("pick", fn(a -> {
            // pick({"stone", "andesite", "cobblestone"}) or pick({stone = 3, mossy_cobblestone = 1})
            LuaTable t = a.checktable(1);
            if (t.length() > 0) {
                return t.get(rand(g, t.length()));
            }
            double total = 0;
            for (LuaValue k = t.next(LuaValue.NIL).arg1(); !k.isnil(); k = t.next(k).arg1()) {
                total += t.get(k).checkdouble();
            }
            double roll = g.get("math").get("random").call().todouble() * total;
            LuaValue last = LuaValue.NIL;
            for (LuaValue k = t.next(LuaValue.NIL).arg1(); !k.isnil(); k = t.next(k).arg1()) {
                last = k;
                roll -= t.get(k).checkdouble();
                if (roll <= 0) {
                    return k;
                }
            }
            return last;
        }));
        g.set("AIR", LuaValue.valueOf("minecraft:air"));
        return g;
    }

    private static int rand(Globals g, int n) {
        return g.get("math").get("random").call(LuaValue.valueOf(n)).toint();
    }

    private void checkTime() {
        if (System.currentTimeMillis() > deadline) {
            throw new LuaError("script ran longer than " + (timeoutMs / 1000.0) + "s; aborting");
        }
        if (Thread.currentThread().isInterrupted()) {
            throw new LuaError("cancelled");
        }
    }

    private static String id(BlockState state) {
        return BuiltInRegistries.BLOCK.getKey(state.getBlock()).toString();
    }

    private static int i(Varargs a, int n) {
        return (int) Math.floor(a.checkdouble(n));
    }

    private static void applyOverrides(LuaTable params, Map<String, String> overrides) {
        for (Map.Entry<String, String> e : overrides.entrySet()) {
            LuaValue current = params.get(e.getKey());
            String raw = e.getValue();
            LuaValue value;
            if (current.isboolean()) {
                value = LuaValue.valueOf(raw.equalsIgnoreCase("true") || raw.equals("1") || raw.equalsIgnoreCase("yes"));
            } else if (current.isnumber()) {
                try {
                    value = LuaValue.valueOf(Double.parseDouble(raw));
                } catch (NumberFormatException ex) {
                    throw new LuaError("param '" + e.getKey() + "' wants a number, got '" + raw + "'");
                }
            } else {
                value = LuaValue.valueOf(raw);
            }
            params.set(e.getKey(), value);
        }
    }

    @FunctionalInterface
    private interface Body {
        Varargs apply(Varargs args);
    }

    private static VarArgFunction fn(Body body) {
        return new VarArgFunction() {
            @Override
            public Varargs invoke(Varargs args) {
                return body.apply(args);
            }
        };
    }
}
