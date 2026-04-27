package com.example.notenlerner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.view.View;

/**
 * Guitar fretboard for the Pentatonik screen. Highlights scale notes and the root.
 * Strings are drawn high-E (top) → low-E (bottom), 13 frets including the open position.
 */
final class FretboardView extends View {
    private static final String[] NOTE_NAMES = {
            "C", "C\u266F", "D", "D\u266F", "E", "F", "F\u266F", "G", "G\u266F", "A", "A\u266F", "B"
    };
    private static final int[] STRING_MIDIS_HIGH_TO_LOW = {64, 59, 55, 50, 45, 40};
    private static final int FRET_COUNT = 13;

    private final Paint bgPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint nutPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fretPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stringPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint inlayPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotFillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint dotTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint rootTextPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint fretNumPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF backgroundRect = new RectF();

    private int rootIdx = 9; // A
    private int activePitchClass = 9;
    private int positionStartFret = 0;
    private boolean[] scaleClasses = new boolean[12];

    FretboardView(Context context) {
        super(context);
        bgPaint.setStyle(Paint.Style.FILL);

        nutPaint.setColor(0xCCF5F5F7);
        nutPaint.setStyle(Paint.Style.FILL);

        fretPaint.setColor(0x2DF5F5F7);
        fretPaint.setStrokeWidth(dp(1f));

        stringPaint.setStyle(Paint.Style.STROKE);
        stringPaint.setColor(0x8CF5F5F7);

        inlayPaint.setColor(0x59FFC857);
        inlayPaint.setStyle(Paint.Style.FILL);

        dotFillPaint.setStyle(Paint.Style.FILL);
        dotStrokePaint.setStyle(Paint.Style.STROKE);
        dotStrokePaint.setStrokeWidth(dp(1.4f));

        dotTextPaint.setColor(0xFFFFC857);
        dotTextPaint.setTextAlign(Paint.Align.CENTER);
        dotTextPaint.setTextSize(dp(9.5f));
        dotTextPaint.setFakeBoldText(true);
        dotTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        rootTextPaint.setColor(0xFF1A1300);
        rootTextPaint.setTextAlign(Paint.Align.CENTER);
        rootTextPaint.setTextSize(dp(9.5f));
        rootTextPaint.setFakeBoldText(true);
        rootTextPaint.setTypeface(Typeface.DEFAULT_BOLD);

        fretNumPaint.setColor(0x66F5F5F7);
        fretNumPaint.setTextAlign(Paint.Align.CENTER);
        fretNumPaint.setTextSize(dp(9.5f));
    }

    void setScale(int rootIdx, int[] intervals) {
        this.rootIdx = ((rootIdx % 12) + 12) % 12;
        this.activePitchClass = this.rootIdx;
        boolean[] set = new boolean[12];
        for (int interval : intervals) {
            set[((this.rootIdx + interval) % 12 + 12) % 12] = true;
        }
        this.scaleClasses = set;
        invalidate();
    }

    void setPractice(int rootIdx, int[] intervals, int positionStartFret, int activePitchClass) {
        this.rootIdx = ((rootIdx % 12) + 12) % 12;
        this.positionStartFret = Math.max(0, Math.min(10, positionStartFret));
        this.activePitchClass = ((activePitchClass % 12) + 12) % 12;
        boolean[] set = new boolean[12];
        for (int interval : intervals) {
            set[((this.rootIdx + interval) % 12 + 12) % 12] = true;
        }
        this.scaleClasses = set;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desired = dp(186);
        setMeasuredDimension(width, resolveSize(desired, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth();
        float h = getHeight();

        // Warm gradient background
        backgroundRect.set(dp(2), dp(2), w - dp(2), h - dp(2));
        bgPaint.setShader(new LinearGradient(
                0, 0, 0, h,
                0xFF1A1310, 0xFF0E0A08, Shader.TileMode.CLAMP));
        canvas.drawRoundRect(backgroundRect, dp(18), dp(18), bgPaint);
        bgPaint.setShader(null);

        float leftPad = dp(28);
        float rightPad = dp(12);
        float usable = w - leftPad - rightPad;
        float fretW = usable / FRET_COUNT;
        float stringSpacing = dp(22);
        float top = dp(22);
        float boardH = stringSpacing * (STRING_MIDIS_HIGH_TO_LOW.length - 1);

        // Nut
        canvas.drawRoundRect(
                leftPad - dp(3), top - dp(2),
                leftPad, top + boardH + dp(2),
                dp(1), dp(1), nutPaint);

        // Frets
        for (int i = 0; i < FRET_COUNT; i++) {
            float x = leftPad + (i + 1) * fretW;
            canvas.drawLine(x, top, x, top + boardH, fretPaint);
        }

        // Inlays at frets 3, 5, 7, 9
        float inlayR = dp(3);
        for (int f : new int[]{3, 5, 7, 9}) {
            float cx = leftPad + (f - 0.5f) * fretW;
            canvas.drawCircle(cx, top + boardH / 2f, inlayR, inlayPaint);
        }
        // 12th-fret double inlay
        float cx12 = leftPad + 11.5f * fretW;
        canvas.drawCircle(cx12, top + boardH / 2f - dp(14), inlayR, inlayPaint);
        canvas.drawCircle(cx12, top + boardH / 2f + dp(14), inlayR, inlayPaint);

        // Current five-fret practice position.
        float posLeft = positionStartFret == 0 ? leftPad - dp(18) : leftPad + (positionStartFret - 1) * fretW;
        float posRight = leftPad + (positionStartFret + 4) * fretW;
        cellHighlight(canvas, posLeft, top - dp(13), Math.min(posRight, w - rightPad), top + boardH + dp(13));

        // Strings + scale notes
        for (int si = 0; si < STRING_MIDIS_HIGH_TO_LOW.length; si++) {
            float y = top + si * stringSpacing;
            stringPaint.setStrokeWidth(dp(0.6f) + si * dp(0.18f));
            canvas.drawLine(leftPad, y, w - rightPad, y, stringPaint);

            int openMidi = STRING_MIDIS_HIGH_TO_LOW[si];
            for (int fret = 0; fret <= FRET_COUNT; fret++) {
                int midi = openMidi + fret;
                int pc = ((midi % 12) + 12) % 12;
                if (!scaleClasses[pc]) continue;
                boolean inPosition = fret >= positionStartFret && fret <= positionStartFret + 4;

                float dotCx = (fret == 0) ? (leftPad - dp(14)) : leftPad + (fret - 0.5f) * fretW;
                boolean isRoot = (pc == rootIdx);
                boolean isActive = (pc == activePitchClass) && inPosition;
                float r = dp(9);

                if (isActive) {
                    dotFillPaint.setColor(0xFFFFF1B8);
                    canvas.drawCircle(dotCx, y, r, dotFillPaint);
                    canvas.drawText(NOTE_NAMES[pc], dotCx, y + dp(3.5f), rootTextPaint);
                } else if (isRoot) {
                    dotFillPaint.setColor(inPosition ? 0xFFFFC857 : 0x66FFC857);
                    canvas.drawCircle(dotCx, y, r, dotFillPaint);
                    canvas.drawText(NOTE_NAMES[pc], dotCx, y + dp(3.5f), rootTextPaint);
                } else {
                    dotFillPaint.setColor(inPosition ? 0xFF131316 : 0xFF0B0B0D);
                    dotStrokePaint.setColor(inPosition ? 0xFFFFC857 : 0x55FFC857);
                    canvas.drawCircle(dotCx, y, r, dotFillPaint);
                    canvas.drawCircle(dotCx, y, r, dotStrokePaint);
                    canvas.drawText(NOTE_NAMES[pc], dotCx, y + dp(3.5f), dotTextPaint);
                }
            }
        }

        // Fret numbers
        float numY = top + boardH + dp(16);
        for (int f : new int[]{3, 5, 7, 9, 12}) {
            float fx = leftPad + (f - 0.5f) * fretW;
            canvas.drawText(Integer.toString(f), fx, numY, fretNumPaint);
        }
    }

    private float dp(float v) {
        return v * getResources().getDisplayMetrics().density;
    }

    private void cellHighlight(Canvas canvas, float left, float top, float right, float bottom) {
        dotFillPaint.setColor(0x18FFC857);
        canvas.drawRoundRect(left, top, right, bottom, dp(12), dp(12), dotFillPaint);
    }

    private int dp(int v) {
        return (int) (v * getResources().getDisplayMetrics().density + 0.5f);
    }
}
