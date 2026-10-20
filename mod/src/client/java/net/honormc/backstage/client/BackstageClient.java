package net.honormc.backstage.client;

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
import net.honormc.backstage.client.editor.EditorClient;
import net.honormc.backstage.client.editor.EditorNetworking;
import net.honormc.backstage.client.lua.ScriptRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public final class BackstageClient implements ClientModInitializer {
    private static final double REACH = 256;

    private BrushSession session;
    private EditorClient editor;

    @Override
    public void onInitializeClient() {
        ScriptStore store = new ScriptStore();
        store.init();
        session = new BrushSession(store);
        editor = EditorNetworking.install();

        ClientCommandRegistrationCallback.EVENT.register((dispatcher, ctx) -> registerCommands(dispatcher));

        UseBlockCallback.EVENT.register((player, level, hand, hit) -> {
            if (!level.isClientSide() || !holdingWand(player, hand)) {
                return InteractionResult.PASS;
            }
            useWand(player);
            return InteractionResult.FAIL;
        });
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

    private void registerCommands(CommandDispatcher<FabricClientCommandSource> dispatcher) {
        var root = dispatcher.register(literal("bs")
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
                }))
                .then(literal("editor")
                        .executes(c -> {
                            editor.describe().forEach(BrushSession::info);
                            return 1;
                        })
                        .then(literal("resync").executes(c -> {
                            if (!editor.resync()) {
                                BrushSession.error("The editor is not available on this server.");
                                return 0;
                            }
                            BrushSession.info("§7Asked the server for a fresh copy of this map's markers.");
                            return 1;
                        }))));
        dispatcher.register(literal("hb").executes(root.getCommand()).redirect(root));
    }

    private int help() {
        BrushSession.info("""
                §6Backstage§7 — Lua build scripts. Wand: §f%s§7 (use = run, sneak+use = preview)
                §f/bs list§7, §f/bs use <script>§7, §f/bs info§7, §f/bs set <param> <value>§7, §f/bs unset <param>
                §f/bs run§7 / §f/bs preview§7 at your crosshair, §f/bs apply§7, §f/bs clear§7, §f/bs undo§7, §f/bs cancel
                §f/bs radius <n>§7 (paint scripts), §f/bs rotate <true|false>§7, §f/bs seed <n|random>§7, §f/bs wand§7, §f/bs dir
                §f/bs editor§7 (server editor status), §f/bs editor resync"""
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
            BrushSession.error("No script '" + name + "'. /bs list shows what's there.");
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
