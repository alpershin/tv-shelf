package ru.local.tvshelf;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ComposeShader;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

final class ShelfBadgeCorner extends View {
    private final Bitmap softened;
    private final Paint blur = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint shade = new Paint(Paint.ANTI_ALIAS_FLAG);

    ShelfBadgeCorner(Context context, Bitmap artwork) {
        super(context);
        // Blend a low-resolution copy into the corner without redrawing the whole tile.
        softened = Bitmap.createScaledBitmap(artwork, 12, 12, true);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        if (width == 0 || height == 0) return;

        float radius = 50 * getResources().getDisplayMetrics().density;
        BitmapShader icon = new BitmapShader(softened, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        Matrix scale = new Matrix();
        scale.setScale((float) width / softened.getWidth(), (float) height / softened.getHeight());
        icon.setLocalMatrix(scale);
        RadialGradient mask = new RadialGradient(width, 0, radius,
                new int[]{0xFF000000, 0xCC000000, 0x00000000}, null, Shader.TileMode.CLAMP);
        blur.setShader(new ComposeShader(icon, mask, PorterDuff.Mode.DST_IN));
        shade.setShader(new RadialGradient(width, 0, radius,
                new int[]{0x99000000, 0x59000000, 0x00000000}, null, Shader.TileMode.CLAMP));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        float radius = 50 * getResources().getDisplayMetrics().density;
        float start = Math.max(0, getWidth() - radius);
        float bottom = Math.min(getHeight(), radius);
        canvas.drawRect(start, 0, getWidth(), bottom, blur);
        canvas.drawRect(start, 0, getWidth(), bottom, shade);
    }
}
