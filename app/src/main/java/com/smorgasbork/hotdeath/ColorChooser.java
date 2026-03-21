package com.smorgasbork.hotdeath;

import android.graphics.Color;
import java.util.Arrays;

/**
 * Singleton that manages the color-chooser overlay animation.
 * Segments fan in/out when the player needs to pick a wild-card color.
 */
public class ColorChooser implements Animatable {

    public static final int numSegments = 4;

    private static volatile ColorChooser instance;

    // ── animation state ──────────────────────────────────────────────────────
    private final int[]   segmentColors = new int[numSegments];
    private final float[] segmentScales = new float[numSegments];

    private boolean show;
    private long    startTime;
    private long    duration;
    private boolean isAnimating;

    // ── singleton ─────────────────────────────────────────────────────────────
    private ColorChooser() {}

    public static ColorChooser getInstance() {
        if (instance == null) {
            synchronized (ColorChooser.class) {
                if (instance == null) {
                    instance = new ColorChooser();
                    instance.reset();
                }
            }
        }
        return instance;
    }

    // ── public API ────────────────────────────────────────────────────────────

    public void reset() {
        show = false;
        Arrays.fill(segmentScales, 0f);
        Arrays.fill(segmentColors, Color.TRANSPARENT);
    }

    @Override
    public void startAnimation(AnimationParams params) {
        this.show        = params.toFaceUp;
        this.startTime   = params.startTime;
        this.duration    = params.duration;
        this.isAnimating = true;
    }

    @Override
    public void update() {
        if (!isAnimating) return;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= duration) {
            elapsed      = duration;
            isAnimating  = false;
        }

        float progress = (float) elapsed / duration;

        for (int i = 0; i < numSegments; i++) {
            // Each segment fans in/out with a staggered delay.
            float raw  = 2f * numSegments * progress - numSegments - i;
            float t    = Math.min(1f, Math.max(0f, raw));
            float segT = show ? t : 1f - t;

            segmentScales[i] = segT;
            int alpha = (int) (255 * segT);
            int rgb   = GameTable.getColorRgb(i + 1) & 0x00FFFFFF;
            segmentColors[i] = (alpha << 24) | rgb;
        }
    }

    public boolean isAnimating() {
        return isAnimating;
    }

    public int   getSegmentColor(int i) { return segmentColors[i]; }
    public float getSegmentScale(int i) { return segmentScales[i]; }
}