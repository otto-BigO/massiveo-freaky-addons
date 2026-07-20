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

- **Celle Scanner**: scans celle signs in loaded chunks, HUD + through-wall ESP of celler that free up soon, optional report to a shared Discord dashboard.
- **Celle Finder**: search for a specific celle id and pathfind / walk directly to it.
- **Mine Celler**: highlights celler you own, share, or are invited to from `/ce find`.
- **Celle Alarm**: sound and screen countdown alerts when a followed celle expires (2m, 1m, 30s, and countdown).

### Tracking

- **Bande ESP**: green outline boxes through walls on players in your bande (manual name list).
- **Chest Alarm**: notification + note-block sound when the chest-alarm line hits chat.
- **Spiller Info**: shift + right-click a player for a 3D model, armor + enchants, held items, skin preview, and celle details (works on offline players too).
- **PvP Mine**: drop-timer sign on a HUD, plus an alert when a player enters the PvP mine.

### Automation

- **Auto Mine**: mines a fixed mine area on a serpentine pattern, pathfinds to deposit when full, auto-eats, and climbs ladders. Features state machine navigation, immediate reach mining, smart ghost block auto-resync, and player obstacle detour pathfinding. Automation, off by default, use at your own risk.
- **Auto Fish**: automated fishing bot that reels in on splash and recasts in fish zones.
- **Auto Crate**: automated crate opener that right-clicks crates holding keys.
- **Fast Mine**: double-speed block mining synchronized with manual player left-clicking.
- **Anti-AFK**: small periodic actions (strafes, swings, rotations) so the idle timer never trips.
- **Farm-bot**: automatically harvests and replants mature crops.
- **Auto-Følg**: automatically pathfinds, walks, and runs behind another player (`/følg <navn>`).

### Quality of life

- **Troll Lyde**: goofy client-side sounds on death, kill, jump, AFK (only you hear them).
- **Item-log**: a small "+N item" notification toast in the bottom-right on item pickup.
- **Rustnings-skins**: distinct textures for Protection 1-4 iron and diamond armor so you can tell gear apart without a full texture pack.
- **Rustnings-HUD**: equipped armor display with durability and low-durability warning.
- **Item Værdi**: item worth (DB or diamonds) in tooltips from the FreakyVille price guide.
- **Prisguide**: browse FreakyVille's price guide in-game, fetched live.
- **Opdatering**: GitHub auto-updater with an optional pre-release test channel.
- **Flip Case**: CS:GO-style case opening animation when flipping players (replaces flip chest GUI).
- **Kiste-organisering**: left-click chests to add floating 3D icons for inventory organization.
- **Jernlåge-lyde**: plays door sounds when iron doors open/close on the server.
- **Spiller-logger**: displays 3D logout markers where other players logged off.
- **Spiller ESP**: renders 3D player bounding boxes and nametags through walls.

A settings gear in the hub opens a HUD editor to drag every HUD where you want it.

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
