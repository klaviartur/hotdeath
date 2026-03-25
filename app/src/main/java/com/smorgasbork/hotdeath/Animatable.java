package com.smorgasbork.hotdeath;

public interface Animatable {

    void startAnimation(AnimationParams params);
    boolean isAnimating();
    void update();
 
}
