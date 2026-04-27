package com.example.notenlerner;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Notenlerner design tokens + UI builders. All colors mirror the design bundle's CSS variables.
 */
final class Ui {
    // Surfaces
    static final int BG_0 = 0xFF000000;
    static final int BG_1 = 0xFF0B0B0D;
    static final int BG_2 = 0xFF131316;
    static final int BG_3 = 0xFF1C1C1F;
    static final int BG_ELEV = 0xFF202024;
    static final int HAIRLINE = 0x14FFFFFF;
    static final int HAIRLINE_STRONG = 0x24FFFFFF;

    // Text
    static final int FG_0 = 0xFFF5F5F7;
    static final int FG_1 = 0xD9EBEBF5;
    static final int FG_2 = 0x99EBEBF5;
    static final int FG_3 = 0x61EBEBF5;

    // Accents
    static final int MINT = 0xFF30D9A8;
    static final int MINT_DIM = 0x2E30D9A8;
    static final int AMBER = 0xFFFFC857;
    static final int AMBER_DIM = 0x2EFFC857;
    static final int CORAL = 0xFFFF6F6F;
    static final int BLUE = 0xFF0A84FF;
    static final int VIOLET = 0xFFBF7BFF;

    private Ui() {}

    static int dp(Context ctx, float v) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, v, ctx.getResources().getDisplayMetrics()));
    }

    /** Rounded card background with hairline border. */
    static GradientDrawable card(Context ctx, int fillColor, float radiusDp, int strokeColor) {
        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(fillColor);
        d.setCornerRadius(dp(ctx, radiusDp));
        if (strokeColor != Color.TRANSPARENT) {
            d.setStroke(dp(ctx, 1f), strokeColor);
        }
        return d;
    }

    /** Eyebrow label — small uppercase tracking. */
    static TextView eyebrow(Context ctx, String text, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text != null ? text.toUpperCase() : "");
        tv.setTextColor(color);
        tv.setTextSize(11);
        tv.setLetterSpacing(0.12f);
        tv.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
        return tv;
    }

    static TextView title(Context ctx, String text, float size) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(FG_0);
        tv.setTextSize(size);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setLetterSpacing(-0.025f);
        return tv;
    }

    /** Big rounded display number — like CSS .nl-display. */
    static TextView display(Context ctx, String text, float size, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(size);
        tv.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        tv.setLetterSpacing(-0.04f);
        tv.setIncludeFontPadding(false);
        return tv;
    }

    static TextView body(Context ctx, String text, float size, int color) {
        TextView tv = new TextView(ctx);
        tv.setText(text);
        tv.setTextColor(color);
        tv.setTextSize(size);
        tv.setLetterSpacing(-0.01f);
        return tv;
    }

    /** Solid pill button. */
    static TextView button(Context ctx, String text, int bg, int textColor, int height) {
        TextView b = new TextView(ctx);
        b.setText(text);
        b.setTextColor(textColor);
        b.setTextSize(15);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setLetterSpacing(-0.01f);
        b.setGravity(Gravity.CENTER);
        b.setBackground(card(ctx, bg, 14, Color.TRANSPARENT));
        b.setMinimumHeight(dp(ctx, height));
        b.setPadding(dp(ctx, 14), 0, dp(ctx, 14), 0);
        b.setClickable(true);
        b.setFocusable(true);
        return b;
    }

    /** Ghost button with hairline border. */
    static TextView ghostButton(Context ctx, String text) {
        TextView b = button(ctx, text, Color.TRANSPARENT, FG_0, 50);
        b.setBackground(card(ctx, Color.TRANSPARENT, 14, HAIRLINE_STRONG));
        return b;
    }

    /** Stat pill — small rounded card with eyebrow label and big value. */
    static LinearLayout statPill(Context ctx, String label, String value, int valueColor) {
        LinearLayout col = new LinearLayout(ctx);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackground(card(ctx, BG_2, 16, HAIRLINE));
        int p = dp(ctx, 12);
        col.setPadding(p, dp(ctx, 12), p, dp(ctx, 12));

        // Stat-pill labels live in narrow columns (3-up on Home), so we tighten the
        // eyebrow and force a single line with end-ellipsis instead of breaking the
        // word in two ("GENAUIGKE / IT").
        TextView lbl = eyebrow(ctx, label, FG_2);
        lbl.setTextSize(10);
        lbl.setLetterSpacing(0.06f);
        lbl.setSingleLine(true);
        lbl.setEllipsize(android.text.TextUtils.TruncateAt.END);
        col.addView(lbl, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView val = display(ctx, value, 22, valueColor);
        LinearLayout.LayoutParams vp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        vp.topMargin = dp(ctx, 4);
        col.addView(val, vp);
        return col;
    }

    /** Hairline divider line — 1px @ HAIRLINE color. */
    static View hairline(Context ctx) {
        View v = new View(ctx);
        v.setBackgroundColor(HAIRLINE);
        return v;
    }

    /** LinearLayout.LayoutParams with weight=1, full-width row. */
    static LinearLayout.LayoutParams weight1() {
        return new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
    }

    static LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    /** Apply margin to the most-recent layout params. */
    static LinearLayout.LayoutParams margin(LinearLayout.LayoutParams lp, Context ctx,
                                            int l, int t, int r, int b) {
        lp.setMargins(dp(ctx, l), dp(ctx, t), dp(ctx, r), dp(ctx, b));
        return lp;
    }
}
