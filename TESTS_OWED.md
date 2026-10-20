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
| P5 markers | As an editor on `./gradlew :minestom:runDev`, `/bs tool point`, right-click a block, then leave and rejoin | A green point with a `point_1` label is drawn again after rejoining |
| P5 markers | `/bs group new flag`, hold right-click and drag across about 200 blocks, wait a second, then `/edit dump dev` | The blocks glow orange (magenta while painting) and the dump lists group `flag` with 200 blocks |
| P5 markers | `/bs group box`, click two corners of a box over 3 500 solid blocks | Chat warns the group is capped at 3 500; no more blocks are added |
| P5 markers | `/bs tool region`, click two corners; then `/bs region poly`, click 5 vertices, `/bs region done` | Both outlines are drawn as cyan bars with a label at the centre |
| P5 markers | Select a marker, then `/bs tag add boss`, `/bs prop set hp num 500`, `/bs rename boss_spawn`; `/bs delete` another; restart `runDev` and rejoin | Tag, prop, new id and the deletion all survive the restart (`/bs info` and `/edit dump dev`) |
| P5 markers | `/bs delete` a marker, `/bs editor undo`, then `/bs editor redo` | Undo brings the marker back with its tags and props; redo removes it again |
| P5 markers | `/bs list all`, click a line, then click `[tp]` | The marker is selected and highlighted yellow for 10 s; `[tp]` only fills `/tp x y z` into the chat box |
| P5 markers | `/bs editor show groups off`, then `on`; `/bs editor radius 16` | Group overlay disappears and returns; markers further than 16 blocks are no longer drawn |
| P5 markers | Join the dev server with the mod as a player who is not an editor | No overlays are drawn; `/bs tool point` then a click says the editor is not available, and nothing changes on the server |
| P5 markers | `/bs tool off`, `/bs use tower`, right-click with the wand | The Lua brush previews and builds exactly as before |
| P5 markers | Repeat the point, group, region, rename and undo checks on Lunar Client | Same results; overlays render without render hooks |
