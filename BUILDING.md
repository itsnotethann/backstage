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
`/devmark <id>`) in `minestom/run/`, which is gitignored. Config: `minestom/run/config/editor/config.yml`
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

### Not done yet

- No Minestom receiver yet, so multiplayer apply only works on a Fabric server running the mod.
- Session settings (script, params, radius, wand) reset on restart.
- Undo doesn't restore block-entity data (chest contents etc).
