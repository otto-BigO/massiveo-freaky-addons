# Auto Mine v2 - Plan (code tomorrow)

Scratch planning doc. Delete before finalizing a release.

## Baseline (what's there now)
- `plan` = ordered serpentine list of every box block (top layer down, snake Z, step X). `planIndex` walks it; `planTarget()` returns the next non-air in-box block, skipping air.
- `doMine()`: record breaks, get target, aim at it, mine whatever in-box block is under the crosshair if in reach, else `approach()` toward it.
- Drops: `findItem()`/`currentDrop()` find nearest EntityItem we own (`isOurDrop` = within 1.9 blocks of a block we broke in the last 20s), then chase within `COLLECT_R`.
- Deposit: full -> walk to Skraldespand, ping player to shift-click junk in.
- Reset: teleport jump + "will be resetting" chat -> `restartPlan()`.

---

## Feature 1 - Iron ore priority
**Goal:** iron ore is the prize (a chance drop on every block). Cobblestone/sandstone/lapis are junk. Don't waste time on junk drops - keep breaking blocks to roll more iron.

**Approach:**
- Classify a dropped `EntityItem`: `IRON` vs `JUNK`.
- Collection only **detours** for iron drops. Junk drops are not chased - but junk we walk over while mining is still auto-picked-up (that's fine, it just fills the bag and gets trashed at the Skraldespand later).
- Keep the mine flow tight: when a junk item pops, don't stop - continue to the next block ("nudge over") to roll more iron.

**Iron item (CONFIRMED):** vanilla 1.8.9 `iron_ore` block.
```java
private boolean isIronDrop(EntityItem e) {
    ItemStack s = e.getEntityItem();
    return s != null && s.getItem() == Item.getItemFromBlock(Blocks.iron_ore);
}
```
`findItem()` -> only return iron drops that are also ours (see Feature 2). Junk = not a detour target.

**Deposit / store routing (CONFIRMED) - replaces the junk-only deposit:**
```
if (inventoryFull) {
    if (hasJunk)      -> go trash junk at the Skraldespand (55 61 -691), existing flow
    else /* iron */   -> go to the old drop-off 20 60 -684 and play a notification,
                          wait for the player to store the iron, then resume
} else {
    keep mining
}
```
- So junk gets trashed first (frees space, keeps iron). When the bag is pure iron and full, walk to **20 60 -684**, play a notification sound + chat, and wait until there's space again (player stores the iron), then go back to mining.
- Reuse the existing "walk to a point + ping" pattern; the iron store point is `IRON_DROP = (20, 60, -684)`.

---

## Feature 2 - Whose drop is it (ownership)
**Goal:** other players mine the same block/area. Only ever collect items **we** produced.

**Problem with current `isOurDrop`:** pure position match over a 20s window -> if another player drops something where we recently mined, we'd grab it.

**Better approach - claim by entity id at spawn:**
- Track our recent breaks: `pos -> time` (already have `broken`).
- Track `seenItems` (entity ids we've already judged) and `ourDrops` (entity ids claimed as ours).
- Each tick, for every `EntityItem` **new** this tick (id not in `seenItems`): add to `seenItems`; if it spawned near one of our breaks within a **tight** window (~1.5 blocks, ~2.5s), claim it -> add id to `ourDrops`.
- Collection only targets ids in `ourDrops`. Prune old ids.

**Why better:** we claim at the moment of spawn (tight time+space), then follow by entity id even if the item drifts. Another player's drop rarely matches our exact break time+place, so it's never claimed.

**Snippet:**
```java
private final Set<Integer> seenItems = new HashSet<Integer>();
private final Set<Integer> ourDrops  = new HashSet<Integer>();

private void claimDrops(Minecraft mc) {
    long now = System.currentTimeMillis();
    for (Object o : mc.theWorld.loadedEntityList) {
        if (!(o instanceof EntityItem)) continue;
        EntityItem it = (EntityItem) o;
        int id = it.getEntityId();
        if (!seenItems.add(id)) continue;      // already judged
        if (bornFromOurBreak(it, now)) ourDrops.add(id);
    }
    // prune despawned / old ids periodically
}
```
`bornFromOurBreak` = near a `broken` pos within ~1.5 blocks and the break was in the last ~2.5s.

Feeds Feature 1: collect if `ourDrops.contains(id) && isIronDrop(it)`.

---

## Feature 3 - Ladder recovery
**Goal:** sometimes the bot ends up going down a ladder and can't get out. If it detects it's on a ladder, climb to the top of the mine and resume mining from where it left off.

**Approach:**
- Detection: `Pathfinder.isLadder(world, feet)` (or feet.up()) is true during the mining loop (i.e. we're on a ladder when we should be mining).
- Action: enter a `climbOut` state -> use the existing ladder-climb (face the wall, hold forward, NO jump) until we reach the top (feet Y >= MAX_Y, off the ladder / on the top surface).
- Resume: `planIndex` is preserved (we never reset it here), so once we're back near the box, normal `doMine()` picks up at the same block. "Resume from last position" is automatic.
- Guard against oscillation: only trigger climbOut when actually on a ladder; drop the state once off it and back on solid ground at the top.

**Snippet:**
```java
// top of doMine(), before normal mining logic
if (Pathfinder.isLadder(mc.theWorld, feet) || Pathfinder.isLadder(mc.theWorld, feet.up())) {
    climbLadderToTop(mc);   // reuse climb keys until posY >= MAX_Y and off the ladder
    return;
}
```

---

## Feature 4 - Layer detection
Two separate needs.

### 4A - Safe layer (don't fall into someone else's pit)
**Goal:** if another player has dug a pit under our area (e.g. 5 layers down 1 block below us), we can't stand/walk there. Detect it and skip that layer - mine the same layer the other player is on instead.

**Note:** `planTarget()` already skips *air* blocks, so if the top layers are mined out it lands on the first solid block, roughly the working surface. The gap is **safety while moving/standing**: within the box we use straight `approach()` (no floor checks), so we can walk off a pit edge.

**Approach:**
- Add a `layerSafe(y)` check: sample the working columns at layer `y`; if a large fraction have no floor for several blocks below (a pit), the layer is unsafe to stand on.
- If the current target's layer is unsafe, **skip the whole layer**: advance `planIndex` past every remaining block with that Y, then re-target. This drops us to the layer that actually has a floor = the other player's layer.
- Also: when walking toward an in-box target, prefer the floor-following move (reuse `Pathfinder.canStand`/pit checks) so we don't stroll into a hole.

**Snippet (skip a layer):**
```java
private void skipLayer(int y) {
    while (planIndex < plan.size() && plan.get(planIndex).getY() == y) planIndex++;
}
```
`layerSafe` = scan the ~columns of the box at layer `y`, count how many have solid ground within 2 below; unsafe if too few.

This is the trickiest feature - expect to tune the "unsafe" threshold in-game.

### 4B - Layer completeness (clean up stragglers)
**Goal:** when a layer is "done", make sure every block in it is actually broken; if not, break the leftover "undetected" blocks before descending.

**Approach:**
- Track `currentLayerY`. When `planTarget()`'s Y drops below `currentLayerY` (about to descend), first scan that finished layer for any non-air in-box block.
- If any remain, redirect to mine them (set them as the target) before allowing the descent.
- Once the layer is fully air, update `currentLayerY` and continue.

**Snippet:**
```java
private BlockPos leftoverInLayer(Minecraft mc, int y) {
    for (int x = MIN_X; x <= MAX_X; x++)
        for (int z = MIN_Z; z <= MAX_Z; z++) {
            BlockPos p = new BlockPos(x, y, z);
            if (!mc.theWorld.isAirBlock(p)) return p;   // still there -> clean it
        }
    return null;
}
```
Cost is one 16x3 scan per layer transition - cheap.

---

## Decisions (answered)
1. **Iron storage** - full of iron -> walk to `20 60 -684` and play a notification, wait for the player to store it, then resume. (RESOLVED)
2. **Iron drop item** - vanilla 1.8.9 `iron_ore` block. (RESOLVED)
3. **Deposit routing** - full + has junk -> Skraldespand (trash); full + pure iron -> 20 60 -684 (store + notify). Junk still auto-collected via walk-overs. (RESOLVED)

## Still to watch (minor, during coding)
- **Other players mining our target block** - if someone breaks the block we're aiming at, our break ghost-reverts. The crosshair-mining + SKIP_STUCK mostly handles it; add: if our target went air without us finishing it, just advance.

## Suggested build order
1. **Feature 2** (ownership by entity id) - foundation, tightens what "our drop" means.
2. **Feature 1** (iron-only collection) - builds on #2. Resolve the iron-storage question first.
3. **Feature 3** (ladder recovery) - self-contained, quick win.
4. **Feature 4B** (layer completeness) - moderate, self-contained.
5. **Feature 4A** (safe-layer / pit avoidance) - hardest, do last, tune in-game.
