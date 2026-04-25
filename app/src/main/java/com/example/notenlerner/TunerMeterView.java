package com.example.notenlerner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.View;

final class TunerMeterView extends View {
    private static final float DISPLAY_CENT_RANGE = 50f;

    private final Paint basePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tickPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint needlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    private float cents;
    private boolean detected;

    TunerMeterView(Context context) {
        super(context);
        basePaint.setColor(0xFF26313A);
        basePaint.setStrokeWidth(dp(6));
        basePaint.setStrokeCap(Paint.Cap.ROUND);

        tickPaint.setColor(0xFF93A3AD);
        tickPaint.setStrokeWidth(dp(2));
        tickPaint.setStrokeCap(Paint.Cap.ROUND);

        needlePaint.setColor(0xFF6EE7B7);
        needlePaint.setStrokeWidth(dp(5));
        needlePaint.setStrokeCap(Paint.Cap.ROUND);

        textPaint.setColor(0xFF93A3AD);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTextSize(dp(13));
    }

    void setCents(float cents, boolean detected) {
        this.cents = cents;
        this.detected = detected;
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

        float left = dp(28);
        float right = getWidth() - dp(28);
        float centerX = (left + right) / 2f;
        float y = dp(44);

        canvas.drawLine(left, y, right, y, basePaint);
        drawTick(canvas, left, y, "-50");
        drawTick(canvas, centerX, y, "0");
        drawTick(canvas, right, y, "+50");

        if (!detected) {
            return;
        }

        float clamped = Math.max(-DISPLAY_CENT_RANGE, Math.min(DISPLAY_CENT_RANGE, cents));
        float x = centerX + (clamped / DISPLAY_CENT_RANGE) * ((right - left) / 2f);
        needlePaint.setColor(Math.abs(cents) <= 5f ? 0xFF6EE7B7 : 0xFFFFC857);
        canvas.drawLine(x, y - dp(32), x, y + dp(24), needlePaint);
    }

    private void drawTick(Canvas canvas, float x, float y, String label) {
        canvas.drawLine(x, y - dp(16), x, y + dp(16), tickPaint);
        canvas.drawText(label, x, y + dp(40), textPaint);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
