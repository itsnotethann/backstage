description = "Round tower with a spiral stair, arrow slits and crenellations"

params = {
  radius = 4,
  height = 18,
  wall = "stone_bricks",
  accent = "mossy_stone_bricks",
  stair = "spruce_planks",
}

function build()
  local r, h = params.radius, params.height
  local cx, cz = 0, r + 1   -- centre the tower in front of the player

  -- foundation and walls, mossy near the bottom
  cylinder(cx, -1, cz, r, 1, "cobblestone")
  for y = 0, h - 1 do
    for a = 0, 359, 2 do
      local x = cx + math.floor(r * math.cos(math.rad(a)) + 0.5)
      local z = cz + math.floor(r * math.sin(math.rad(a)) + 0.5)
      local mossy = y < 4 and rand() < 0.35 - y * 0.08
      set(x, y, z, mossy and params.accent or params.wall)
    end
  end
  cylinder(cx, 0, cz, r - 1, h, AIR)

  -- spiral stair around a central pillar
  fill(cx, 0, cz, cx, h - 1, cz, "stripped_spruce_log")
  local steps = {{1, 0}, {1, 1}, {0, 1}, {-1, 1}, {-1, 0}, {-1, -1}, {0, -1}, {1, -1}}
  for y = 0, h - 2 do
    local s = steps[(y % #steps) + 1]
    set(cx + s[1], y, cz + s[2], params.stair)
    set(cx + s[1] * 2, y, cz + s[2] * 2, params.stair)
  end

  -- floor at the top and battlements
  cylinder(cx, h, cz, r, 1, params.wall)
  set(cx, h, cz, "spruce_trapdoor[half=top]")
  for a = 0, 359, 2 do
    local x = cx + math.floor(r * math.cos(math.rad(a)) + 0.5)
    local z = cz + math.floor(r * math.sin(math.rad(a)) + 0.5)
    if (x + z) % 2 == 0 then
      set(x, h + 1, z, params.wall)
    end
  end

  -- arrow slits every 5 blocks, on the four sides
  for y = 3, h - 3, 5 do
    set(cx + r, y, cz, AIR); set(cx + r, y + 1, cz, AIR)
    set(cx - r, y, cz, AIR); set(cx - r, y + 1, cz, AIR)
    set(cx, y, cz + r, AIR); set(cx, y + 1, cz + r, AIR)
  end

  -- doorway facing the player
  set(cx, 0, cz - r, AIR)
  set(cx, 1, cz - r, AIR)
end
