package com.otto.cellescanner;

import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityOtherPlayerMP;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.client.audio.PositionedSoundRecord;
import net.minecraft.util.ResourceLocation;
import net.minecraft.client.gui.ScaledResolution;
import org.lwjgl.opengl.GL11;

import java.io.IOException;

/**
 * CS:GO-style case-opening GUI for FreakyVille's "Flip!" chest.
 *
 * Layout
 * ------
 *   Full-screen dark overlay
 *   Reel box: a horizontal strip of alternating player cells (A, B, A, B, ...)
 *   Centre marker: a vertical gold line / arrow
 *   Below the reel: winner banner ("X vandt!") once the animation finishes
 *
 * Animation phases
 * ----------------
 *   SPINNING  - constant-speed scroll until the winner is known
 *   SETTLING  - cubic ease-out over SETTLE_MS, target = winner cell
 *   LANDED    - shows winner banner; dims on timeout, dismissible by click
 *
 * The two players' 3D models are fetched once up front and drawn at each
 * visible reel cell position; only two EntityOtherPlayerMP instances exist.
 */
public class FlipCaseGui extends GuiScreen {

    // ------------------------------------------------------------------
    // Tuning constants
    // ------------------------------------------------------------------

    /**
     * Total number of cells in the reel. MUST be even: the rendered cell player is
     * chosen by parity (idx % 2), and the reel wraps by REEL_SIZE*CELL_W during the
     * spin. An odd size flips the A/B parity across that wrap, so the winner cell can
     * end up rendering the wrong player. Even keeps parity consistent everywhere.
     */
    private static final int REEL_SIZE       = 30;
    /** Width of each reel cell in pixels (before scale). */
    private static final int CELL_W          = 100;
    /** How many cells are visible at once (determines reel box width). */
    private static final int VISIBLE_CELLS   = 7;
    /** Height of the reel strip. */
    private static final int REEL_H          = 160;
    /** Constant scroll speed during the SPINNING phase (pixels per ms). */
    private static final float SPIN_SPEED    = 0.85f;  // px/ms
    /** Duration of the ease-out settle in ms. */
    private static final long SETTLE_MS      = 4500;
    /** How long the winner banner stays before auto-dismiss (ms). */
    private static final long BANNER_HOLD_MS = 6000;
    /** If no winner arrives within this long, stop spinning and close (safety). */
    private static final long SPIN_TIMEOUT_MS = 20000;
    /** ID for the close button. */
    private static final int ID_CLOSE        = 0;

    // ------------------------------------------------------------------
    // Reel state
    // ------------------------------------------------------------------

    private enum Phase { SPINNING, SETTLING, LANDED }

    /** Flag set briefly to allow our own sounds through the mute filter. */
    static boolean allowOurSound = false;

    private Phase phase = Phase.SPINNING;

    /** Pixel offset of the reel (left edge of cell 0 relative to reel box left). */
    private float reelOffset = 0f;

    /** The reel is built so cell[WINNER_INDEX] = winner. */
    private static final int WINNER_INDEX = REEL_SIZE - (VISIBLE_CELLS / 2 + 1);

    /** reelOffset value that puts the winner cell exactly at the centre marker. */
    private float targetOffset;

    /** Timestamp when settling started. */
    private long settleStart;
    /** reelOffset at the start of settling (to interpolate from). */
    private float settleFrom;
    /** Dynamic settle duration in ms to ensure smooth deceleration. */
    private long settleDuration = SETTLE_MS;

    private long lastFrameMs = -1;
    private int lastTickCell = -1;
    /** When the reel started spinning (for the no-winner safety timeout). */
    private long spinStart = System.currentTimeMillis();
    /** When we entered the LANDED phase (for the banner auto-dismiss). */
    private long landedAt = 0;

    // ------------------------------------------------------------------
    // Players
    // ------------------------------------------------------------------

    private final String nameA;   // always you (initiator)
    private final String nameB;   // opponent

    /** Which player won (nameA or nameB). Null until known. */
    private String winner = null;

    private EntityOtherPlayerMP entityA;
    private EntityOtherPlayerMP entityB;

    // ------------------------------------------------------------------
    // Constructor
    // ------------------------------------------------------------------

    public FlipCaseGui(String playerA, String playerB) {
        this.nameA = playerA;
        this.nameB = playerB;

        // Build the reel sequence: alternating A/B finishing on the winner slot.
        // (WINNER_INDEX is always B if we don't know the winner yet; we swap on settle.)
        // Skin fetches are kicked off now; entities built lazily once skins arrive.
        SkinFetcher.get(nameA);
        SkinFetcher.get(nameB);

        // Pre-compute where the reel would need to stop so that WINNER_INDEX is centred.
        // Centre of the reel box = (VISIBLE_CELLS / 2) * CELL_W + CELL_W / 2.
        // Cell WINNER_INDEX left edge = WINNER_INDEX * CELL_W.
        // We want: WINNER_INDEX * CELL_W - reelOffset == centreX - CELL_W/2
        // => reelOffset = WINNER_INDEX * CELL_W - (VISIBLE_CELLS / 2) * CELL_W
        targetOffset = (WINNER_INDEX - VISIBLE_CELLS / 2) * CELL_W;
    }

    /** Returns the opponent's name (player B). Used by FlipWatcher for chat-winner caching. */
    public String getNameB() {
        return nameB;
    }

    // ------------------------------------------------------------------
    // Called by FlipWatcher when the winner becomes known
    // ------------------------------------------------------------------

    public void setWinner(String winnerName) {
        if (phase == Phase.LANDED) {
            return;
        }
        this.winner = winnerName;
        boolean winnerIsA = nameA.equalsIgnoreCase(winnerName);

        // Calculate ideal deceleration distance D = v0 * T / 4.
        float D = SPIN_SPEED * SETTLE_MS / 4.0f;
        float idealTargetOffset = reelOffset + D;
        float idealCell = idealTargetOffset / CELL_W;

        // Round to nearest cell index with the correct parity.
        // Player A = even indices, Player B = odd indices.
        int targetCell = Math.round(idealCell);
        boolean cellIsA = (targetCell % 2 == 0);
        if (winnerIsA != cellIsA) {
            // Always push forward, never backward.
            targetCell += 1;
        }

        // Ensure target is always ahead of current position (never backward).
        float candidateOffset = targetCell * CELL_W - (VISIBLE_CELLS / 2) * CELL_W;
        while (candidateOffset <= reelOffset) {
            targetCell += 2; // skip 2 to keep same parity
            candidateOffset = targetCell * CELL_W - (VISIBLE_CELLS / 2) * CELL_W;
        }

        // Center the winning cell in the viewport by offsetting it by half the visible area.
        targetOffset = candidateOffset;

        if (phase == Phase.SPINNING) {
            phase     = Phase.SETTLING;
            settleStart = System.currentTimeMillis();
            settleFrom  = reelOffset;
            float S = targetOffset - settleFrom;
            settleDuration = (long) (4.0f * S / SPIN_SPEED);
        }
    }

    // ------------------------------------------------------------------
    // GuiScreen lifecycle
    // ------------------------------------------------------------------

    @Override
    public void initGui() {
        this.buttonList.clear();
        this.buttonList.add(new StyledButton(ID_CLOSE,
                this.width / 2 - 50, this.height - 36, 100, 20, "Luk"));
    }

    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }

    // ------------------------------------------------------------------
    // Tick / update
    // ------------------------------------------------------------------

    @Override
    public void updateScreen() {
        Minecraft mc = Minecraft.getMinecraft();

        // Build entities lazily once skins are ready.
        if (entityA == null) {
            entityA = PlayerModelRenderer.buildEntity(nameA, SkinFetcher.get(nameA));
        }
        if (entityB == null) {
            entityB = PlayerModelRenderer.buildEntity(nameB, SkinFetcher.get(nameB));
        }

        long now = System.currentTimeMillis();
        if (lastFrameMs < 0) {
            lastFrameMs = now;
        }
        float dt = now - lastFrameMs;
        lastFrameMs = now;

        switch (phase) {
            case SPINNING:
                reelOffset += SPIN_SPEED * dt;
                // Wrap offset to avoid float overflow on very long spins.
                float wrapLen = REEL_SIZE * CELL_W;
                if (reelOffset > wrapLen) {
                    reelOffset -= wrapLen;
                    settleFrom -= wrapLen;
                }
                // Safety: if the winner never arrives (unrecognised result / message
                // scrolled off), don't spin forever - just close.
                if (winner == null && now - spinStart > SPIN_TIMEOUT_MS) {
                    mc.displayGuiScreen(null);
                }
                break;

            case SETTLING: {
                float t = Math.min(1f, (float)(now - settleStart) / settleDuration);
                float eased = 1f - (float)Math.pow(1f - t, 4); // quartic ease-out
                reelOffset = settleFrom + (targetOffset - settleFrom) * eased;
                if (t >= 1f) {
                    reelOffset = targetOffset;
                    phase = Phase.LANDED;
                    landedAt = now; // start the banner auto-dismiss timer

                    // Play win or lose sound
                    try {
                        boolean youWon = nameA.equalsIgnoreCase(winner);
                        String soundName = youWon ? "cellescanner:flip.win" : "cellescanner:flip.lose";
                        allowOurSound = true;
                        mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation(soundName), 1.0F));
                        allowOurSound = false;
                    } catch (Throwable ex) {
                        allowOurSound = false;
                    }
                }
                break;
            }

            case LANDED:
                // Auto-dismiss after the banner has been shown for BANNER_HOLD_MS.
                if (now - landedAt > BANNER_HOLD_MS) {
                    mc.displayGuiScreen(null);
                }
                break;
        }

        // Ticking sound
        float cellPosAtMarker = (reelOffset + (VISIBLE_CELLS * CELL_W / 2.0f)) / CELL_W;
        int currentCell = (int) cellPosAtMarker;
        if (currentCell != lastTickCell) {
            if (lastTickCell != -1 && phase != Phase.LANDED) {
                float pitch = 1.5f;
                if (phase == Phase.SETTLING) {
                    float t = Math.min(1f, (float)(System.currentTimeMillis() - settleStart) / settleDuration);
                    float eased = 1f - (float)Math.pow(1f - t, 4);
                    pitch = 1.5f - 0.7f * eased; // dynamically pitch down as it slows down
                }
                mc.getSoundHandler().playSound(PositionedSoundRecord.create(new ResourceLocation("gui.button.press"), pitch));
            }
            lastTickCell = currentCell;
        }
    }

    // ------------------------------------------------------------------
    // Rendering
    // ------------------------------------------------------------------

    @Override
    public void drawScreen(int mouseX, int mouseY, float pt) {
        Minecraft mc = Minecraft.getMinecraft();

        // Full-screen dark overlay.
        drawRect(0, 0, this.width, this.height, 0xCC000000);

        int reelBoxW = VISIBLE_CELLS * CELL_W;
        int reelBoxX = (this.width  - reelBoxW) / 2;
        int reelBoxY = (this.height - REEL_H) / 2 - 20;

        // Reel background.
        Style.roundedRect(reelBoxX - 4, reelBoxY - 4, reelBoxX + reelBoxW + 4, reelBoxY + REEL_H + 4, 0xAA000000);
        Style.roundedRect(reelBoxX - 2, reelBoxY - 2, reelBoxX + reelBoxW + 2, reelBoxY + REEL_H + 2, 0xFF121215);

        // Enable scissoring for the reel content.
        int scale = new ScaledResolution(mc).getScaleFactor();
        GL11.glEnable(GL11.GL_SCISSOR_TEST);
        GL11.glScissor(reelBoxX * scale, mc.displayHeight - (reelBoxY + REEL_H) * scale, reelBoxW * scale, REEL_H * scale);

        // Draw visible cells.
        int firstCell = (int)(reelOffset / CELL_W) - 1;
        for (int ci = firstCell; ci <= firstCell + VISIBLE_CELLS + 2; ci++) {
            int idx = ((ci % REEL_SIZE) + REEL_SIZE) % REEL_SIZE;
            boolean isA = (idx % 2 == 0);
            EntityOtherPlayerMP ent = isA ? entityA : entityB;
            String name = isA ? nameA : nameB;
            int color = isA ? 0xFF4A90D9 : 0xFFE07B39; // blue for A, orange for B

            float cellLeft = reelBoxX + ci * CELL_W - reelOffset;
            int cx = (int) cellLeft;

            // Cell card background
            Style.roundedRect(cx + 4, reelBoxY + 8, cx + CELL_W - 4, reelBoxY + REEL_H - 8, 0xFF202026);
            // Bottom accent color line
            drawRect(cx + 4, reelBoxY + REEL_H - 12, cx + CELL_W - 4, reelBoxY + REEL_H - 8, color);

            // 3D model.
            PlayerModelRenderer.draw(mc, ent,
                    cx + 10, reelBoxY + 14, CELL_W - 20, REEL_H - 40,
                    mouseX, mouseY, null);

            // Player name label.
            String label = EnumChatFormatting.WHITE + name;
            int labelW = fontRendererObj.getStringWidth(name);
            drawCenteredString(fontRendererObj, label, cx + CELL_W / 2, reelBoxY + REEL_H - 24, 0xFFFFFF);
        }

        GL11.glDisable(GL11.GL_SCISSOR_TEST);

        // Centre marker (glow effect).
        int markerX = reelBoxX + reelBoxW / 2;
        drawRect(markerX - 1, reelBoxY - 4, markerX + 1, reelBoxY + REEL_H + 4, 0xFFFFD700);
        drawRect(markerX - 2, reelBoxY - 4, markerX - 1, reelBoxY + REEL_H + 4, 0x55FFD700);
        drawRect(markerX + 1, reelBoxY - 4, markerX + 2, reelBoxY + REEL_H + 4, 0x55FFD700);
        
        drawCenteredString(fontRendererObj, "\u00a7e\u25bc", markerX, reelBoxY - 12, 0xFFFFFF);
        drawCenteredString(fontRendererObj, "\u00a7e\u25b2", markerX, reelBoxY + REEL_H + 4, 0xFFFFFF);

        // Title.
        drawCenteredString(fontRendererObj,
                EnumChatFormatting.GOLD + "" + EnumChatFormatting.BOLD + "FLIP!",
                this.width / 2, reelBoxY - 28, 0xFFD700);

        // Player name labels outside the reel (your name left, opponent right).
        drawString(fontRendererObj,
                EnumChatFormatting.AQUA + nameA,
                reelBoxX, reelBoxY + REEL_H + 8, 0x55FFFF);
        int oppW = fontRendererObj.getStringWidth(nameB);
        drawString(fontRendererObj,
                EnumChatFormatting.GOLD + nameB,
                reelBoxX + reelBoxW - oppW, reelBoxY + REEL_H + 8, 0xFFAA00);

        // Phase-specific overlays.
        if (phase == Phase.SPINNING && winner == null) {
            drawCenteredString(fontRendererObj,
                    EnumChatFormatting.GRAY + "Afventer resultat...",
                    this.width / 2, reelBoxY + REEL_H + 22, 0xAAAAAA);
        } else if (phase == Phase.SETTLING) {
            drawCenteredString(fontRendererObj,
                    EnumChatFormatting.YELLOW + "Afslutter...",
                    this.width / 2, reelBoxY + REEL_H + 22, 0xFFFF55);
        } else if (phase == Phase.LANDED && winner != null) {
            boolean youWon = nameA.equalsIgnoreCase(winner);
            String banner = youWon
                    ? EnumChatFormatting.GREEN + "" + EnumChatFormatting.BOLD + "DU VANDT! \uD83C\uDF89"
                    : EnumChatFormatting.RED   + "" + EnumChatFormatting.BOLD + winner + " VANDT!";
            // Shadow box behind banner.
            int bw = fontRendererObj.getStringWidth(EnumChatFormatting.getTextWithoutFormattingCodes(banner)) + 20;
            int bx = this.width / 2 - bw / 2;
            int by = reelBoxY + REEL_H + 14;
            drawRect(bx - 2, by - 4, bx + bw + 2, by + 14, 0xCC000000);
            drawCenteredString(fontRendererObj, banner, this.width / 2, by, 0xFFFFFF);
        }

        super.drawScreen(mouseX, mouseY, pt);
    }

    // ------------------------------------------------------------------
    // Input
    // ------------------------------------------------------------------

    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == ID_CLOSE) {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }

    @Override
    protected void keyTyped(char c, int key) throws IOException {
        // ESC closes.
        if (key == 1) {
            Minecraft.getMinecraft().displayGuiScreen(null);
        }
    }
}
