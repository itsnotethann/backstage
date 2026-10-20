package net.honormc.backstage.client.editor;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Pattern;
import net.honormc.backstage.protocol.EditorProtocol;
import net.honormc.backstage.protocol.model.Prop;

public final class PropParser {
    public static final List<String> TYPES = List.of("str", "num", "bool", "block", "item");

    private static final Pattern RESOURCE = Pattern.compile("(?:[a-z0-9_.-]+:)?[a-z0-9_./-]+");
    private static final Pattern BLOCK_STATE = Pattern.compile(
            "((?:[a-z0-9_.-]+:)?[a-z0-9_./-]+)(\\[[a-z0-9_]+=[a-z0-9_]+(?:,[a-z0-9_]+=[a-z0-9_]+)*])?");

    private PropParser() {
    }

    public static Prop parse(String type, String value) {
        Prop prop = switch (type) {
            case "str" -> new Prop.Str(value);
            case "num" -> number(value);
            case "bool" -> bool(value);
            case "block" -> block(value);
            case "item" -> item(value);
            default -> throw new IllegalArgumentException("Unknown prop type '" + type + "'. Use one of "
                    + String.join(", ", TYPES) + ".");
        };
        String text = switch (prop) {
            case Prop.Str p -> p.value();
            case Prop.BlockProp p -> p.state();
            case Prop.ItemProp p -> p.snbt();
            default -> "";
        };
        if (text.getBytes(StandardCharsets.UTF_8).length > EditorProtocol.MAX_STRING_BYTES) {
            throw new IllegalArgumentException("That value is longer than " + EditorProtocol.MAX_STRING_BYTES + " bytes.");
        }
        return prop;
    }

    public static String describe(Prop prop) {
        return switch (prop) {
            case Prop.Str p -> "str \"" + p.value() + "\"";
            case Prop.Num p -> "num " + (p.value() == Math.rint(p.value()) && Math.abs(p.value()) < 1e15
                    ? Long.toString((long) p.value()) : Double.toString(p.value()));
            case Prop.Bool p -> "bool " + p.value();
            case Prop.BlockProp p -> "block " + p.state();
            case Prop.ItemProp p -> "item " + p.snbt();
            case Prop.ListProp p -> "list of " + p.values().size();
        };
    }

    private static Prop number(String value) {
        double parsed;
        try {
            parsed = Double.parseDouble(value.trim());
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("'" + value + "' is not a number.");
        }
        if (!Double.isFinite(parsed)) {
            throw new IllegalArgumentException("'" + value + "' is not a finite number.");
        }
        return new Prop.Num(parsed);
    }

    private static Prop bool(String value) {
        return switch (value.trim().toLowerCase()) {
            case "true" -> new Prop.Bool(true);
            case "false" -> new Prop.Bool(false);
            default -> throw new IllegalArgumentException("'" + value + "' is not true or false.");
        };
    }

    private static Prop block(String value) {
        String state = value.trim();
        var matcher = BLOCK_STATE.matcher(state);
        if (!matcher.matches()) {
            throw new IllegalArgumentException("'" + value + "' is not a block state like minecraft:oak_stairs[facing=north].");
        }
        return new Prop.BlockProp(state.contains(":") && state.indexOf(':') < bracket(state) ? state : "minecraft:" + state);
    }

    private static Prop item(String value) {
        String text = value.trim();
        if (RESOURCE.matcher(text).matches()) {
            String id = text.contains(":") ? text : "minecraft:" + text;
            return new Prop.ItemProp("{id:\"" + id + "\",count:1}");
        }
        if (text.length() < 2 || text.charAt(0) != '{' || text.charAt(text.length() - 1) != '}' || !text.contains("id")) {
            throw new IllegalArgumentException("'" + value + "' is not an item id or item SNBT like {id:\"minecraft:diamond\",count:1}.");
        }
        return new Prop.ItemProp(text);
    }

    private static int bracket(String state) {
        int index = state.indexOf('[');
        return index < 0 ? state.length() : index;
    }
}
