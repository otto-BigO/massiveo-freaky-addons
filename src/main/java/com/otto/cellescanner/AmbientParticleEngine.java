package com.otto.cellescanner;

import net.minecraft.client.gui.Gui;

import java.util.Random;

/**
 * Slow drifting motes rendered behind the menu card, in the active theme colour.
 *
 * The field covers the whole screen but fades out as it approaches the card, so the
 * motion reads as atmosphere around the menu rather than clutter underneath the text.
 * Movement is in pixels per second off a wall clock, so the drift looks the same at
 * 30 fps and at 240 fps.
 */
public final class AmbientParticleEngine {

    public static final AmbientParticleEngine INSTANCE = new AmbientParticleEngine();

    private static final int COUNT = 54;
    /** How far past the card edge the motes are still hidden, giving a soft gap. */
    private static final int PANEL_FEATHER = 26;
    private static final float MAX_STEP_SECONDS = 0.05f;

    private static final Random RANDOM = new Random();

    private static class Mote {
        float x, y;
        float driftX, driftY;
        float size;
        float depth;        // 0 far, 1 near. Drives parallax and brightness.
        float twinklePhase;
        float twinkleSpeed;
    }

    private final Mote[] motes = new Mote[COUNT];
    private long lastMs = 0L;
    private int lastW = -1, lastH = -1;

    private AmbientParticleEngine() {
        for (int i = 0; i < COUNT; i++) {
            motes[i] = new Mote();
        }
    }

    /** Scatters the field across a screen of this size. */
    private void seed(int screenW, int screenH) {
        for (Mote m : motes) {
            m.x = RANDOM.nextFloat() * screenW;
            m.y = RANDOM.nextFloat() * screenH;
            respawnMotion(m);
        }
        lastW = screenW;
        lastH = screenH;
    }

    private void respawnMotion(Mote m) {
        m.depth = 0.25f + RANDOM.nextFloat() * 0.75f;
        m.driftY = -(5f + RANDOM.nextFloat() * 13f) * m.depth;
        m.driftX = (RANDOM.nextFloat() - 0.5f) * 7f;
        m.size = 1f + m.depth * 1.6f;
        m.twinklePhase = RANDOM.nextFloat() * (float) Math.PI * 2f;
        m.twinkleSpeed = 0.7f + RANDOM.nextFloat() * 1.1f;
    }

    /**
     * Advances and draws the field. Call right after the background dim and before
     * the card, so the motes sit behind it.
     *
     * @param px1 card bounds, the region the motes fade away from
     */
    public void renderBehind(int screenW, int screenH,
                             int px1, int py1, int px2, int py2,
                             int mouseX, int mouseY) {
        if (screenW <= 0 || screenH <= 0) {
            return;
        }
        if (lastW != screenW || lastH != screenH) {
            seed(screenW, screenH);
        }

        long now = System.currentTimeMillis();
        if (lastMs == 0L) {
            lastMs = now;
        }
        float dt = (now - lastMs) / 1000f;
        lastMs = now;
        // A long pause (alt-tab, world load) must not teleport the whole field.
        if (dt > MAX_STEP_SECONDS) {
            dt = MAX_STEP_SECONDS;
        }
        if (dt < 0f) {
            dt = 0f;
        }

        int accent = Style.getAccentColor() & 0xFFFFFF;
        float seconds = now / 1000f;

        // Parallax: the field leans away from the pointer by a few pixels.
        float leanX = (mouseX - screenW * 0.5f) / Math.max(1f, screenW * 0.5f);
        float leanY = (mouseY - screenH * 0.5f) / Math.max(1f, screenH * 0.5f);

        for (Mote m : motes) {
            m.x += m.driftX * dt;
            m.y += m.driftY * dt;

            if (m.y < -6f) {
                m.y = screenH + 6f;
                m.x = RANDOM.nextFloat() * screenW;
                respawnMotion(m);
            }
            if (m.x < -6f) {
                m.x = screenW + 6f;
            } else if (m.x > screenW + 6f) {
                m.x = -6f;
            }

            float drawX = m.x - leanX * m.depth * 6f;
            float drawY = m.y - leanY * m.depth * 4f;

            float mask = panelMask(drawX, drawY, px1, py1, px2, py2);
            if (mask <= 0.01f) {
                continue;
            }

            float twinkle = 0.55f + 0.45f * (float) Math.sin(seconds * m.twinkleSpeed + m.twinklePhase);
            float a = 0.42f * m.depth * twinkle * mask;
            int alpha = (int) (a * 255f);
            if (alpha <= 2) {
                continue;
            }
            if (alpha > 255) {
                alpha = 255;
            }

            int cx = (int) drawX;
            int cy = (int) drawY;
            int s = Math.max(1, Math.round(m.size));

            // Dim halo, then a brighter core, which reads as a soft dot without textures.
            int halo = ((alpha / 3) << 24) | accent;
            Gui.drawRect(cx - s, cy - s, cx + s, cy + s, halo);
            int core = (alpha << 24) | accent;
            Gui.drawRect(cx, cy, cx + 1, cy + 1, core);
        }
    }

    /**
     * 0 inside the card, ramping to 1 once clear of it, so nothing draws over the
     * menu content and the motes do not pop as they cross the edge.
     */
    private static float panelMask(float x, float y, int px1, int py1, int px2, int py2) {
        float dx = Math.max(Math.max(px1 - x, x - px2), 0f);
        float dy = Math.max(Math.max(py1 - y, y - py2), 0f);
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist >= PANEL_FEATHER) {
            return 1f;
        }
        return dist / PANEL_FEATHER;
    }
}
