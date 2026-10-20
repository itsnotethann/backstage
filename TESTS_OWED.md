# Tests owed

Checks that need a real Minecraft client.

| task | what to do | expected |
|---|---|---|
| P1 rename | Install the jar in a Fabric 26.2 client and run `/bs list` and `/hb list` | Both list the bundled scripts |
| P1 rename | Put a script in `<game>/honorbrush/scripts/` with no `<game>/backstage/`, then start the client | Folder is moved to `<game>/backstage/` and `/bs use <script>` finds the script |
| P4 mod handshake | With the mod installed, join `./gradlew :minestom:runDev` (`localhost:25566`) as a player allowed to edit (op level 4 or listed in `editors:`) | "Editor available" toast with the server name; `/bs editor` shows available, map `dev`, writable |
| P4 mod handshake | With the mod installed, join the dev server as a player who is not op and not in `editors:`, then run `/bs editor` | No toast and no chat line; `/bs editor` says denied (no permission) |
| P4 mod handshake | Join the dev server with a vanilla client (no mod) | Joins and plays normally; no errors in the server or client log |
| P4 mod handshake | With the mod installed, join any server without the receiver (e.g. a vanilla server) and run `/bs editor` | No toast; `/bs editor` says the server has no Backstage editor |
