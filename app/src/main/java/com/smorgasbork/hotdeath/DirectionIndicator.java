package com.smorgasbork.hotdeath;

import android.graphics.Color;
import java.util.Arrays;

/**
 * Singleton that manages the ring-segment direction indicator animation.
 * Segments animate in/out to show the current play direction and color.
 */
public class DirectionIndicator implements Animatable {

    public static final int numSegments = 12;

    private static volatile DirectionIndicator instance;

    // ── persistent state ──────────────────────────────────────────────────────
    private final int[] segmentColors = new int[numSegments];
    private int   color     = Color.TRANSPARENT;
    private int   direction = Game.DIR_NONE;

    // ── animation state ───────────────────────────────────────────────────────
    private int  startColor;
    private int  targetColor;
    private int  startDirection;
    private int  targetDirection;
    private long startTime;
    private long duration;
    private boolean isAnimating;

    // ── singleton ─────────────────────────────────────────────────────────────
    private DirectionIndicator() {}

    public static DirectionIndicator getInstance() {
        if (instance == null) {
            synchronized (DirectionIndicator.class) {
                if (instance == null) {
                    instance = new DirectionIndicator();
                    instance.reset();
                }
            }
        }
        return instance;
    }

    // ── public API ────────────────────────────────────────────────────────────

    public void reset() {
        color     = Color.TRANSPARENT;
        direction = Game.DIR_NONE;
        Arrays.fill(segmentColors, Color.TRANSPARENT);
    }

    @Override
    public void startAnimation(AnimationParams params) {
        this.startColor     = this.color;
        this.targetColor    = params.toColor;
        this.startDirection = this.direction;
        this.targetDirection = params.toDirection;
        this.startTime      = params.startTime;
        // Direction changes get double time so the wipe-out / wipe-in is visible.
        this.duration       = (targetDirection != startDirection)
                ? params.duration * 2
                : params.duration;
        this.isAnimating    = true;
    }

    @Override
    public void update() {
        if (!isAnimating) return;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= duration) {
            elapsed    = duration;
            color      = targetColor;
            direction  = targetDirection;
            isAnimating = false;
        }

        float progress = (float) elapsed / duration;

        if (targetDirection != startDirection) {
            updateDirectionChange(progress);
        } else if (targetColor != startColor) {
            updateColorChange(progress);
        }
    }

    /**
     * Two-phase wipe: fade out old direction segments, then fade in new ones.
     */
    private void updateDirectionChange(float progress) {
        if (direction != targetDirection) {
            // Phase 1 – wipe out (first half of animation).
            if (progress <= 0.5f) {
                for (int i = 0; i < numSegments; i++) {
                    float t     = Math.min(1f, Math.max(0f,
                            numSegments - i - 2f * numSegments * progress));
                    int   alpha = (int) (((color >> 24) & 0xFF) * t);
                    segmentColors[i] = (alpha << 24) | (color & 0x00FFFFFF);
                }
            } else {
                // Flip to new direction at the midpoint.
                direction = targetDirection;
                color     = targetColor;
            }
        } else {
            // Phase 2 – wipe in new segments.
            for (int i = 0; i < numSegments; i++) {
                float t     = Math.min(1f, Math.max(0f,
                        24f * progress - numSegments - i));
                int   alpha = (int) (255 * t);
                segmentColors[i] = (alpha << 24) | (color & 0x00FFFFFF);
            }
        }
    }

    /** Segment-by-segment color swap (no direction change). */
    private void updateColorChange(float progress) {
        for (int i = 0; i < numSegments; i++) {
            segmentColors[i] = (progress * numSegments >= i + 1)
                    ? targetColor
                    : startColor;
        }
    }

    public boolean isAnimating() {
        return isAnimating;
    }

    public int getDirection()            { return direction; }
    public int getSegmentColor(int i)    { return segmentColors[i]; }
}