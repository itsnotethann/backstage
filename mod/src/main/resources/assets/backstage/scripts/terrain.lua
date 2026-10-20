description = "Paint: raise or carve rolling hills with noise (grass, dirt, stone layers)"

params = {
  radius = 12,
  scale = 18,        -- bigger = broader hills
  strength = 6,      -- max height change in blocks
  carve = false,     -- true digs valleys instead
}

function paint(x, y, z, wx, wy, wz)
  -- falloff so the edge of the brush blends into the ground
  local r = params.radius
  local dist = math.sqrt(x * x + z * z) / r
  if dist > 1 then return nil end
  local falloff = 1 - dist * dist

  local n = (fbm(wx / params.scale, 0, wz / params.scale, 4) + 1) / 2
  local lift = math.floor(n * params.strength * falloff + 0.5)
  if lift <= 0 then return nil end

  local ground = highest(x, z)
  if params.carve then
    if y > ground - lift and y <= ground then return AIR end
    if y == ground - lift and isSolid(x, y, z) then return "grass_block" end
    return nil
  end

  local top = ground + lift
  if y > ground and y <= top then
    if y == top then return "grass_block" end
    if y >= top - 3 then return "dirt" end
    return "stone"
  end
  if y == ground and isSolid(x, y, z) and lift > 0 then
    return "dirt"   -- the old grass is now buried
  end
end
