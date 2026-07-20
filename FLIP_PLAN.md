# Flip Case Opening addon - Plan (code later)

Scratch planning doc. Delete before finalizing a release.

## Concept
FreakyVille's "Flip manden" is a 2-player item gamble - you bet items against another
player, winner takes both. When a flip runs the server opens a chest GUI with the two
players' heads. We want to replace/overlay that with a CS:GO-style case-opening
animation: a horizontal reel of the two players' **3D models** (like the Player Info
GUI) that scrolls, decelerates, and lands on the winner.

Reference style: https://github.com/Ev-Hoang/Bedrock-Chest-Case-Opening

## What we can reuse (already in the mod)
- **3D player model**: `GuiPlayerInfo.drawModel()` (line ~306) renders a player via
  `GuiInventory.drawEntityOnScreen` + the `hideGUI` trick + a `FakeSkinPlayer`
  (an `EntityOtherPlayerMP` built from a `GameProfile`, line ~393). Extract this into a
  reusable `PlayerModelRenderer.draw(entity, x, y, scale, ...)`.
- **Skins**: `SkinFetcher.get(username)` -> async `Entry{location, slim, status}`.
  Fetch both players once, cache.
- **Fake entity from a name**: same pattern as Player Info (GameProfile from name +
  FakeSkinPlayer with the fetched skin).

## Confirmed from screenshots
1. **GUI title** = `Flip!` (a chest, 6 rows / 54 slots). Detect
   `mc.currentScreen instanceof GuiChest` whose title is "Flip!".
2. **The two players are shown as coloured blocks, NOT skulls** - a blue set and an
   orange set (each colour = one player), arranged in a reel-ish grid with a green
   "marker" slot. So there are no player heads to read owner NBT from. The two player
   **names** must come from either the blocks' display-name/lore (if the plugin names
   them) or from chat (see below). One player is always **you** (mc.thePlayer).
3. **Winner is announced in chat** (exact prefixes seen):
   - Coin Flip: `[Coin Flip] <name> wins!`  -> winner = `<name>`.
   - ItemFlip loss: `[ItemFlip] Desværre, du har tabt flippet`  -> **you lost**
     (winner = the opponent). There's presumably a matching win line
     (`[ItemFlip] ... du har vundet ...`) - confirm the exact text.
   So: parse `[Coin Flip] X wins!` for the name, and the `[ItemFlip]` win/lose lines for
   "did I win". Between the two we can always resolve the winner.
4. **The flip runs in the background** - closing the GUI does NOT cancel it. So we can
   safely **replace** the chest with our own full GuiScreen; the result still comes over
   chat. No need for the GuiChest-overlay trick.

## Still to pin down (one thing)
- **How do we get the OPPONENT's name?** You are always player 1. For player 2:
  - Best case: the coloured blocks carry a display name/lore = the player's name -> read
    it from the container item stacks when "Flip!" opens.
  - Else: a chat line when the flip starts (e.g. "X flipper mod dig" / accept message) -
    give me that text if it exists.
  - Worst case: derive it from the winner line (only tells us the opponent when *you*
    lose). 
  -> First code step is a **content dumper** (below) to see exactly what the blocks
     carry, which decides this.

## How a flip actually runs (from Otto)
- You put an item ("diamond") up for flip; anyone can see + accept it.
- You walk off; when someone accepts you get a chat notification ("X has accepted your
  flip" or similar - this names the opponent for the initiator).
- The result is decided server-side; the server holds the items.
- **The accepter sees the `Flip!` reveal animation instantly** (GUI opens on accept).
- **You (initiator) see it later**: go to the flip man -> "see flips" -> click your
  active flip -> the `Flip!` GUI opens and shows it.

So the addon trigger is the same in both cases: **the `Flip!` GuiChest opening**. But the
**winner timing differs**, which matters:
- **Instant (accepter)**: the `[Coin Flip]/[ItemFlip]` chat line arrives around when the
  GUI opens -> animate and land on the chat winner. Easy.
- **View-later (initiator)**: the flip already resolved earlier, so there may be **no
  fresh chat line** when you open it. The winner then has to be read from the **GUI
  state** (e.g. which colour the green marker lands on, or a highlighted/awarded block).
  -> the dumper must also log how the `Flip!` GUI looks/animates for a resolved flip, so
  we learn how it marks the winner.

Fallback if the GUI winner can't be read cleanly: cache winners from chat when flips
resolve (keyed by opponent) and match them when you open the flip later.

## Architecture
- **FlipWatcher** (`@SubscribeEvent`): watches for the flip GUI opening and for the
  winner signal (chat/GUI). Parses the two player names from the skull items. Holds
  the flip state: playerA, playerB, winner (once known).
- **FlipCaseGui** (extends `GuiChest`): keeps the server container open (safe), but
  overrides `drawScreen` to render the case-opening reel over/instead of the slots.
  Opened by swapping `mc.currentScreen` to it when a flip is detected (or by rendering
  as an overlay on the existing GuiChest if swapping is risky).
- **PlayerModelRenderer** (extracted): `draw(EntityOtherPlayerMP, x, y, scale, yaw)`.
- **FlipAnim**: the reel state (offset, speed, target index, timing/easing).

## The animation
- Build a reel sequence: `[A, B, A, B, ... , winner]` - enough cells that it scrolls for
  a few seconds, with the **winner cell** at a known index near the end.
- A fixed **center marker** (vertical line / arrow). Scroll the reel left so the winner
  cell ends up centered.
- **Easing**: ease-out over ~4-6s (fast -> slow), landing exactly on the winner cell.
  Standard CS:GO curve: `offset(t) = end * (1 - pow(1 - t, 3))` (cubic ease-out).
- **Render only visible cells** (~5-7 on screen): for each visible cell compute its x
  from the scroll offset and draw player A or B (only two fake entities needed, drawn at
  different x). `drawEntityOnScreen` per visible cell.
- Land: flash + sound (`random.orb` or a custom), then show "X vandt!" and the winner's
  model centered. Close on click / after a few seconds.

## Winner-sync (depends on Q3)
- If the winner arrives by **chat**: start the reel spinning as soon as the GUI opens;
  when the winner message arrives, lock the target cell to the winner and let the
  ease-out finish on it. (Keep spinning at constant speed until we know, then decelerate
  onto the winner - exactly how real case openings feel.)
- If the winner is in the **GUI**: read it and set the target immediately.

## Performance notes
- Only 2 distinct models (A and B) exist; we just draw them at several x positions. Keep
  visible cells low (~6) and the model scale modest. The animation is short, so a small
  per-frame cost is fine. Watch out under LabyMod (render-thread watchdog) - the model
  render is the heavy bit; test there.
- Fetch both skins once up front; don't refetch per frame.

## Build order
1. **Content dumper**: when a `Flip!` GuiChest opens, print each non-empty slot's item
   + display name + lore to chat/log, AND keep logging as the slots change (so we see
   how the reveal animates and how the winner/green-marker is marked, for the view-later
   case). This tells us where the opponent's name lives and how to read the winner from
   the GUI. Quick, throwaway.
2. **Detect + resolve players**: recognise "Flip!" opening; player 1 = you, player 2 =
   from the dump result (block name) or chat. Log both names.
3. **Winner detection**: parse `[Coin Flip] X wins!` and the `[ItemFlip]` win/lose lines
   -> winner name. Log it.
4. **FlipCaseGui skeleton** (replaces the chest, since it runs in background): draw the
   two players' models static, fetch both skins.
5. **Reel rendering**: extract `PlayerModelRenderer`, draw the alternating A/B reel with
   a centre marker.
6. **Scroll + ease-out** landing on the winner (spin at constant speed until the chat
   winner arrives, then decelerate onto them).
7. **Polish**: landing flash + sound, "X vandt!" result text, click/timeout to close.

## Config / hub
- New addon tile "Flip Case" under **Quality of life** (or a new "Gambling" genre?),
  with an on/off toggle in `CelleConfig` (default on). Off = vanilla flip GUI untouched.
