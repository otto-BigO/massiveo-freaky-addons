package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.inventory.GuiChest;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerChest;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;
import net.minecraftforge.client.event.ClientChatReceivedEvent;
import net.minecraftforge.fml.common.eventhandler.EventPriority;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;
import net.minecraftforge.fml.common.gameevent.TickEvent;

/**
 * Watches for FreakyVille's "Flip!" chest GUI and for winner announcements in chat.
 * When both players are known, opens FlipCaseGui instead of the vanilla chest.
 *
 * <h3>Player-name discovery (confirmed from debug log)</h3>
 * The 54-slot "Flip!" chest contains {@code minecraft:skull meta=3} items whose
 * display names ARE the two player names (e.g. "massiveO", "Throwerv2"). We scan
 * all slots for skull items, collect unique names, and identify the opponent as
 * the name that is NOT {@code mc.thePlayer.getName()}.
 *
 * <h3>Winner discovery</h3>
 * Two chat patterns are handled:
 * <ul>
 *   <li>{@code [Coin Flip] <name> wins!}   – gives the winner by name directly.</li>
 *   <li>{@code [ItemFlip] ... vundet ...}  – you won.</li>
 *   <li>{@code [ItemFlip] ... tabt ...}    – you lost (winner = opponent).</li>
 * </ul>
 * When the GUI is already open the winner is forwarded immediately. When the GUI
 * opens after the winner was cached (view-later / initiator flow) the winner is
 * set right away.
 */
public class FlipWatcher {

    private static final String FLIP_TITLE = "Flip!";

    // ------------------------------------------------------------------
    // State
    // ------------------------------------------------------------------

    /** The active flip GUI, if one is showing. */
    private FlipCaseGui activeGui = null;

    /** Whether we have already replaced the chest for this flip session. */
    private boolean replaced = false;

    /** Cached winner name from chat (for the view-later case). */
    private String cachedWinner = null;
    /** Opponent name for the cached winner (to match the right flip). */
    private String cachedOpponent = null;

    // ------------------------------------------------------------------
    // Tick: detect "Flip!" chest opening and replace with FlipCaseGui
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (CelleScannerMod.config == null || !CelleScannerMod.config.flipCaseEnabled) {
            return;
        }

        Minecraft mc = Minecraft.getMinecraft();

        // If we currently have an active FlipCaseGui and it is no longer showing,
        // clean up state so the next flip can start fresh.
        if (activeGui != null && mc.currentScreen != activeGui) {
            activeGui   = null;
            replaced    = false;
        }

        // Nothing to do if not on a GuiChest.
        if (!(mc.currentScreen instanceof GuiChest) || replaced) {
            return;
        }

        // Read the inventory to confirm it's the "Flip!" screen.
        IInventory inv = getLowerInventory((GuiChest) mc.currentScreen);
        if (inv == null) {
            return;
        }
        String title = inv.getDisplayName() != null
                ? inv.getDisplayName().getUnformattedText() : "";
        
        // The title might have spaces or variations, and we only want the 54-slot reveal chest
        // (not the 45-slot selection menu).
        if (!title.toLowerCase().contains("flip") || inv.getSizeInventory() != 54) {
            return;
        }

        // Scan skulls to find the two player names.
        String me       = mc.thePlayer != null ? mc.thePlayer.getName() : "";
        String opponent = null;
        for (int i = 0; i < inv.getSizeInventory(); i++) {
            ItemStack stack = inv.getStackInSlot(i);
            if (stack == null) {
                continue;
            }
            // Skull meta=3 (player head) with a display name = player name.
            if (stack.getItem() == net.minecraft.init.Items.skull
                    && stack.getMetadata() == 3
                    && stack.hasDisplayName()) {
                String n = stack.getDisplayName().trim();
                n = net.minecraft.util.EnumChatFormatting.getTextWithoutFormattingCodes(n);
                if (!n.isEmpty() && !n.equals(" ") && !n.equalsIgnoreCase(me)) {
                    opponent = n;
                    break;
                }
            }
        }

        if (opponent == null) {
            // Opponent not found yet (e.g. still the type-select menu) - wait.
            return;
        }

        // Both players known - open FlipCaseGui.
        replaced   = true;
        activeGui  = new FlipCaseGui(me, opponent);

        // If we already have a cached winner for this opponent, set it immediately
        // (initiator / view-later case where the flip resolved before this GUI opened).
        if (cachedWinner != null && (cachedOpponent == null || opponent.equalsIgnoreCase(cachedOpponent))) {
            activeGui.setWinner(cachedWinner);
            cachedWinner   = null;
            cachedOpponent = null;
        }

        mc.displayGuiScreen(activeGui);

        DebugLog.log("FlipWatcher", "Flip opened: me=" + me + " vs " + opponent);
    }

    // ------------------------------------------------------------------
    // Chat: winner detection
    // ------------------------------------------------------------------

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onChat(ClientChatReceivedEvent event) {
        if (CelleScannerMod.config == null || !CelleScannerMod.config.flipCaseEnabled) {
            return;
        }

        String raw = net.minecraft.util.EnumChatFormatting
                .getTextWithoutFormattingCodes(event.message.getUnformattedText());

        String winner = null;

        // Pattern 1: [Coin Flip] <name> wins!
        if (raw.startsWith("[Coin Flip]") && raw.contains("wins!")) {
            // e.g. "[Coin Flip] massiveO wins!"
            int start = raw.indexOf(']') + 2;
            int end   = raw.indexOf(" wins!");
            if (start > 1 && end > start) {
                winner = raw.substring(start, end).trim();
            }
        }

        // Pattern 2: [ItemFlip] ... vundet ...   -> you won
        if (winner == null && raw.startsWith("[ItemFlip]") && raw.contains("vundet")) {
            Minecraft mc = Minecraft.getMinecraft();
            winner = mc.thePlayer != null ? mc.thePlayer.getName() : null;
        }

        // Pattern 3: [ItemFlip] ... tabt ...     -> you lost (winner = opponent)
        if (winner == null && raw.startsWith("[ItemFlip]") && raw.contains("tabt")) {
            winner = opponentFromActiveGui();
        }

        if (winner == null) {
            return;
        }

        DebugLog.log("FlipWatcher", "Winner detected from chat: " + winner);

        if (activeGui != null) {
            activeGui.setWinner(winner);
        } else {
            // Cache for the view-later case (initiator opens the flip GUI after the
            // result was already announced in chat).
            cachedWinner   = winner;
            cachedOpponent = opponentFromActiveGui();
        }
    }

    // ------------------------------------------------------------------
    // Sound: mute background/world sounds during the animation
    // ------------------------------------------------------------------

    @SubscribeEvent
    public void onPlaySound(net.minecraftforge.client.event.sound.PlaySoundEvent event) {
        if (CelleScannerMod.config == null || !CelleScannerMod.config.flipCaseEnabled) {
            return;
        }
        if (!(Minecraft.getMinecraft().currentScreen instanceof FlipCaseGui)) {
            return;
        }
        // event.name is the sound's PATH only (e.g. "flip.win", "gui.button.press"),
        // NOT "domain:path". That was the bug: "flip.win" doesn't start with
        // "cellescanner:", so the win/lose sounds got muted along with the world.
        // Allow our flip sounds and the reel ticking; mute everything else while the
        // case-opening is on screen.
        String n = event.name;
        if (FlipCaseGui.allowOurSound
                || (n != null && (n.contains("flip.win") || n.contains("flip.lose")
                        || n.contains("flip/win") || n.contains("flip/lose")
                        || n.equals("gui.button.press")))) {
            return;
        }
        event.result = null;
    }

    // ------------------------------------------------------------------
    // Helpers
    // ------------------------------------------------------------------

    private String opponentFromActiveGui() {
        if (activeGui != null) {
            // FlipCaseGui stores both names; opponent is nameB.
            // We expose it via a getter added below.
            return activeGui.getNameB();
        }
        return null;
    }

    private static IInventory getLowerInventory(GuiChest gui) {
        try {
            Container c = gui.inventorySlots;
            if (c instanceof ContainerChest) {
                return ((ContainerChest) c).getLowerChestInventory();
            }
        } catch (Throwable ignored) {
        }
        return null;
    }
}
