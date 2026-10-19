description = "Glass dome on a stone ring, with a door facing you"

params = {
  radius = 10,
  glass = "light_blue_stained_glass",
  frame = "polished_andesite",
  floor = "smooth_stone",
}

function build()
  local r = params.radius
  local cz = r + 1

  -- shell: frame ribs every 45 degrees, glass between
  local ir = math.ceil(r)
  for x = -ir, ir do
    for y = 0, ir do
      for z = -ir, ir do
        local d = x * x + y * y + z * z
        if d <= (r + 0.5) ^ 2 and d >= (r - 0.5) ^ 2 then
          local angle = math.deg(math.atan2(z, x)) % 45
          local rib = angle < 4 or angle > 41 or y == 0
          set(x, y, cz + z, rib and params.frame or params.glass)
        end
      end
    end
  end
  cylinder(0, -1, cz, r, 1, params.floor)

  -- doorway on the player's side
  fill(-1, 0, cz - r - 1, 1, 2, cz - r + 1, AIR)
end
