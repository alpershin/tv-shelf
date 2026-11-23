package ru.local.tvshelf;

import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.RectF;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.Drawable;

final class ShelfArtwork {
    private static final int SIZE = 192;
    private static final int HUES = 24;

    final Bitmap image;
    final int accent;

    ShelfArtwork(Drawable drawable, int fallback) {
        Bitmap source = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(source);
        if (drawable instanceof AdaptiveIconDrawable) {
            AdaptiveIconDrawable adaptive = (AdaptiveIconDrawable) drawable;
            Drawable background = adaptive.getBackground();
            Drawable foreground = adaptive.getForeground();
            if (background != null) draw(background, canvas, 0, 0, SIZE, SIZE);
            if (foreground != null) {
                int inset = Math.round(SIZE * AdaptiveIconDrawable.getExtraInsetFraction());
                draw(foreground, canvas, -inset, -inset, SIZE + inset, SIZE + inset);
            }
        } else {
            int width = Math.max(1, drawable.getIntrinsicWidth());
            int height = Math.max(1, drawable.getIntrinsicHeight());
            float scale = Math.max((float) SIZE / width, (float) SIZE / height);
            int x = Math.round((SIZE - width * scale) / 2f);
            int y = Math.round((SIZE - height * scale) / 2f);
            draw(drawable, canvas, x, y, SIZE - x, SIZE - y);
        }
        image = normalizePadding(source);
        accent = dominantColor(image, fallback);
    }

    private static void draw(Drawable drawable, Canvas canvas, int x, int y, int right, int bottom) {
        drawable.setBounds(x, y, right, bottom);
        drawable.draw(canvas);
    }

    private static Bitmap normalizePadding(Bitmap source) {
        int left = SIZE;
        int top = SIZE;
        int right = 0;
        int bottom = 0;
        int[] pixels = new int[SIZE * SIZE];
        source.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        for (int y = 0; y < SIZE; y++) {
            for (int x = 0; x < SIZE; x++) {
                if (Color.alpha(pixels[y * SIZE + x]) < 64) continue;
                left = Math.min(left, x);
                top = Math.min(top, y);
                right = Math.max(right, x + 1);
                bottom = Math.max(bottom, y + 1);
            }
        }
        int width = right - left;
        int height = bottom - top;
        if (width <= 0 || height <= 0 || Math.max(width, height) >= SIZE * 0.92f) return source;

        Bitmap fullTile = Bitmap.createBitmap(SIZE, SIZE, Bitmap.Config.ARGB_8888);
        float scale = (float) SIZE / Math.max(width, height);
        float x = (SIZE - width * scale) / 2f;
        float y = (SIZE - height * scale) / 2f;
        new Canvas(fullTile).drawBitmap(source, new Rect(left, top, right, bottom),
                new RectF(x, y, SIZE - x, SIZE - y), new Paint(Paint.FILTER_BITMAP_FLAG));
        return fullTile;
    }

    private static int dominantColor(Bitmap bitmap, int fallback) {
        float[] weight = new float[HUES];
        float[] red = new float[HUES];
        float[] green = new float[HUES];
        float[] blue = new float[HUES];
        float[] hsv = new float[3];
        int[] pixels = new int[SIZE * SIZE];
        bitmap.getPixels(pixels, 0, SIZE, 0, 0, SIZE, SIZE);
        for (int y = 5; y < SIZE; y += 5) {
            for (int x = 5; x < SIZE; x += 5) {
                int pixel = pixels[y * SIZE + x];
                if (Color.alpha(pixel) < 200) continue;
                Color.colorToHSV(pixel, hsv);
                if (hsv[1] < 0.24f || hsv[2] < 0.13f) continue;
                int hue = (int) ((hsv[0] + 7.5f) / 15f) % HUES;
                float sampleWeight = hsv[1] * hsv[1] * (0.15f + hsv[2] * hsv[2]);
                weight[hue] += sampleWeight;
                red[hue] += Color.red(pixel) * sampleWeight;
                green[hue] += Color.green(pixel) * sampleWeight;
                blue[hue] += Color.blue(pixel) * sampleWeight;
            }
        }
        int strongest = 0;
        for (int i = 1; i < HUES; i++) {
            if (weight[i] > weight[strongest]) strongest = i;
        }
        if (weight[strongest] < 1f) return fallback;
        float sum = weight[strongest];
        Color.RGBToHSV(Math.round(red[strongest] / sum), Math.round(green[strongest] / sum),
                Math.round(blue[strongest] / sum), hsv);
        hsv[1] = Math.min(0.86f, Math.max(0.48f, hsv[1]));
        hsv[2] = Math.max(0.76f, hsv[2]);
        return Color.HSVToColor(hsv);
    }
}
