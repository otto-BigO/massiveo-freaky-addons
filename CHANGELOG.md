# Changelog

Older releases are on the GitHub releases page.

## 4.5.3

- Clicking Celle Buyer in the menu opened nothing. It silently toggled the
  addon on and off instead, so the settings and the pick list could not be
  reached at all.
- The same bug hid ten other screens: Celle Bot, VK Stealer, Chest Alarm, Troll
  Lyde, Item Log, Anti AFK, Armour HUD, Prisguide, Opdatering and Auto Følg. All
  of them open properly now.
- The cause was a list of addon names kept in the hub, which decided whether a
  tile opened a screen or just toggled. Every addon added after the list was
  written was missing from it, and two names on it had no screen behind them at
  all. Each addon says for itself now, right next to the code that opens it, and
  the compiler will not accept a new addon that has not answered the question.

## 4.5.2

- `/celler buyer` opens the Celle Buyer, and `add`, `fjern`, `liste` and `ryd`
  manage the pick list from chat, so a celle can be picked while standing in
  front of it instead of typing the id back in from memory.
- Turning the buyer on with an empty pick list now says so in chat. That
  combination runs but never fires, and there was nothing to tell you why.
- The mod list showed a literal ${version} instead of a version number, on every
  build since the token was added. processResources already copies the
  resources, so the mcmod.info handling was adding a second copy on top of that
  and the expanded one was the copy being thrown away.
- The command help still described `/celler bot <url>`, which stopped existing
  in 4.2.4 when the webhook was built into the mod.

## 4.5.1

- Fixed "The batch file cannot be found" at the end of a Windows update. The
  helper that swaps the jar after the game closes ended by deleting itself, and
  cmd.exe reads a batch file as it runs it, so it was left looking for the next
  line of a file that was no longer there. The update itself had already
  applied by that point, since the jar is moved into place before that line, so
  this was a real error message about nothing. It exits the script before
  removing itself now.
- The helper waited forever for the old jar's lock to clear. A jar that could
  never be deleted, for instance because of file permissions, left a hidden
  process spinning until the machine was restarted. It gives up after a minute.
- The helper is written to the temp directory instead of the mods folder, where
  it never belonged.

## 4.5.0

- Celle Buyer only buys celler you picked. Valgte celler opens a list you type
  ids into, and nothing off that list is touched. This is on by default and an
  empty list buys nothing, because an addon that claims whatever happens to free
  up next to you is not something to leave running. Turn the list off if you
  really do want it to take anything in reach.
- Each picked celle gets a rainbow box in the world, colour running around the
  edges so it cannot be mistaken for the green and amber status boxes. Picks
  ignore the ESP distance limit and draw even with the general celle ESP switched
  off, since a short list you typed in by hand is worth seeing either way.
- Boxes come from the recorded position rather than the live scan, so a pick
  stays boxed once its sign has been seen even after you walk away from it. A
  celle that has never been scanned says so in the list instead of quietly having
  no box.
- Ids are matched without case. The same celle shows up as c1289 while owned and
  C1289 once free, and matching the sign's own casing would have split one celle
  into two and missed the buy.

## 4.4.0

- New addon, Celle Buyer. Claims a celle the moment it becomes buyable. The
  timing is not guesswork: every countdown value is an exact multiple of 1200
  seconds and the sign steps down one notch every 1199.6s, measured over twelve
  consecutive ticks, so a celle that expires on schedule can be predicted from an
  anchor taken twenty minutes earlier. It arms before the sign visibly changes.
- Skjult sigte sends the rotation in the packet only, so the camera never swings
  and you keep whatever view you were holding.
- Udvidet rækkevidde lets you stand back out of the crowd instead of pressed
  against the sign. Off by default and held at vanilla reach until you turn it
  on, then adjustable up to the six blocks the server will still accept. Past
  that is only rejected packets.
- Uses the sign by position rather than through whatever the crosshair is over,
  which is the part that survives a crowd. A player standing between you and the
  sign blocks the client raytrace but not this, and being knocked around only
  matters if it puts you out of reach.
- Forudklik starts clicking slightly before the predicted flip so the click is
  already in flight when the server releases the celle. Clicking a sold sign
  costs nothing, which is what makes the lead safe to spend.
- Also fires reactively, because a celle can be dumped from days out when an
  owner sells up and nothing predicts that. An armed target that gains time
  instead of losing it is dropped rather than clicked at, since an owner renewing
  one tick before expiry is the normal way a camp ends.

## 4.3.0

- The offset measurement was measuring nothing. The tick that takes a celle to
  zero lands about a millisecond before it becomes free, and that tick replaced
  the anchor first, so the prediction was always "free right now" and the answer
  was always 1ms. It predicts forward from the last anchor that still had time
  on it instead, which is the number an actual prediction would accumulate.
- Records whether the celle reached zero by counting down or was dumped there
  from days out. Both end up free, but only the first is predictable, and mixing
  them makes the measurement look far worse than it is.

## 4.2.9

- The timing summary hid the most interesting thing in the log. A countdown that
  goes UP is an owner buying more time, not a tick, and those were being counted
  as neither. They are reported separately now, because a celle you are waiting
  on can simply be renewed out from under you.
- Cadence is measured between two real ticks on the same celle. The stored gap
  could span a first sighting or a rehydration, which is not a cadence.
- Reports whether the values are exact multiples of the drop, which is what says
  the sign counts in fixed steps rather than continuously.

## 4.2.8

- Fixed the timing log throwing away the first anchor for every celle. It read
  the confirmed flag from before the update, which is false on the very first
  witnessed change, so an anchor was only kept from the second one onward. With
  signs refreshing every 20 minutes that cost a full cycle per celle before any
  measurement could begin. Use this build for measuring, not 4.2.7.

## 4.2.7

- Added a passive timing log for celle signs. It records only, never acts, and
  exists to answer two things that cannot be guessed: how often the countdown
  really changes and by how much, and how far a prediction of "this hits zero
  now" lands from the moment the celle actually becomes free. The sign shows
  whole minutes, so reading "5m" means anywhere from 300 to 359 seconds, and
  that hidden offset has to be measured before anything can be timed off it.
- `/celler timing` reports what the log has learned so far: the real tick
  cadence, the measured offset, and whether there is enough data to trust it.
- Writes to cellescanner_timing.jsonl next to the config. Off with
  timingLogEnabled in the config.

## 4.2.6

- A report that was rate limited past its retries, or failed on the network, was
  dropped silently. The scanner had already recorded that data as sent, so it
  would not try again until something else changed, which turned a rate limit
  into missing data rather than a delay. Failed reports are now offered again on
  the next scan.

## 4.2.5

- Celle Bot is now a tile in the menu, under Celler. It had no entry at all in
  4.2.4, so the only way to reach the on/off switch was the `/celler bot`
  command, which is not obvious.

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
