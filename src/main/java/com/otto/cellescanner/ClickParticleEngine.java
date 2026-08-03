package com.otto.cellescanner;

import net.minecraft.client.gui.Gui;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Apple-style UI Particle Burst Engine for Minecraft 1.8.9 GUIs.
 * Spawns tactile, glowing "POOF!" particle bursts on button clicks in the active theme color.
 * Uses a pre-allocated object pool for zero GC allocation during rendering.
 */
public class ClickParticleEngine {

    public static final ClickParticleEngine INSTANCE = new ClickParticleEngine();
    private static final Random RANDOM = new Random();
    private static final int MAX_PARTICLES = 120;

    private static class Particle {
        float x, y;
        float vx, vy;
        float size;
        int color;
        long spawnTimeMs;
        long lifetimeMs;
        boolean active;
    }

    private final List<Particle> pool = new ArrayList<Particle>();
    private long lastUpdateMs = 0L;

    private ClickParticleEngine() {
        for (int i = 0; i < MAX_PARTICLES; i++) {
            pool.add(new Particle());
        }
    }

    /**
     * Spawns a radial "POOF!" burst of 10 glowing theme particles at (clickX, clickY).
     */
    public void spawnBurst(float clickX, float clickY, int themeColor) {
        int count = 10 + RANDOM.nextInt(4);
        long now = System.currentTimeMillis();

        for (int i = 0; i < count; i++) {
            Particle p = getFreeParticle();
            if (p == null) break;

            float angle = (float) (RANDOM.nextDouble() * Math.PI * 2.0);
            float speed = 1.2f + RANDOM.nextFloat() * 2.5f;

            p.x = clickX;
            p.y = clickY;
            p.vx = (float) Math.cos(angle) * speed;
            p.vy = (float) Math.sin(angle) * speed;
            p.size = 2.0f + RANDOM.nextFloat() * 2.5f;
            p.color = themeColor;
            p.spawnTimeMs = now;
            p.lifetimeMs = 280 + RANDOM.nextInt(120);
            p.active = true;
        }
    }

    private Particle getFreeParticle() {
        for (Particle p : pool) {
            if (!p.active) return p;
        }
        // Full: reuse the oldest burst rather than always stealing slot 0, which
        // made that one particle flicker on every click once the pool filled up.
        Particle oldest = pool.get(0);
        for (Particle p : pool) {
            if (p.spawnTimeMs < oldest.spawnTimeMs) {
                oldest = p;
            }
        }
        return oldest;
    }

    /**
     * Updates and draws all active particles. Call at end of drawScreen().
     */
    public void renderAndUpdate() {
        long now = System.currentTimeMillis();

        // Motion used to be applied per frame, so bursts flew several times
        // faster on a 240 fps machine than on a 60 fps one. Step by real time
        // instead, normalised so the tuned speeds still look the same at 60 fps.
        if (lastUpdateMs == 0L) {
            lastUpdateMs = now;
        }
        float dt = (now - lastUpdateMs) / 1000f;
        lastUpdateMs = now;
        if (dt > 0.05f) dt = 0.05f;
        if (dt < 0f) dt = 0f;
        float step = dt * 60f;

        for (Particle p : pool) {
            if (!p.active) continue;

            long elapsed = now - p.spawnTimeMs;
            if (elapsed >= p.lifetimeMs) {
                p.active = false;
                continue;
            }

            float progress = (float) elapsed / (float) p.lifetimeMs;
            float alphaProgress = 1.0f - EaseUtils.easeOutCubic(progress);

            p.x += p.vx * 0.4f * step;
            p.y += p.vy * 0.4f * step;
            p.vy += 0.05f * step; // Gentle gravity

            int alphaInt = Math.max(0, Math.min(255, (int) (alphaProgress * 255)));
            int col = (alphaInt << 24) | (p.color & 0xFFFFFF);

            int px = (int) p.x;
            int py = (int) p.y;
            int s = Math.max(1, (int) (p.size * alphaProgress));

            Gui.drawRect(px - s, py - s, px + s, py + s, col);
        }
    }
}
