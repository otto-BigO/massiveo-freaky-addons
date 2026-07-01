# Massiveo's Freaky Addons

A client-side Forge 1.8.9 addon hub for the FreakyVille Minecraft server. Press
**B** (or type `/celler`) to open the hub and pick an addon. Everything runs
locally on your own client.

The mod id is still `cellescanner` internally, so config and save files from the
original Celle Scanner keep working.

## Addons

The hub groups the addons by category.

### Celler

- **Celle Scanner**: scans celle signs (`SOLGT!` / `TIL SALG!`) in loaded
  chunks, shows a draggable HUD and a through-wall ESP of celler that become
  available within a configurable time window, and can report to a shared
  Discord dashboard (see the companion CelleScannerBot project).
- **Mine Celler**: runs `/ce find <you>`, reads the reply, and highlights your
  own, co-owned and invited celler with a gold ESP box. Click one to point the
  finder compass at it.

### PvP

- **Bande ESP**: a green outline through walls on players in your bande. Uses a
  manual name list, with an optional "same scoreboard team" auto-detect.
- **Chest Alarm**: an on-screen notification plus a note-block sound when the
  server's chest-alarm line shows up in chat. The keyword is configurable.

### World

- **Anti-AFK**: small periodic actions so the server idle timer never trips. Off
  by default; some servers forbid AFK macros, so use at your own discretion.
- **Rustnings-skins**: draws Protection 1-4 iron and diamond armour with
  distinct textures so you can tell gear apart on players, without OptiFine or a
  full texture pack. Reads the Protection enchant level.
- **Item Vaerdi**: adds a value line to an item's tooltip, based on the
  FreakyVille price guide. Prices live in `config/massiveo_prices.json` and can
  be reloaded in-game.
- **Prisguide**: browse FreakyVille's price guide in-game, fetched live from
  their site.
- **Opdatering**: the auto-updater (below).

## Auto-update

On launch the mod checks this repo's latest GitHub release. If it is newer than
the running version it downloads the new jar into your mods folder and removes
the old one, so the next time you start the game you are on the new version. A
mod cannot relaunch Minecraft itself, so it just tells you in chat to restart.
On systems that lock the running jar (Windows) it points you to the download
instead of leaving two jars behind. Toggle it under the Opdatering addon.

## Install

Download the jar from the latest release and drop it in your Forge 1.8.9 `mods`
folder.

## Build

Standard ForgeGradle 2.1 project targeting Forge `1.8.9-11.15.1.2318-1.8.9`.
Build with Java 8:

```
./gradlew build
```

The jar lands in `build/libs/`.

## Config

Files in your `.minecraft/config` folder:

- `cellescanner.json` holds all addon settings.
- `massiveo_prices.json` holds the item-value prices (editable, reloadable
  in-game).
- `cellescanner_positions.json` remembers scanned celle positions.

## Notes

- Client-side only (`clientSideOnly = true`). It reads text from signs and chat
  that are already loaded on your client and does not touch server logic or
  other players' data.
- The companion **CelleScannerBot** is a separate Node.js project that merges
  celle reports from multiple players into one shared Discord dashboard. It is
  not part of this repo.
