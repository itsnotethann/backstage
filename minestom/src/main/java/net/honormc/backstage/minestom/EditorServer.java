package net.honormc.backstage.minestom;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;
import net.honormc.backstage.protocol.ClientboundPacket;
import net.honormc.backstage.protocol.ClientboundPacket.Denied;
import net.honormc.backstage.protocol.ClientboundPacket.EditRejected;
import net.honormc.backstage.protocol.ClientboundPacket.MapState;
import net.honormc.backstage.protocol.ClientboundPacket.MarkerChanged;
import net.honormc.backstage.protocol.ClientboundPacket.MarkerRemoved;
import net.honormc.backstage.protocol.ClientboundPacket.RegistrySync;
import net.honormc.backstage.protocol.ClientboundPacket.SyncBatch;
import net.honormc.backstage.protocol.ClientboundPacket.SyncBegin;
import net.honormc.backstage.protocol.ClientboundPacket.SyncEnd;
import net.honormc.backstage.protocol.ClientboundPacket.Welcome;
import net.honormc.backstage.protocol.DeniedReason;
import net.honormc.backstage.protocol.EditorCodec;
import net.honormc.backstage.protocol.EditorProtocol;
import net.honormc.backstage.protocol.ProtocolException;
import net.honormc.backstage.protocol.ServerboundPacket;
import net.honormc.backstage.protocol.ServerboundPacket.Hello;
import net.honormc.backstage.protocol.ServerboundPacket.RemoveMarker;
import net.honormc.backstage.protocol.ServerboundPacket.RenameMarker;
import net.honormc.backstage.protocol.ServerboundPacket.Resync;
import net.honormc.backstage.protocol.ServerboundPacket.UpsertMarker;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.minestom.server.MinecraftServer;
import net.minestom.server.command.CommandManager;
import net.minestom.server.entity.Player;
import net.minestom.server.event.Event;
import net.minestom.server.event.EventNode;
import net.minestom.server.event.player.PlayerDisconnectEvent;
import net.minestom.server.event.player.PlayerPluginMessageEvent;
import net.minestom.server.event.player.PlayerSpawnEvent;
import net.minestom.server.instance.Instance;

public final class EditorServer {
    static final String REGISTER_CHANNEL = "minecraft:register";
    static final int BATCH_BYTES = 256_000;

    private static final System.Logger LOGGER = System.getLogger("backstage");
    private static final AtomicReference<EditorServer> INSTANCE = new AtomicReference<>();

    private final EventNode<Event> parent;
    private final EventNode<Event> node = EventNode.all("backstage");
    private final Path configFile;
    private final Map<UUID, Player> welcomed = new ConcurrentHashMap<>();
    private final Map<Instance, String> bindings = new ConcurrentHashMap<>();
    private final Map<String, LoadedMap> maps = new HashMap<>();
    private final MarkerSaver saver = new MarkerSaver();
    private final EditorRegistry registry = new EditorRegistry(this::registryChanged);
    private volatile EditorConfig config;
    private volatile EditorPermission permission;
    private CommandManager commandManager;
    private EditorCommands commands;

    private record LoadedMap(MapMarkers markers, MarkerFile file) {
    }

    private EditorServer(EventNode<Event> parent, EditorConfig config, Path configFile) {
        this.parent = parent;
        this.config = config;
        this.configFile = configFile;
        this.permission = EditorPermission.defaults(() -> this.config);
        node.addListener(PlayerSpawnEvent.class, this::onSpawn);
        node.addListener(PlayerPluginMessageEvent.class, this::onPluginMessage);
        node.addListener(PlayerDisconnectEvent.class, event -> welcomed.remove(event.getPlayer().getUuid()));
    }

    public static EditorServer install(EventNode<Event> parent, EditorConfig config) {
        return install(parent, config, null);
    }

    public static EditorServer install(EventNode<Event> parent, Path configFile) {
        return install(parent, EditorConfig.load(configFile), configFile);
    }

    private static EditorServer install(EventNode<Event> parent, EditorConfig config, Path configFile) {
        EditorServer server = new EditorServer(parent, config, configFile);
        if (!INSTANCE.compareAndSet(null, server)) {
            throw new IllegalStateException("EditorServer is already installed");
        }
        parent.addChild(server.node);
        return server;
    }

    public EditorConfig config() {
        return config;
    }

    public EditorPermission permission() {
        return permission;
    }

    public void permission(EditorPermission permission) {
        this.permission = permission;
    }

    public EditorRegistry registry() {
        return registry;
    }

    public MapMarkers bind(Instance instance, String map, Path polar) {
        MarkerFile file = MarkerFile.forPolar(polar);
        String previous = bindings.get(instance);
        if (previous != null && !previous.equals(map)) {
            unbind(instance);
        }
        LoadedMap loaded;
        synchronized (this) {
            loaded = maps.get(map);
            if (loaded == null) {
                MapMarkers markers;
                try {
                    markers = file.load(map);
                } catch (IOException e) {
                    throw new UncheckedIOException("cannot bind map " + map, e);
                }
                loaded = new LoadedMap(markers, file);
                markers.listener(listener(loaded));
                maps.put(map, loaded);
            }
            bindings.put(instance, map);
        }
        for (Player player : instance.getPlayers()) {
            if (welcomed.containsKey(player.getUuid())) {
                sendMapState(player, instance);
            }
        }
        return loaded.markers();
    }

    public void unbind(Instance instance) {
        LoadedMap released = null;
        synchronized (this) {
            String map = bindings.remove(instance);
            if (map == null) {
                return;
            }
            if (!bindings.containsValue(map)) {
                released = maps.remove(map);
            }
        }
        if (released != null) {
            saver.flush(released.markers(), released.file());
        }
        for (Player player : instance.getPlayers()) {
            if (welcomed.containsKey(player.getUuid())) {
                send(player, new MapState(false, "", false));
            }
        }
    }

    public synchronized Optional<MapMarkers> markers(String map) {
        return Optional.ofNullable(maps.get(map)).map(LoadedMap::markers);
    }

    public void registerCommands(CommandManager commandManager) {
        EditorCommands commands = new EditorCommands(this);
        commandManager.register(commands);
        this.commandManager = commandManager;
        this.commands = commands;
    }

    public void saveAll() {
        List<LoadedMap> loaded;
        synchronized (this) {
            loaded = List.copyOf(maps.values());
        }
        for (LoadedMap map : loaded) {
            saver.flush(map.markers(), map.file());
        }
    }

    public void shutdown() {
        saveAll();
        saver.shutdown();
        parent.removeChild(node);
        if (commandManager != null) {
            commandManager.unregister(commands);
        }
        welcomed.clear();
        INSTANCE.compareAndSet(this, null);
    }

    boolean reload() {
        if (configFile == null) {
            return false;
        }
        config = EditorConfig.load(configFile);
        recheck(MinecraftServer.getConnectionManager().getOnlinePlayers());
        return true;
    }

    void recheck(Collection<Player> players) {
        for (Player player : players) {
            boolean allowed = permission.mayEdit(player);
            boolean editing = welcomed.containsKey(player.getUuid());
            if (editing && !allowed) {
                revoke(player);
            } else if (!editing && allowed) {
                advertise(player);
            }
        }
    }

    List<String> status() {
        List<String> lines = new ArrayList<>();
        Map<Instance, MapMarkers> bound = new HashMap<>();
        synchronized (this) {
            bindings.forEach((instance, map) -> bound.put(instance, maps.get(map).markers()));
        }
        if (bound.isEmpty()) {
            lines.add("No bound instances.");
        }
        bound.forEach((instance, markers) -> lines.add("Instance " + instance.getUuid() + " -> map " + markers.map()
                + ", revision " + markers.revision() + ", writable " + markers.writable()));
        List<String> names = welcomed.values().stream().map(Player::getUsername).sorted().toList();
        lines.add("Welcomed editors (" + names.size() + "): " + (names.isEmpty() ? "none" : String.join(", ", names)));
        return lines;
    }

    private MapMarkers.Listener listener(LoadedMap loaded) {
        return new MapMarkers.Listener() {
            @Override
            public void changed(MapMarkers markers, long revision, Marker marker) {
                broadcast(markers.map(), new MarkerChanged(markers.map(), revision, marker));
                saver.schedule(markers, loaded.file());
            }

            @Override
            public void removed(MapMarkers markers, long revision, MarkerType type, String id) {
                broadcast(markers.map(), new MarkerRemoved(markers.map(), revision, type, id));
                saver.schedule(markers, loaded.file());
            }
        };
    }

    private void onSpawn(PlayerSpawnEvent event) {
        Player player = event.getPlayer();
        if (event.isFirstSpawn()) {
            advertise(player);
        } else if (welcomed.containsKey(player.getUuid())) {
            sendMapState(player, event.getInstance());
        }
    }

    private void onPluginMessage(PlayerPluginMessageEvent event) {
        if (!event.getIdentifier().equals(EditorProtocol.CHANNEL)) {
            return;
        }
        Player player = event.getPlayer();
        ServerboundPacket packet;
        try {
            packet = EditorCodec.decodeServerbound(event.getMessage());
        } catch (ProtocolException e) {
            if (welcomed.remove(player.getUuid()) != null) {
                LOGGER.log(System.Logger.Level.WARNING, "Dropping editor session of {0}: {1}", player.getUsername(),
                        e.getMessage());
            }
            return;
        }
        if (packet instanceof Hello hello) {
            handshake(player, hello);
            return;
        }
        if (!welcomed.containsKey(player.getUuid())) {
            return;
        }
        if (packet instanceof Resync) {
            sendMapState(player, player.getInstance());
            return;
        }
        if (!permission.mayEdit(player)) {
            revoke(player);
            return;
        }
        edit(player, packet);
    }

    private void handshake(Player player, Hello hello) {
        welcomed.remove(player.getUuid());
        if (hello.protocol() < EditorProtocol.VERSION) {
            send(player, new Denied(DeniedReason.CLIENT_OUTDATED, EditorProtocol.VERSION));
        } else if (hello.protocol() > EditorProtocol.VERSION) {
            send(player, new Denied(DeniedReason.SERVER_OUTDATED, EditorProtocol.VERSION));
        } else if (!permission.mayEdit(player)) {
            send(player, new Denied(DeniedReason.NO_PERMISSION, EditorProtocol.VERSION));
        } else {
            welcomed.put(player.getUuid(), player);
            send(player, new Welcome(EditorProtocol.VERSION, config.serverName()));
            send(player, new RegistrySync(registry.snapshot()));
            sendMapState(player, player.getInstance());
        }
    }

    private void edit(Player player, ServerboundPacket packet) {
        String map;
        MarkerType type;
        String id;
        switch (packet) {
            case UpsertMarker p -> {
                map = p.map();
                type = p.marker().type();
                id = p.marker().id();
            }
            case RemoveMarker p -> {
                map = p.map();
                type = p.type();
                id = p.id();
            }
            case RenameMarker p -> {
                map = p.map();
                type = p.type();
                id = p.from();
            }
            default -> {
                return;
            }
        }
        LoadedMap current = loadedFor(player.getInstance());
        MapMarkers markers = current == null ? null : current.markers();
        String rejection = rejection(markers, registry.snapshot().types(), map, type);
        if (rejection != null) {
            send(player, new EditRejected(map, type, id, rejection));
            return;
        }
        try {
            switch (packet) {
                case UpsertMarker p -> markers.upsert(p.marker());
                case RemoveMarker p -> {
                    if (!markers.remove(p.type(), p.id())) {
                        send(player, new EditRejected(map, type, id, "No " + type.name().toLowerCase() + " named " + id + "."));
                    }
                }
                case RenameMarker p -> {
                    if (markers.get(p.type(), p.from()).isEmpty()) {
                        send(player, new EditRejected(map, type, id, "No " + type.name().toLowerCase() + " named " + id + "."));
                    } else if (!markers.rename(p.type(), p.from(), p.to())) {
                        send(player, new EditRejected(map, type, id, "A " + type.name().toLowerCase() + " named " + p.to() + " already exists."));
                    }
                }
                default -> {
                }
            }
        } catch (IllegalArgumentException | IllegalStateException e) {
            send(player, new EditRejected(map, type, id, e.getMessage()));
        }
    }

    static String rejection(MapMarkers markers, Collection<MarkerType> types, String map, MarkerType type) {
        if (markers == null) {
            return "No editable map here.";
        }
        if (!markers.map().equals(map)) {
            return "You are on map " + markers.map() + ", not " + map + ".";
        }
        if (!markers.writable()) {
            return "Map " + map + " is read-only: its markers file is newer than this server.";
        }
        if (!types.contains(type)) {
            return "This server does not use " + type.name().toLowerCase() + " markers.";
        }
        return null;
    }

    private void sendMapState(Player player, Instance instance) {
        LoadedMap loaded = loadedFor(instance);
        if (loaded == null) {
            send(player, new MapState(false, "", false));
            return;
        }
        MapMarkers markers = loaded.markers();
        send(player, new MapState(true, markers.map(), markers.writable()));
        sync(player, markers.snapshot());
    }

    private void sync(Player player, MarkerSnapshot snapshot) {
        String map = snapshot.map();
        send(player, new SyncBegin(map, snapshot.revision(), snapshot.size()));
        int base = EditorCodec.encode(new SyncBatch(map, List.of())).length;
        List<Marker> batch = new ArrayList<>();
        int bytes = 0;
        for (Marker marker : snapshot.all()) {
            int size = EditorCodec.encode(new SyncBatch(map, List.of(marker))).length - base;
            if (!batch.isEmpty() && bytes + size > BATCH_BYTES) {
                send(player, new SyncBatch(map, batch));
                batch = new ArrayList<>();
                bytes = 0;
            }
            batch.add(marker);
            bytes += size;
        }
        if (!batch.isEmpty()) {
            send(player, new SyncBatch(map, batch));
        }
        send(player, new SyncEnd(map));
    }

    private void broadcast(String map, ClientboundPacket packet) {
        byte[] body = EditorCodec.encode(packet);
        for (Player player : welcomed.values()) {
            LoadedMap loaded = loadedFor(player.getInstance());
            if (loaded != null && loaded.markers().map().equals(map)) {
                player.sendPluginMessage(EditorProtocol.CHANNEL, body);
            }
        }
    }

    private void registryChanged() {
        if (welcomed.isEmpty()) {
            return;
        }
        byte[] body = EditorCodec.encode(new RegistrySync(registry.snapshot()));
        for (Player player : welcomed.values()) {
            player.sendPluginMessage(EditorProtocol.CHANNEL, body);
        }
    }

    private void advertise(Player player) {
        player.sendPluginMessage(REGISTER_CHANNEL, EditorProtocol.CHANNEL.getBytes(StandardCharsets.UTF_8));
    }

    private void revoke(Player player) {
        welcomed.remove(player.getUuid());
        send(player, new Denied(DeniedReason.REVOKED, EditorProtocol.VERSION));
    }

    private static void send(Player player, ClientboundPacket packet) {
        player.sendPluginMessage(EditorProtocol.CHANNEL, EditorCodec.encode(packet));
    }

    private synchronized LoadedMap loadedFor(Instance instance) {
        if (instance == null) {
            return null;
        }
        String map = bindings.get(instance);
        return map == null ? null : maps.get(map);
    }
}
