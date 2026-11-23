package ru.local.tvshelf;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;

final class ShelfTile extends FrameLayout {
    private static final float REST_SCALE = 0.90f;
    private float focusScale = REST_SCALE;
    private float pulseScale = 1f;
    private ValueAnimator focusAnimation;

    ShelfTile(Context context) {
        super(context);
        setWillNotDraw(false);
        setClipChildren(false);
        setClipToPadding(false);
    }

    void pulse(float scale) {
        pulseScale = scale;
        invalidate();
    }

    int restingInset() {
        return isFocused() ? 0 : Math.round(getWidth() * (1f - REST_SCALE) / 2f);
    }

    @Override
    protected void onFocusChanged(boolean focused, int direction, Rect previouslyFocusedRect) {
        super.onFocusChanged(focused, direction, previouslyFocusedRect);
        if (focusAnimation != null) focusAnimation.cancel();
        float target = focused ? 1f : REST_SCALE;
        if (!isLaidOut() || !ValueAnimator.areAnimatorsEnabled()) {
            focusScale = target;
            invalidate();
            return;
        }
        focusAnimation = ValueAnimator.ofFloat(focusScale, target);
        focusAnimation.setDuration(170);
        focusAnimation.setInterpolator(new DecelerateInterpolator());
        focusAnimation.addUpdateListener(animation -> {
            focusScale = (float) animation.getAnimatedValue();
            invalidate();
        });
        focusAnimation.start();
    }

    @Override
    protected void onDetachedFromWindow() {
        if (focusAnimation != null) focusAnimation.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    public void draw(Canvas canvas) {
        float scale = focusScale * pulseScale;
        int checkpoint = canvas.save();
        canvas.scale(scale, scale, getWidth() / 2f, getHeight() / 2f);
        super.draw(canvas);
        canvas.restoreToCount(checkpoint);
    }
}
