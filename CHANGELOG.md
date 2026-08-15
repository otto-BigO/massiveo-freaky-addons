# Changelog

Older releases are on the GitHub releases page.

## 4.11.1

- Rapporter sender nu kontoens id med. Et Minecraft-navn er ikke en identitet:
  en konto kan skifte navn, og det gamle navn er derefter frit for en anden at
  tage. Kun på navnet ser et navneskift ud som en ny person og deler én
  spillers historik i to. Klienten har allerede id'et fra sin egen spilprofil,
  så det koster ingen forespørgsel.
- Forbindelser bliver lukket efter brug. Tre steder kaldte aldrig disconnect,
  så hver forespørgsel efterlod sin socket. Bot-tjekket er det der betyder
  noget, for det kører hvert par minutter så længe spillet er åbent.

## 4.11.0

- Deling siger til hvis botten ikke er der. En webhook der svarer 204 beviser
  kun at Discord tog imod beskeden, ikke at noget har læst den. Modet kigger
  derfor på botens eget output og hvornår det sidst blev skrevet: friskt
  betyder online, gammelt betyder at den er gået i stå, og intet svar betyder
  at endpointet ikke er tilgængeligt. Status står på Celle Bot-skærmen, og
  chatten siger kun til når tilstanden skifter, ikke ved hver rapport.
- Rapporter sendes uanset hvad status siger. Botten læser nu det der ligger og
  venter i kanalen når den starter op, så en rapport sendt under nedetid bliver
  forsinket i stedet for tabt.

## 4.10.0

- Bande ESP is just ESP. It has not been bande-only for a long time: it
  colours bande and friends, vagter and everyone else, and the all-players
  switch made the old name actively misleading. An existing config carries
  across, and the bande concept itself stays, since that is still who the
  colours are about.
- The menu shows what is switched on. A panel beside it lists every active
  addon wherever it lives in the categories, left-click for its settings,
  right-click to switch it off. It scrolls, fades in and out with the last
  addon, and only appears when there is room beside the card.
- Reference screens are not listed as active. A price guide being "on" is not
  something you are running.
- Auto Armour actually puts the armour on. It shift-clicked the piece, and
  vanilla only routes that into the armour slot when the slot is empty; any
  other time it moves the item between the inventory and the hotbar, which is
  why the inventory opened, armour moved, and none of it ended up worn.
- A hovered menu button lost its left and right edges. The list is clipped to
  exactly the button width and hovering grows a button by 2%, so the sides fell
  outside the clip and left two lines hanging in the air.
- The menu replayed its entrance animation continuously. It decided the active
  panel was out of date by comparing how many addons are on against how many
  rows fit, which with more than nine active is never equal, so it rebuilt
  itself every tick at every screen size.
- Tving genhentning on the Opdatering screen, and `/celler update force`.
  Reinstalls the newest release whatever the version numbers say, for a jar
  that is the right version but wrong: a build replaced under the same number,
  or a download that landed damaged.
- A fresh install starts with every addon switched off, so nothing begins doing
  things to your game before you have asked. Celle Bot and Opdatering stay on,
  since a client that never reports is invisible to everyone else and
  auto-update is how a new install stops being an old one.
- Joining a server tells the bot you are here, so you appear on the website's
  user list straight away instead of only after walking past a celle sign.

## 4.9.0

- A green vagt box no longer lands on anyone whose username happens to contain
  "vagt". The check tested the username for "vagt", "guard", "officer", "mod"
  and "admin" as plain substrings, so Vagtel, Nomad and Modest were all staff as
  far as it was concerned, and "mod" alone catches a lot of names.
- The roster of 109 real accounts is the authority, and your own staff list is
  matched on the whole username. It used to also accept a list entry appearing
  anywhere inside a display name, which turned one short entry into a match on
  half the server.
- Guessing from rank words is off by default. With it on it only ever looks at a
  bracketed rank the server assigned, like "[Vagt] Otto", never at the username.
- Spiller Logger waits for the server to say somebody left. It reported anyone
  walking out of render distance as a logout. The tab list was supposed to tell
  those apart, but this server drops distant players out of tab too, so it
  confirmed the false ones rather than filtering them.
- Their last position is kept when their entity goes away, so when the leave
  message does arrive the marker still lands where they were standing. A rejoin
  message clears the marker.

## 4.8.2

- Navne ESP now colours the name you actually see. The server does not use
  player nametags at all: it hangs an invisible armour stand above each player
  and puts the name on that. The addon only ever touched the player's own tag,
  which nothing renders, so recolouring it changed nothing and names stayed
  grey. Armour stands are handled now, and only when a player is standing under
  one, so holograms and celle signs are left alone.
- The label is kept exactly as the server writes it, rank and all, minus the
  colour codes. Only the colour changes.
- Bande ESP's outline renders through walls again. Switching the depth test off
  was not enough on its own, because the model renderer sets up its own state
  between that and the actual draw and turns it straight back on. Forcing the
  depth comparison to always pass survives that, since it neuters the test
  itself rather than setting a flag somebody else is free to flip back.
- The build no longer drops a copy of the jar on the Desktop.

## 4.8.1

- Navne ESP drew names in whatever dark colour the server prefixes them with,
  which made them hard to read. The tag used the server's formatted display
  name, and the colour codes inside that override the colour passed to the font
  renderer, so the addon's own colour never applied. It draws the plain username
  in its own colour now, and only keeps the server's formatting when colouring
  by role is switched off.
- Stronger dark backing behind the name for contrast.
- A colour too dark to read as text is lifted until it is readable. The palette
  is shared with the ESP boxes, where a dark colour is still a perfectly good
  outline, but the same colour as text is a smudge. The three shipped colours
  are untouched; only a genuinely dark custom one is raised, keeping its hue.

## 4.8.0

- Navne ESP is a real addon now, under Tracking. It was written but never
  registered on the event bus and its tile was commented out, so it had no way
  of running at all. That is why nothing ever appeared.
- Names are tinted by who the player is, sharing the Bande ESP palette so the
  two addons never disagree about someone's colour: bande and friends one
  colour, vagter another, everyone else a third.
- A name grows with distance and never shrinks below normal, so a far-off one
  stays readable instead of collapsing into a smudge. How fast it grows is a
  setting, and it is capped, because without a ceiling a name across the map
  covers the screen.
- Settings for through-walls, colouring, a distance limit and showing only
  bande and vagter, which is what keeps a busy area from becoming a wall of
  overlapping text.
- Past the distance limit the vanilla nametag is left alone rather than
  cancelled, so a distant player still has a name instead of none at all.
- It defaults to off. It used to default on while being dead code, so simply
  wiring it up would have switched tags on for everyone who never asked.
- Fixed Bande ESP's outline mode corrupting the view. The handler that sets up
  the outline returns early for a player who is not highlighted, without
  pushing anything, but the handler that cleans up popped the matrix stack
  regardless. Popping a matrix nobody pushed underflows the stack and every
  draw after it uses the wrong matrix, which with one unhighlighted player in
  sight was every frame.

## 4.7.1

- "Skift hakke ved" was a flat number of durability points, and that is what
  made the bot look like it could only use one kind of pickaxe. A gold pickaxe
  only holds 33 durability in total, so any threshold above 33 meant a brand new
  gold one was never good enough to pick up. Above 132 the same went for stone,
  above 251 for iron. The stepper went to 500.
- It is a percentage of each pickaxe's own durability now, so 20% means the same
  thing for a gold pickaxe as for a diamond one and no kind can be ruled out by
  accident. Zero still means use it until it breaks.
- The control sits on the Hakker screen next to the pickaxe switches, which is
  where it was actually being looked for. The row in Finjustering edits the same
  setting rather than being a second, competing one.
- An existing points value is converted rather than dropped, read against an
  iron pickaxe and capped at 50%, so no setting carried over can leave a fresh
  pickaxe of any kind unusable.

## 4.7.0

- Auto Mine, Indstillinger, Hakker: pick which pickaxes the bot may mine with.
  The five vanilla kinds each have a switch, and the button shows what is on
  without opening it. Defaults match what the code did before, so nothing
  changes until you say otherwise.
- A pickaxe the server made itself is now always allowed. The old whitelist
  named the five vanilla items and rejected everything else, so a custom
  pickaxe was refused and the bot stood next to a working tool it would not
  pick up. That is the likelier reason it looked iron-only, since gold and
  diamond were already permitted.
- Turning every kind off is called out on both screens rather than leaving the
  bot standing in the mine with nothing it is willing to hold.

## 4.6.0

- Auto Mine has named mines now. Each one keeps its own area and its own three
  positions, so a second mine is a second profile rather than re-teaching the
  bot every time you move. Under Auto Mine, Indstillinger, the top button names
  the mine you are editing and opens the list.
- The pickaxe sign and the Skraldespand can be set by pointing at them. Both
  were compiled in, aimed at one mine on one server, so the bot only ever knew
  how to buy a pickaxe and empty its bag in that one place.
- Where mined iron is handed in was hardcoded the same way and is now part of a
  profile too. Leaving that one fixed would have meant a second mine worked
  right up until the bag filled with iron.
- A mine with nothing set still uses the built-in positions, so an existing
  setup keeps working untouched, and a config from before this becomes your
  first profile with its area intact rather than being reset to the default box.
- Ryd mine-område cleared a field the bot had stopped reading, so it looked like
  it worked and changed nothing. It clears the selected mine's area now.

## 4.5.4

- Celle Buyer aimed at the sign for the whole Klargør window instead of just
  before the click. Klargør can be set as far out as ten minutes and only means
  "watch this one", so with skjult sigte off it dragged the player's view onto a
  sign and held it there for minutes, and with it on it sent a steady stream of
  look packets at a single sign the entire time. It aims about a second before
  the click now, which is still ahead of it rather than sharing its tick.

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
