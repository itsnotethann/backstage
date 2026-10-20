description = "Small timber-frame house with a stair roof; door faces you"

params = {
  width = 7,     -- along x (odd works best)
  depth = 9,     -- along z
  wall = "white_terracotta",
  beam = "spruce_log",
  roof = "dark_oak",   -- wood type for stairs/planks
}

function build()
  local w, d = params.width, params.depth
  local hw = math.floor(w / 2)
  local x1, x2 = -hw, hw
  local z1, z2 = 0, d - 1
  local wallH = 4

  -- floor and walls
  fill(x1, -1, z1, x2, -1, z2, "spruce_planks")
  box(x1, 0, z1, x2, wallH - 1, z2, params.wall)
  fill(x1 + 1, 0, z1 + 1, x2 - 1, wallH - 1, z2 - 1, AIR)

  -- corner posts and a beam ring
  for _, c in ipairs({{x1, z1}, {x2, z1}, {x1, z2}, {x2, z2}}) do
    fill(c[1], 0, c[2], c[1], wallH - 1, c[2], params.beam)
  end
  fill(x1, wallH - 1, z1, x2, wallH - 1, z1, params.beam .. "[axis=x]")
  fill(x1, wallH - 1, z2, x2, wallH - 1, z2, params.beam .. "[axis=x]")
  fill(x1, wallH - 1, z1, x1, wallH - 1, z2, params.beam .. "[axis=z]")
  fill(x2, wallH - 1, z1, x2, wallH - 1, z2, params.beam .. "[axis=z]")

  -- door (front wall faces the player) and windows
  set(0, 0, z1, "spruce_door[facing=north,half=lower]")
  set(0, 1, z1, "spruce_door[facing=north,half=upper]")
  for z = z1 + 2, z2 - 2, 3 do
    set(x1, 1, z, "glass_pane[north=true,south=true]")
    set(x2, 1, z, "glass_pane[north=true,south=true]")
  end

  -- gable roof running front to back: slopes rise toward the centre x
  local wood = params.roof
  for i = 0, hw + 1 do
    local y = wallH + i
    local lx, rx = x1 - 1 + i, x2 + 1 - i
    if lx > rx then break end
    for z = z1 - 1, z2 + 1 do
      if lx == rx then
        set(lx, y, z, wood .. "_slab[type=bottom]")
      else
        set(lx, y, z, wood .. "_stairs[facing=east]")   -- walk up toward +x
        set(rx, y, z, wood .. "_stairs[facing=west]")   -- walk up toward -x
      end
    end
    -- fill the gable ends
    if lx + 1 <= rx - 1 then
      fill(lx + 1, y, z1, rx - 1, y, z1, wood .. "_planks")
      fill(lx + 1, y, z2, rx - 1, y, z2, wood .. "_planks")
    end
  end

  -- lantern by the door
  set(1, 2, z1 - 1, "lantern[hanging=false]")
  set(1, 1, z1 - 1, "spruce_fence")
end
