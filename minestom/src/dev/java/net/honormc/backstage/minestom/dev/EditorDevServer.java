package net.honormc.backstage.minestom.dev;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import net.honormc.backstage.minestom.EditorConfig;
import net.honormc.backstage.minestom.EditorServer;
import net.honormc.backstage.minestom.MapMarkers;
import net.honormc.backstage.protocol.model.FieldDef;
import net.honormc.backstage.protocol.model.FieldType;
import net.honormc.backstage.protocol.model.KindDef;
import net.honormc.backstage.protocol.model.ListDef;
import net.honormc.backstage.protocol.model.ListEntry;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.kyori.adventure.text.Component;
import net.minestom.server.Auth;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.builder.Command;
import net.minestom.server.command.builder.arguments.ArgumentType;
import net.minestom.server.command.builder.arguments.ArgumentWord;
import net.minestom.server.coordinate.Pos;
import net.minestom.server.entity.GameMode;
import net.minestom.server.entity.Player;
import net.minestom.server.event.GlobalEventHandler;
import net.minestom.server.event.player.AsyncPlayerConfigurationEvent;
import net.minestom.server.instance.InstanceContainer;
import net.minestom.server.instance.LightingChunk;
import net.minestom.server.instance.block.Block;

public final class EditorDevServer {
    public static final int PORT = 25566;

    private EditorDevServer() {
    }

    public static void main(String[] args) {
        MinecraftServer server = MinecraftServer.init(new Auth.Offline());

        InstanceContainer instance = MinecraftServer.getInstanceManager().createInstanceContainer();
        instance.setChunkSupplier(LightingChunk::new);
        instance.setGenerator(unit -> unit.modifier().fillHeight(0, 40, Block.GRASS_BLOCK));

        GlobalEventHandler events = MinecraftServer.getGlobalEventHandler();
        events.addListener(AsyncPlayerConfigurationEvent.class, event -> {
            event.setSpawningInstance(instance);
            Player player = event.getPlayer();
            player.setRespawnPoint(new Pos(0, 40, 0));
            player.setGameMode(GameMode.CREATIVE);
        });

        EditorServer editor = EditorServer.install(events, EditorConfig.DEFAULT_PATH);
        editor.registry()
                .kind(new KindDef("world_boss", "World boss", MarkerType.POINT, List.of(
                        new FieldDef("spawn", "Spawn", FieldType.POINT_REF, true, ""),
                        new FieldDef("flag", "Flag", FieldType.GROUP_REF, true, ""),
                        new FieldDef("hp", "HP", FieldType.NUMBER, false, ""))))
                .list(new ListDef("effects", List.of(
                        new ListEntry("flame", "Flame"),
                        new ListEntry("smoke", "Smoke"))));
        editor.bind(instance, "dev", Path.of("dev.polar"));
        editor.registerCommands(MinecraftServer.getCommandManager());
        MinecraftServer.getCommandManager().register(devmark(editor));
        MinecraftServer.getCommandManager().getCommand("edit").addSubcommand(clear(editor));
        MinecraftServer.getSchedulerManager().buildShutdownTask(editor::shutdown);

        server.start("0.0.0.0", PORT);
    }

    private static Command clear(EditorServer editor) {
        Command command = new Command("clear");
        ArgumentWord map = ArgumentType.Word("map");
        command.setCondition((sender, input) -> !(sender instanceof Player player) || editor.permission().mayEdit(player));
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(Component.text("Usage: /edit clear <map>")));
        command.addSyntax((sender, context) -> {
            Optional<MapMarkers> found = editor.markers(context.get(map));
            if (found.isEmpty()) {
                sender.sendMessage(Component.text("Map " + context.get(map) + " is not bound."));
                return;
            }
            MapMarkers markers = found.get();
            List<Marker> all = new ArrayList<>();
            all.addAll(markers.points());
            all.addAll(markers.groups());
            all.addAll(markers.regions());
            try {
                for (Marker marker : all) {
                    markers.remove(marker.type(), marker.id());
                }
                sender.sendMessage(Component.text("Removed " + all.size() + " markers from " + markers.map() + "."));
            } catch (IllegalStateException e) {
                sender.sendMessage(Component.text(e.getMessage()));
            }
        }, map);
        return command;
    }

    private static Command devmark(EditorServer editor) {
        Command command = new Command("devmark");
        ArgumentWord id = ArgumentType.Word("id");
        command.setCondition((sender, input) -> sender instanceof Player player && editor.permission().mayEdit(player));
        command.setDefaultExecutor((sender, context) -> sender.sendMessage(Component.text("Usage: /devmark <id>")));
        command.addSyntax((sender, context) -> {
            Player player = (Player) sender;
            MapMarkers markers = editor.markers("dev").orElseThrow();
            Pos pos = player.getPosition();
            try {
                long revision = markers.upsert(new Point(context.get(id), pos.x(), pos.y(), pos.z(), pos.yaw(),
                        pos.pitch(), List.of(), Map.of()));
                sender.sendMessage(Component.text("Point " + context.get(id) + " saved at revision " + revision + "."));
            } catch (IllegalArgumentException | IllegalStateException e) {
                sender.sendMessage(Component.text(e.getMessage()));
            }
        }, id);
        return command;
    }
}
