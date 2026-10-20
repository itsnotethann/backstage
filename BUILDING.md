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

`./gradlew :minestom:runDev` boots `EditorDevServer` (offline mode, port 25566, flat world, map `dev`,
`/devmark <id>`, `/edit clear <map>` to remove every marker) in `minestom/run/`, which is gitignored. Config: `minestom/run/config/editor/config.yml`
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

Output: `mod/build/libs/backstage-1.0.0.jar` (LuaJ 3.0.1 nested via `include`). `./gradlew :mod:test`
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
  chunked send), `BackstageClient` (`/bs` commands, wand callbacks), `PreviewRenderer` (particle preview,
  no render hooks so it works on Lunar).
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
- `/bs editor` prints the state, map, writable flag, marker counts per type, registry counts and overlay
  entity count. `/bs editor resync` asks the server for `MapState` and a full sync.

### Markers

- The client never edits `MarkerStore` ahead of the server. Every edit is sent as `UpsertMarker`,
  `RemoveMarker` or `RenameMarker` and shows up only when `MarkerChanged`/`MarkerRemoved` comes back. The one
  local preview is the group being painted (`GroupDraft`, drawn magenta), which is not in the store.
- `EditorClient.edit` checks every packet against the `EditorProtocol` limits (`MarkerCheck`, including the
  encoded size) before sending, and records it in a 50-step per-session undo stack (`MarkerHistory`) that
  is separate from the brush undo. An `EditRejected` for the last sent edit drops it from the stack and prints
  one red chat line. The stack clears on disconnect, `Denied` and map change.
- `/bs tool <select|point|group|region|off>` picks the wand tool; `off` (the default) leaves the wand on Lua
  brushes exactly as before. With a tool on, the action bar shows the tool and the marker under the
  crosshair (type, id, tags, prop count).
  - point: right-click a block places `point_<n>` on the hit face with your yaw; sneak + right-click a point
    picks it up, the next right-click puts it down.
  - group: `/bs group new <id>` or `/bs group edit <id>`, then hold right-click and drag (sneak to remove).
    `/bs group box` (next two clicks add every non-air block in the box), `/bs group fill` (next click adds
    connected blocks of the same type, 64 blocks out at most), `/bs group done`. Capped at 3 500 blocks with a
    chat warning; `UpsertMarker` goes out 500 ms after the last change, never per block.
  - region: two right-clicks make a box `region_<n>`; `/bs region box [id]`, `/bs region poly [id]` then a
    click per vertex and `/bs region done`. Y defaults to the clicked heights; `/bs region height <minY>
    <maxY>` sets it for the region being drawn, or changes the selected region.
  - select: right-click picks the nearest point along the crosshair, else a group block, else the region you
    stand in or look at.
- On the selected marker: `/bs rename <newId>`, `/bs delete`, `/bs tag add|remove <tag>`,
  `/bs prop set <key> <str|num|bool|block|item> <value>`, `/bs prop remove <key>`, `/bs info` (while a tool is
  on; with the tool off `/bs info` still describes the brush script). `/bs select <type> <id>` selects by id.
- `/bs list <points|groups|regions|all> [tag]` is the outliner (`/bs list` alone still lists scripts): each
  line selects the marker and highlights it for 10 s on click, and `[tp]` only suggests `/tp x y z`.
- `/bs editor undo` and `/bs editor redo` step through marker edits.

### Rendering

- `MarkerRenderer` draws the store as client-only display entities (negative ids, added straight to the
  `ClientLevel`, never sent to the server); no render hooks, so it works on Lunar. Points: a small glowing
  block with a nose towards its yaw, plus a text label (id, tags). Groups: a glowing block display scaled
  1.002 over each block. Regions: thin glowing bars along every edge and a label at the centre. Green
  points, orange groups, cyan regions, yellow for the selection, magenta for the group being painted.
- Only markers within the overlay radius are drawn (`/bs editor radius <n>`, default 96). Group block
  displays are capped at 1 024 per group and 4 096 in total, nearest groups first; hitting a cap logs once.
- Each refresh diffs the store against what is drawn and rebuilds only changed markers. Everything is
  removed on disconnect, `Denied`, a map change or a new level. `/bs editor show
  <points|groups|regions|labels> <on|off>` toggles each overlay.

### Not done yet

- Session settings (script, params, radius, wand) reset on restart.
- Undo doesn't restore block-entity data (chest contents etc).
- Custom editor screens, models, kinds and forms (later EDITOR phases).
