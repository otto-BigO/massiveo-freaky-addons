package com.otto.cellescanner;

import org.lwjgl.opengl.GL11;

/**
 * The shared open animation for addon screens: the ambient field behind the card,
 * a breathing halo around it, and a short slide up as the panel settles.
 *
 * Screens used to copy this by hand, so most of them simply never got it. Holding
 * it in one place means every screen opens the same way and a change lands
 * everywhere at once.
 *
 * Usage: call {@link #restart()} from initGui, {@link #begin} right after
 * drawDefaultBackground, and {@link #end()} at the end of drawScreen.
 */
public final class ScreenIntro {

    private static final long DURATION_MS = 260L;
    private static final float SLIDE_PX = 12.0f;

    private final AnimationValue anim = new AnimationValue(0f);
    /** Guards the matrix stack if end() is ever reached without a begin(). */
    private boolean pushed = false;

    /** Plays the intro from the start. Call from initGui. */
    public void restart() {
        anim.setValueInstant(0.0f);
        anim.animateTo(1.0f, DURATION_MS);
    }

    /** 0 while opening, 1 once settled. Useful for fading extra content in. */
    public float progress() {
        return anim.getValue();
    }

    /** Draws the backdrop and applies the slide. Always pair with {@link #end()}. */
    public void begin(int screenW, int screenH, int mouseX, int mouseY) {
        float p = anim.getValue();
        drawBackdrop(screenW, screenH, mouseX, mouseY, p);
        GL11.glPushMatrix();
        GL11.glTranslatef(0.0f, -(1.0f - p) * SLIDE_PX, 0.0f);
        pushed = true;
    }

    /**
     * The backdrop without the slide, and with nothing pushed, so no end() is needed.
     *
     * Screens where you drag things to a position use this. Translating their content
     * while the intro plays would put the visuals a few pixels away from the area that
     * actually responds to the mouse, which is exactly the thing those screens are for.
     */
    public void backdropOnly(int screenW, int screenH, int mouseX, int mouseY) {
        drawBackdrop(screenW, screenH, mouseX, mouseY, 1.0f);
    }

    private void drawBackdrop(int screenW, int screenH, int mouseX, int mouseY, float p) {
        int halfW = Style.cardHalfWidth(screenW);
        int halfH = Style.cardHalfHeight(screenH);
        int cx = screenW / 2;
        int cy = screenH / 2;

        AmbientParticleEngine.INSTANCE.renderBehind(screenW, screenH,
                cx - halfW, cy - halfH, cx + halfW, cy + halfH, mouseX, mouseY);

        float breathe = 0.5f + 0.5f * (float) Math.sin(System.currentTimeMillis() / 1400.0);
        Style.cardGlow(screenW, screenH, p * (0.45f + 0.55f * breathe));
    }

    /** Restores the matrix. Safe to call even if begin() did not run. */
    public void end() {
        if (pushed) {
            GL11.glPopMatrix();
            pushed = false;
        }
    }
}
