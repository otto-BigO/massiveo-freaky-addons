# Changelog

Older releases are on the GitHub releases page.

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
