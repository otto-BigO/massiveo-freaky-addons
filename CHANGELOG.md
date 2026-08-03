# Changelog

Older releases are on the GitHub releases page.

## 4.2.4

- The reporting webhook now ships with the mod, so scanning is shared out of the
  box. There is nothing to paste in and nothing to set up.
- The webhook is fixed and cannot be pointed anywhere else. The Celle Bot screen
  is just an on/off switch and a test button now, and `/celler bot` takes
  `on`, `off` or `test`. Everyone reports into the same channel, so a client
  pointing somewhere else would quietly drop out of the shared picture.
- Reporting defaults to on. A config written before this, which had it off with
  no webhook, is switched on once, since that meant "never set up" rather than
  "turned off on purpose". After that your choice is kept.
- Celle scanner runs lighter. The upcoming list was rebuilt and re-sorted from
  scratch twice every frame, once for the HUD and once for the ESP. The ESP also
  measured the distance to every celle twice per frame, each with a square root.
  The sign scan cleaned all four lines of every sign in every loaded chunk before
  deciding it was not a celle sign at all.

## 4.2.3

- New addon, VK Stealer. Locks a vagt or officer in reach and lands the
  finishing hit. Because a crowd is usually beating on the same guard it holds
  off by default until the target is low, then commits. All 109 staff accounts
  are matched by exact username, with rank.
- Settings screen for VK Stealer: steal mode and its threshold, swing delay,
  reach, silent or smooth aim, line of sight, and what to do when health cannot
  be read. A live line shows the locked guard, their rank and health.
- Armour skins now apply when the server writes Protection as lore text rather
  than as a real enchantment, which is how guard and shop gear arrives. The
  golden helmet was the clearest case since it needs any enchantment at all.
- Theme screen fixes. The opacity slider and stepper disagreed on their range
  and the first fifth of the bar did nothing. Dragging a slider rewrote the
  config file on every frame. A custom accent colour left text green instead of
  following the theme. The live preview card could be dragged off screen with
  no way to get it back, and clicking it also pressed the button underneath.
- Drifting background particles behind the menus, in the theme colour, fading
  out around the card so they never cover the content.
- The open animation now runs on every screen instead of only the ones that had
  it copied in by hand.
- Button presses actually animate now. The press compression was being
  overwritten by the hover state on the very next frame.
- Click particles no longer fly faster on a high refresh rate machine.

## 4.2.0

- Fixed the broken Armour Skins textures. The MesterHolm set was flat and
  untextured, and iron armour was locked to it even when Hypixel+ was picked.
- New armour mapping: iron P2 Mineral, iron P3 Unstable Dragon, iron P4 Tank
  Wither, diamond P1 Vanguard, diamond P2 Rampart, diamond P3 Speed Wither,
  diamond P4 Shadow Assassin, and the enchanted gold helmet uses Divan.
- Added the real Mineral Helmet and Unstable Dragon Helmet head textures.
  Those two sets use player heads on SkyBlock, so they had no armour layer.
- The MesterHolm and Hypixel+ options render differently again, so the choice
  in the preview menu actually changes something.
- Inventory icons follow the selected texture pack and update without a
  restart. Iron P2 icons were missing entirely and have been added.

## 2.0.0

- Major AutoMine rewrite: state machine (DESTINATION vs MINING phases), pre-aiming target blocks, and immediate reach mining.
- Fast Mine addon: double-speed block breaking synchronized with manual player left-clicking.
- Smart Ghost Block Detection: automatic resync hits for client-air/server-solid desync and mining stalls.
- Dynamic Obstacle Pathfinder: routes around standing players and skips occupied target blocks.
- Proximity Layer Sweeps: clears closest unmined stragglers before descending.
- Visual Target Overlay: renders a wireframe box around current target block (cyan during travel, green during mining).
- Mine Area Outline: automatically hides when AutoMine is disabled.

## 1.1.6

- Scanned celler stay on the HUD and ESP through chunk unloads, relogs and
  deaths. A session cache holds every celle seen this run and clears at game
  close, so the "KOMMER SNART" list no longer empties when you walk away.
- Bande ESP boxes hug the player instead of standing off the model.
- The Bande member list scrolls, so every member is reachable (was capped at 4).
- Celle Finder is its own tile in the Celler theme, out of the scanner menu.
- Removed the Mine Celler radar.
- Anti-AFK can step to the side and back to the same spot ("Skridt til siden").
- Shelved the Mod-brugere addon for now.

## 1.1.5

- **Flip Case opening**: a CS:GO-style case-opening animation for FreakyVille's
  "Flip!" GUI. Shows the two players' 3D models on a reel that spins and eases to a
  stop on the winner (read from chat), with a ticking sound and win/lose sounds.
- **Auto-Fish**: an AFK fishing bot that reels in on the splash and recasts. It
  auto-equips a fishing rod from your hotbar and stops with a chat alert if you run
  out of rods.
- **Auto-Crate**: an automated crate opener that right-clicks crates while holding a
  key token (music disc), auto-equips keys from the hotbar, pauses during the chest
  animation, and turns off when you're out of keys.
- **Item Log overhaul**: the item-log HUD now tracks both pickups and losses, colour
  coded by rarity. Legendary (gold): heads, keys, Nether Stars. Rare (blue):
  diamonds/emerald blocks, weapons, armor. Uncommon (green): iron/gold gear + ingots,
  redstone, lapis, coal, tools. Common (grey): cobble, sandstone, wood, dirt. Losing
  items (drop/deposit) shows a red notification with a minus prefix.
- **Interactive Armor Skin previewer**: material (Diamond/Iron) and level (P1-P4)
  cycle controls in the skins selector, updating the 3D preview equipment live.
- **PortalRouting** for Walk to celle: routes through a portal entrance when the
  target celle's gang area is gated.
- Bug fixes: LabyForge duplicate-class startup crash, Auto Mine ladder look-away /
  slide-down, and Celle tracker "TIL SALG!" sign parsing.

## 1.1.4

- **Walk to celle**: a "Gå til celle" button in Celle Finder pathfinds to a scanned
  celle and walks there, drawing the route as a line on the floor.
- **Pathfinding** (shared by Walk to celle and Auto Mine): plans a route that goes
  around walls, cuts diagonally instead of zig-zagging, sprints and sprint-jumps on
  long straights, climbs ladders, and paths in segments toward far celler that are
  beyond render distance. Movement validation tightened so it stops trying jumps it
  can't make.
- **Celle ids are clickable**: left-click a celle id (in Celle Finder or Spiller
  Info) to copy it, right-click to pathfind and walk to it.
- **New hub genres**: Tracking and Quality of life, with the addons regrouped.
- **Auto Mine**: new mine area, climbs the ladders to get out, and restarts cleanly
  off the "will be resetting" chat warning. Auto-eat now also works during Walk to
  celle so it doesn't starve on long walks.

## 1.1.3

- **Auto Mine** addon: mines a fixed mine area on a set serpentine pattern,
  collects its own drops, auto-eats so it never starves, and recovers on its own
  when the mine resets and teleports you out. It never moves items itself: when
  full it walks to the Skraldespand, opens it and pings you to shift-click your
  junk in; when a pickaxe breaks it equips a hotbar spare or walks to the shop
  and pings you. Off by default.
- **Mod-brugere**: a small purple badge before other mod users' names (test).
- **HUD editor**: a settings gear in the hub opens an editor to drag every HUD
  (Celle, Rustnings-HUD, Item-log, PvP Mine) where you want it.
- PvP Mine drop timer now keeps counting down (and loops on reset) even before
  the sign is read.
- Item-log no longer reports a "new pickaxe" while you mine with one.
