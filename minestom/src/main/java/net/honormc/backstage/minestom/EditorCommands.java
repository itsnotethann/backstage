package net.honormc.backstage.minestom;

import java.io.IOException;
import java.util.Optional;
import net.kyori.adventure.nbt.TagStringIO;
import net.kyori.adventure.text.Component;
import net.minestom.server.command.CommandSender;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.entity.Player;

final class EditorCommands extends Command {
    static final int DUMP_LINE = 240;

    EditorCommands(EditorServer server) {
        super("edit");
        setCondition((sender, input) -> allowed(server, sender));
        setDefaultExecutor((sender, context) -> sender.sendMessage(Component.text("Usage: /edit <dump|save|reload|status>")));

        Command dump = new Command("dump");
        ArgumentWord map = ArgumentType.Word("map");
        dump.setDefaultExecutor((sender, context) -> sender.sendMessage(Component.text("Usage: /edit dump <map>")));
        dump.addSyntax((sender, context) -> dump(server, sender, context.get(map)), map);

        Command save = new Command("save");
        save.setDefaultExecutor((sender, context) -> {
            server.saveAll();
            sender.sendMessage(Component.text("Saved all maps."));
        });

        Command reload = new Command("reload");
        reload.setDefaultExecutor((sender, context) -> {
            try {
                sender.sendMessage(Component.text(server.reload()
                        ? "Reloaded editor config and re-checked online players."
                        : "This server was installed without a config file; nothing to reload."));
            } catch (RuntimeException e) {
                sender.sendMessage(Component.text("Reload failed: " + e.getMessage()));
            }
        });

        Command status = new Command("status");
        status.setDefaultExecutor((sender, context) -> server.status().forEach(line -> sender.sendMessage(Component.text(line))));

        for (Command sub : new Command[]{dump, save, reload, status}) {
            sub.setCondition((sender, input) -> allowed(server, sender));
            addSubcommand(sub);
        }
    }

    private static boolean allowed(EditorServer server, CommandSender sender) {
        return !(sender instanceof Player player) || server.permission().mayEdit(player);
    }

    private static void dump(EditorServer server, CommandSender sender, String map) {
        Optional<MapMarkers> markers = server.markers(map);
        if (markers.isEmpty()) {
            sender.sendMessage(Component.text("Map " + map + " is not bound."));
            return;
        }
        String snbt;
        try {
            snbt = TagStringIO.tagStringIO().asString(MarkerSidecar.write(markers.get().snapshot()));
        } catch (IOException e) {
            sender.sendMessage(Component.text("Dump failed: " + e.getMessage()));
            return;
        }
        for (int start = 0; start < snbt.length(); start += DUMP_LINE) {
            sender.sendMessage(Component.text(snbt.substring(start, Math.min(snbt.length(), start + DUMP_LINE))));
        }
    }
}
