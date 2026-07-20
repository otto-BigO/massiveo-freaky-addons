# Massiveo's Freaky Addons

A client-side Forge 1.8.9 addon hub for the FreakyVille prison server. Press
**B** (or type `/celler`) to open the hub and pick an addon. Everything runs on
your own client.

## Install

1. Install Forge for Minecraft 1.8.9.
2. Download the jar from the [latest release](../../releases/latest) and drop it
   in your `mods` folder.
3. Launch. Auto-update keeps it current (toggle it under the Opdatering addon).

## Features

<details>
<summary><b>Show all features</b></summary>

### Celler

- **Celle Scanner**: scans celle signs in loaded chunks, HUD + through-wall ESP
  of celler that free up soon, optional report to a shared Discord dashboard.
  Includes a Celle Finder with "Gå til celle" that pathfinds and walks you to a
  scanned celle. Left-click a celle id anywhere to copy it, right-click to walk
  to it.
- **Mine Celler**: highlights the celler you own, share or are invited to, from
  `/ce find`.

### Tracking

- **Bande ESP**: a box through walls on players in your bande (manual name list).
- **Chest Alarm**: notification + sound when the chest-alarm line hits chat.
- **Spiller Info**: shift + right-click a player for a 3D model, their armor +
  enchants, their celler and each celle's details. Works on offline players too
  (skin from Mojang).
- **PvP Mine**: the drop-timer sign on a HUD, plus an alert when a player is in
  the mine.
- **Mod-brugere**: a small badge before other mod users' names (test).

### Quality of life

- **Troll Lyde**: goofy sounds on your own events (death, kill, jump, AFK), only
  you hear them.
- **Item-log**: a small "+N item" notification in the bottom-right when items
  enter your inventory.
- **Anti-AFK**: small periodic actions so the idle timer never trips. Off by
  default.
- **Rustnings-skins**: distinct textures for Protection 1-4 iron and diamond
  armor so you can tell gear apart, without a full texture pack.
- **Rustnings-HUD**: your equipped armor with durability and a low warning.
- **Item Vaerdi**: an item's worth (DB or diamonds) in its tooltip, from the
  FreakyVille price guide.
- **Prisguide**: browse FreakyVille's price guide in-game, fetched live.
- **Opdatering**: the GitHub auto-updater, with an optional pre-release channel.

### World

- **Auto Mine**: mines a fixed mine area on a serpentine pattern, pathfinds to deposit when full, auto-eats, and climbs ladders. Features state machine navigation, immediate reach mining, smart ghost block auto-resync, and player obstacle detour pathfinding. Automation, off by default, use at your own risk.
- **Fast Mine**: double-speed block mining synchronized with manual player left-clicking. Automation, off by default, use at your own risk.

A settings gear in the hub opens a HUD editor to drag every HUD where you want
it.

</details>

The mod id stays `cellescanner` internally, so older config and save files keep
working.

## Build

```
./gradlew clean build
```

Requires Java 8. The jar lands in `build/libs`.

## License

Code is released under the [MIT License](LICENSE). Bundled sound effects are the
property of their respective owners and are included for personal use only.
