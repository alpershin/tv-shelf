package ru.local.tvshelf;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.os.Build;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;

final class ShelfViewport extends HorizontalScrollView {
    private final Paint mask = new Paint();
    private final int blurBand;
    private final int feather;
    private final int samplePadding;
    private final int radius;
    private RenderNode leftStrip;
    private RenderNode rightStrip;

    ShelfViewport(Context context) {
        super(context);
        float density = getResources().getDisplayMetrics().density;
        blurBand = Math.round(52 * density);
        feather = Math.round(29 * density);
        samplePadding = Math.round(16 * density);
        radius = Math.max(1, Math.round(5 * density));
        mask.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.DST_IN));
    }

    void revealTerminalTile(View tile) {
        ViewGroup row = (ViewGroup) getChildAt(0);
        if (row == null || getWidth() == 0) return;
        if (tile == row.getChildAt(0) && row.getLeft() + tile.getLeft() < getScrollX()) {
            scrollTo(row.getLeft() + tile.getLeft() - getPaddingLeft(), 0);
        } else if (tile == row.getChildAt(row.getChildCount() - 1)
                && row.getLeft() + tile.getRight() > getScrollX() + getWidth()) {
            scrollTo(row.getLeft() + tile.getRight() - getWidth() + getPaddingRight(), 0);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        ViewGroup row = (ViewGroup) getChildAt(0);
        if (row == null || row.getChildCount() == 0 || getWidth() < blurBand * 2) {
            super.dispatchDraw(canvas);
            return;
        }

        int left = getScrollX();
        int right = left + getWidth();
        View first = row.getChildAt(0);
        View last = row.getChildAt(row.getChildCount() - 1);
        int insetFirst = first instanceof ShelfTile ? ((ShelfTile) first).restingInset() : 0;
        int insetLast = last instanceof ShelfTile ? ((ShelfTile) last).restingInset() : 0;
        boolean moreLeft = row.getLeft() + first.getLeft() + insetFirst < left;
        boolean moreRight = row.getLeft() + last.getRight() - insetLast > right;
        if (!moreLeft && !moreRight) {
            super.dispatchDraw(canvas);
            return;
        }
        int height = getHeight();
        int layer = canvas.saveLayer(left, 0, right, height, null);
        super.dispatchDraw(canvas);
        if (Build.VERSION.SDK_INT >= 31 && canvas.isHardwareAccelerated()) {
            if (moreLeft) blur(canvas, left, height, true);
            if (moreRight) blur(canvas, right, height, false);
        }
        if (moreLeft) maskBand(canvas, left, left + feather, height, false);
        if (moreRight) maskBand(canvas, right - feather, right, height, true);
        canvas.restoreToCount(layer);
    }

    private void blur(Canvas canvas, int boundary, int height, boolean left) {
        RenderNode strip = left ? leftStrip : rightStrip;
        if (strip == null) {
            strip = new RenderNode(left ? "shelf-left-edge" : "shelf-right-edge");
            strip.setRenderEffect(RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
            if (left) leftStrip = strip;
            else rightStrip = strip;
        }

        int start = left ? boundary : boundary - blurBand;
        int end = start + blurBand;
        int sampleStart = start - samplePadding;
        int sampleWidth = blurBand + samplePadding * 2;
        strip.setPosition(sampleStart, 0, sampleStart + sampleWidth, height);
        RecordingCanvas sample = strip.beginRecording(sampleWidth, height);
        sample.translate(-sampleStart, 0);
        super.dispatchDraw(sample);
        strip.endRecording();

        int layer = canvas.saveLayer(start, 0, end, height, null);
        canvas.clipRect(start, 0, end, height);
        canvas.drawRenderNode(strip);
        maskBand(canvas, start, end, height, left);
        canvas.restoreToCount(layer);
    }

    private void maskBand(Canvas canvas, int start, int end, int height, boolean reverse) {
        int first = reverse ? Color.BLACK : Color.TRANSPARENT;
        int last = reverse ? Color.TRANSPARENT : Color.BLACK;
        mask.setShader(new LinearGradient(start, 0, end, 0, first, last, Shader.TileMode.CLAMP));
        canvas.drawRect(start, 0, end, height, mask);
    }
}
