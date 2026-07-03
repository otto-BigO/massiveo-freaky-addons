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
            for (Map.Entry<String, Integer> e : cur.entrySet()) {
                int before = prev.containsKey(e.getKey()) ? prev.get(e.getKey()) : 0;
                int delta = e.getValue() - before;
                if (delta > 0) {
                    addPickup(e.getKey(), repr.get(e.getKey()), delta);
                }
            }
        }
        prev.clear();
        prev.putAll(cur);
        primed = true;
    }

    private static String keyOf(ItemStack s) {
        return Item.getIdFromItem(s.getItem()) + ":" + s.getItemDamage() + ":" + s.getDisplayName();
    }

    private void addPickup(String key, ItemStack stack, int delta) {
        long now = System.currentTimeMillis();
        for (Entry e : entries) {
            if (e.key.equals(key) && now - e.time < MERGE_MS) {
                e.count += delta;
                e.time = now;
                return;
            }
        }
        Entry e = new Entry();
        e.key = key;
        e.name = EnumChatFormatting.getTextWithoutFormattingCodes(stack.getDisplayName());
        e.count = delta;
        e.time = now;
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
            maxW = Math.max(maxW, fr.getStringWidth("+" + e.count + " " + e.name));
        }
        int boxH = entries.size() * lineH;
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
                fr.drawStringWithShadow("+" + e.count + " " + e.name, x, y, (a << 24) | 0x55FF55);
            }
            y += lineH;
        }
        GlStateManager.color(1f, 1f, 1f, 1f);
    }
}
