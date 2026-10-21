package net.honormc.backstage.client.editor;

import com.mojang.blaze3d.opengl.GlTexture;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.Window;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import net.fabricmc.fabric.api.client.keymapping.v1.KeyMappingHelper;
import net.fabricmc.loader.api.FabricLoader;
import net.honormc.backstage.client.BrushSession;
import net.honormc.backstage.client.editor.render.FrameBlur;
import net.honormc.backstage.client.editor.render.OverlayFrame;
import net.honormc.backstage.client.editor.render.WorldOverlay;
import net.honormc.backstage.client.editor.ui.Anim;
import net.honormc.backstage.client.editor.ui.EditorUi;
import net.honormc.backstage.client.editor.ui.ImGuiLayer;
import net.honormc.backstage.client.editor.ui.Toasts;
import net.honormc.backstage.protocol.DeniedReason;
import net.honormc.backstage.protocol.model.Group;
import net.honormc.backstage.protocol.model.Marker;
import net.honormc.backstage.protocol.model.Point;
import net.honormc.backstage.protocol.model.Region;
import net.honormc.backstage.protocol.model.Shape;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.player.Abilities;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.lwjgl.glfw.GLFW;

public final class EditorMode {
    public static final double REACH = 256;
    public static final long REJECTED_MS = 5_000;
    public static final long FLY_MS = 450;

    public static final KeyMapping.Category CATEGORY = KeyMapping.Category.register(Identifier.fromNamespaceAndPath("backstage", "editor"));
    private static EditorMode instance;

    private record Fly(Vec3 from, Vec3 to, long start) {
    }

    private final Minecraft mc;
    private final EditorClient editor;
    private final BrushSession brushes;
    private final EditorTools tools;
    private final EditorLayout layout;
    private final Path layoutFile;
    private final EditorShortcuts shortcuts = EditorShortcuts.defaults();
    private final KeyMapping toggleKey;
    private final WorldOverlay overlay = new WorldOverlay();
    private final FrameBlur blur = new FrameBlur();
    private final Toasts toasts = new Toasts();
    private ImGuiLayer imgui;
    private EditorUi ui;
    private boolean active;
    private boolean looking;
    private boolean grabbing;
    private boolean hudWasHidden;
    private boolean bobWas;
    private boolean flyingWas;
    private float speedWas;
    private double lookX;
    private double lookY;
    private final Matrix4f projection = new Matrix4f();
    private final Matrix4f view = new Matrix4f();
    private Vec3 camera = Vec3.ZERO;
    private boolean cameraValid;
    private Fly fly;
    private long openedAt;
    private String rejected;
    private long rejectedAt;
    private long started = System.nanoTime();

    public EditorMode(Minecraft mc, EditorClient editor, BrushSession brushes, KeyMapping toggleKey) {
        this.mc = mc;
        this.editor = editor;
        this.brushes = brushes;
        this.layoutFile = FabricLoader.getInstance().getGameDir().resolve("backstage").resolve("editor.json");
        this.layout = EditorLayout.read(layoutFile);
        this.tools = new EditorTools(editor, brushes, new EditorTools.Notifier() {
            @Override
            public void ok(String title, String message) {
                toasts.push(Toasts.Kind.OK, title, message);
            }

            @Override
            public void error(String message) {
                toasts.push(Toasts.Kind.ERROR, message, "");
            }
        });
        this.toggleKey = toggleKey;
        instance = this;
    }

    public static KeyMapping toggleKey() {
        return new KeyMapping("key.backstage.editor", InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_F8, CATEGORY);
    }

    public static EditorMode get() {
        return instance;
    }

    public static boolean capturing() {
        return instance != null && instance.active && instance.mc.gui.screen() == null;
    }

    public static boolean blocksGrab() {
        return instance != null && instance.active && !instance.grabbing;
    }

    public boolean active() {
        return active;
    }

    public int fps() {
        return mc == null ? 0 : mc.getFps();
    }

    public EditorClient editor() {
        return editor;
    }

    public EditorTools tools() {
        return tools;
    }

    public BrushSession brushes() {
        return brushes;
    }

    public EditorLayout layout() {
        return layout;
    }

    public EditorShortcuts shortcuts() {
        return shortcuts;
    }

    public Toasts toasts() {
        return toasts;
    }

    public WorldOverlay overlay() {
        return overlay;
    }

    public long openedAt() {
        return openedAt;
    }

    public String rejected() {
        return rejected != null && System.currentTimeMillis() - rejectedAt < REJECTED_MS ? rejected : null;
    }

    public String toggleKeyName() {
        return toggleKey.getTranslatedKeyMessage().getString();
    }

    public int toggleKeyCode() {
        InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(toggleKey);
        return key.getType() == InputConstants.Type.KEYSYM ? key.getValue() : -1;
    }

    public List<Integer> movementKeys() {
        List<Integer> keys = new ArrayList<>();
        if (mc == null) {
            return keys;
        }
        for (KeyMapping mapping : new KeyMapping[]{mc.options.keyUp, mc.options.keyDown, mc.options.keyLeft,
                mc.options.keyRight, mc.options.keyJump, mc.options.keyShift, mc.options.keySprint}) {
            InputConstants.Key key = KeyMappingHelper.getBoundKeyOf(mapping);
            if (key.getType() == InputConstants.Type.KEYSYM) {
                keys.add(key.getValue());
            }
        }
        return keys;
    }

    public List<EditorShortcuts.Conflict> conflicts() {
        return shortcuts.conflicts(toggleKeyCode(), toggleKeyName(), movementKeys());
    }

    public void rejected(String reason) {
        rejected = reason;
        rejectedAt = System.currentTimeMillis();
        if (active) {
            toasts.push(Toasts.Kind.ERROR, "Edit rejected", reason);
        }
    }

    public boolean notify(String title, String message, boolean error) {
        if (!active) {
            return false;
        }
        toasts.push(error ? Toasts.Kind.ERROR : Toasts.Kind.OK, title, message);
        return true;
    }

    public void tick() {
        while (toggleKey.consumeClick()) {
            if (!active) {
                enter();
            }
        }
        if (!active) {
            return;
        }
        String problem = unavailable();
        if (problem != null || mc.player == null) {
            leave();
            if (problem != null) {
                EditorNetworking.toast("Editor closed", problem);
            }
            return;
        }
        tools.tick();
        LocalPlayer player = mc.player;
        Abilities abilities = player.getAbilities();
        if (abilities.mayfly && !abilities.flying) {
            abilities.flying = true;
            player.onUpdateAbilities();
        }
        abilities.setFlyingSpeed(layout.flySpeed);
    }

    public String unavailable() {
        return switch (editor.state()) {
            case EditorState.None s -> "This server has no Backstage editor.";
            case EditorState.Handshaking s -> "Still connecting to the editor.";
            case EditorState.Denied s -> "Editor access denied: " + reason(s.reason()) + ".";
            case EditorState.Available s -> s.bound() ? null : "No map is bound for editing on this server.";
        };
    }

    private static String reason(DeniedReason reason) {
        return switch (reason) {
            case NO_PERMISSION -> "you do not have editor permission";
            case CLIENT_OUTDATED -> "this mod is older than the server's editor";
            case SERVER_OUTDATED -> "the server's editor is older than this mod";
            case REVOKED -> "your editor access was revoked";
        };
    }

    public void enter() {
        if (active || mc.player == null) {
            return;
        }
        String problem = unavailable();
        if (problem == null && !(mc.gameRenderer.mainRenderTarget().getColorTexture() instanceof GlTexture)) {
            problem = "The editor needs the OpenGL renderer. Set Graphics API to OpenGL in Video Settings.";
        }
        if (problem != null) {
            EditorNetworking.toast("Editor unavailable", problem);
            return;
        }
        if (imgui == null) {
            imgui = new ImGuiLayer(mc.getWindow().handle());
            ui = new EditorUi(this, imgui.fonts());
        }
        active = true;
        looking = false;
        openedAt = System.currentTimeMillis();
        mc.mouseHandler.releaseMouse();
        hudWasHidden = mc.gui.hud.isHidden();
        if (!hudWasHidden) {
            mc.gui.hud.toggle();
        }
        bobWas = mc.options.bobView().get();
        mc.options.bobView().set(false);
        LocalPlayer player = mc.player;
        Abilities abilities = player.getAbilities();
        flyingWas = abilities.flying;
        speedWas = abilities.getFlyingSpeed();
        if (abilities.mayfly) {
            abilities.flying = true;
            player.onUpdateAbilities();
        }
        abilities.setFlyingSpeed(layout.flySpeed);
        tools.tool(EditorTools.Tool.of(layout.tool));
        tools.brushRadius = layout.brushRadius;
        tools.brushMode = switch (layout.brushMode) {
            case "erase" -> EditorTools.BrushMode.ERASE;
            case "fill" -> EditorTools.BrushMode.FILL;
            default -> EditorTools.BrushMode.PAINT;
        };
        tools.sameBlock = layout.sameBlock;
        tools.boxAsGroup = layout.boxAsGroup;
        BrushSession.messages(line -> toasts.push(line.startsWith("§c") ? Toasts.Kind.ERROR : Toasts.Kind.INFO,
                line.replaceAll("§.", ""), ""));
        ui.opened();
        if (editor.state() instanceof EditorState.Available a) {
            toasts.push(Toasts.Kind.OK, "Editor connected", a.serverName() + " · map " + a.map() + " · "
                    + (a.writable() ? "writable" : "read-only"));
        }
    }

    public void leave() {
        if (!active) {
            return;
        }
        tools.cancelGesture();
        tools.closeGroup();
        saveLayout();
        active = false;
        looking = false;
        fly = null;
        BrushSession.messages(null);
        overlay.clear();
        if (mc.gui.hud.isHidden() != hudWasHidden) {
            mc.gui.hud.toggle();
        }
        mc.options.bobView().set(bobWas);
        LocalPlayer player = mc.player;
        if (player != null) {
            Abilities abilities = player.getAbilities();
            abilities.setFlyingSpeed(speedWas);
            if (abilities.flying != flyingWas) {
                abilities.flying = flyingWas && abilities.mayfly;
                player.onUpdateAbilities();
            }
        }
        if (mc.gui.screen() == null && mc.isWindowActive()) {
            grab();
        }
    }

    public void saveLayout() {
        layout.tool = tools.tool().key();
        layout.brushRadius = tools.brushRadius;
        layout.brushMode = tools.brushMode.name().toLowerCase(Locale.ROOT);
        layout.sameBlock = tools.sameBlock;
        layout.boxAsGroup = tools.boxAsGroup;
        layout.write(layoutFile);
    }

    private void grab() {
        grabbing = true;
        try {
            mc.mouseHandler.grabMouse();
        } finally {
            grabbing = false;
        }
    }

    public void disconnected() {
        if (active) {
            leave();
        }
        tools.cancelGesture();
    }

    public boolean key(int key, int scancode, int action, int mods) {
        if (!capturing()) {
            return false;
        }
        imgui.key(key, scancode, action, mods);
        if (action == GLFW.GLFW_RELEASE) {
            return false;
        }
        if (ui.capturesKeys() || imgui.wantsText()) {
            if (key == GLFW.GLFW_KEY_ESCAPE && action == GLFW.GLFW_PRESS) {
                ui.escape();
            }
            return true;
        }
        EditorShortcuts.Action shortcut = shortcuts.resolve(key, mods, toggleKeyCode(), movementKeys());
        if (shortcut != null) {
            boolean repeatable = shortcut == EditorShortcuts.Action.UNDO || shortcut == EditorShortcuts.Action.REDO
                    || shortcut == EditorShortcuts.Action.REDO_ALT || shortcut == EditorShortcuts.Action.RADIUS_DOWN
                    || shortcut == EditorShortcuts.Action.RADIUS_UP;
            if (action == GLFW.GLFW_PRESS || repeatable) {
                run(shortcut, key == GLFW.GLFW_KEY_ESCAPE);
            }
            return true;
        }
        if (movementKeys().contains(key)) {
            return false;
        }
        return key != GLFW.GLFW_KEY_F2 && key != GLFW.GLFW_KEY_F3 && key != GLFW.GLFW_KEY_F11;
    }

    public void run(EditorShortcuts.Action action, boolean escape) {
        switch (action) {
            case TOOL_SELECT -> tools.tool(EditorTools.Tool.SELECT);
            case TOOL_POINT -> tools.tool(EditorTools.Tool.POINT);
            case TOOL_GROUP -> tools.tool(EditorTools.Tool.GROUP);
            case TOOL_BOX -> tools.tool(EditorTools.Tool.BOX);
            case TOOL_REGION -> tools.tool(EditorTools.Tool.REGION);
            case TOOL_MOVE -> tools.tool(EditorTools.Tool.MOVE);
            case TOOL_BRUSH -> tools.tool(EditorTools.Tool.BRUSH);
            case UNDO -> tools.undo(false);
            case REDO, REDO_ALT -> tools.undo(true);
            case DELETE -> ui.requestDelete();
            case DUPLICATE -> tools.duplicate();
            case SELECT_NONE -> tools.selectNone();
            case FOCUS -> focusSelection();
            case SEARCH -> ui.focusSearch();
            case PALETTE -> ui.openPalette();
            case CONFIRM -> tools.confirm();
            case SNAP_YAW -> tools.snapYaw();
            case RADIUS_DOWN -> tools.brushRadius(-1);
            case RADIUS_UP -> tools.brushRadius(1);
            case HISTORY -> layout.historyOpen = !layout.historyOpen;
            case LEAVE -> {
                if (escape && (ui.escape() || tools.escape())) {
                    return;
                }
                leave();
            }
        }
    }

    public boolean mouseButton(int button, int action, int mods) {
        if (!capturing()) {
            return false;
        }
        if (looking) {
            if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT && action == GLFW.GLFW_RELEASE) {
                imgui.mouseButton(button, action, mods);
                stopLooking();
            }
            return true;
        }
        imgui.mouseButton(button, action, mods);
        if (action == GLFW.GLFW_PRESS) {
            if (imgui.wantsMouse()) {
                return true;
            }
            EditorTools.Cursor cursor = cursor();
            if (button == GLFW.GLFW_MOUSE_BUTTON_LEFT && cursor != null) {
                tools.mouseDown(cursor);
            } else if (button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
                startLooking();
            }
        } else if (action == GLFW.GLFW_RELEASE && button == GLFW.GLFW_MOUSE_BUTTON_LEFT) {
            EditorTools.Cursor cursor = cursor();
            if (cursor != null) {
                tools.mouseUp(cursor);
            }
        }
        return true;
    }

    public boolean scroll(double x, double y) {
        if (!capturing()) {
            return false;
        }
        if (!looking) {
            imgui.scroll(x, y);
            if (imgui.wantsMouse()) {
                return true;
            }
        }
        int steps = (int) Math.signum(y);
        if (steps == 0) {
            return true;
        }
        if (!looking && ctrl()) {
            switch (tools.tool()) {
                case GROUP -> tools.brushRadius(steps);
                case POINT -> tools.rotate(steps * 15);
                default -> {
                }
            }
            return true;
        }
        layout.flySpeed = Math.clamp(layout.flySpeed * (steps > 0 ? 1.18f : 1 / 1.18f), EditorLayout.MIN_FLY,
                EditorLayout.MAX_FLY);
        if (mc.player != null) {
            mc.player.getAbilities().setFlyingSpeed(layout.flySpeed);
        }
        ui.flySpeedChanged();
        return true;
    }

    public boolean character(int codepoint) {
        if (!capturing()) {
            return false;
        }
        imgui.character(codepoint);
        return true;
    }

    private void startLooking() {
        looking = true;
        lookX = mc.mouseHandler.xpos();
        lookY = mc.mouseHandler.ypos();
        tools.cancelGesture();
        grab();
    }

    private void stopLooking() {
        looking = false;
        mc.mouseHandler.releaseMouse();
        GLFW.glfwSetCursorPos(mc.getWindow().handle(), lookX, lookY);
    }

    public boolean looking() {
        return looking;
    }

    public boolean ctrl() {
        Window window = mc.getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL);
    }

    public boolean shift() {
        Window window = mc.getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT);
    }

    public boolean alt() {
        Window window = mc.getWindow();
        return InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_ALT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_ALT);
    }

    public Marquee.View view() {
        if (!cameraValid) {
            return null;
        }
        Window window = mc.getWindow();
        Matrix4f viewProjection = new Matrix4f(projection).mul(view);
        return new Marquee.View(viewProjection, camera.x, camera.y, camera.z, window.getScreenWidth(), window.getScreenHeight());
    }

    public EditorTools.Cursor cursor() {
        Marquee.View view = view();
        if (view == null || mc.level == null) {
            return null;
        }
        float x = (float) mc.mouseHandler.xpos();
        float y = (float) mc.mouseHandler.ypos();
        MarkerPick.Ray ray = ray(view, x, y);
        Vec3 from = new Vec3(ray.x(), ray.y(), ray.z());
        Vec3 to = from.add(ray.dx() * REACH, ray.dy() * REACH, ray.dz() * REACH);
        BlockHitResult hit = mc.level.clip(new ClipContext(from, to, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE,
                CollisionContext.empty()));
        if (hit.getType() != HitResult.Type.BLOCK) {
            hit = null;
        }
        return new EditorTools.Cursor(ray, hit, x, y, view, ctrl(), shift(), alt());
    }

    public MarkerPick.Ray ray(Marquee.View view, float x, float y) {
        Matrix4f inverse = new Matrix4f(view.viewProjection()).invert();
        float nx = 2 * x / view.width() - 1;
        float ny = 1 - 2 * y / view.height();
        Vector4f point = new Vector4f(nx, ny, 0.5f, 1).mul(inverse);
        Vec3 dir = new Vec3(point.x / point.w, point.y / point.w, point.z / point.w).normalize();
        return new MarkerPick.Ray(camera.x, camera.y, camera.z, dir.x, dir.y, dir.z, REACH);
    }

    public void worldRendered(CameraRenderState state, RenderTarget target) {
        projection.set(state.projectionMatrix);
        view.set(state.viewRotationMatrix);
        camera = state.pos;
        cameraValid = state.initialized;
        if (!active || mc.player == null) {
            return;
        }
        overlay.collect(editor.store(), tools, layout, camera);
        float time = (System.nanoTime() - started) / 1e9f;
        overlay.render(new OverlayFrame(target, projection, view, camera.x, camera.y, camera.z, time), tools,
                capturing() && alt() && !imgui.wantsText());
    }

    public void frameRendered() {
        if (!capturing() || mc.player == null) {
            return;
        }
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        if (target.width <= 0 || target.height <= 0) {
            return;
        }
        EditorScale.ui(EditorScale.of(mc.getWindow().getGuiScale()));
        updateFly();
        if (!looking) {
            EditorTools.Cursor cursor = cursor();
            if (cursor != null) {
                tools.hover(cursor);
            }
        }
        blur.update(target);
        imgui.frame(target, () -> ui.draw(blur.texture()));
    }

    public void flyTo(Marker marker) {
        LocalPlayer player = mc.player;
        double[] centre = MarkerText.centre(marker);
        if (player == null || centre == null) {
            return;
        }
        double size = switch (marker) {
            case Point p -> 1;
            case Group g -> {
                GroupOutline.Shape b = GroupOutline.bounds(g.blocks());
                yield Math.max(b.maxX() - b.minX(), Math.max(b.maxY() - b.minY(), b.maxZ() - b.minZ())) + 1;
            }
            case Region r -> switch (r.shape()) {
                case Shape.Box b -> Math.max(b.maxX() - b.minX(), Math.max(b.maxY() - b.minY(), b.maxZ() - b.minZ())) + 1;
                case Shape.Polygon p -> {
                    int[] xz = p.xz();
                    int minX = Integer.MAX_VALUE;
                    int minZ = Integer.MAX_VALUE;
                    int maxX = Integer.MIN_VALUE;
                    int maxZ = Integer.MIN_VALUE;
                    for (int i = 0; i < xz.length; i += 2) {
                        minX = Math.min(minX, xz[i]);
                        maxX = Math.max(maxX, xz[i]);
                        minZ = Math.min(minZ, xz[i + 1]);
                        maxZ = Math.max(maxZ, xz[i + 1]);
                    }
                    yield Math.max(maxX - minX, Math.max(maxZ - minZ, p.maxY() - p.minY())) + 1;
                }
            };
        };
        double distance = Math.clamp(size * 1.6, 5, 96);
        Vec3 look = player.getViewVector(1);
        Vec3 eye = new Vec3(centre[0], centre[1], centre[2]).subtract(look.scale(distance));
        Vec3 feet = eye.subtract(0, player.getEyeHeight(), 0);
        fly = new Fly(player.position(), feet, System.currentTimeMillis());
    }

    public void focusSelection() {
        List<Marker> markers = tools.selectedMarkers();
        if (markers.isEmpty()) {
            toasts.push(Toasts.Kind.ERROR, "Nothing selected to fly to.", "");
            return;
        }
        flyTo(markers.getLast());
    }

    private void updateFly() {
        LocalPlayer player = mc.player;
        if (fly == null || player == null) {
            return;
        }
        float t = Math.clamp((System.currentTimeMillis() - fly.start()) / (float) FLY_MS, 0, 1);
        float e = Anim.easeInOut(t);
        Vec3 pos = fly.from().lerp(fly.to(), e);
        player.setPos(pos.x, pos.y, pos.z);
        player.setDeltaMovement(Vec3.ZERO);
        if (t >= 1) {
            fly = null;
        }
    }

}
