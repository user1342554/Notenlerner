package com.example.notenlerner;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.view.View;

/**
 * Music staff renderer for the Notenlehrer screen.
 * Apple-like dark mode: hairline staff on transparent surface, off-white note head, treble clef.
 */
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

    private static final int TARGET_COLOR = 0xFFF5F5F7;

    private final Paint staffPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint notePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stemPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clefPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint accidentalPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF noteOval = new RectF();

    private NoteName note;
    private NoteName playedNote;
    private int playedColor = TARGET_COLOR;

    StaffNoteView(Context context) {
        super(context);
        // Staff lines — fg_1 at ~85% white
        staffPaint.setColor(0xD9F5F5F7);
        staffPaint.setStrokeWidth(dp(1.4f));
        staffPaint.setStyle(Paint.Style.STROKE);
        staffPaint.setStrokeCap(Paint.Cap.ROUND);

        // Note head — fg_0
        notePaint.setColor(0xFFF5F5F7);
        notePaint.setStyle(Paint.Style.FILL);

        stemPaint.setColor(0xFFF5F5F7);
        stemPaint.setStrokeWidth(dp(1.8f));
        stemPaint.setStyle(Paint.Style.STROKE);
        stemPaint.setStrokeCap(Paint.Cap.ROUND);

        clefPaint.setColor(0xFFF5F5F7);
        clefPaint.setTextAlign(Paint.Align.CENTER);
        clefPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));

        accidentalPaint.setColor(0xFFF5F5F7);
        accidentalPaint.setTextAlign(Paint.Align.CENTER);
        accidentalPaint.setTypeface(Typeface.create(Typeface.SERIF, Typeface.BOLD));
    }

    void setNote(NoteName note) {
        this.note = note;
        invalidate();
    }

    void setPlayedNote(NoteName note, int color) {
        this.playedNote = note;
        this.playedColor = color;
        invalidate();
    }

    void clearPlayedNote() {
        if (this.playedNote != null) {
            this.playedNote = null;
            invalidate();
        }
    }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = MeasureSpec.getSize(widthMeasureSpec);
        int desiredHeight = dp(200);
        int height = resolveSize(desiredHeight, heightMeasureSpec);
        setMeasuredDimension(width, height);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        int width = getWidth();
        int height = getHeight();

        float lineSpacing = dp(14);
        float halfSpacing = lineSpacing / 2f;
        float left = dp(28);
        float right = width - dp(20);
        float topLineY = height / 2f - lineSpacing * 2f - dp(6);
        float bottomLineY = topLineY + lineSpacing * 4f;

        // 5 staff lines
        for (int i = 0; i < 5; i++) {
            float y = topLineY + i * lineSpacing;
            canvas.drawLine(left, y, right, y, staffPaint);
        }

        // Treble clef
        clefPaint.setTextSize(dp(56));
        canvas.drawText("𝄞", left + dp(18), bottomLineY + dp(6), clefPaint);

        float targetCenterX = width * 0.62f;

        // Played note: draw to the LEFT of the target so the user can compare positions.
        // Drawn first so the (white) target glyph wins any overlap.
        if (playedNote != null) {
            float playedCenterX = targetCenterX - dp(48);
            drawNoteAt(canvas, playedNote, playedCenterX, playedColor,
                    topLineY, bottomLineY, lineSpacing, halfSpacing);
        }

        if (note != null) {
            drawNoteAt(canvas, note, targetCenterX, TARGET_COLOR,
                    topLineY, bottomLineY, lineSpacing, halfSpacing);
        }
    }

    private void drawNoteAt(Canvas canvas, NoteName n, float centerX, int color,
                            float topLineY, float bottomLineY,
                            float lineSpacing, float halfSpacing) {
        int writtenMidi = n.midiNumber + 12;
        int letterIndex = letterIndex(n.name.charAt(0));
        int writtenOctave = writtenMidi / 12 - 1;
        int bottomLineIndex = 4 * 7 + letterIndex('E');
        int writtenDiatonicIndex = writtenOctave * 7 + letterIndex;
        int stepsAboveBottomLine = writtenDiatonicIndex - bottomLineIndex;
        float centerY = bottomLineY - stepsAboveBottomLine * halfSpacing;

        notePaint.setColor(color);
        stemPaint.setColor(color);
        accidentalPaint.setColor(color);

        drawLedgerLines(canvas, centerX, centerY, topLineY, bottomLineY, lineSpacing, halfSpacing);

        if (n.name.contains("#")) {
            accidentalPaint.setTextSize(dp(26));
            canvas.drawText("♯", centerX - dp(28), centerY + dp(6), accidentalPaint);
        }

        drawNoteHead(canvas, centerX, centerY);
        drawStem(canvas, centerX, centerY, topLineY, bottomLineY);
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
        float ledgerHalfWidth = dp(20);
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

    private void drawNoteHead(Canvas canvas, float centerX, float centerY) {
        // Slanted oval — design uses rotate(-22deg)
        noteOval.set(
                centerX - dp(9.5f),
                centerY - dp(6.8f),
                centerX + dp(9.5f),
                centerY + dp(6.8f)
        );
        canvas.save();
        canvas.rotate(-22f, centerX, centerY);
        canvas.drawOval(noteOval, notePaint);
        canvas.restore();
    }

    private void drawStem(Canvas canvas, float centerX, float centerY, float topLineY, float bottomLineY) {
        float middleY = (topLineY + bottomLineY) / 2f;
        // Notes below middle line: stem up (right side); above: stem down (left side)
        boolean stemUp = centerY > middleY - dp(3);
        if (stemUp) {
            float x = centerX + dp(8.5f);
            canvas.drawLine(x, centerY - dp(1), x, centerY - dp(44), stemPaint);
        } else {
            float x = centerX - dp(8.5f);
            canvas.drawLine(x, centerY + dp(1), x, centerY + dp(44), stemPaint);
        }
    }

    private int letterIndex(char letter) {
        if (letter < NOTE_LETTER_INDEX.length && NOTE_LETTER_INDEX[letter] >= 0) {
            return NOTE_LETTER_INDEX[letter];
        }
        return 0;
    }

    private float dp(float value) {
        return value * getResources().getDisplayMetrics().density;
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
