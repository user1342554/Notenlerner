package com.example.notenlerner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * 16-step finger-exercise pattern grid (8 wide × 2 rows). The active step glows in the finger color.
 */
final class PatternGridView extends View {
    private static final int COLS = 8;
    private static final int ROWS = 2;

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private int[] pattern = new int[16];
    private int activeIndex = -1;

    PatternGridView(Context context) {
        super(context);
        cellPaint.setStyle(Paint.Style.FILL);
        cellStrokePaint.setStyle(Paint.Style.STROKE);
        cellStrokePaint.setStrokeWidth(dp(0.5f));
        cellStrokePaint.setColor(0x14FFFFFF);
        glowPaint.setStyle(Paint.Style.FILL);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(18));
        textPaint.setFakeBoldText(true);
        textPaint.setTypeface(Typeface.DEFAULT_BOLD);
    }

    void setPattern(int[] pattern) {
        if (pattern.length >= 16) {
            System.arraycopy(pattern, 0, this.pattern, 0, 16);
        }
        invalidate();
    }

    void setActive(int idx) {
        this.activeIndex = idx;
        invalidate();
    }

    static int fingerColor(int finger) {
        switch (finger) {
            case 1:  return 0xFF30D9A8;
            case 2:  return 0xFF0A84FF;
            case 3:  return 0xFFFFC857;
            case 4:  return 0xFFBF7BFF;
            default: return 0xFFF5F5F7;
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        float gap = dp(6);
        float cellW = (width - gap * (COLS - 1)) / COLS;
        int height = (int) (cellW * ROWS + gap * (ROWS - 1));
        setMeasuredDimension(width, resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float gap = dp(6);
        float cellW = (getWidth() - gap * (COLS - 1)) / COLS;
        float cellH = cellW;

        for (int i = 0; i < 16; i++) {
            int row = i / COLS;
            int col = i % COLS;
            float x = col * (cellW + gap);
            float y = row * (cellH + gap);
            rect.set(x, y, x + cellW, y + cellH);

            int finger = pattern[i];
            int fingerCol = fingerColor(finger);
            boolean active = (i == activeIndex);

            if (active) {
                // Glow halo
                cellPaint.setColor((fingerCol & 0x00FFFFFF) | 0x33000000);
                rect.inset(-dp(3), -dp(3));
                canvas.drawRoundRect(rect, dp(13), dp(13), cellPaint);
                rect.inset(dp(3), dp(3));

                cellPaint.setColor(fingerCol);
                canvas.drawRoundRect(rect, dp(10), dp(10), cellPaint);

                textPaint.setColor(0xFF000000);
            } else {
                cellPaint.setColor(0xFF202024);
                canvas.drawRoundRect(rect, dp(10), dp(10), cellPaint);
                canvas.drawRoundRect(rect, dp(10), dp(10), cellStrokePaint);
                textPaint.setColor(fingerCol);
            }

            String label = Integer.toString(finger);
            float tx = rect.centerX();
            float ty = rect.centerY() + dp(6.5f);
            canvas.drawText(label, tx, ty, textPaint);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }
}
