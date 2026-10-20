description = "Paint: re-skin exposed surfaces with a weighted block palette"

params = {
  radius = 6,
  depth = 1,   -- how many blocks below the surface to recolour
}

local palette = {
  stone_bricks = 6,
  cracked_stone_bricks = 2,
  mossy_stone_bricks = 2,
  andesite = 1,
}

function paint(x, y, z, wx, wy, wz)
  if not isSolid(x, y, z) then return nil end
  -- only recolour blocks within `depth` of an air block above
  for d = 1, params.depth do
    if isAir(x, y + d, z) then
      return pick(palette)
    end
  end
  return nil
end
