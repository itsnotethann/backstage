# Tests owed

Checks that need a real Minecraft client.

| task | what to do | expected |
|---|---|---|
| P1 rename | Install the jar in a Fabric 26.2 client and run `/bs list` and `/hb list` | Both list the bundled scripts |
| P1 rename | Put a script in `<game>/honorbrush/scripts/` with no `<game>/backstage/`, then start the client | Folder is moved to `<game>/backstage/` and `/bs use <script>` finds the script |
| P3 receiver | Start `./gradlew :minestom:runDev` and join `localhost:25566` with the mod as a player listed in `editors:` (op level 4 works the same on a host that can op) | "Editor available" toast with server name `Build` |
| P3 receiver | Join the dev server with the mod as a player who is not op and not in `editors:`, then run `/bs editor` | No toast; `/bs editor` reports denied (no permission) |
| P3 receiver | While joined without permission, add your UUID under `editors:` in `minestom/run/config/editor/config.yml` and have a second, already-allowed client (offline mode, any name) run `/edit reload` | First client gets the toast without rejoining |
| P3 receiver | As an editor run `/devmark a`, then `/bs editor` | Point count for map `dev` goes up by one |
| P3 receiver | After `/devmark a`, stop and restart `runDev`, then run `/edit dump dev` | Dump lists point `a`; `minestom/run/dev.markers.dat` exists and no `.tmp` is left |
| P3 receiver | Join the dev server with a vanilla client (no mod) | Joins normally, no errors in the server log |
| P4 mod handshake | With the mod installed, join `./gradlew :minestom:runDev` (`localhost:25566`) as a player allowed to edit (op level 4 or listed in `editors:`) | "Editor available" toast with the server name; `/bs editor` shows available, map `dev`, writable |
| P4 mod handshake | With the mod installed, join the dev server as a player who is not op and not in `editors:`, then run `/bs editor` | No toast and no chat line; `/bs editor` says denied (no permission) |
| P4 mod handshake | Join the dev server with a vanilla client (no mod) | Joins and plays normally; no errors in the server or client log |
| P4 mod handshake | With the mod installed, join any server without the receiver (e.g. a vanilla server) and run `/bs editor` | No toast; `/bs editor` says the server has no Backstage editor |
| P6 editor mode | As an editor on `./gradlew :minestom:runDev`, press `F8` | Panels slide in, HUD and crosshair are hidden, the cursor is free; hold right mouse to look, WASD/Space/Shift fly, scroll changes fly speed |
| P6 editor mode | Press `F8` again, then `F8` and `Esc` | Each leaves editor mode: HUD back, mouse grabbed, flying and fly speed as before, no pause menu |
| P6 editor mode | Join the dev server as a player who is not an editor and press `F8` | Toast "Editor unavailable: Editor access denied: you do not have editor permission." and nothing opens |
| P6 editor mode | Join a server without the receiver and press `F8` | Toast says the server has no Backstage editor |
| P6 editor mode | Press `2`, click a block, drag the yaw ring, press `R`; `6`, drag an arrow; `Delete`; then restart `runDev` and rejoin | Point placed, turned, snapped, moved and deleted with no commands; the final state survives the restart |
| P6 editor mode | Press `3`, `+ Group`, type `flag`, Enter, drag across blocks, Ctrl-drag over some, Alt-click a block; restart `runDev` | Blocks highlight as painted with one merged outline; erased ones drop; fill adds connected blocks; meter counts up to 3 500; group survives the restart |
| P6 editor mode | Press `4`, drag from one block to another, drag a face handle, Enter (Region, then again with Group) | A box region is created, then a group of the solid blocks in the box; both survive a restart |
| P6 editor mode | Press `5`, click five blocks, click the first; select the region and drag a vertex and the top handle | Polygon region created and reshaped; survives a restart |
| P6 editor mode | Press `7`, pick `tower` in the options, Shift-click a block, Apply preview; click a block | Preview particles, then the tower is placed; brush messages appear as toasts, not chat |
| P6 editor mode | Type in the Outliner search, click a tag chip, Shift-click two rows, double-click a row, press `F` | List filters by id and tag; both rows selected; camera flies to the marker; `F` flies to the selection |
| P6 editor mode | In the Inspector rename a marker (Enter), scrub X, add a tag (Tab completes), add props of each type, pick a block, delete a prop, Delete then Confirm | Every edit is applied by the server and shows in `/edit dump dev`; delete asks for confirm |
| P6 editor mode | Shift-select three markers, add a tag in the Inspector, remove a shared tag | All three get and lose the tag |
| P6 editor mode | `6` with a point selected, drag the X arrow; select a group and drag Y; hold Ctrl while dragging a point | Point and group move in whole blocks; Ctrl moves the point freely |
| P6 editor mode | Make edits with four tools, press `Ctrl+Z` four times, `Ctrl+Y` twice, open View → History and click the first step | Each tool's edit undoes and redoes; History jumps back to that step |
| P6 editor mode | `Ctrl+K`, type part of a marker id, Enter; `Ctrl+K`, type "undo", Enter | Palette jumps to the marker; runs Undo |
| P6 editor mode | Paint a 3 500-block group, fly around it with F3 open | Stays at 60 fps or more with the outline and fill drawn; note the fps |
| P6 editor mode | Hold `Alt` with a marker behind a wall | Its outline shows through the wall |
| P6 editor mode | Change GUI scale 2 → 4 while in editor mode | UI and text scale with it and stay crisp |
| P6 editor mode | Close the game in editor mode with the dock resized and labels off, then reopen and press `F8` | Layout and options come back from `backstage/editor.json` |
| P6 editor mode | Set Graphics API to Vulkan, restart, press `F8` | Toast says the editor needs the OpenGL renderer |
| P6 editor mode | Join the dev server with a vanilla client (no mod) | Joins and plays normally |
| P6 editor mode | Outside editor mode, hold the blaze rod, `/bs use tower`, right-click | The Lua brush previews and builds as before |
| P6 dev server | Start `./gradlew :minestom:runDev` | Console shows Minestom startup and one Backstage line with port 25566, map `dev` and the editor count; a client with a real account joins in online mode |
