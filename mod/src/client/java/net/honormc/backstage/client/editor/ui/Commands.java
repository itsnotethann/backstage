package net.honormc.backstage.client.editor.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.function.BooleanSupplier;
import net.honormc.backstage.client.editor.EditorLayout;
import net.honormc.backstage.client.editor.EditorMode;
import net.honormc.backstage.client.editor.EditorShortcuts.Action;
import net.honormc.backstage.client.editor.EditorTools;

final class Commands {
    record Command(String menu, String title, String icon, List<String> caps, BooleanSupplier enabled,
                   BooleanSupplier checked, Runnable run) {
        boolean on() {
            return enabled.getAsBoolean();
        }

        boolean ticked() {
            return checked != null && checked.getAsBoolean();
        }
    }

    private Commands() {
    }

    static List<Command> all(EditorUi ui) {
        EditorMode mode = ui.mode;
        EditorTools tools = ui.tools;
        EditorLayout layout = mode.layout();
        BooleanSupplier always = () -> true;
        BooleanSupplier selection = () -> !tools.selection().isEmpty();
        List<Command> commands = new ArrayList<>();
        commands.add(new Command("File", "Resync markers", Icons.REFRESH, List.of(), always, null, () -> {
            if (mode.editor().resync()) {
                mode.toasts().push(Toasts.Kind.OK, "Resync requested", "Fetching a fresh copy of this map.");
            }
        }));
        commands.add(new Command("File", "Leave editor", Icons.LOGOUT, ui.caps(Action.LEAVE), always, null, mode::leave));
        commands.add(new Command("Edit", "Undo", Icons.UNDO, ui.caps(Action.UNDO), always, null, () -> tools.undo(false)));
        commands.add(new Command("Edit", "Redo", Icons.REDO, ui.caps(Action.REDO), always, null, () -> tools.undo(true)));
        commands.add(new Command("Edit", "Delete", Icons.TRASH, ui.caps(Action.DELETE), selection, null, tools::delete));
        commands.add(new Command("Edit", "Duplicate", Icons.COPY, ui.caps(Action.DUPLICATE), selection, null, tools::duplicate));
        commands.add(new Command("Edit", "Select none", Icons.X, ui.caps(Action.SELECT_NONE), selection, null, tools::selectNone));
        commands.add(new Command("Edit", "Fly to selection", Icons.FOCUS, ui.caps(Action.FOCUS), selection, null,
                mode::focusSelection));
        commands.add(new Command("View", "Points", Icons.MAP_PIN, List.of(), always, () -> layout.points,
                () -> layout.points = !layout.points));
        commands.add(new Command("View", "Groups", Icons.BRUSH, List.of(), always, () -> layout.groups,
                () -> layout.groups = !layout.groups));
        commands.add(new Command("View", "Regions", Icons.POLYGON, List.of(), always, () -> layout.regions,
                () -> layout.regions = !layout.regions));
        commands.add(new Command("View", "Labels", Icons.INFO, List.of(), always, () -> layout.labels,
                () -> layout.labels = !layout.labels));
        commands.add(new Command("View", "History", Icons.HISTORY, ui.caps(Action.HISTORY), always, () -> layout.historyOpen,
                () -> layout.historyOpen = !layout.historyOpen));
        commands.add(new Command("View", "Tool options", Icons.OPTIONS, List.of(), always, () -> layout.optionsOpen,
                () -> layout.optionsOpen = !layout.optionsOpen));
        commands.add(new Command("View", "Reset layout", Icons.SIDEBAR, List.of(), always, null, () -> {
            EditorLayout fresh = new EditorLayout();
            layout.dockWidth = fresh.dockWidth;
            layout.outlinerShare = fresh.outlinerShare;
            layout.optionsOpen = fresh.optionsOpen;
            layout.historyOpen = fresh.historyOpen;
            layout.points = layout.groups = layout.regions = layout.labels = true;
            layout.renderRadius = fresh.renderRadius;
            mode.saveLayout();
        }));
        commands.add(new Command("Help", "Keyboard shortcuts", Icons.KEYBOARD, List.of(), always, null,
                () -> ui.side.shortcutsOpen = true));
        commands.add(new Command("Help", "Command palette", Icons.COMMAND, ui.caps(Action.PALETTE), always, null,
                ui.palette::show));
        return commands;
    }

    static List<Command> tools(EditorUi ui) {
        List<Command> commands = new ArrayList<>();
        for (ToolRail.Slot slot : ToolRail.SLOTS) {
            commands.add(new Command("Tool", slot.tool().title() + " tool", slot.icon(), ui.caps(slot.action()), () -> true,
                    () -> ui.tools.tool() == slot.tool(), () -> ui.tools.tool(slot.tool())));
        }
        return commands;
    }
}
