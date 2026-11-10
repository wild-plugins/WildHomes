# WildHomes

Homes for Paper and Folia, managed entirely through Paper dialogs, with cross-server teleports on proxy networks.

- Paper / Folia 26.1+, Java 25
- Works out of the box on a single server with SQLite, no other plugins needed
- Optional: PlaceholderAPI, MariaDB/MySQL for networks

## Dialogs

`/home` opens the homes dialog: one button per slot, four per row.

| Button | Meaning |
|---|---|
| Home (with its icon) | Opens the home: Teleport, Change icon, Rename, Delete, Back |
| New Home | Sets a home at your position in that slot |
| Locked | Above your home limit; the tooltip is set in the language file |
| Show more | Shows every slot up to `homes.max-slots` |

Change icon lists every item with its sprite and a search field. Rename uses a text field. Delete asks for confirmation (`dialogs.delete.confirm`).

## Commands

| Command | |
|---|---|
| `/home` | Open the homes dialog |
| `/home <name>` | Teleport to a home |
| `/sethome [name]` | Set a home, or move an existing one with that name |
| `/delhome <name>` | Delete a home |
| `/home-admin open <player>` | Manage the homes of any player, online or offline |
| `/home-admin tp <player> <home>` | Teleport to a home of a player |
| `/home-admin delete <player> <home>` | Delete a home of a player |
| `/home-admin reload` | Reload `config.yml` and the language |

`/wild-homes-admin` is an alias of `/home-admin`. Aliases are set in `config.yml` -> `commands` (restart needed).

## Permissions

| Permission | Default | |
|---|---|---|
| `wildhomes.use` | true | `/home`, the dialog and teleporting |
| `wildhomes.sethome` | true | Set and move homes |
| `wildhomes.delhome` | true | Delete homes |
| `wildhomes.rename` | true | Rename homes |
| `wildhomes.icon` | true | Change home icons |
| `wildhomes.crossserver` | true | Teleport to homes on other servers |
| `wildhomes.amount.<n>` | - | May have `<n>` homes, the highest wins. Without one: `homes.default-amount` |
| `wildhomes.amount.unlimited` | op | As many homes as `homes.max-slots` |
| `wildhomes.bypass.warmup` | op | No countdown |
| `wildhomes.bypass.cooldown` | op | No cooldown |
| `wildhomes.bypass.world` | op | Set homes in `homes.disabled-worlds` |
| `wildhomes.admin.view` | op | `/home-admin open`, see other players' homes |
| `wildhomes.admin.teleport` | op | Teleport to other players' homes |
| `wildhomes.admin.set` | op | Set or move homes for other players |
| `wildhomes.admin.rename` | op | Rename other players' homes |
| `wildhomes.admin.icon` | op | Change icons of other players' homes |
| `wildhomes.admin.delete` | op | Delete other players' homes |
| `wildhomes.admin.reload` | op | `/home-admin reload` |
| `wildhomes.admin` | op | All `wildhomes.admin.*` nodes |

Example: helpers get `admin.view` and `admin.teleport`, moderators additionally `admin.rename` and `admin.delete`.

## Files

| File | Content |
|---|---|
| `config.yml` | Every value: limits, teleport, dialog layout, aliases |
| `database.yml` | `sqlite` or `mariadb`, connection, table prefix |
| `lang/english.yml`, `lang/german.yml` | Every message and dialog text (MiniMessage) |

Switch the language with `language: german` and `/home-admin reload`. For another language copy `english.yml`, translate it and set `language` to its file name.

## Cross-server

1. Every server uses the same MariaDB database (`database.yml` -> `type: mariadb`, same `table-prefix`).
2. `config.yml` -> `cross-server.enabled: true` and `server-name` set to the name of the server in the proxy config.
3. Velocity: `bungee-plugin-message-channel = true` (the default).

A home on another server is teleported to by switching the player through the proxy; the target server places them at the home when they join. No plugin on the proxy is needed.

## Placeholders (PlaceholderAPI)

| Placeholder | |
|---|---|
| `%wildhomes_amount%` | Number of homes |
| `%wildhomes_limit%` | Home limit |
| `%wildhomes_free%` | Free slots |
| `%wildhomes_names%` | All home names |
| `%wildhomes_home_<slot>%` | Name of the home in a slot |
| `%wildhomes_home_<slot>_world%` / `_server` / `_x` / `_y` / `_z` | Its location |

## Building

```bash
mvn clean package
```

The jar is `target/WildHomes-<version>.jar`. `tools/SpriteMap.java` rebuilds `sprites.txt` from client jars (`26.1=<jar> 26.2=<jar> ...`, oldest first), `tools/DialogShots.java` renders the preview images in `store/media/`.

## Icons and versions

The icon list only contains items that exist on the server and are enabled in its world. With ViaVersion, each player only sees items their own client version knows, and homes with a newer icon show the default icon to older clients. No missing-texture icons.
