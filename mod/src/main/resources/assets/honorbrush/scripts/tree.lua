description = "Big organic tree: tapered trunk, branches, leaf clusters"

params = {
  height = 14,
  log = "oak_log",
  leaves = "oak_leaves[persistent=true]",
  branches = 5,
}

local function blob(cx, cy, cz, r)
  local ir = math.ceil(r)
  for x = -ir, ir do
    for y = -ir, ir do
      for z = -ir, ir do
        local d = math.sqrt(x * x + y * y * 1.6 + z * z)
        if d <= r + noise((cx + x) / 3.1, (cy + y) / 3.1, (cz + z) / 3.1) * 1.2 then
          if not string.find(get(cx + x, cy + y, cz + z), "_log") then
            set(cx + x, cy + y, cz + z, params.leaves)
          end
        end
      end
    end
  end
end

function build()
  local h = params.height
  local log = params.log

  -- trunk, 2x2 at the base thinning to 1x1
  for y = 0, h do
    set(0, y, 0, log)
    if y < h * 0.4 then
      set(1, y, 0, log); set(0, y, 1, log); set(1, y, 1, log)
    end
  end
  -- roots
  for _, d in ipairs({{-1, 0}, {2, 1}, {0, -1}, {1, 2}}) do
    set(d[1], 0, d[2], log)
  end

  -- branches angled up and out, leaf cluster at each tip
  for b = 1, params.branches do
    local angle = (b / params.branches) * math.pi * 2 + rand() * 0.8
    local startY = math.floor(h * 0.5 + rand() * h * 0.35)
    local len = 3 + rand(3)
    local tx = math.floor(math.cos(angle) * len + 0.5)
    local tz = math.floor(math.sin(angle) * len + 0.5)
    local ty = startY + 2 + rand(2)
    local axis = math.abs(tx) > math.abs(tz) and "x" or "z"
    line(0, startY, 0, tx, ty, tz, log .. "[axis=" .. axis .. "]")
    blob(tx, ty + 1, tz, 2.5 + rand())
  end

  blob(0, h + 1, 0, 3.5)
end
