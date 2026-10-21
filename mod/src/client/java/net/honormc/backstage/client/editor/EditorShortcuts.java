package net.honormc.backstage.client.editor;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import org.lwjgl.glfw.GLFW;

public final class EditorShortcuts {
    public enum Action {
        TOOL_SELECT("Select tool"),
        TOOL_POINT("Point tool"),
        TOOL_GROUP("Group brush"),
        TOOL_BOX("Box tool"),
        TOOL_REGION("Region polygon"),
        TOOL_MOVE("Move tool"),
        TOOL_BRUSH("Lua brush"),
        UNDO("Undo"),
        REDO("Redo"),
        REDO_ALT("Redo"),
        DELETE("Delete selection"),
        DUPLICATE("Duplicate selection"),
        SELECT_NONE("Select none"),
        FOCUS("Fly to selection"),
        SEARCH("Search the outliner"),
        PALETTE("Command palette"),
        CONFIRM("Confirm box or polygon"),
        SNAP_YAW("Snap point yaw to 45°"),
        RADIUS_DOWN("Brush radius down"),
        RADIUS_UP("Brush radius up"),
        HISTORY("History panel"),
        LEAVE("Leave editor");

        private final String label;

        Action(String label) {
            this.label = label;
        }

        public String label() {
            return label;
        }
    }

    public record Key(int key, boolean ctrl, boolean shift, boolean alt) {
        public static Key of(int key) {
            return new Key(key, false, false, false);
        }

        public static Key ctrl(int key) {
            return new Key(key, true, false, false);
        }

        public static Key ctrlShift(int key) {
            return new Key(key, true, true, false);
        }

        public boolean bare() {
            return !ctrl && !shift && !alt;
        }

        public boolean matches(int key, int mods) {
            return this.key == key && ctrl == ((mods & GLFW.GLFW_MOD_CONTROL) != 0)
                    && shift == ((mods & GLFW.GLFW_MOD_SHIFT) != 0) && alt == ((mods & GLFW.GLFW_MOD_ALT) != 0);
        }

        public List<String> caps() {
            List<String> caps = new ArrayList<>();
            if (ctrl) {
                caps.add("Ctrl");
            }
            if (shift) {
                caps.add("Shift");
            }
            if (alt) {
                caps.add("Alt");
            }
            caps.add(name(key));
            return caps;
        }

        public String label() {
            return String.join("+", caps());
        }
    }

    public record Binding(Action action, Key key) {
    }

    public record Conflict(Action action, Key key, String with) {
    }

    public static final List<Binding> DEFAULTS = List.of(
            new Binding(Action.TOOL_SELECT, Key.of(GLFW.GLFW_KEY_1)),
            new Binding(Action.TOOL_POINT, Key.of(GLFW.GLFW_KEY_2)),
            new Binding(Action.TOOL_GROUP, Key.of(GLFW.GLFW_KEY_3)),
            new Binding(Action.TOOL_BOX, Key.of(GLFW.GLFW_KEY_4)),
            new Binding(Action.TOOL_REGION, Key.of(GLFW.GLFW_KEY_5)),
            new Binding(Action.TOOL_MOVE, Key.of(GLFW.GLFW_KEY_6)),
            new Binding(Action.TOOL_BRUSH, Key.of(GLFW.GLFW_KEY_7)),
            new Binding(Action.UNDO, Key.ctrl(GLFW.GLFW_KEY_Z)),
            new Binding(Action.REDO, Key.ctrl(GLFW.GLFW_KEY_Y)),
            new Binding(Action.REDO_ALT, Key.ctrlShift(GLFW.GLFW_KEY_Z)),
            new Binding(Action.DELETE, Key.of(GLFW.GLFW_KEY_DELETE)),
            new Binding(Action.DUPLICATE, Key.ctrl(GLFW.GLFW_KEY_D)),
            new Binding(Action.SELECT_NONE, Key.ctrlShift(GLFW.GLFW_KEY_A)),
            new Binding(Action.FOCUS, Key.of(GLFW.GLFW_KEY_F)),
            new Binding(Action.SEARCH, Key.ctrl(GLFW.GLFW_KEY_F)),
            new Binding(Action.PALETTE, Key.ctrl(GLFW.GLFW_KEY_K)),
            new Binding(Action.CONFIRM, Key.of(GLFW.GLFW_KEY_ENTER)),
            new Binding(Action.SNAP_YAW, Key.of(GLFW.GLFW_KEY_R)),
            new Binding(Action.RADIUS_DOWN, Key.of(GLFW.GLFW_KEY_LEFT_BRACKET)),
            new Binding(Action.RADIUS_UP, Key.of(GLFW.GLFW_KEY_RIGHT_BRACKET)),
            new Binding(Action.HISTORY, Key.ctrl(GLFW.GLFW_KEY_H)),
            new Binding(Action.LEAVE, Key.of(GLFW.GLFW_KEY_ESCAPE)));

    private final List<Binding> bindings;

    public EditorShortcuts(List<Binding> bindings) {
        this.bindings = List.copyOf(bindings);
    }

    public static EditorShortcuts defaults() {
        return new EditorShortcuts(DEFAULTS);
    }

    public List<Binding> bindings() {
        return bindings;
    }

    public Key key(Action action) {
        for (Binding binding : bindings) {
            if (binding.action() == action) {
                return binding.key();
            }
        }
        return null;
    }

    public Action resolve(int key, int mods, int toggleKey, Collection<Integer> movementKeys) {
        if (key == toggleKey && (mods & (GLFW.GLFW_MOD_CONTROL | GLFW.GLFW_MOD_ALT)) == 0) {
            return Action.LEAVE;
        }
        for (Binding binding : bindings) {
            if (!binding.key().matches(key, mods)) {
                continue;
            }
            if (binding.key().bare() && binding.action() != Action.LEAVE && movementKeys.contains(key)) {
                return null;
            }
            return binding.action();
        }
        return null;
    }

    public List<Conflict> conflicts(int toggleKey, String toggleName, Collection<Integer> movementKeys) {
        List<Conflict> conflicts = new ArrayList<>();
        for (Binding binding : bindings) {
            Key key = binding.key();
            if (binding.action() == Action.LEAVE || key.ctrl() || key.alt()) {
                continue;
            }
            if (key.key() == toggleKey) {
                conflicts.add(new Conflict(binding.action(), key, toggleName));
            } else if (key.bare() && movementKeys.contains(key.key())) {
                conflicts.add(new Conflict(binding.action(), key, "a movement key"));
            }
        }
        return conflicts;
    }

    public static String name(int key) {
        return switch (key) {
            case GLFW.GLFW_KEY_ESCAPE -> "Esc";
            case GLFW.GLFW_KEY_ENTER -> "Enter";
            case GLFW.GLFW_KEY_DELETE -> "Del";
            case GLFW.GLFW_KEY_BACKSPACE -> "Backspace";
            case GLFW.GLFW_KEY_TAB -> "Tab";
            case GLFW.GLFW_KEY_SPACE -> "Space";
            case GLFW.GLFW_KEY_LEFT_BRACKET -> "[";
            case GLFW.GLFW_KEY_RIGHT_BRACKET -> "]";
            case GLFW.GLFW_KEY_UP -> "Up";
            case GLFW.GLFW_KEY_DOWN -> "Down";
            case GLFW.GLFW_KEY_LEFT -> "Left";
            case GLFW.GLFW_KEY_RIGHT -> "Right";
            default -> {
                if (key >= GLFW.GLFW_KEY_F1 && key <= GLFW.GLFW_KEY_F25) {
                    yield "F" + (key - GLFW.GLFW_KEY_F1 + 1);
                }
                if (key >= GLFW.GLFW_KEY_0 && key <= GLFW.GLFW_KEY_9 || key >= GLFW.GLFW_KEY_A && key <= GLFW.GLFW_KEY_Z) {
                    yield String.valueOf((char) key);
                }
                yield "#" + key;
            }
        };
    }
}
