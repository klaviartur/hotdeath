package com.smorgasbork.hotdeath;

import android.os.Handler;
import android.os.Looper;

import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;

/**
 * Drives all on-screen animations at ~60 fps via a single Handler-posted
 * Runnable. Animations are added from any thread; the actual tick loop runs
 * only on the main thread.
 */
public class AnimationManager {

    private static final long FRAME_INTERVAL_MS = 16L; // ~60 fps

    private final GameTable          gameTable;
    private final Handler            handler = new Handler(Looper.getMainLooper());
    private final List<Animatable>   animatables = new LinkedList<>();
    private boolean                  loopRunning = false;

    private final Runnable tickRunnable = new Runnable() {
        @Override
        public void run() {
            boolean anyActive = tick();
            gameTable.postInvalidate();
            if (anyActive) {
                handler.postDelayed(this, FRAME_INTERVAL_MS);
            } else {
                loopRunning = false;
            }
        }
    };

    public AnimationManager(GameTable gameTable) {
        this.gameTable = gameTable;
    }

    /**
     * Starts an animation. Safe to call from any thread.
     *
     * @param animatable the object to animate
     * @param params     animation parameters
     */
    public void startAnimation(Animatable animatable, AnimationParams params) {
        handler.post(() -> {
            animatable.startAnimation(params);
            if (!animatables.contains(animatable)) {
                animatables.add(animatable);
            }
            if (!loopRunning) {
                loopRunning = true;
                handler.post(tickRunnable);
            }
        });
    }

    /** Advances all animations by one frame; returns true if any are still running. */
    private boolean tick() {
        boolean anyActive = false;
        Iterator<Animatable> it = animatables.iterator();
        while (it.hasNext()) {
            Animatable a = it.next();
            if (a.isAnimating()) {
                a.update();
                anyActive = true;
            } else {
                it.remove();
            }
        }
        return anyActive;
    }

    /** Read-only view of active animatables (for debugging / testing). */
    public List<Animatable> getAnimatables() {
        return java.util.Collections.unmodifiableList(animatables);
    }
}