package net.honormc.backstage.client.editor;

import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.argument;
import static net.fabricmc.fabric.api.client.command.v2.ClientCommands.literal;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.TreeSet;
import net.fabricmc.fabric.api.client.command.v2.FabricClientCommandSource;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Prop;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;

public final class EditorCommands {
    public static final int LIST_LIMIT = 100;

    private static final List<String> TOOLS = List.of("select", "point", "group", "region", "off");
    private static final List<String> TYPES = List.of("point", "group", "region");
    private static final List<String> PLURALS = List.of("points", "groups", "regions", "all");

    private final EditorClient editor;
    private final EditorTools tools;
    private final MarkerRenderer renderer;

    public EditorCommands(EditorClient editor, EditorTools tools, MarkerRenderer renderer) {
        this.editor = editor;
        this.tools = tools;
        this.renderer = renderer;
    }

    public void attach(LiteralArgumentBuilder<FabricClientCommandSource> bs) {
        bs.then(literal("tool").then(argument("tool", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(TOOLS, b))
                .executes(c -> {
                    String name = StringArgumentType.getString(c, "tool");
                    if (!TOOLS.contains(name)) {
                        EditorTools.error("Unknown tool '" + name + "'. Use one of " + String.join(", ", TOOLS) + ".");
                        return 0;
                    }
                    tools.tool(EditorTools.Tool.valueOf(name.toUpperCase(Locale.ROOT)));
                    return 1;
                })));

        bs.then(literal("group")
                .then(literal("new").then(argument("id", StringArgumentType.word())
                        .executes(c -> tools.groupNew(StringArgumentType.getString(c, "id")) ? 1 : 0)))
                .then(literal("edit").then(argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(ids(MarkerType.GROUP), b))
                        .executes(c -> tools.groupEdit(StringArgumentType.getString(c, "id")) ? 1 : 0)))
                .then(literal("box").executes(c -> {
                    tools.groupMode(true);
                    return 1;
                }))
                .then(literal("fill").executes(c -> {
                    tools.groupMode(false);
                    return 1;
                }))
                .then(literal("done").executes(c -> {
                    tools.groupDone();
                    return 1;
                })));

        bs.then(literal("region")
                .then(literal("box")
                        .executes(c -> {
                            tools.regionBox(null);
                            return 1;
                        })
                        .then(argument("id", StringArgumentType.word()).executes(c -> {
                            tools.regionBox(StringArgumentType.getString(c, "id"));
                            return 1;
                        })))
                .then(literal("poly")
                        .executes(c -> {
                            tools.regionPoly(null);
                            return 1;
                        })
                        .then(argument("id", StringArgumentType.word()).executes(c -> {
                            tools.regionPoly(StringArgumentType.getString(c, "id"));
                            return 1;
                        })))
                .then(literal("done").executes(c -> {
                    tools.regionDone();
                    return 1;
                }))
                .then(literal("height").then(argument("minY", IntegerArgumentType.integer(-2048, 2047))
                        .then(argument("maxY", IntegerArgumentType.integer(-2048, 2047)).executes(c -> {
                            tools.regionHeight(IntegerArgumentType.getInteger(c, "minY"),
                                    IntegerArgumentType.getInteger(c, "maxY"));
                            return 1;
                        })))));

        bs.then(literal("select").then(argument("type", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(TYPES, b))
                .then(argument("id", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(idsFor(c), b))
                        .executes(this::select))));

        bs.then(literal("rename").then(argument("newId", StringArgumentType.word())
                .executes(c -> tools.rename(StringArgumentType.getString(c, "newId")) ? 1 : 0)));

        bs.then(literal("delete").executes(c -> tools.delete() ? 1 : 0));

        bs.then(literal("tag")
                .then(literal("add").then(argument("tag", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(allTags(), b))
                        .executes(c -> tagAdd(StringArgumentType.getString(c, "tag")))))
                .then(literal("remove").then(argument("tag", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(selectedTags(), b))
                        .executes(c -> tagRemove(StringArgumentType.getString(c, "tag"))))));

        bs.then(literal("prop")
                .then(literal("set").then(argument("key", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(allKeys(), b))
                        .then(argument("type", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(PropParser.TYPES, b))
                                .then(argument("value", StringArgumentType.greedyString())
                                        .executes(c -> propSet(StringArgumentType.getString(c, "key"),
                                                StringArgumentType.getString(c, "type"),
                                                StringArgumentType.getString(c, "value")))))))
                .then(literal("remove").then(argument("key", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(selectedKeys(), b))
                        .executes(c -> propRemove(StringArgumentType.getString(c, "key"))))));

        bs.then(literal("list").then(argument("type", StringArgumentType.word())
                .suggests((c, b) -> SharedSuggestionProvider.suggest(PLURALS, b))
                .executes(c -> list(StringArgumentType.getString(c, "type"), null))
                .then(argument("tag", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(allTags(), b))
                        .executes(c -> list(StringArgumentType.getString(c, "type"), StringArgumentType.getString(c, "tag"))))));

        bs.then(literal("editor")
                .then(literal("show").then(argument("overlay", StringArgumentType.word())
                        .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("points", "groups", "regions", "labels"), b))
                        .then(argument("state", StringArgumentType.word())
                                .suggests((c, b) -> SharedSuggestionProvider.suggest(List.of("on", "off"), b))
                                .executes(c -> show(StringArgumentType.getString(c, "overlay"),
                                        StringArgumentType.getString(c, "state"))))))
                .then(literal("radius").then(argument("blocks", IntegerArgumentType.integer(8, 512)).executes(c -> {
                    renderer.radius(IntegerArgumentType.getInteger(c, "blocks"));
                    EditorTools.info("§7Markers are drawn within §f" + renderer.radius() + "§7 blocks.");
                    return 1;
                })))
                .then(literal("undo").executes(c -> {
                    tools.undo(false);
                    return 1;
                }))
                .then(literal("redo").executes(c -> {
                    tools.undo(true);
                    return 1;
                })));
    }

    public int info() {
        Marker marker = tools.selectedMarker();
        if (marker == null) {
            EditorTools.error(tools.selected() == null ? "Nothing selected. Use the select tool or /bs list."
                    : "The selected marker no longer exists.");
            return 0;
        }
        MarkerText.details(marker).forEach(EditorTools::info);
        return 1;
    }

    private int select(CommandContext<FabricClientCommandSource> c) {
        MarkerType type = type(StringArgumentType.getString(c, "type"));
        String id = StringArgumentType.getString(c, "id");
        if (type == null) {
            EditorTools.error("Type must be point, group or region.");
            return 0;
        }
        Marker marker = editor.store().get(type, id);
        if (marker == null) {
            EditorTools.error("No " + MarkerText.type(type) + " named " + id + ".");
            return 0;
        }
        tools.select(new EditorTools.Selection(type, id), true);
        EditorTools.info("§7Selected " + MarkerText.summary(marker) + "§7, highlighted for 10 s.");
        return 1;
    }

    private int tagAdd(String tag) {
        Marker marker = tools.requireSelected();
        if (marker == null) {
            return 0;
        }
        String problem = MarkerCheck.tag(tag);
        if (problem != null) {
            EditorTools.error(problem);
            return 0;
        }
        return tools.change(marker, MarkerEdits.addTag(marker, tag), "§7Tagged §f" + marker.id() + "§7 with §f" + tag + "§7.") ? 1 : 0;
    }

    private int tagRemove(String tag) {
        Marker marker = tools.requireSelected();
        if (marker == null) {
            return 0;
        }
        if (!marker.tags().contains(tag)) {
            EditorTools.error(marker.id() + " has no tag " + tag + ".");
            return 0;
        }
        return tools.change(marker, MarkerEdits.removeTag(marker, tag), "§7Removed tag §f" + tag + "§7 from §f" + marker.id() + "§7.") ? 1 : 0;
    }

    private int propSet(String key, String type, String value) {
        Marker marker = tools.requireSelected();
        if (marker == null) {
            return 0;
        }
        String problem = MarkerCheck.id(key);
        if (problem != null) {
            EditorTools.error("Prop key " + problem);
            return 0;
        }
        Prop prop;
        try {
            prop = PropParser.parse(type, value);
        } catch (IllegalArgumentException e) {
            EditorTools.error(e.getMessage());
            return 0;
        }
        return tools.change(marker, MarkerEdits.setProp(marker, key, prop), "§7" + marker.id() + "§7: §f" + key
                + "§7 = §f" + PropParser.describe(prop)) ? 1 : 0;
    }

    private int propRemove(String key) {
        Marker marker = tools.requireSelected();
        if (marker == null) {
            return 0;
        }
        if (!marker.props().containsKey(key)) {
            EditorTools.error(marker.id() + " has no prop " + key + ".");
            return 0;
        }
        return tools.change(marker, MarkerEdits.removeProp(marker, key), "§7Removed prop §f" + key + "§7 from §f" + marker.id() + "§7.") ? 1 : 0;
    }

    private int list(String plural, String tag) {
        if (!(editor.state() instanceof EditorState.Available available) || !available.bound()) {
            EditorTools.error(editor.state() instanceof EditorState.Available ? "No editable map here."
                    : "The editor is not available on this server.");
            return 0;
        }
        MarkerType type = Outliner.type(plural);
        if (type == null && !plural.equals("all")) {
            EditorTools.error("Use /bs list <points|groups|regions|all> [tag]. /bs list alone lists Lua scripts.");
            return 0;
        }
        List<Marker> markers = Outliner.filter(editor.store().all(), type, tag);
        EditorTools.info("§7" + markers.size() + " " + (type == null ? "markers" : plural) + (tag == null ? "" : " tagged §f" + tag + "§7")
                + " on §f" + available.map() + "§7:");
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) {
            return 1;
        }
        for (Marker marker : markers.subList(0, Math.min(LIST_LIMIT, markers.size()))) {
            mc.player.sendSystemMessage(line(marker));
        }
        if (markers.size() > LIST_LIMIT) {
            EditorTools.info("§7... and " + (markers.size() - LIST_LIMIT) + " more. Add a tag to narrow the list.");
        }
        return 1;
    }

    private static Component line(Marker marker) {
        String type = MarkerText.type(marker.type());
        MutableComponent line = Component.literal("  ").append(Component.literal("[" + type + " " + marker.id() + "]")
                .withStyle(style -> style.withColor(ChatFormatting.AQUA)
                        .withClickEvent(new ClickEvent.RunCommand("/bs select " + type + " " + marker.id()))
                        .withHoverEvent(new HoverEvent.ShowText(Component.literal("Select and highlight for 10 s")))));
        if (!marker.tags().isEmpty()) {
            line.append(Component.literal(" " + String.join(", ", marker.tags())).withStyle(ChatFormatting.GRAY));
        }
        double[] at = MarkerText.anchor(marker);
        if (at != null) {
            String tp = "/tp " + MarkerText.xyz(at[0], at[1], at[2]);
            line.append(Component.literal(" "))
                    .append(Component.literal("[tp]").withStyle(style -> style.withColor(ChatFormatting.GREEN)
                            .withClickEvent(new ClickEvent.SuggestCommand(tp))
                            .withHoverEvent(new HoverEvent.ShowText(Component.literal(tp)))));
        }
        return line;
    }

    private int show(String overlay, String state) {
        MarkerRenderer.Overlay which = switch (overlay) {
            case "points" -> MarkerRenderer.Overlay.POINTS;
            case "groups" -> MarkerRenderer.Overlay.GROUPS;
            case "regions" -> MarkerRenderer.Overlay.REGIONS;
            case "labels" -> MarkerRenderer.Overlay.LABELS;
            default -> null;
        };
        if (which == null || !state.equals("on") && !state.equals("off")) {
            EditorTools.error("Use /bs editor show <points|groups|regions|labels> <on|off>.");
            return 0;
        }
        renderer.show(which, state.equals("on"));
        EditorTools.info("§7" + overlay + " overlay §f" + state + "§7.");
        return 1;
    }

    private static MarkerType type(String name) {
        return switch (name) {
            case "point" -> MarkerType.POINT;
            case "group" -> MarkerType.GROUP;
            case "region" -> MarkerType.REGION;
            default -> null;
        };
    }

    private Set<String> ids(MarkerType type) {
        return new TreeSet<>(editor.store().markers(type).keySet());
    }

    private Set<String> idsFor(CommandContext<FabricClientCommandSource> c) {
        MarkerType type = type(StringArgumentType.getString(c, "type"));
        return type == null ? Set.of() : ids(type);
    }

    private Set<String> allTags() {
        Set<String> tags = new TreeSet<>();
        for (Marker marker : editor.store().all()) {
            tags.addAll(marker.tags());
        }
        return tags;
    }

    private Set<String> allKeys() {
        Set<String> keys = new TreeSet<>();
        for (Marker marker : editor.store().all()) {
            keys.addAll(marker.props().keySet());
        }
        return keys;
    }

    private Set<String> selectedTags() {
        Marker marker = tools.peekSelected();
        return marker == null ? Set.of() : new TreeSet<>(marker.tags());
    }

    private Set<String> selectedKeys() {
        Marker marker = tools.peekSelected();
        return marker == null ? Set.of() : new TreeSet<>(marker.props().keySet());
    }
}
