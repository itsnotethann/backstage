# Building Backstage

One Gradle multi-project build, Gradle 9.7.1 wrapper (Loom 1.18 needs 9.7), Java 25.

```bash
./gradlew build
```

| Subproject | Artifact | What |
|---|---|---|
| `protocol/` | `net.honormc:backstage-protocol` | Wire protocol shared by mod and servers. |
| `minestom/` | `net.honormc:backstage-minestom` | Minestom receiver. |
| `mod/` | `mod/build/libs/honorbrush-1.0.0.jar` | Fabric 26.2 client mod. |

## mod

Lua build scripts and brushes: drop a `.lua` file in `<game dir>/honorbrush/scripts/`, `/hb use <name>`,
right-click with the wand (blaze rod).

Output: `mod/build/libs/honorbrush-1.0.0.jar` (LuaJ 3.0.1 nested via `include`). `./gradlew :mod:test`
runs `ScriptRuntimeTest` under fabric-loader-junit with Minecraft bootstrapped and a fake flat world; it
runs every bundled script in all four facings.

Install: copy the jar into a Fabric 26.2 client's `mods/` (needs Fabric API).

### Layout

- `src/main` (both sides): `HonorBrush` registers the two serverbound payloads; `server/ServerEdits`
  applies them (creative + gamemaster permission, or singleplayer owner) with flags
  `UPDATE_CLIENTS | UPDATE_KNOWN_SHAPE | UPDATE_SUPPRESS_DROPS` (no physics) and keeps a 25-step undo
  stack per player.
- `src/client`: `lua/ScriptRuntime` (sandbox + API, reads through `WorldView`, writes into an `Edit`),
  `lua/Frame` (script↔world coords and state rotation), `BrushSession` (settings, background Lua thread,
  chunked send), `HonorBrushClient` (`/hb` commands, wand callbacks), `PreviewRenderer` (particle preview,
  no render hooks so it works on Lunar).
- `src/main/resources/assets/honorbrush/scripts/`: `API.md` (the script reference, kept current in the
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
