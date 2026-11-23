package ru.local.tvshelf;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.view.animation.DecelerateInterpolator;

final class ShelfLighting extends View {
    private final Paint atmosphere = new Paint();
    private final Paint glow = new Paint();
    private final boolean night;
    private final int base;
    private final int[] location = new int[2];
    private final int[] origin = new int[2];
    private ValueAnimator colorTransition;
    private ValueAnimator pulse;
    private int accent;
    private float brightness = 0.86f;
    private float centerX;
    private float centerY;
    private float extent;
    private boolean showing;
    private boolean windowActive;
    private ShelfTile selectedTile;

    ShelfLighting(Context context) {
        super(context);
        night = (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                == Configuration.UI_MODE_NIGHT_YES;
        base = context.getColor(R.color.surface);
        accent = context.getColor(R.color.accent);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    int outline(int color) {
        return mix(color, night ? Color.WHITE : getContext().getColor(R.color.on_surface),
                night ? 0.38f : 0.28f);
    }

    void follow(View tile) {
        if (selectedTile != tile) {
            scaleTile(1f);
            selectedTile = (ShelfTile) tile;
        }
        if (tile == null || getWidth() == 0 || tile.getWidth() == 0) return;
        tile.getLocationInWindow(location);
        getLocationInWindow(origin);
        centerX = location[0] - origin[0] + tile.getWidth() / 2f;
        centerY = location[1] - origin[1] + tile.getHeight() / 2f;
        extent = Math.max(tile.getWidth(), tile.getHeight());
        updateShaders();
    }

    void showFocus(boolean visible) {
        showing = visible;
        updatePulse();
        invalidate();
    }

    void setWindowActive(boolean active) {
        windowActive = active;
        updatePulse();
    }

    void selectColor(int color) {
        if (colorTransition != null) colorTransition.cancel();
        if (!ValueAnimator.areAnimatorsEnabled() || accent == color) {
            accent = color;
            updateShaders();
            return;
        }
        colorTransition = ValueAnimator.ofObject(new ArgbEvaluator(), accent, color);
        colorTransition.setDuration(240);
        colorTransition.setInterpolator(new DecelerateInterpolator());
        colorTransition.addUpdateListener(animation -> {
            accent = (int) animation.getAnimatedValue();
            updateShaders();
        });
        colorTransition.start();
    }

    private void updatePulse() {
        if (!showing || !windowActive || !ValueAnimator.areAnimatorsEnabled()) {
            if (pulse != null) {
                pulse.cancel();
                pulse = null;
            }
            brightness = 0.86f;
            scaleTile(1f);
            invalidate();
            return;
        }
        if (pulse != null) return;
        pulse = ValueAnimator.ofFloat(0.72f, 1f);
        pulse.setDuration(1550);
        pulse.setRepeatCount(ValueAnimator.INFINITE);
        pulse.setRepeatMode(ValueAnimator.REVERSE);
        pulse.setInterpolator(new AccelerateDecelerateInterpolator());
        pulse.addUpdateListener(animation -> {
            brightness = (float) animation.getAnimatedValue();
            scaleTile(1f + (brightness - 0.72f) / 0.28f * 0.023f);
            invalidate();
        });
        pulse.start();
    }

    private void scaleTile(float scale) {
        if (selectedTile != null) selectedTile.pulse(scale);
    }

    @Override
    protected void onDetachedFromWindow() {
        setWindowActive(false);
        if (colorTransition != null) colorTransition.cancel();
        super.onDetachedFromWindow();
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        updateShaders();
    }

    private void updateShaders() {
        if (getWidth() == 0) return;
        float x = extent == 0 ? getWidth() * 0.33f : centerX;
        float y = extent == 0 ? getHeight() * 0.49f : centerY;
        atmosphere.setShader(new RadialGradient(
                x, y, getWidth() * 0.83f,
                new int[]{mix(base, accent, night ? 0.30f : 0.24f),
                        mix(base, accent, night ? 0.12f : 0.10f), base},
                new float[]{0f, 0.53f, 1f}, Shader.TileMode.CLAMP));
        if (extent > 0) {
            glow.setShader(new RadialGradient(x, y, extent * 0.81f,
                    new int[]{withAlpha(accent, night ? 196 : 124),
                            withAlpha(accent, night ? 110 : 75), withAlpha(accent, 0)},
                    new float[]{0.27f, 0.57f, 1}, Shader.TileMode.CLAMP));
        }
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.drawColor(base);
        if (atmosphere.getShader() != null) canvas.drawPaint(atmosphere);
        if (showing && glow.getShader() != null) {
            glow.setAlpha(Math.round(255 * brightness));
            canvas.drawRect(centerX - extent, centerY - extent,
                    centerX + extent, centerY + extent, glow);
        }
    }

    private static int withAlpha(int color, int alpha) {
        return (color & 0x00ffffff) | (alpha << 24);
    }

    private static int mix(int start, int end, float amount) {
        return Color.rgb(Math.round(Color.red(start) + (Color.red(end) - Color.red(start)) * amount),
                Math.round(Color.green(start) + (Color.green(end) - Color.green(start)) * amount),
                Math.round(Color.blue(start) + (Color.blue(end) - Color.blue(start)) * amount));
    }
}
