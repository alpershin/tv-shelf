package ru.local.tvshelf;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.view.View;

final class Glyph extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final String type;
    private boolean muted;

    Glyph(Context context, String type, int color) {
        super(context);
        this.type = type;
        paint.setColor(color);
        paint.setStrokeWidth(1.7f);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setStyle(Paint.Style.STROKE);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    void state(int color, boolean muted) {
        this.muted = muted;
        paint.setColor(color);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float scale = Math.min(getWidth(), getHeight()) / 24f;
        canvas.save();
        canvas.translate((getWidth() - 24 * scale) / 2f, (getHeight() - 24 * scale) / 2f);
        canvas.scale(scale, scale);
        switch (type) {
            case "wifi":
                curve(canvas, 3, 9, 12, 1, 21, 9);
                curve(canvas, 6, 12, 12, 6, 18, 12);
                curve(canvas, 9, 15, 12, 12, 15, 15);
                canvas.drawCircle(12, 18, 1, paint);
                break;
            case "vpn":
                path(canvas, 12, 3, 20, 6, 19, 14, 16, 18, 12, 21, 8, 18, 5, 14, 4, 6, 12, 3);
                path(canvas, 9, 12, 11, 14, 15, 10);
                break;
            case "browser":
                canvas.drawCircle(12, 12, 8, paint);
                canvas.drawOval(8, 4, 16, 20, paint);
                canvas.drawLine(4, 12, 20, 12, paint);
                break;
            case "recent":
                canvas.drawRoundRect(3, 7, 16, 19, 2, 2, paint);
                path(canvas, 8, 4, 21, 4, 21, 16, 19, 16);
                path(canvas, 9, 10, 9, 13, 12, 13);
                break;
            case "apps":
                for (int x : new int[]{4, 13}) {
                    for (int y : new int[]{4, 13}) canvas.drawRoundRect(x, y, x + 7, y + 7, 1.6f, 1.6f, paint);
                }
                break;
            case "settings":
                canvas.drawLine(3, 7, 8, 7, paint);
                canvas.drawCircle(11, 7, 3, paint);
                canvas.drawLine(14, 7, 21, 7, paint);
                canvas.drawLine(3, 17, 12, 17, paint);
                canvas.drawCircle(15, 17, 3, paint);
                canvas.drawLine(18, 17, 21, 17, paint);
                break;
            case "profile":
                canvas.drawCircle(12, 8, 3.4f, paint);
                curve(canvas, 5, 19, 12, 9, 19, 19);
                break;
            case "display":
                canvas.drawRoundRect(3, 4, 21, 16, 2, 2, paint);
                canvas.drawLine(9, 20, 15, 20, paint);
                canvas.drawLine(12, 16, 12, 20, paint);
                break;
            case "add":
                canvas.drawLine(5, 12, 19, 12, paint);
                canvas.drawLine(12, 5, 12, 19, paint);
                break;
            default:
                canvas.drawRoundRect(4, 4, 20, 20, 3, 3, paint);
                canvas.drawLine(8, 12, 16, 12, paint);
        }
        if (muted) canvas.drawLine(4, 20, 20, 4, paint);
        canvas.restore();
    }

    private void curve(Canvas canvas, float x, float y, float cx, float cy, float endX, float endY) {
        Path line = new Path();
        line.moveTo(x, y);
        line.quadTo(cx, cy, endX, endY);
        canvas.drawPath(line, paint);
    }

    private void path(Canvas canvas, float... coordinates) {
        Path line = new Path();
        line.moveTo(coordinates[0], coordinates[1]);
        for (int i = 2; i < coordinates.length; i += 2) line.lineTo(coordinates[i], coordinates[i + 1]);
        canvas.drawPath(line, paint);
    }
}
