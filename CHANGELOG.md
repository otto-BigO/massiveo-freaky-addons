# Changelog

Older releases are on the GitHub releases page.

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
