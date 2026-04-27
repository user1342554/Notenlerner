package com.example.notenlerner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * Draws a Notenlerner home tile background: rounded rect with a corner-positioned
 * radial accent haze, hairline border. Mirrors CSS .nl-tile + .nl-haze* classes.
 *
 * Corners (where the haze blooms from):
 *   0 = top-left, 1 = top-right, 2 = bottom-right, 3 = bottom-left
 */
final class TileBackgroundView extends View {
    private final Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint hazePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();
    private final int hazeColor;
    private final int corner;
    private final float radius;

    TileBackgroundView(Context ctx, int hazeColor, int corner) {
        super(ctx);
        this.hazeColor = hazeColor;
        this.corner = corner;
        this.radius = dp(26);

        fillPaint.setColor(Ui.BG_2);
        fillPaint.setStyle(Paint.Style.FILL);

        borderPaint.setColor(Ui.HAIRLINE);
        borderPaint.setStyle(Paint.Style.STROKE);
        borderPaint.setStrokeWidth(dp(1));

        hazePaint.setStyle(Paint.Style.FILL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();
        rect.set(0, 0, w, h);

        // Base fill
        canvas.drawRoundRect(rect, radius, radius, fillPaint);

        // Haze: radial gradient anchored at one corner; clipped to rounded rect
        canvas.save();
        // Use a path-clip via clipping with a clipRect can't do rounded corners — use saveLayer + composite via second pass.
        // Simple approach: draw the haze on a slightly inset rounded rect and rely on alpha.
        rect.set(1, 1, w - 1, h - 1);
        float cx, cy;
        float r = (float) Math.hypot(w, h) * 0.9f;
        switch (corner) {
            case 1:  cx = w; cy = 0; break;          // top-right
            case 2:  cx = w; cy = h; break;          // bottom-right
            case 3:  cx = 0; cy = h; break;          // bottom-left
            case 0:
            default: cx = 0; cy = 0; break;          // top-left
        }
        hazePaint.setShader(new RadialGradient(
                cx, cy, r,
                hazeColor, 0x00000000, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(rect, radius - 1, radius - 1, hazePaint);
        hazePaint.setShader(null);
        canvas.restore();

        // Border
        rect.set(0.5f, 0.5f, w - 0.5f, h - 0.5f);
        canvas.drawRoundRect(rect, radius, radius, borderPaint);
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
