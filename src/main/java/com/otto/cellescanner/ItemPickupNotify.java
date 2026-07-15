package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.FontRenderer;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.EnumChatFormatting;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * A small SkyHanni-style item pickup log in the bottom-right: when items enter
 * your inventory, a "+N Item" line appears and fades out. Detected by diffing
 * the inventory each tick (increase = pickup), so it works client-side and
 * doesn't false-trigger on just moving items around.
 */
public class ItemPickupNotify {

    public static int lastWidth = 96;
    public static int lastHeight = 44;

    private static final long LIFETIME_MS = 4000L;
    private static final long FADE_MS = 800L;
    private static final long MERGE_MS = 3000L;  // add to an existing line if the same item comes again soon
    private static final int MAX_LINES = 6;

    private final Map<String, Integer> prev = new HashMap<String, Integer>();
    private boolean primed = false;
    private int tick = 0;
    private final List<Entry> entries = new ArrayList<Entry>();

    private static final class Entry {
        String key;
        String name;
        int count;
        long time;
        int color;
    }

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END || !CelleScannerMod.config.itemPickupEnabled) {
            return;
        }
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.thePlayer == null || mc.thePlayer.inventory == null) {
            primed = false;
            prev.clear();
            return;
        }
        if (++tick % 2 != 0) {
            return;
        }

        Map<String, Integer> cur = new HashMap<String, Integer>();
        Map<String, ItemStack> repr = new HashMap<String, ItemStack>();
        for (ItemStack s : mc.thePlayer.inventory.mainInventory) {
            if (s == null) {
                continue;
            }
            String key = keyOf(s);
            Integer c = cur.get(key);
            cur.put(key, (c == null ? 0 : c) + s.stackSize);
            if (!repr.containsKey(key)) {
                repr.put(key, s);
            }
        }

        if (primed) {
            java.util.Set<String> allKeys = new java.util.HashSet<String>();
            allKeys.addAll(prev.keySet());
            allKeys.addAll(cur.keySet());

            for (String k : allKeys) {
                int before = prev.containsKey(k) ? prev.get(k) : 0;
                int after = cur.containsKey(k) ? cur.get(k) : 0;
                int delta = after - before;
                if (delta != 0) {
                    addTransaction(k, repr.get(k), delta);
                }
            }
        }
        prev.clear();
        prev.putAll(cur);
        primed = true;
    }

    private static String keyOf(ItemStack s) {
        // For tools/armor the "damage" is durability, not a variant - excluding it
        // means losing durability (e.g. mining with a pickaxe) isn't mistaken for
        // picking up a new one. For everything else the damage is the subtype
        // (wool colour, dye, etc.) so it stays part of the identity.
        int meta = s.isItemStackDamageable() ? 0 : s.getItemDamage();
        return Item.getIdFromItem(s.getItem()) + ":" + meta + ":" + s.getDisplayName();
    }

    private int getRarityColor(String displayName, ItemStack stack) {
        if (stack != null) {
            Item item = stack.getItem();
            if (item == net.minecraft.init.Items.skull
                    || item instanceof net.minecraft.item.ItemRecord) {
                return 0xFFBB00;
            }
            if (item == net.minecraft.init.Items.diamond
                    || item == net.minecraft.init.Items.emerald
                    || item == Item.getItemFromBlock(net.minecraft.init.Blocks.diamond_block)
                    || item == Item.getItemFromBlock(net.minecraft.init.Blocks.emerald_block)) {
                return 0x55AAFF;
            }
        }

        String lower = displayName.toLowerCase();

        // 1. Legendary (Gold: 0xFFBB00) - Player heads, key music discs
        if (lower.contains("hoved")
                || lower.contains("head")
                || lower.contains("skalle")
                || lower.contains("polet")
                || lower.contains("nøgle")
                || lower.contains("key")
                || lower.contains("crate")) {
            return 0xFFBB00;
        }

        // 2. Rare (Blue: 0x55AAFF) - Diamonds, emeralds, and diamond items
        if (lower.contains("diamond")
                || lower.contains("diamant")
                || lower.contains("emerald")
                || lower.contains("smaragd")) {
            return 0x55AAFF;
        }

        // 3. Uncommon (Green: 0x55FF55) - Iron, gold, coal blocks, redstone, lapis, tools/armor
        if (lower.contains("jern")
                || lower.contains("iron")
                || lower.contains("guld")
                || lower.contains("gold")
                || lower.contains("redstone")
                || lower.contains("lapis")
                || lower.contains("kul")
                || lower.contains("coal")
                || lower.contains("sværd")
                || lower.contains("sword")
                || lower.contains("økse")
                || lower.contains("axe")
                || lower.contains("hakke")
                || lower.contains("pickaxe")
                || lower.contains("skovl")
                || lower.contains("shovel")
                || lower.contains("hjelm")
                || lower.contains("helmet")
                || lower.contains("brystplade")
                || lower.contains("chestplate")
                || lower.contains("bukser")
                || lower.contains("leggings")
                || lower.contains("støvler")
                || lower.contains("boots")) {
            return 0x55FF55;
        }

        // 4. Common (Grey: 0x888888) - Blocks (Cobble, Sandstone, etc.)
        return 0x888888;
    }

    private void addTransaction(String key, ItemStack stack, int delta) {
        long now = System.currentTimeMillis();
        for (Entry e : entries) {
            boolean sameSign = (delta > 0 && e.count > 0) || (delta < 0 && e.count < 0);
            if (e.key.equals(key) && sameSign && now - e.time < MERGE_MS) {
                e.count += delta;
                e.time = now;
                return;
            }
        }
        Entry e = new Entry();
        e.key = key;

        String[] parts = key.split(":", 3);
        String displayName = parts.length > 2 ? parts[2] : "Ukendt";
        e.name = EnumChatFormatting.getTextWithoutFormattingCodes(displayName);

        e.count = delta;
        e.time = now;
        e.color = (delta > 0) ? getRarityColor(displayName, stack) : 0xFF5555; // Red if negative, else rarity color
        entries.add(e);
        while (entries.size() > MAX_LINES) {
            entries.remove(0);
        }
    }

    @SubscribeEvent
    public void onRender(RenderGameOverlayEvent.Post event) {
        if (event.type != RenderGameOverlayEvent.ElementType.ALL
                || !CelleScannerMod.config.itemPickupEnabled || entries.isEmpty()) {
            return;
        }
        try {
            render();
        } catch (Throwable ignored) {
        }
    }

    private void render() {
        Minecraft mc = Minecraft.getMinecraft();
        FontRenderer fr = mc.fontRendererObj;
        ScaledResolution sr = new ScaledResolution(mc);
        long now = System.currentTimeMillis();

        Iterator<Entry> it = entries.iterator();
        while (it.hasNext()) {
            if (now - it.next().time > LIFETIME_MS) {
                it.remove();
            }
        }
        if (entries.isEmpty()) {
            return;
        }

        GlStateManager.enableBlend();
        int lineH = fr.FONT_HEIGHT + 1;
        int maxW = 0;
        for (Entry e : entries) {
            String sign = e.count > 0 ? "+" : "";
            maxW = Math.max(maxW, fr.getStringWidth(sign + e.count + " " + e.name));
        }
        int boxH = entries.size() * lineH;
        lastWidth = Math.max(96, maxW);
        lastHeight = Math.max(20, boxH);
        int sw = sr.getScaledWidth();
        int sh = sr.getScaledHeight();
        int x = CelleScannerMod.config.itemPickupX != null ? CelleScannerMod.config.itemPickupX : sw - maxW - 4;
        int y = CelleScannerMod.config.itemPickupY != null ? CelleScannerMod.config.itemPickupY : sh - boxH - 4;

        // Newest first, from the anchor downward.
        for (int i = entries.size() - 1; i >= 0; i--) {
            Entry e = entries.get(i);
            long age = now - e.time;
            float alpha = age > LIFETIME_MS - FADE_MS ? (LIFETIME_MS - age) / (float) FADE_MS : 1.0f;
            if (alpha > 0.03f) {
                int a = (int) (alpha * 255);
                if (a < 8) {
                    a = 8;
                }
                String sign = e.count > 0 ? "+" : "";
                fr.drawStringWithShadow(sign + e.count + " " + e.name, x, y, (a << 24) | e.color);
            }
            y += lineH;
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
