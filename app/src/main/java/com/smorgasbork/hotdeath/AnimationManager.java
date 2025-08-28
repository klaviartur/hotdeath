package com.smorgasbork.hotdeath;

import android.os.Handler;
import android.util.Log;

import java.util.ArrayList;
import java.util.List;

public class AnimationManager {
    private final GameTable gameTable;
    private final Handler handler = new Handler();
    private Runnable animRunnable;
    private final List<Card> animatingCards = new ArrayList<>();

    public AnimationManager(GameTable gameTable) {
        this.gameTable = gameTable;
    }

    public void startCardAnimation(Card card, float toX, float toY, float toRot, boolean faceUp, long duration) {
        card.startAnimation(toX, toY, toRot, faceUp, duration);
        animatingCards.add(card);

        if (animRunnable == null) {
            animRunnable = new Runnable() {
                @Override
                public void run() {
                    boolean shouldContinue = false;
                    for (Card card : animatingCards) {
                        if (card.isAnimating()) {
                            card.updatePosition();
                            shouldContinue = true;
                        }
//                        else {
//                            Log.d("Animation", "removing card " + + card.getID());
//                            animatingCards.remove(card);
//                        }
                    }
                    gameTable.postInvalidate(); // Request redraw on GameTable
                    if (shouldContinue) {
                        handler.postDelayed(this, 16); // Aim for ~60fps
                    } else {
                        animatingCards.clear(); // Clear finished animations
                    }
                }
            };
        }
        handler.post(animRunnable);
    }

    public List<Card> getAnimatingCards() {
        return animatingCards;
    }
}
