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
