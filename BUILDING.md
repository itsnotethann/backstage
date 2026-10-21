# Building Backstage

One Gradle multi-project build, Gradle 9.7.1 wrapper (Loom 1.18 needs 9.7), Java 25.

```bash
./gradlew build
```

| Subproject | Artifact | What |
|---|---|---|
| `protocol/` | `net.honormc:backstage-protocol` | Wire protocol shared by mod and servers. |
| `minestom/` | `net.honormc:backstage-minestom` | Minestom receiver. |
| `mod/` | `mod/build/libs/backstage-1.0.0.jar` | Fabric 26.2 client mod. |

## protocol

Package `net.honormc.backstage.protocol`: the `backstage:editor` plugin channel, packet records
(`ServerboundPacket`, `ClientboundPacket`), data and registry records (`model`), and `EditorCodec`, which the
mod and the receivers both call to encode and decode message bodies. `./gradlew :protocol:test` covers round
trips of every packet, VarInt/VarLong boundaries, `BlockPosPacking`, and every decode/encode rejection.

Publish to `mavenLocal()` as `net.honormc:backstage-protocol:1.0.0`:

```bash
./gradlew :protocol:publishToMavenLocal
```

### Rules

- JDK only. No Minecraft, Minestom, Fabric, Adventure or Netty classes in this subproject, so the mod and
  every server can share it.
- Any wire change bumps `EditorProtocol.VERSION`: an id table, a record layout, a limit or an enum value.
  Adding a packet bumps it too. No optional fields; client and server must match exactly.
- `int` fields (box corners, polygon vertices, min/max Y) are VarInts. A shape is a VarInt tag (`0` box,
  `1` polygon) then its fields; a polygon writes its vertex count, then the x,z pairs.

## minestom

Package `net.honormc.backstage.minestom`: the Minestom receiver. `EditorServer.install(parent, config)` (or
`install(parent, configFile)`, which also enables `/edit reload`) adds a `backstage` event node, handles the
handshake, permissions and marker edits, and stores markers per map in a sidecar file. Minestom is
`compileOnly`, so the host server supplies it.

```bash
./gradlew :minestom:publishToMavenLocal
```

publishes `net.honormc:backstage-minestom:1.0.0` (plain jar). `./gradlew :minestom:shadowJar` builds
`minestom/build/libs/backstage-minestom-1.0.0-all.jar` with the protocol bundled and snakeyaml relocated to
`net.honormc.backstage.shaded.yaml`; that is the jar a non-OFA server embeds.

`./gradlew :minestom:runDev` boots `EditorDevServer` (online mode, port 25566, flat world, map `dev`,
`/devmark <id>`, `/edit clear <map>` to remove every marker) in `minestom/run/`, which is gitignored. The `dev`
source set has `slf4j-simple` and `slf4j-jdk-platform-logging`, so Minestom and the receiver's
`System.Logger` warnings print to the console; it logs one line on start with the port, map and editor count. Config: `minestom/run/config/editor/config.yml`
(written on first start; add your UUID under `editors:` and run `/edit reload`).

### Rules

- Sidecar path: `polar.resolveSibling(<name without ".polar"> + ".markers.dat")`, so `desert.polar` →
  `desert.markers.dat`. `bind` throws if the path does not end in `.polar`; the `.polar` itself need not exist.
- The sidecar is gzipped NBT written with Adventure `BinaryTagIO` (`format: "backstage-markers"`,
  `version: 1`). Lists are ordered by id and props by key, so the same data always writes the same bytes.
- Saves are debounced 2 s per map on one virtual thread, written to `<file>.tmp` and moved over the file
  atomically. The first save in a process copies the old file to `<file>.bak`. `unbind`, `saveAll` and
  `shutdown` save synchronously.
- A sidecar with a newer `version` binds empty and read-only (`MapState.writable = false`) and is never
  written. A wrong `format` refuses to bind.
- Any change to the sidecar layout bumps `MarkerSidecar.VERSION` and adds an upgrade step in `MarkerFile.load`.
- Every refused edit gets one `EditRejected` with a plain reason: no bound map, another map, a read-only map,
  a marker type the registry narrowed away, a missing marker, a rename onto an existing id, or a marker that
  fails `MapMarkers.checkShape` (box min above max, polygon minY above maxY, non-finite point).

## mod

Lua build scripts and brushes: drop a `.lua` file in `<game dir>/backstage/scripts/`, `/bs use <name>` (`/hb` still works),
right-click with the wand (blaze rod).

Output: `mod/build/libs/backstage-1.0.0.jar` (LuaJ 3.0.1 and imgui-java nested via `include`). `./gradlew :mod:test`
runs `ScriptRuntimeTest` under fabric-loader-junit with Minecraft bootstrapped and a fake flat world; it
runs every bundled script in all four facings.

Install: copy the jar into a Fabric 26.2 client's `mods/` (needs Fabric API).

### Layout

- `src/main` (both sides): `Backstage` registers the two serverbound payloads; `server/ServerEdits`
  applies them (creative + gamemaster permission, or singleplayer owner) with flags
  `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS` (no physics) and keeps a 25-step undo
  stack per player.
- `src/client`: `lua/ScriptRuntime` (sandbox + API, reads through `WorldView`, writes into an `Edit`),
  `lua/Frame` (script↔world coords and state rotation), `BrushSession` (settings, background Lua thread,
  chunked send), `BackstageClient` (`/bs` commands, wand callbacks), `PreviewRenderer` (particle preview).
- `src/main/resources/assets/backstage/scripts/`: `API.md` (the script reference, kept current in the
  scripts folder on every launch) and the example scripts (copied once, never overwritten).

### Rules

- Scripts never touch the world. The client runs Lua, builds an `Edit`, and sends it in
  `EditChunkPayload`s (≤2000 entries; serverbound payloads cap at 32 KiB). The server is the only place
  blocks change. A server without the mod can preview but not apply.
- The script API is `API.md`. Change a Lua function → update `API.md` in the same change.
- Build frame: `+x` right, `+y` up, `+z` forward, states authored as if forward = north. Paint frame:
  world axes. Don't change either without updating `API.md` and the tests.
- Block ids sent over the wire are `Block.getId` (vanilla protocol ids), so a Minestom receiver can
  decode them directly.

### Editor

The mod nests `protocol` (`implementation` + `include project(':protocol')`, so the jar carries
`META-INF/jars/protocol-1.0.0.jar`) and speaks it on one plugin channel, `backstage:editor`.

- `editor/EditorPayload` (both sides) is the only payload type: raw message bytes, registered on
  `serverboundPlay()` and `clientboundPlay()`. Bodies are decoded in client code with `EditorCodec`, never in
  the payload codec.
- `client/editor/EditorClient` holds the state: `None`, `Handshaking`, `Available(serverName, map, bound,
  writable, markerCount, registry)`, `Denied(reason, serverProtocol)`. `EditorNetworking` wires it to Fabric.
- `Hello` is sent only when the server registers `backstage:editor` (Fabric's
  `ServerboundPlayChannelEvents.REGISTER`) and the state is `None` or `Denied`, so a server that never
  advertises the channel never sees editor traffic, and `/edit reload` on the server can welcome a player
  it denied before.
- `Welcome` shows a vanilla `SystemToast` "Editor available" with the server name; no render hooks, so it
  works on Lunar. Sync packets fill a staged set that replaces the markers at `SyncEnd`;
  `MarkerChanged`/`MarkerRemoved` patch both.
- `Denied(NO_PERMISSION)` is silent, outdated reasons print one chat line with both protocol numbers,
  `REVOKED` prints one line. Disconnect resets to `None`. A body that fails to decode logs once and resets
  to `None`.
- `/bs editor` prints the state, map, writable flag, marker counts per type, registry counts, the editor key
  and how many group blocks the overlay draws. `/bs editor resync` asks the server for `MapState` and a full
  sync. `/bs` alone prints three lines pointing at the editor key.

### Editor mode

`F8` (rebindable in Controls under "Backstage") opens editor mode when the state is `Available` and a map is
bound; otherwise a toast gives the reason (no editor here, still connecting, denied with the reason, no map
bound, or the Vulkan renderer). `Esc` or `F8` leaves; leaving restores the HUD, view bobbing, flying and fly
speed, and grabs the mouse again.

- Controls: the cursor is free and the HUD is hidden. Hold right mouse to look around, WASD/Space/Shift fly,
  scroll changes fly speed (0.01 to 0.25), Ctrl+scroll is for tools (brush radius, point yaw). Left click
  ray-casts from the cursor. `MouseHandlerMixin` and `KeyboardHandlerMixin` route input to the editor first;
  clicks and keys the UI uses never reach the game, movement keys and F2/F3/F11 do.
- Tools (`1`-`7`): Select (click, Shift add, Ctrl remove, drag a marquee), Point (click places on the hit face,
  drag the yaw ring, `R` snaps to 45°), Group brush (`+ Group` or pick a group in the Outliner; drag paints a
  sphere of radius `[`/`]`, Ctrl erases, Alt-click floods, "same block only"), Box (drag corner to corner,
  face handles resize, Enter makes a box region or fills a new group), Region polygon (click vertices, click
  the first or Enter closes, drag vertices and the top/bottom handles of a selected polygon), Move (axis
  gizmo snapping to blocks, Ctrl for free point moves, centre handle moves on the horizontal plane) and Brush
  (runs the selected Lua script at the cursor, Shift-click previews). The blaze rod wand only runs Lua
  brushes, outside editor mode.
- `Ctrl+Z`, `Ctrl+Y`, `Ctrl+Shift+Z` use `MarkerHistory` for every tool; View → History (`Ctrl+H`) lists the
  steps and jumps to one. `Delete` removes the selection, `Ctrl+D` duplicates it with the next free ids and
  switches to Move, `F` flies the camera to it, `Ctrl+K` opens the command palette (markers, tools, every
  menu action, fuzzy matched), `Ctrl+F` focuses the Outliner search. `EditorShortcuts` resolves keys: the
  toggle key always leaves, bare shortcuts on a movement key yield to movement, and Help lists conflicts.
- Panels: top bar (menus, connection pill, palette box), tool rail, tool options, Outliner (tree by type,
  search on id and tags, tag chips, eye toggle, Shift/Ctrl click, double-click flies to), Inspector (rename
  on Enter, drag fields for positions and box corners, Y for polygons, tags with autocomplete, typed props
  with a block picker, delete with confirm, shared tags across a multi-selection), status bar, tool hint and
  toasts. Edit rejections show in red in the status bar for 5 s. No chat lines while the editor is open.
- `<game dir>/backstage/editor.json` (`EditorLayout`) keeps the dock width, Outliner/Inspector split, open
  panels, overlay toggles, render radius, last tool, brush options and fly speed.
- File → "Save on server" is hidden: protocol v1 has no save packet and the receiver saves on its own.

### Editor UI

- Dear ImGui through imgui-java 1.92.7.1 (`imgui-java-binding`, `imgui-java-lwjgl3` and the Windows, Linux
  and macOS natives, all `include`d; their LWJGL is excluded and the game's LWJGL 3.4.1 is used).
  `MinecraftMixin` calls `EditorMode.frameRendered` right after `GameRenderer.render`; ImGui draws into the
  main render target before it is presented. It runs only while editor mode is open and no screen is
  showing; outside editor mode no ImGui frame is built.
- Needs the OpenGL backend (the 26.2 default). With Graphics API set to Vulkan there is no GL context and the
  editor refuses to open with a toast.
- `ImGuiImplGlfw` is initialised without its callbacks (the game keeps its own) and the editor forwards events
  to it; the monitor callback is restored after init. Raw GL is wrapped in `GlState` save/restore so the
  game's state cache stays valid.
- `Theme` holds every colour token from the mockup; `Kit` draws the widgets with the draw list (glass panels,
  chips, sliders, segmented controls, switches, meters, drag-to-scrub number fields, keycaps, tooltips);
  `Anim` keeps per-widget animation values keyed by ImGui id and uses frame time. Panels slide in with a
  30 ms stagger, the tool pill and segmented selection slide, toasts slide in and leave after 4 s.
- `FrameBlur` blits the frame to a quarter-size texture and runs a two-pass Gaussian (two iterations) once
  per frame; each panel draws its rectangle of that texture under a `rgba(16,18,26,0.62)` fill. If the blur
  shader fails the fill falls back to 0.86 alpha.
- Fonts are rasterised at `RasterizerDensity` 2 with oversampling and rebuilt when the GUI scale changes;
  sizes scale with GUI scale / 3 (clamped 0.75 to 2.5).
- Bundled fonts, subset to Latin and the icons used: Inter and JetBrains Mono (SIL OFL 1.1) and Tabler Icons
  webfont 3.49.0 (MIT). Licences are in `assets/backstage/licenses/`. Dear ImGui and imgui-java are MIT.

### Markers

- The client never edits `MarkerStore` ahead of the server. Every edit is sent as `UpsertMarker`,
  `RemoveMarker` or `RenameMarker` and shows up only when `MarkerChanged`/`MarkerRemoved` comes back. The one
  local preview is the group being painted (`GroupDraft`), sent 500 ms after the last change.
- `EditorClient.edit` checks every packet against the `EditorProtocol` limits (`MarkerCheck`, including the
  encoded size) before sending, and records it in a 50-step per-session undo stack (`MarkerHistory`) that
  is separate from the brush undo. An `EditRejected` for the last sent edit drops it from the stack. The
  stack clears on disconnect, `Denied` and map change.

### Rendering

- `WorldOverlay` draws markers on `LevelRenderEvents.END_MAIN` with its own GL program into the main target
  (colour and depth, reverse-Z `GEQUAL` test). Lines are screen-space quads expanded in the vertex shader
  and drawn twice (a wide additive glow, then the crisp line); the selection uses the accent colour and
  marching dashes, the marker under the cursor a lighter tint. Holding `Alt` adds see-through lines.
- Groups merge shared faces (`GroupOutline`): only exposed faces get the translucent fill and only edges
  where the surface ends or bends are drawn, joined into long segments. Regions draw edges and translucent
  faces; points a small diamond with a yaw arrow.
- Meshes are cached per marker and rebuilt only when the marker changes. Markers past the render radius
  (default 128, 16 to 512) are skipped unless selected; group blocks drawn are capped at 60 000 in total, so
  a group at the 3 500-block protocol cap is always drawn whole. The frame rate on a 3 500-block group is
  still owed in-game.
- Tool previews: the brush sphere at the cursor, the box being dragged, polygon edges to the cursor, a ghost
  point and a ghost of the selection while moving. Labels, the move gizmo and the handles are drawn by ImGui
  at the projected positions; labels are glass pills that fade towards the render radius.
- Only drawn while editor mode is open. No display entities.

### Not done yet

- Session settings for Lua scripts (params, seed) reset on restart.
- Undo doesn't restore block-entity data (chest contents etc).
- Models, kinds and forms (later EDITOR phases).
