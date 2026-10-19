package net.honormc.brush.client;

import it.unimi.dsi.fastutil.longs.Long2ObjectMap;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.ThreadLocalRandom;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.honormc.brush.HonorBrush;
import net.honormc.brush.client.lua.Edit;
import net.honormc.brush.client.lua.ScriptRuntime;
import net.honormc.brush.net.EditChunkPayload;
import net.honormc.brush.net.UndoPayload;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import org.luaj.vm2.LuaError;

/**
 * Everything the player has set up: the active script, its parameter
 * overrides, brush radius, rotation, seed and wand, plus the preview waiting
 * for {@code /hb apply}. Scripts run on one background thread; results come
 * back to the client thread before anything is shown or sent.
 */
public final class BrushSession {

    private static final long PREVIEW_MS = 120_000;

    private final ScriptStore store;
    private final ExecutorService worker = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "HonorBrush-Lua");
        t.setDaemon(true);
        return t;
    });

    private String script;
    private final Map<String, Map<String, String>> overrides = new HashMap<>();
    private int radius = 5;
    private boolean rotate = true;
    private Long fixedSeed;
    private Item wand = Items.BLAZE_ROD;

    private Future<?> running;
    private Edit preview;
    private long previewUntil;
    private int nextOp = 1;

    public BrushSession(ScriptStore store) {
        this.store = store;
    }

    // ---- running ----

    /**
     * Runs the active script at a clicked block; applies it, or keeps it as a preview.
     * Paint scripts centre on {@code hit}; build scripts start on the block in front of {@code face}.
     */
    public void run(BlockPos hit, Direction face, Direction facing, boolean previewOnly) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            return;
        }
        if (script == null) {
            error("No script selected. /hb list, then /hb use <name>.");
            return;
        }
        if (running != null && !running.isDone()) {
            error("A script is still running. /hb cancel to stop it.");
            return;
        }
        String source;
        try {
            source = store.read(script);
        } catch (Exception e) {
            error(e.getMessage());
            return;
        }

        long seed = fixedSeed != null ? fixedSeed : ThreadLocalRandom.current().nextLong();
        ScriptRuntime.Request request = new ScriptRuntime.Request(script, source, hit, hit.relative(face), facing, rotate, radius, seed,
                new LinkedHashMap<>(overrides.getOrDefault(script, Map.of())));
        ScriptRuntime runtime = new ScriptRuntime(mc.level, line -> mc.execute(() -> info("§7[" + script + "] §f" + line)));
        String name = script;

        info("§7Running §f" + name + "§7...");
        long started = System.nanoTime();
        running = worker.submit(() -> {
            Edit edit;
            try {
                edit = runtime.run(request);
            } catch (LuaError e) {
                mc.execute(() -> error(name + ": " + firstLine(e.getMessage())));
                return;
            } catch (Throwable t) {
                HonorBrush.LOG.error("HonorBrush script {} crashed", name, t);
                mc.execute(() -> error(name + " crashed: " + t));
                return;
            }
            long ms = (System.nanoTime() - started) / 1_000_000;
            mc.execute(() -> finished(name, edit, ms, previewOnly));
        });
    }

    private void finished(String name, Edit edit, long ms, boolean previewOnly) {
        if (edit.isEmpty()) {
            info("§e" + name + " produced no blocks (" + ms + " ms).");
            return;
        }
        if (previewOnly) {
            preview = edit;
            previewUntil = System.currentTimeMillis() + PREVIEW_MS;
            BlockPos a = edit.min(), b = edit.max();
            info("§bPreview: §f" + edit.size() + "§b blocks, " + (b.getX() - a.getX() + 1) + "×" + (b.getY() - a.getY() + 1)
                    + "×" + (b.getZ() - a.getZ() + 1) + " (" + ms + " ms). §f/hb apply§b to place, §f/hb clear§b to drop.");
            return;
        }
        preview = null;
        send(edit, ms);
    }

    public void applyPreview() {
        if (preview == null) {
            error("No preview to apply. Sneak + use the wand (or /hb preview) first.");
            return;
        }
        Edit edit = preview;
        preview = null;
        send(edit, 0);
    }

    private void send(Edit edit, long ms) {
        if (!ClientPlayNetworking.canSend(EditChunkPayload.TYPE)) {
            error("This server doesn't have HonorBrush, so it can't place edits. It works in singleplayer, "
                    + "or on a server running the mod.");
            return;
        }
        int op = nextOp++;
        int total = edit.size();
        long[] positions = new long[total];
        int[] states = new int[total];
        int n = 0;
        for (Long2ObjectMap.Entry<BlockState> e : edit.changes().long2ObjectEntrySet()) {
            positions[n] = e.getLongKey();
            states[n] = Block.getId(e.getValue());
            n++;
        }
        for (int from = 0; from < total; from += EditChunkPayload.MAX_ENTRIES) {
            int to = Math.min(total, from + EditChunkPayload.MAX_ENTRIES);
            ClientPlayNetworking.send(new EditChunkPayload(op,
                    Arrays.copyOfRange(positions, from, to), Arrays.copyOfRange(states, from, to), to == total));
        }
        if (ms > 0) {
            info("§7Sent " + total + " blocks (script took " + ms + " ms).");
        }
    }

    public void undo() {
        if (!ClientPlayNetworking.canSend(UndoPayload.TYPE)) {
            error("This server doesn't have HonorBrush.");
            return;
        }
        ClientPlayNetworking.send(UndoPayload.INSTANCE);
    }

    public boolean cancel() {
        if (running != null && !running.isDone()) {
            running.cancel(true);
            return true;
        }
        return false;
    }

    // ---- preview ----

    /** The edit waiting for {@code /hb apply}, or null once it is applied, cleared or expired. */
    public Edit preview() {
        if (preview != null && System.currentTimeMillis() > previewUntil) {
            preview = null;
        }
        return preview;
    }

    public void clearPreview() {
        preview = null;
    }

    // ---- settings ----

    public String script() {
        return script;
    }

    public void script(String script) {
        this.script = script;
    }

    public Map<String, String> overrides() {
        return script == null ? Map.of() : overrides.getOrDefault(script, Map.of());
    }

    public void setParam(String key, String value) {
        overrides.computeIfAbsent(script, k -> new LinkedHashMap<>()).put(key, value);
    }

    public boolean unsetParam(String key) {
        Map<String, String> map = overrides.get(script);
        return map != null && map.remove(key) != null;
    }

    public int radius() {
        return radius;
    }

    public void radius(int radius) {
        this.radius = radius;
    }

    public boolean rotate() {
        return rotate;
    }

    public void rotate(boolean rotate) {
        this.rotate = rotate;
    }

    public Long seed() {
        return fixedSeed;
    }

    public void seed(Long seed) {
        this.fixedSeed = seed;
    }

    public Item wand() {
        return wand;
    }

    public void wand(Item wand) {
        this.wand = wand;
    }

    public String wandName() {
        return BuiltInRegistries.ITEM.getKey(wand).toString();
    }

    public ScriptStore store() {
        return store;
    }

    // ---- chat ----

    static void info(String text) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player != null) {
            mc.player.sendSystemMessage(Component.literal(text));
        }
    }

    static void error(String text) {
        info("§c" + text);
    }

    private static String firstLine(String message) {
        if (message == null) {
            return "error";
        }
        int nl = message.indexOf('\n');
        return nl < 0 ? message : message.substring(0, nl);
    }
}
