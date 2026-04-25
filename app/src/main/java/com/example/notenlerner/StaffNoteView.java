package com.example.notenlerner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

final class StaffNoteView extends View {
    private static final int[] NOTE_LETTER_INDEX = new int[128];

    static {
        for (int i = 0; i < NOTE_LETTER_INDEX.length; i++) {
            NOTE_LETTER_INDEX[i] = -1;
        }
        NOTE_LETTER_INDEX['C'] = 0;
        NOTE_LETTER_INDEX['D'] = 1;
        NOTE_LETTER_INDEX['E'] = 2;
        NOTE_LETTER_INDEX['F'] = 3;
        NOTE_LETTER_INDEX['G'] = 4;
        NOTE_LETTER_INDEX['A'] = 5;
        NOTE_LETTER_INDEX['B'] = 6;
    }

    private final Paint staffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint noteStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paperPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF noteOval = new RectF();
    private final RectF paperRect = new RectF();

    private NoteName note;

    StaffNoteView(Context context) {
        super(context);
        staffPaint.setColor(0xFF20272D);
        staffPaint.setStrokeWidth(dp(2));
        staffPaint.setStyle(Paint.Style.STROKE);
        staffPaint.setStrokeCap(Paint.Cap.ROUND);

        notePaint.setColor(0xFF111820);
        notePaint.setStyle(Paint.Style.FILL);

        noteStrokePaint.setColor(0xFF111820);
        noteStrokePaint.setStrokeWidth(dp(2));
        noteStrokePaint.setStyle(Paint.Style.STROKE);
        noteStrokePaint.setStrokeCap(Paint.Cap.ROUND);

        paperPaint.setColor(0xFFF3EFE5);
        paperPaint.setStyle(Paint.Style.FILL);

        textPaint.setColor(0xFF111820);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setFakeBoldText(true);
    }

    void setNote(NoteName note) {
        this.note = note;
        invalidate();
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desiredHeight = dp(158);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        paperRect.set(dp(4), dp(8), width - dp(4), getHeight() - dp(8));
        canvas.drawRoundRect(paperRect, dp(8), dp(8), paperPaint);

        float lineSpacing = dp(13);
        float halfSpacing = lineSpacing / 2f;
        float left = dp(20);
        float right = width - dp(20);
        float topLineY = getHeight() / 2f - lineSpacing * 2f;
        float bottomLineY = topLineY + lineSpacing * 4f;

        int stepsAboveBottomLine = 0;
        if (note != null) {
            int writtenMidi = note.midiNumber + 12;
            int letterIndex = letterIndex(note.name.charAt(0));
            int writtenOctave = writtenMidi / 12 - 1;
            int bottomLineIndex = 4 * 7 + letterIndex('E');
            int writtenDiatonicIndex = writtenOctave * 7 + letterIndex;
            stepsAboveBottomLine = writtenDiatonicIndex - bottomLineIndex;

            float rawNoteY = bottomLineY - stepsAboveBottomLine * halfSpacing;
            float minNoteY = paperRect.top + dp(28);
            float maxNoteY = paperRect.bottom - dp(28);
            float shift = clamp(rawNoteY, minNoteY, maxNoteY) - rawNoteY;
            topLineY += shift;
            bottomLineY += shift;
        }

        for (int i = 0; i < 5; i++) {
            float y = topLineY + i * lineSpacing;
            canvas.drawLine(left, y, right, y, staffPaint);
        }

        textPaint.setTextSize(dp(48));
        textPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
        textPaint.setColor(0xFF111820);
        canvas.drawText("𝄞", left + dp(44), bottomLineY + dp(3), textPaint);

        if (note == null) {
            return;
        }

        float noteCenterX = width * 0.62f;
        float noteCenterY = bottomLineY - stepsAboveBottomLine * halfSpacing;
        drawLedgerLines(canvas, noteCenterX, noteCenterY, topLineY, bottomLineY, lineSpacing, halfSpacing);
        drawNoteHead(canvas, noteCenterX, noteCenterY, lineSpacing);

        if (note.name.contains("#")) {
            textPaint.setTextSize(dp(32));
            textPaint.setTypeface(Typeface.DEFAULT_BOLD);
            textPaint.setColor(0xFF111820);
            canvas.drawText("♯", noteCenterX - dp(42), noteCenterY + dp(11), textPaint);
        }

        drawStem(canvas, noteCenterX, noteCenterY, bottomLineY);
    }

    private void drawLedgerLines(
            Canvas canvas,
            float noteCenterX,
            float noteCenterY,
            float topLineY,
            float bottomLineY,
            float lineSpacing,
            float halfSpacing
    ) {
        float ledgerHalfWidth = dp(28);
        if (noteCenterY < topLineY) {
            for (float y = topLineY - lineSpacing; y >= noteCenterY - halfSpacing; y -= lineSpacing) {
                canvas.drawLine(noteCenterX - ledgerHalfWidth, y, noteCenterX + ledgerHalfWidth, y, staffPaint);
            }
        } else if (noteCenterY > bottomLineY) {
            for (float y = bottomLineY + lineSpacing; y <= noteCenterY + halfSpacing; y += lineSpacing) {
                canvas.drawLine(noteCenterX - ledgerHalfWidth, y, noteCenterX + ledgerHalfWidth, y, staffPaint);
            }
        }
    }

    private void drawNoteHead(Canvas canvas, float centerX, float centerY, float lineSpacing) {
        noteOval.set(
                centerX - lineSpacing * 0.9f,
                centerY - lineSpacing * 0.58f,
                centerX + lineSpacing * 0.9f,
                centerY + lineSpacing * 0.58f
        );
        canvas.save();
        canvas.rotate(-18f, centerX, centerY);
        canvas.drawOval(noteOval, notePaint);
        canvas.drawOval(noteOval, noteStrokePaint);
        canvas.restore();
    }

    private void drawStem(Canvas canvas, float centerX, float centerY, float bottomLineY) {
        float stemHeight = dp(58);
        float x = centerX + dp(14);
        if (centerY < bottomLineY - dp(16)) {
            x = centerX - dp(14);
            canvas.drawLine(x, centerY, x, centerY + stemHeight, noteStrokePaint);
        } else {
            canvas.drawLine(x, centerY, x, centerY - stemHeight, noteStrokePaint);
        }
    }

    private int letterIndex(char letter) {
        if (letter < NOTE_LETTER_INDEX.length && NOTE_LETTER_INDEX[letter] >= 0) {
            return NOTE_LETTER_INDEX[letter];
        }
        return 0;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
