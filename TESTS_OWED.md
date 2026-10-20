# Tests owed

Checks that need a real Minecraft client.

| task | what to do | expected |
|---|---|---|
| P1 rename | Install the jar in a Fabric 26.2 client and run `/bs list` and `/hb list` | Both list the bundled scripts |
| P1 rename | Put a script in `<game>/honorbrush/scripts/` with no `<game>/backstage/`, then start the client | Folder is moved to `<game>/backstage/` and `/bs use <script>` finds the script |
