package net.honormc.backstage.client.editor.ui;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import imgui.ImGui;
import imgui.ImGuiIO;
import imgui.type.ImInt;
import java.util.List;
import java.util.Map;
import net.honormc.backstage.client.BrushSession;
import net.honormc.backstage.client.ScriptStore;
import net.honormc.backstage.client.editor.EditorClient;
import net.honormc.backstage.client.editor.EditorMode;
import net.honormc.backstage.client.editor.EditorTools;
import net.honormc.backstage.client.editor.EditorTools.Selection;
import net.honormc.backstage.protocol.BlockPosPacking;
import net.honormc.backstage.protocol.ClientboundPacket;
import net.honormc.backstage.protocol.EditorCodec;
import net.honormc.backstage.protocol.EditorProtocol;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.MarkerType;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Prop;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;
import net.minecraft.SharedConstants;
import net.minecraft.server.Bootstrap;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class EditorUiTest {
    @BeforeAll
    static void bootstrap() {
        SharedConstants.tryDetectVersion();
        Bootstrap.bootStrap();
    }

    private static List<Marker> markers() {
        long[] blocks = new long[40];
        for (int i = 0; i < blocks.length; i++) {
            blocks[i] = BlockPosPacking.pack(i % 8, 64, i / 8);
        }
        return List.of(
                new Point("boss_spawn", 12, 64, -40, 90, 0, List.of("boss"), Map.of("hp", new Prop.Num(500))),
                new Group("well_top", blocks, List.of("boss", "well"), Map.of("particle", new Prop.Str("soul_fire_flame"),
                        "active", new Prop.Bool(true), "floor", new Prop.BlockProp("minecraft:stone"),
                        "loot", new Prop.ItemProp("{id:\"minecraft:diamond\",count:1}"),
                        "steps", new Prop.ListProp(List.of(new Prop.Num(1))))),
                new Region("arena", new Shape.Polygon(new int[]{0, 0, 10, 0, 10, 10, 0, 10}, 60, 70), List.of("pvp"), Map.of()),
                new Region("lobby", new Shape.Box(0, 60, 0, 4, 70, 4), List.of(), Map.of()));
    }

    private static EditorClient client() {
        EditorClient client = new EditorClient(new EditorClient.Output() {
            @Override
            public void send(byte[] body) {
            }

            @Override
            public void toast(String title, String message) {
            }

            @Override
            public void chat(String line) {
            }
        }, "1.0.0");
        client.channelsRegistered(List.of(EditorProtocol.CHANNEL));
        client.receive(EditorCodec.encode(new ClientboundPacket.Welcome(EditorProtocol.VERSION, "Build")));
        client.receive(EditorCodec.encode(new ClientboundPacket.MapState(true, "desert", true)));
        client.receive(EditorCodec.encode(new ClientboundPacket.SyncBegin("desert", 214, 4)));
        client.receive(EditorCodec.encode(new ClientboundPacket.SyncBatch("desert", markers())));
        client.receive(EditorCodec.encode(new ClientboundPacket.SyncEnd("desert")));
        return client;
    }

    private static void frames(Fonts fonts, EditorUi ui, int count) {
        ImGuiIO io = ImGui.getIO();
        for (int i = 0; i < count; i++) {
            io.setDeltaTime(1 / 60f);
            ImGui.newFrame();
            ImGui.pushFont(fonts.body, Fonts.px(13, fonts.scale));
            ui.draw(0);
            ImGui.popFont();
            ImGui.render();
        }
    }

    @Test
    void everyPanelDrawsWithoutTrippingImGui() {
        EditorClient client = client();
        assertEquals(4, client.store().count());
        EditorMode mode = new EditorMode(null, client, new BrushSession(new ScriptStore()), EditorMode.toggleKey());
        ImGui.createContext();
        ImGuiIO io = ImGui.getIO();
        io.setIniFilename(null);
        io.setDisplaySize(1600, 900);
        Fonts fonts = new Fonts();
        for (float scale : new float[]{1, 1.5f}) {
            fonts.build(io.getFonts(), scale);
            io.getFonts().getTexDataAsRGBA32(new ImInt(), new ImInt());
            io.getFonts().setTexID(1);
            Style.apply(scale);
            EditorUi ui = new EditorUi(mode, fonts);
            ui.opened();
            EditorTools tools = mode.tools();
            frames(fonts, ui, 20);
            for (EditorTools.Tool tool : EditorTools.Tool.values()) {
                tools.tool(tool);
                frames(fonts, ui, 3);
            }
            tools.select(new Selection(MarkerType.POINT, "boss_spawn"), false);
            frames(fonts, ui, 3);
            tools.select(new Selection(MarkerType.GROUP, "well_top"), false);
            frames(fonts, ui, 3);
            tools.select(new Selection(MarkerType.REGION, "arena"), false);
            frames(fonts, ui, 3);
            tools.select(new Selection(MarkerType.REGION, "lobby"), false);
            frames(fonts, ui, 3);
            tools.select(new Selection(MarkerType.POINT, "boss_spawn"), true);
            tools.select(new Selection(MarkerType.GROUP, "well_top"), true);
            assertEquals(3, tools.selection().size());
            frames(fonts, ui, 3);
            ui.openPalette();
            frames(fonts, ui, 3);
            assertTrue(ui.capturesKeys());
            ui.escape();
            mode.layout().historyOpen = true;
            ui.side.shortcutsOpen = true;
            mode.toasts().push(Toasts.Kind.OK, "Editor connected", "Build · map desert · writable");
            mode.toasts().push(Toasts.Kind.ERROR, "Edit rejected", "Map desert is read-only.");
            mode.rejected("Map desert is read-only.");
            ui.focusSearch();
            ui.flySpeedChanged();
            frames(fonts, ui, 5);
            tools.groupNew("flag");
            tools.tool(EditorTools.Tool.GROUP);
            frames(fonts, ui, 3);
            tools.selectNone();
            frames(fonts, ui, 3);
            mode.layout().historyOpen = false;
            ui.side.shortcutsOpen = false;
        }
        ImGui.destroyContext();
    }
}
