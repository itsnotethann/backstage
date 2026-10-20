# Backstage script API

Backstage runs Lua 5.2 scripts (LuaJ) from this folder. Every `.lua` file here is a
script; subfolders work too (`/bs use castles/keep`). Files are re-read on every run,
so saving a file is all it takes to update it.

In game: `/bs use <script>`, then right-click a block with the wand (blaze rod by
default). Sneak + right-click previews with particles; `/bs apply` places the preview.
`/bs undo` reverts the last edit (25 steps). `/bs set <param> <value>` overrides a param.

## A script is one of two kinds

**Build script**: defines `build()`. Called once. Coordinates are relative to the
block in front of the clicked face (`0,0,0`), in a frame that turns with the player:

- `+x` = right, `+y` = up, `+z` = forward (away from the player)
- Write block states as if **forward is north**: `oak_stairs[facing=north]` faces away
  from the player, `facing=south` faces back at them, `east` is right, `west` is left.
  Positions and states are rotated together when the player faces another way.
  (`/bs rotate false` pins the frame to the player facing north.)

**Paint script**: defines `paint(x, y, z, wx, wy, wz)`. Called for every block in a
sphere around the clicked block. `x, y, z` are offsets from the centre (world axes, no
rotation), `wx, wy, wz` are world coordinates (use these for noise so dabs line up).
Return a block string to place, or `nil` to leave the block alone. The radius is
`params.radius` if the script declares it, otherwise `/bs radius` (default 5, max 128).

If a script defines both, `build` wins.

## Header (optional but recommended)

```lua
description = "Round stone tower with a spiral stair"
params = {
  radius = 4,             -- numbers, booleans and strings; type comes from the default
  height = 20,
  wall   = "stone_bricks",
  windows = true,
}
```

`/bs info` shows the description and params; `/bs set height 30` overrides one
(it is parsed as the default's type). Read them as `params.height`.

## Blocks

Blocks are command-syntax strings: `"stone"`, `"minecraft:oak_log[axis=x]"`,
`"oak_stairs[facing=north,half=top]"`. Unspecified properties use the block's defaults.
`"air"` (or the constant `AIR`) clears. A bad block string stops the script with an error.

## Functions

All coordinates are in the script's frame (see above) and are floored to integers.

| Function | Does |
|---|---|
| `set(x, y, z, block)` | Place one block. `nil` block = no-op. Later writes to the same block win. |
| `get(x, y, z)` | Full state string, e.g. `"minecraft:oak_stairs[facing=north,half=bottom,...]"`. Sees this script's own earlier writes. |
| `blockAt(x, y, z)` | Just the id, e.g. `"minecraft:grass_block"`. |
| `isAir(x, y, z)` / `isSolid(x, y, z)` | Booleans. |
| `highest(x, z)` | Y (relative) of the topmost non-air block in that column. |
| `fill(x1,y1,z1, x2,y2,z2, block)` | Solid cuboid (corners inclusive, any order). |
| `box(x1,y1,z1, x2,y2,z2, block)` | Hollow cuboid: just the six faces. Fill with `AIR` first to clear inside. |
| `sphere(cx,cy,cz, r, block [, hollow])` | Ball (or one-block shell). |
| `cylinder(cx,y,cz, r, height, block [, hollow])` | Vertical cylinder from `y` up. |
| `line(x1,y1,z1, x2,y2,z2, block)` | Straight line. |
| `noise(x [, y, z])` | Smooth gradient noise, about -1..1. **Scale inputs down** (`wx/24`): it is 0 at integers. |
| `fbm(x, y, z [, octaves=4])` | Layered noise, about -1..1, more detail. |
| `rand()` / `rand(n)` / `rand(a, b)` | Same as `math.random`. Seeded per run. |
| `pick({"stone","andesite"})` | Random element. Weighted: `pick({stone=3, cobblestone=1})`. |
| `print(...)` | Prints to the player's chat (first 30 lines). |

Also: `seed` (this run's seed), `params`, standard `math`, `string`, `table`, `bit32`.
Not available: `io`, `os`, `require`, `load`, `dofile`, `debug`.

## Limits

- 30 seconds of run time, 4,000,000 blocks per edit. `/bs cancel` stops a running script.
- Changes ignore physics: no block updates, so stairs/fences/panes keep exactly the
  states you write (set `east=true` etc. on fences and panes yourself), sand doesn't fall,
  water doesn't flow until something nearby updates.
- Undo restores block states but not container contents or other block-entity data.
- Placing needs creative mode and op (or singleplayer). On a server without Backstage,
  scripts still run and preview, but `apply` is refused.

## Patterns

```lua
-- Build: a 5x5 hut, door facing the player
function build()
  fill(-2, 0, 0, 2, 3, 4, "oak_planks")
  fill(-1, 0, 1, 1, 2, 3, AIR)                -- hollow it
  set(0, 0, 0, "oak_door[facing=north,half=lower]")
  set(0, 1, 0, "oak_door[facing=north,half=upper]")
end
```

```lua
-- Paint: noisy blob that only replaces solid blocks
params = { radius = 6, block = "moss_block" }
function paint(x, y, z, wx, wy, wz)
  if not isSolid(x, y, z) then return nil end
  if fbm(wx / 12, wy / 12, wz / 12) > 0 then return params.block end
end
```

Facing cheat sheet (frame-north = forward = `+z`):

- **Stairs**: `facing` is the direction you walk **up** them. A slope rising toward `+z`
  is `facing=north`; rising toward `-z` is `facing=south`. `half=top` flips them upside down.
- **Doors, beds, furnaces, chests**: `facing` is the direction the player looked when
  placing. A door you walk through going forward is `facing=north`.
- **Logs/pillars**: `axis=x|y|z`. Rotation swaps `x` and `z` for you.
