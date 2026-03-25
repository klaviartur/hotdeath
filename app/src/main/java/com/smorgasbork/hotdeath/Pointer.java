package com.smorgasbork.hotdeath;

/**
 * Singleton that owns the rotation/scale state of the turn-pointer arrow.
 * Implements {@link Animatable} so it participates in the shared animation loop.
 */
public class Pointer implements Animatable {

    private static Pointer instance;

    private float   rot   = 0f;
    private float   scale = 1f;

    private float   startRot;
    private float   targetRot;
    private boolean jump;           // true → instant cut (scale shrinks then grows)
    private long    startTime;
    private long    duration;
    private boolean isAnimating;

    private Pointer() {}

    public static synchronized Pointer getInstance() {
        if (instance == null) instance = new Pointer();
        return instance;
    }

    // -----------------------------------------------------------------------
    // Animatable
    // -----------------------------------------------------------------------

    @Override
    public void startAnimation(AnimationParams params) {
        startTime  = params.startTime;
        duration   = params.duration;
        startRot   = rot;
        targetRot  = params.toRot;
        scale      = 1f;
        jump       = params.toDirection == Game.DIR_NONE;

        if (!jump) {
            // Ensure rotation sweeps in the correct direction
            if (params.toDirection == Game.DIR_CLOCKWISE && targetRot <= startRot) {
                targetRot += 360f;
            } else if (params.toDirection == Game.DIR_CCLOCKWISE && targetRot >= startRot) {
                targetRot -= 360f;
            }
        }
        isAnimating = true;
    }

    @Override
    public void update() {
        if (!isAnimating) return;

        long elapsed = System.currentTimeMillis() - startTime;
        if (elapsed >= duration) {
            rot         = ((targetRot % 360f) + 360f) % 360f;
            scale       = 1f;
            isAnimating = false;
            return;
        }

        float t = (float) elapsed / duration;
        if (jump) {
            // Pointer "pops" — shrink to zero at mid-point then expand again
            rot   = t < 0.5f ? startRot : targetRot;
            scale = Math.abs(1f - 2f * t);
        } else {
            rot   = startRot + t * (targetRot - startRot);
            scale = 1f;
        }
    }

    @Override
    public boolean isAnimating() { return isAnimating; }

    // -----------------------------------------------------------------------
    // Accessors
    // -----------------------------------------------------------------------

    public float getRot()        { return rot; }
    public float getScale()      { return scale; }
    public void  setRot(float r) { rot = r; }
}