package com.example.notenlerner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;

/**
 * Segmented cents strip — 11 cells covering ±50 cents in 10-cent steps.
 * Active cell lights up mint when in tune (|cents| <= 5), amber otherwise.
 */
final class TunerMeterView extends View {
    private static final int CELL_COUNT = 11;
    private static final int CENTER_INDEX = 5;
    private static final int[] TICKS = {-50, -40, -30, -20, -10, 0, 10, 20, 30, 40, 50};

    private final Paint cellPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint cellStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint labelStrongPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF rect = new RectF();

    private float cents;
    private boolean detected;
    private boolean inTune;

    TunerMeterView(Context context) {
        super(context);
        cellPaint.setStyle(Paint.Style.FILL);
        cellStrokePaint.setStyle(Paint.Style.STROKE);
        cellStrokePaint.setStrokeWidth(dp(0.5f));
        cellStrokePaint.setColor(0x0AFFFFFF);
        glowPaint.setStyle(Paint.Style.FILL);
        labelPaint.setColor(0x61EBEBF5);
        labelPaint.setTextAlign(Paint.Align.CENTER);
        labelPaint.setTextSize(dp(10));
        labelStrongPaint.setColor(0xD9EBEBF5);
        labelStrongPaint.setTextAlign(Paint.Align.CENTER);
        labelStrongPaint.setTextSize(dp(10));
        labelStrongPaint.setFakeBoldText(true);
    }

    void setCents(float cents, boolean detected) {
        this.cents = cents;
        this.detected = detected;
        this.inTune = detected && Math.abs(cents) <= 5f;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        setMeasuredDimension(width, resolveSize(dp(94), heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float width = getWidth();
        float gap = dp(6);
        float totalGap = gap * (CELL_COUNT - 1);
        float cellW = (width - totalGap) / CELL_COUNT;
        float cellH = dp(64);
        float top = dp(0);

        int activeIdx = -1;
        if (detected) {
            activeIdx = Math.max(0, Math.min(CELL_COUNT - 1, Math.round((cents + 50f) / 10f)));
        }

        for (int i = 0; i < CELL_COUNT; i++) {
            float x = i * (cellW + gap);
            rect.set(x, top, x + cellW, top + cellH);

            int fillColor;
            boolean isActive = (i == activeIdx);
            boolean isCenter = (i == CENTER_INDEX);

            if (isActive) {
                fillColor = inTune ? 0xFF30D9A8 : 0xFFFFC857;
            } else if (isCenter) {
                fillColor = 0x1A30D9A8;
            } else {
                fillColor = 0x0FFFFFFF;
            }

            // Shadow/glow for active cell
            if (isActive) {
                glowPaint.setColor(inTune ? 0x6630D9A8 : 0x4DFFC857);
                glowPaint.setMaskFilter(null);
                // Draw a slightly larger transparent halo behind
                rect.inset(-dp(2), -dp(2));
                cellPaint.setColor(inTune ? 0x3330D9A8 : 0x33FFC857);
                canvas.drawRoundRect(rect, dp(10), dp(10), cellPaint);
                rect.inset(dp(2), dp(2));
            }

            cellPaint.setColor(fillColor);
            canvas.drawRoundRect(rect, dp(8), dp(8), cellPaint);
            if (!isActive) {
                canvas.drawRoundRect(rect, dp(8), dp(8), cellStrokePaint);
            }
        }

        // Labels
        float labelY = top + cellH + dp(18);
        for (int i = 0; i < CELL_COUNT; i++) {
            float x = i * (cellW + gap) + cellW / 2f;
            int t = TICKS[i];
            String label = (t == 0) ? "0" : (t > 0 ? "+" + t : Integer.toString(t));
            canvas.drawText(label, x, labelY, t == 0 ? labelStrongPaint : labelPaint);
        }
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
