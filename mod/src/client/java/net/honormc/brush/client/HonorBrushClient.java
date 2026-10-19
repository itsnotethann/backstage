package net.honormc.brush.client;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.LongArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.context.CommandContext;
import java.util.Map;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.command.v2.ClientCommandRegistrationCallback;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.event.player.UseBlockCallback;
import net.fabricmc.fabric.api.event.player.UseItemCallback;
import net.honormc.brush.client.lua.ScriptRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Client entrypoint: the {@code /hb} commands, the wand, and the preview.
 *
 * <p>Use the wand on a block to run the active script there; sneak + use to
 * preview instead. The wand reaches 256 blocks, so it also works on blocks
 * well past normal reach.
 */
public final class HonorBrushClient implements ClientModInitializer {

    private static final double REACH = 256;

    private BrushSession session;

    @Override
    public void onInitializeClient() {
        ScriptStore store = new ScriptStore();
        store.init();
        session = new BrushSession(store);

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> registerCommands(dispatcher));

        // Right-click on a block in reach. FAIL (not SUCCESS) keeps the use packet from reaching the server, so chests and doors stay shut.
        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || !holdingWand(player, hand)) {
                return InteractionResult.PASS;
            }
            useWand(player);
            return InteractionResult.FAIL;
        });
        // Right-click at air (or a block past reach): raycast far and use that.
        UseItemCallback.EVENT.register((player, level, hand) -> {
            if (!level.isClientSide() || !holdingWand(player, hand)) {
                return InteractionResult.PASS;
            }
            useWand(player);
            return InteractionResult.FAIL;
        });

        ClientTickEvents.END_CLIENT_TICK.register(mc -> PreviewRenderer.tick(mc, session));
    }

    private boolean holdingWand(Player player, InteractionHand hand) {
        return hand == InteractionHand.MAIN_HAND && player.getMainHandItem().is(session.wand());
    }

    private void useWand(Player player) {
        runAtCrosshair(player.isShiftKeyDown());
    }

    private void runAtCrosshair(boolean previewOnly) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return;
        }
        HitResult hit = mc.player.pick(REACH, 1.0f, false);
        if (!(hit instanceof BlockHitResult block) || hit.getType() != HitResult.Type.BLOCK) {
            BrushSession.error("Not looking at a block (within " + (int) REACH + ").");
            return;
        }
        session.run(block.getBlockPos(), block.getDirection(), mc.player.getDirection(), previewOnly);
    }

    // ---- commands ----

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        dispatcher.register(literal("hb")
                .executes(c -> help())
                .then(literal("help").executes(c -> help()))
                .then(literal("list").executes(c -> list()))
                .then(literal("dir").executes(c -> {
                    BrushSession.info("§7Scripts folder: §f" + session.store().dir().toAbsolutePath());
                    return 1;
                }))
                .then(literal("use").then(argument("script", StringArgumentType.greedyString())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(session.store().list(), b))
                        .executes(c -> use(StringArgumentType.getString(c, "script")))))
                .then(literal("info").executes(c -> info()))
                .then(literal("set").then(argument("param", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(declaredParams().keySet(), b))
                        .then(argument("value", StringArgumentType.greedyString()).executes(this::set))))
                .then(literal("unset").then(argument("param", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(session.overrides().keySet(), b))
                        .executes(c -> {
                            String key = StringArgumentType.getString(c, "param");
                            BrushSession.info(session.unsetParam(key) ? "§7Reset §f" + key + "§7 to the script default."
                                    : "§e" + key + " wasn't overridden.");
                            return 1;
                        })))
                .then(literal("radius").then(argument("blocks", IntegerArgumentType.integer(0, 128)).executes(c -> {
                    session.radius(IntegerArgumentType.getInteger(c, "blocks"));
                    BrushSession.info("§7Paint radius: §f" + session.radius());
                    return 1;
                })))
                .then(literal("rotate").then(argument("on", BoolArgumentType.bool()).executes(c -> {
                    session.rotate(BoolArgumentType.getBool(c, "on"));
                    BrushSession.info(session.rotate() ? "§7Build scripts turn to face you."
                            : "§7Build scripts always face north.");
                    return 1;
                })))
                .then(literal("seed")
                        .then(literal("random").executes(c -> {
                            session.seed(null);
                            BrushSession.info("§7New random seed on every run.");
                            return 1;
                        }))
                        .then(argument("seed", LongArgumentType.longArg()).executes(c -> {
                            session.seed(LongArgumentType.getLong(c, "seed"));
                            BrushSession.info("§7Seed fixed at §f" + session.seed());
                            return 1;
                        })))
                .then(literal("wand").executes(c -> {
                    Player player = c.getSource().getPlayer();
                    if (player.getMainHandItem().isEmpty()) {
                        BrushSession.error("Hold the item you want as the wand.");
                        return 0;
                    }
                    session.wand(player.getMainHandItem().getItem());
                    BrushSession.info("§7Wand is now §f" + session.wandName());
                    return 1;
                }))
                .then(literal("run").executes(c -> {
                    runAtCrosshair(false);
                    return 1;
                }))
                .then(literal("preview").executes(c -> {
                    runAtCrosshair(true);
                    return 1;
                }))
                .then(literal("apply").executes(c -> {
                    session.applyPreview();
                    return 1;
                }))
                .then(literal("clear").executes(c -> {
                    session.clearPreview();
                    BrushSession.info("§7Preview cleared.");
                    return 1;
                }))
                .then(literal("undo").executes(c -> {
                    session.undo();
                    return 1;
                }))
                .then(literal("cancel").executes(c -> {
                    BrushSession.info(session.cancel() ? "§7Cancelling..." : "§7Nothing is running.");
                    return 1;
                })));
    }

    private int help() {
        BrushSession.info("""
                §6HonorBrush§7 — Lua build scripts. Wand: §f%s§7 (use = run, sneak+use = preview)
                §f/hb list§7, §f/hb use <script>§7, §f/hb info§7, §f/hb set <param> <value>§7, §f/hb unset <param>
                §f/hb run§7 / §f/hb preview§7 at your crosshair, §f/hb apply§7, §f/hb clear§7, §f/hb undo§7, §f/hb cancel
                §f/hb radius <n>§7 (paint scripts), §f/hb rotate <true|false>§7, §f/hb seed <n|random>§7, §f/hb wand§7, §f/hb dir"""
                .formatted(session.wandName()));
        return 1;
    }

    private int list() {
        var names = session.store().list();
        if (names.isEmpty()) {
            BrushSession.info("§eNo scripts yet. Put .lua files in §f" + session.store().dir().toAbsolutePath());
            return 0;
        }
        BrushSession.info("§7Scripts (" + names.size() + "): §f" + String.join("§7, §f", names));
        return 1;
    }

    private int use(String name) {
        if (!session.store().list().contains(name)) {
            BrushSession.error("No script '" + name + "'. /hb list shows what's there.");
            return 0;
        }
        session.script(name);
        BrushSession.info("§7Using §f" + name + "§7.");
        return info();
    }

    private int info() {
        if (session.script() == null) {
            BrushSession.error("No script selected.");
            return 0;
        }
        ScriptRuntime.Info info = describe();
        if (info == null) {
            return 0;
        }
        if (!info.description().isEmpty()) {
            BrushSession.info("§7" + info.description());
        }
        BrushSession.info("§7Mode: §f" + info.mode() + (info.mode().equals("paint") ? "§7, radius §f" + session.radius() : ""));
        Map<String, String> overrides = session.overrides();
        info.params().forEach((key, def) -> {
            String value = overrides.getOrDefault(key, def);
            String mark = overrides.containsKey(key) ? " §e(set)" : "";
            BrushSession.info("§7  " + key + " = §f" + value + mark);
        });
        return 1;
    }

    private int set(CommandContext<FabricClientCommandSource> c) {
        if (session.script() == null) {
            BrushSession.error("No script selected.");
            return 0;
        }
        String key = StringArgumentType.getString(c, "param");
        String value = StringArgumentType.getString(c, "value");
        if (!declaredParams().containsKey(key)) {
            BrushSession.info("§e" + session.script() + " doesn't declare '" + key + "' in params; setting it anyway.");
        }
        session.setParam(key, value);
        BrushSession.info("§7" + key + " = §f" + value);
        return 1;
    }

    private Map<String, String> declaredParams() {
        ScriptRuntime.Info info = session.script() == null ? null : describe();
        return info == null ? Map.of() : info.params();
    }

    private ScriptRuntime.Info describe() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return null;
        }
        try {
            String source = session.store().read(session.script());
            return new ScriptRuntime(mc.level, line -> {}).describe(session.script(), source);
        } catch (Exception e) {
            BrushSession.error(session.script() + ": " + e.getMessage());
            return null;
        }
    }
}
