package com.example.notenlerner;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;

import java.util.Locale;
import java.util.Random;

/**
 * Notenlerner — single-Activity app driving five view-state screens
 * (Home, Notenlehrer, Stimmgerät, Pentatonik, Fingerübungen).
 *
 * The visual language follows the iOS-style dark-mode design bundle:
 *   pure-black surface (#000) with bg_2 cards, mint/blue/amber/violet accents,
 *   hairline borders (8% white), generous whitespace, rounded 22dp cards.
 */
public final class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 1001;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TrainingSession trainingSession = new TrainingSession(new Random());
    private final TunerSession tunerSession = new TunerSession();
    private final PentatonikSession pentatonikSession = new PentatonikSession();

    private PitchTracker pitchTracker;
    private TunerDebugLogger tunerDebugLogger;

    private FrameLayout root;
    private View homeView, trainerView, tunerView, pentatonikView, fingerView;

    // Home stat pills (refreshed on every showHome so values reflect the live session)
    private TextView homeAccuracyValue, homeHitsValue, homeStreakValue;

    // Trainer widgets
    private StaffNoteView staffNoteView;
    private TextView trainerTargetText, trainerTargetHz;
    private TextView trainerStatusText, trainerStatHits, trainerStatAccuracy;
    private GradientDrawable trainerStaffCardBg;
    private TextView trainerToggleButton;
    private final SparseArray<TextView> trainerModeButtons = new SparseArray<>();

    // Tuner widgets
    private TunerMeterView tunerMeterView;
    private TextView tunerLetter, tunerNoteOctave, tunerLabel, tunerCents;
    private TextView tunerFreq, tunerTarget;
    private View tunerCircleRing;
    private final SparseArray<View> tunerStringTiles = new SparseArray<>();

    // Pentatonik widgets
    private TextView pentHeadline, pentNotes;
    private FretboardView pentFretboard;
    private TextView pentPositionValue, pentBpmValue, pentStepValue, pentStartButton;
    private final SparseArray<TextView> pentRootButtons = new SparseArray<>();
    private TextView pentMollBtn, pentDurBtn;
    private int pentBpm = 80;
    private boolean pentRunning = false;
    private final Runnable pentStepRunnable = new Runnable() {
        @Override public void run() {
            if (!pentRunning) return;
            pentatonikSession.nextStep();
            refreshPentatonik();
            mainHandler.postDelayed(this, Math.round(60000f / pentBpm));
        }
    };

    // Finger widgets
    private PatternGridView fingerPatternGrid;
    private TextView fingerExerciseTitle, fingerExerciseSub, fingerEyebrow, fingerBpmText, fingerStepText, fingerRoundText;
    private TextView fingerStartButton;
    private SeekBar fingerTempoSeek;
    private LinearLayout fingerCarousel;
    private int fingerExerciseIdx = 0;
    private int fingerBpm = 80;
    private int fingerTick = 0;
    private int fingerRound = 1;
    private boolean fingerRunning = false;
    private final Runnable fingerStepRunnable = new Runnable() {
        @Override public void run() {
            if (!fingerRunning) return;
            fingerTick = (fingerTick + 1) % 16;
            if (fingerTick == 0) fingerRound++;
            if (fingerPatternGrid != null) fingerPatternGrid.setActive(fingerTick);
            refreshFingerProgress();
            mainHandler.postDelayed(this, Math.round(60000f / fingerBpm));
        }
    };

    private boolean userWantsTracking = true;
    private boolean trainerVisible, tunerVisible;
    private boolean resumed;

    private int statusBarHeightDp;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        statusBarHeightDp = computeStatusBarHeightDp();

        root = new FrameLayout(this);
        root.setBackgroundColor(Ui.BG_0);
        setContentView(root);

        homeView = buildHome();
        trainerView = buildTrainer();
        tunerView = buildTuner();
        pentatonikView = buildPentatonik();
        fingerView = buildFinger();

        showHome();

        tunerDebugLogger = new TunerDebugLogger(this);
        pitchTracker = new PitchTracker(this, new PitchTracker.Listener() {
            @Override public void onPitch(DetectedPitch pitch) {
                mainHandler.post(() -> {
                    if (trainerVisible) updatePitch(pitch);
                    else if (tunerVisible) updateTuner(pitch);
                });
            }
            @Override public void onError(String message) {
                mainHandler.post(() -> {
                    if (trainerStatusText != null) trainerStatusText.setText(message);
                    if (trainerToggleButton != null) trainerToggleButton.setText(getString(R.string.trainer_btn_start));
                });
            }
        });

        refreshTargetAndStats();

        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, REQUEST_RECORD_AUDIO);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        resumed = true;
        if (userWantsTracking
                && (trainerVisible || tunerVisible)
                && pitchTracker != null
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startTracking();
        }
    }

    @Override
    protected void onPause() {
        resumed = false;
        if (pitchTracker != null && pitchTracker.isRunning()) pitchTracker.stop();
        if (fingerRunning) {
            fingerRunning = false;
            mainHandler.removeCallbacks(fingerStepRunnable);
        }
        stopPentPractice();
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (pitchTracker != null) pitchTracker.stop();
        mainHandler.removeCallbacks(fingerStepRunnable);
        mainHandler.removeCallbacks(pentStepRunnable);
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (resumed && userWantsTracking && (trainerVisible || tunerVisible)) startTracking();
        } else if (trainerStatusText != null) {
            trainerStatusText.setText(getString(R.string.perm_required));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Navigation
    // ─────────────────────────────────────────────────────────────

    private void showHome() {
        stopTracking();
        stopPentPractice();
        trainerVisible = false; tunerVisible = false;
        refreshHomeStats();
        swap(homeView);
    }

    private void refreshHomeStats() {
        if (homeAccuracyValue == null) return;
        int acc = trainingSession.accuracyPercent();
        homeAccuracyValue.setText(acc + "%");
        // Mute the mint accent until the user has actually scored — a lit-up "0%" looks
        // like an alert, not a placeholder.
        homeAccuracyValue.setTextColor(trainingSession.attempts() == 0 ? Ui.FG_3 : Ui.MINT);

        homeHitsValue.setText(String.valueOf(trainingSession.correct()));
        homeHitsValue.setTextColor(trainingSession.correct() == 0 ? Ui.FG_3 : Ui.FG_0);

        int streak = trainingSession.streak();
        homeStreakValue.setText(String.valueOf(streak));
        homeStreakValue.setTextColor(streak == 0 ? Ui.FG_3 : Ui.AMBER);
    }

    private void showTrainer() {
        stopPentPractice();
        trainerVisible = true; tunerVisible = false;
        swap(trainerView);
        startTrackingIfNeeded();
    }

    private void showTuner() {
        stopPentPractice();
        trainerVisible = false; tunerVisible = true;
        userWantsTracking = true;
        if (tunerDebugLogger != null) tunerDebugLogger.reset();
        swap(tunerView);
        resetTunerDisplay();
        startTrackingIfNeeded();
    }

    private void showPentatonik() {
        stopTracking();
        trainerVisible = false; tunerVisible = false;
        swap(pentatonikView);
        refreshPentatonik();
    }

    private void showFinger() {
        stopTracking();
        stopPentPractice();
        trainerVisible = false; tunerVisible = false;
        swap(fingerView);
    }

    private void swap(View view) {
        root.removeAllViews();
        root.addView(view, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
    }

    private void stopTracking() {
        if (pitchTracker != null && pitchTracker.isRunning()) pitchTracker.stop();
    }

    private void startTrackingIfNeeded() {
        if (userWantsTracking && resumed && pitchTracker != null
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startTracking();
        }
    }

    private void startTracking() {
        if (!resumed) return;
        pitchTracker.start();
        if (trainerVisible && trainerStatusText != null) {
            trainerStatusText.setText(getString(R.string.trainer_status_play));
            trainerStatusText.setTextColor(Ui.MINT);
            trainerToggleButton.setText(getString(R.string.trainer_btn_stop));
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Home screen
    // ─────────────────────────────────────────────────────────────

    private View buildHome() {
        ScrollView scroll = createScrollContainer();
        LinearLayout col = createScrollColumn(scroll, /*topPadding*/ 60);

        // Title block
        TextView eyebrow = Ui.eyebrow(this, getString(R.string.home_eyebrow, 1), Ui.MINT);
        col.addView(eyebrow, padHorizontal(Ui.matchWrap(), 20));

        TextView title = Ui.title(this, getString(R.string.app_name), 34);
        title.setLetterSpacing(-0.03f);
        LinearLayout.LayoutParams tp = padHorizontal(Ui.matchWrap(), 20);
        tp.topMargin = Ui.dp(this, 6);
        col.addView(title, tp);

        TextView sub = Ui.body(this, getString(R.string.home_subtitle), 17, Ui.FG_2);
        LinearLayout.LayoutParams sp = padHorizontal(Ui.matchWrap(), 20);
        sp.topMargin = Ui.dp(this, 4);
        col.addView(sub, sp);

        // Stats row
        LinearLayout stats = new LinearLayout(this);
        stats.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams sr = padHorizontal(Ui.matchWrap(), 20);
        sr.topMargin = Ui.dp(this, 18);
        col.addView(stats, sr);

        // Real stats from the live training session — no placeholder numbers.
        // Until the user has played a target, these show 0% / 0 / 0.
        LinearLayout accPill = Ui.statPill(this, getString(R.string.home_stat_accuracy), "0%", Ui.MINT);
        homeAccuracyValue = (TextView) accPill.getChildAt(1);
        stats.addView(accPill, marginRight(Ui.weight1(), 5));

        LinearLayout hitsPill = Ui.statPill(this, getString(R.string.home_stat_hits), "0", Ui.FG_0);
        homeHitsValue = (TextView) hitsPill.getChildAt(1);
        stats.addView(hitsPill, marginHoriz(Ui.weight1(), 5, 5));

        LinearLayout streakPill = Ui.statPill(this, getString(R.string.home_stat_streak), "0", Ui.AMBER);
        homeStreakValue = (TextView) streakPill.getChildAt(1);
        stats.addView(streakPill, marginLeft(Ui.weight1(), 5));

        // Tile grid 2x2
        LinearLayout gridRow1 = new LinearLayout(this);
        gridRow1.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams gr1 = padHorizontal(Ui.matchWrap(), 20);
        gr1.topMargin = Ui.dp(this, 22);
        col.addView(gridRow1, gr1);

        gridRow1.addView(buildHomeTile(getString(R.string.tile_trainer),
                getString(R.string.tile_trainer_sub), Ui.MINT, 0, this::showTrainer),
                marginRight(Ui.weight1(), 6));
        gridRow1.addView(buildHomeTile(getString(R.string.tile_tuner),
                getString(R.string.tile_tuner_sub), Ui.BLUE, 1, this::showTuner),
                marginLeft(Ui.weight1(), 6));

        LinearLayout gridRow2 = new LinearLayout(this);
        gridRow2.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams gr2 = padHorizontal(Ui.matchWrap(), 20);
        gr2.topMargin = Ui.dp(this, 12);
        col.addView(gridRow2, gr2);

        gridRow2.addView(buildHomeTile(getString(R.string.tile_pentatonik),
                getString(R.string.tile_pentatonik_sub), Ui.AMBER, 2, this::showPentatonik),
                marginRight(Ui.weight1(), 6));
        gridRow2.addView(buildHomeTile(getString(R.string.tile_finger),
                getString(R.string.tile_finger_sub), Ui.VIOLET, 3, this::showFinger),
                marginLeft(Ui.weight1(), 6));

        // (Removed the "Heute geübt · 12 Min · 38 Noten · 87% korrekt" recent-practice
        // card — it had no real data backing it, just placeholder strings.)

        col.addView(spacer(28));
        return scroll;
    }

    private View buildHomeTile(String title, String subtitle, int accent, int corner, Runnable onClick) {
        // Compact, content-forward tile: icon top-left + title/subtitle below.
        // Replaced the previous tall radial-haze gradient (which read as "stretched")
        // with a flat BG_2 card and a subtle 1px accent-tinted hairline so the accent
        // colour still differentiates tiles without dominating the surface.
        FrameLayout container = new FrameLayout(this);
        int height = Ui.dp(this, 132);
        container.setMinimumHeight(height);
        container.setClickable(true);
        container.setFocusable(true);
        container.setOnClickListener(v -> onClick.run());

        GradientDrawable card = new GradientDrawable();
        card.setShape(GradientDrawable.RECTANGLE);
        card.setColor(Ui.BG_2);
        card.setCornerRadius(Ui.dp(this, 22));
        // Accent-tinted hairline at ~30% alpha — visible but not loud.
        int hairlineAccent = (accent & 0x00FFFFFF) | 0x4D000000;
        card.setStroke(Ui.dp(this, 1), hairlineAccent);
        container.setBackground(card);

        // Icon: small rounded square with the accent dot, top-left
        FrameLayout icon = new FrameLayout(this);
        GradientDrawable ib = new GradientDrawable();
        ib.setShape(GradientDrawable.RECTANGLE);
        ib.setColor((accent & 0x00FFFFFF) | 0x1F000000); // accent at ~12% alpha
        ib.setCornerRadius(Ui.dp(this, 10));
        icon.setBackground(ib);
        TextView iconDot = new TextView(this);
        iconDot.setText("●");
        iconDot.setTextColor(accent);
        iconDot.setTextSize(12);
        iconDot.setGravity(Gravity.CENTER);
        icon.addView(iconDot, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));
        FrameLayout.LayoutParams ip = new FrameLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36));
        ip.leftMargin = Ui.dp(this, 16);
        ip.topMargin = Ui.dp(this, 16);
        container.addView(icon, ip);

        // Title + subtitle anchored to bottom-left. Subtitle gets up to 2 lines so
        // "Noten lesen & spielen" / "Geläufigkeit aufbauen" don't truncate.
        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);

        TextView t = Ui.title(this, title, 17);
        t.setTextColor(Ui.FG_0);
        t.setSingleLine(true);
        t.setEllipsize(android.text.TextUtils.TruncateAt.END);
        text.addView(t, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        TextView s = Ui.body(this, subtitle, 12, Ui.FG_2);
        s.setMaxLines(2);
        s.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(this, 2);
        text.addView(s, sp);

        FrameLayout.LayoutParams tp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        tp.gravity = Gravity.START | Gravity.BOTTOM;
        tp.leftMargin = Ui.dp(this, 16);
        tp.rightMargin = Ui.dp(this, 16);
        tp.bottomMargin = Ui.dp(this, 14);
        container.addView(text, tp);

        return container;
    }

    // ─────────────────────────────────────────────────────────────
    // Trainer (Notenlehrer)
    // ─────────────────────────────────────────────────────────────

    private View buildTrainer() {
        // Layout: [TopBar overlay (top)] + [Scroll content] + [Action bar overlay (bottom)].
        // The action bar (Stop / Weiter / Reset) is the primary control surface so it's
        // pinned to the bottom and always visible — no scrolling required.
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.BG_0);

        ScrollView scroll = createScrollContainer();
        frame.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout col = createScrollColumn(scroll, /*topPadding*/ 56);
        // Reserve room at the bottom for the pinned action bar:
        //   button height 48 + bar padding 8+12 = 68dp, plus the gesture-bar inset.
        // Add a small cushion so the last meter labels aren't kissing the bar.
        col.setPadding(col.getPaddingLeft(), col.getPaddingTop(), col.getPaddingRight(),
                Ui.dp(this, 84) + Ui.dp(this, computeNavBarHeightDp()));

        // Mode selector — two rows of three pill buttons
        TrainingSession.Mode[] modesRow1 = {
                TrainingSession.Mode.OPEN_STRINGS,
                TrainingSession.Mode.C_MAJOR_START,
                TrainingSession.Mode.C_MAJOR_FULL,
        };
        String[] labelsRow1 = {
                getString(R.string.mode_open_strings),
                getString(R.string.mode_cmajor_start),
                getString(R.string.mode_cmajor_full),
        };
        TrainingSession.Mode[] modesRow2 = {
                TrainingSession.Mode.COMMON_NOTES,
                TrainingSession.Mode.FRETS_0_5,
                TrainingSession.Mode.FRETS_0_12,
        };
        String[] labelsRow2 = {
                getString(R.string.mode_common),
                getString(R.string.mode_frets_0_5),
                getString(R.string.mode_frets_0_12),
        };

        LinearLayout.LayoutParams rowParams = padHorizontal(Ui.matchWrap(), 20);

        LinearLayout segRow1 = createSegmented();
        col.addView(segRow1, rowParams);
        for (int i = 0; i < modesRow1.length; i++) {
            final TrainingSession.Mode m = modesRow1[i];
            TextView btn = createSegmentedButton(labelsRow1[i], false, () -> onModeClicked(m));
            segRow1.addView(btn, segItemParams());
            trainerModeButtons.put(m.ordinal(), btn);
        }

        LinearLayout.LayoutParams rowParams2 = padHorizontal(Ui.matchWrap(), 20);
        rowParams2.topMargin = Ui.dp(this, 8);

        LinearLayout segRow2 = createSegmented();
        col.addView(segRow2, rowParams2);
        for (int i = 0; i < modesRow2.length; i++) {
            final TrainingSession.Mode m = modesRow2[i];
            TextView btn = createSegmentedButton(labelsRow2[i], false, () -> onModeClicked(m));
            segRow2.addView(btn, segItemParams());
            trainerModeButtons.put(m.ordinal(), btn);
        }

        // Staff card
        LinearLayout staffCard = new LinearLayout(this);
        staffCard.setOrientation(LinearLayout.VERTICAL);
        trainerStaffCardBg = Ui.card(this, Ui.BG_2, 22, Ui.HAIRLINE_STRONG);
        staffCard.setBackground(trainerStaffCardBg);
        int sp = Ui.dp(this, 14);
        staffCard.setPadding(sp, Ui.dp(this, 18), sp, Ui.dp(this, 14));

        // Eyebrow + Hz row
        LinearLayout staffHeader = new LinearLayout(this);
        staffHeader.setOrientation(LinearLayout.HORIZONTAL);
        TextView staffEyebrow = Ui.eyebrow(this, getString(R.string.trainer_target_label), Ui.FG_2);
        LinearLayout.LayoutParams ehp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        staffHeader.addView(staffEyebrow, ehp);

        trainerTargetHz = Ui.body(this, "—", 13, Ui.FG_2);
        staffHeader.addView(trainerTargetHz);
        staffCard.addView(staffHeader);

        // Staff: tall enough that low notes (E2/F2) and high notes (E5) both fit, and that
        // the played-note glyph sits next to the target without crowding.
        staffNoteView = new StaffNoteView(this);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 200));
        stp.topMargin = Ui.dp(this, 8);
        staffCard.addView(staffNoteView, stp);

        // Target letter (slimmed from 40 → 32sp)
        trainerTargetText = Ui.display(this, "—", 32, Ui.MINT);
        trainerTargetText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams ttp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        ttp.topMargin = Ui.dp(this, 2);
        staffCard.addView(trainerTargetText, ttp);

        LinearLayout.LayoutParams scp = padHorizontal(Ui.matchWrap(), 20);
        scp.topMargin = Ui.dp(this, 14);
        col.addView(staffCard, scp);

        // Status text
        trainerStatusText = Ui.body(this, getString(R.string.trainer_status_play), 13, Ui.FG_2);
        trainerStatusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams stsp = padHorizontal(Ui.matchWrap(), 20);
        stsp.topMargin = Ui.dp(this, 10);
        col.addView(trainerStatusText, stsp);

        // Stat pills row
        LinearLayout statRow = new LinearLayout(this);
        statRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams srp = padHorizontal(Ui.matchWrap(), 20);
        srp.topMargin = Ui.dp(this, 10);
        col.addView(statRow, srp);

        LinearLayout statHits = Ui.statPill(this, getString(R.string.home_stat_hits), "0/0", Ui.FG_0);
        trainerStatHits = (TextView) ((LinearLayout) statHits).getChildAt(1);
        statRow.addView(statHits, marginRight(Ui.weight1(), 5));

        LinearLayout statAcc = Ui.statPill(this, getString(R.string.home_stat_accuracy), "0%", Ui.FG_0);
        trainerStatAccuracy = (TextView) ((LinearLayout) statAcc).getChildAt(1);
        statRow.addView(statAcc, marginLeft(Ui.weight1(), 5));

        // Action row pinned to the bottom of the frame (below) — built outside the
        // scroll column so the user never has to scroll to find Stop/Weiter/Reset.

        // Top bar overlay (above scroll). No trailing mic chip — the OS already
        // shows a recording-indicator dot in the status bar when the mic is live,
        // so the in-app pill was redundant noise.
        frame.addView(buildTopBar(getString(R.string.tile_trainer), true, null),
                topBarParams());

        // Pinned bottom action bar — Stop / Weiter / Reset always reachable.
        frame.addView(buildTrainerActionBar(), trainerActionBarParams());

        return frame;
    }

    private View buildTrainerActionBar() {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setBackgroundColor(Ui.BG_0);
        int pad = Ui.dp(this, 20);
        // Bottom padding includes the gesture-bar / nav-bar inset so buttons aren't
        // crowded against the system home indicator.
        bar.setPadding(pad, Ui.dp(this, 8), pad,
                Ui.dp(this, 12) + Ui.dp(this, computeNavBarHeightDp()));

        trainerToggleButton = Ui.button(this, getString(R.string.trainer_btn_stop), Ui.MINT, 0xFF001A12, 48);
        trainerToggleButton.setOnClickListener(v -> toggleTracking());
        bar.addView(trainerToggleButton, marginRight(Ui.weight1(), 4));

        TextView nextBtn = Ui.ghostButton(this, getString(R.string.trainer_btn_next));
        nextBtn.setOnClickListener(v -> {
            trainingSession.skip();
            refreshTargetAndStats();
            if (trainerStatusText != null) {
                trainerStatusText.setText(getString(R.string.trainer_status_play));
                trainerStatusText.setTextColor(Ui.MINT);
            }
        });
        bar.addView(nextBtn, marginHoriz(Ui.weight1(), 4, 4));

        TextView resetBtn = Ui.ghostButton(this, getString(R.string.trainer_btn_reset));
        resetBtn.setOnClickListener(v -> {
            trainingSession.reset();
            refreshTargetAndStats();
            if (staffNoteView != null) staffNoteView.clearPlayedNote();
            trainerStatusText.setText(getString(R.string.trainer_status_play));
            trainerStatusText.setTextColor(Ui.MINT);
        });
        bar.addView(resetBtn, marginLeft(Ui.weight1(), 4));
        return bar;
    }

    private FrameLayout.LayoutParams trainerActionBarParams() {
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT);
        p.gravity = Gravity.BOTTOM;
        return p;
    }

    private int computeNavBarHeightDp() {
        int resId = getResources().getIdentifier("navigation_bar_height", "dimen", "android");
        int px = resId > 0 ? getResources().getDimensionPixelSize(resId) : 0;
        if (px <= 0) return 0;
        return Math.round(px / getResources().getDisplayMetrics().density);
    }

    private View buildMicChip(boolean on) {
        LinearLayout chip = new LinearLayout(this);
        chip.setOrientation(LinearLayout.HORIZONTAL);
        chip.setGravity(Gravity.CENTER_VERTICAL);
        int padX = Ui.dp(this, 10), padY = Ui.dp(this, 6);
        chip.setPadding(padX, padY, padX, padY);
        GradientDrawable bg = new GradientDrawable();
        bg.setShape(GradientDrawable.RECTANGLE);
        bg.setCornerRadius(Ui.dp(this, 999));
        bg.setColor(on ? Ui.MINT_DIM : 0x10FFFFFF);
        chip.setBackground(bg);

        if (on) {
            View dot = new View(this);
            GradientDrawable dotBg = new GradientDrawable();
            dotBg.setShape(GradientDrawable.OVAL);
            dotBg.setColor(Ui.MINT);
            dot.setBackground(dotBg);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(this, 6), Ui.dp(this, 6));
            dp.rightMargin = Ui.dp(this, 6);
            chip.addView(dot, dp);
        }
        TextView label = Ui.body(this,
                on ? getString(R.string.mic_on) : getString(R.string.mic_off),
                13, on ? Ui.MINT : Ui.FG_2);
        label.setTypeface(label.getTypeface(), android.graphics.Typeface.BOLD);
        chip.addView(label);
        return chip;
    }

    private void onModeClicked(TrainingSession.Mode m) {
        trainingSession.setMode(m);
        if (staffNoteView != null) staffNoteView.clearPlayedNote();
        trainerStatusText.setText(getString(R.string.trainer_status_play));
        trainerStatusText.setTextColor(Ui.MINT);
        refreshTargetAndStats();
    }

    private void toggleTracking() {
        if (pitchTracker == null) return;
        if (pitchTracker.isRunning()) {
            userWantsTracking = false;
            pitchTracker.stop();
            if (staffNoteView != null) staffNoteView.clearPlayedNote();
            trainerStatusText.setText(getString(R.string.trainer_status_paused));
            trainerStatusText.setTextColor(Ui.FG_2);
            trainerToggleButton.setText(getString(R.string.trainer_btn_start));
        } else {
            userWantsTracking = true;
            startTracking();
        }
    }

    private void updatePitch(DetectedPitch pitch) {
        if (!pitch.pitched) {
            if (staffNoteView != null) staffNoteView.clearPlayedNote();
            trainingSession.evaluate(pitch);
            return;
        }

        // Always show the played note on the staff (left of target) while a pitch is detected.
        NoteName nearest = NoteName.fromFrequency(pitch.frequencyHz);
        boolean correctClass = (nearest.midiNumber % 12) == (trainingSession.target().midiNumber % 12);
        if (staffNoteView != null) {
            staffNoteView.setPlayedNote(nearest, correctClass ? Ui.MINT : Ui.CORAL);
        }

        TrainingSession.Evaluation evaluation = trainingSession.evaluate(pitch);
        if (evaluation.type == TrainingSession.Evaluation.Type.SCORED) {
            if (evaluation.correct) {
                trainerStatusText.setText("Richtig");
                trainerStatusText.setTextColor(Ui.MINT);
                trainerStaffCardBg.setStroke(Ui.dp(this, 1), 0x73_30D9A8);
            } else {
                trainerStatusText.setText("Falsch — versuch " + evaluation.expected.displayName());
                trainerStatusText.setTextColor(Ui.CORAL);
                trainerStaffCardBg.setStroke(Ui.dp(this, 1), 0x73_FF6F6F);
            }
            refreshTargetAndStats();
        } else if (evaluation.type == TrainingSession.Evaluation.Type.DETECTED) {
            trainerStatusText.setText("Halte den Ton kurz stabil");
            trainerStatusText.setTextColor(Ui.FG_2);
            trainerStaffCardBg.setStroke(Ui.dp(this, 1), Ui.HAIRLINE_STRONG);
        } else {
            trainerStatusText.setText(getString(R.string.trainer_status_play));
            trainerStatusText.setTextColor(Ui.MINT);
            trainerStaffCardBg.setStroke(Ui.dp(this, 1), Ui.HAIRLINE_STRONG);
        }
    }

    private void refreshTargetAndStats() {
        if (trainerTargetText != null) {
            NoteName target = trainingSession.target();
            trainerTargetText.setText(target.displayName());
            if (trainerTargetHz != null) {
                trainerTargetHz.setText(String.format(Locale.US, "%.1f Hz", target.targetFrequencyHz));
            }
        }
        if (staffNoteView != null) staffNoteView.setNote(trainingSession.target());
        if (trainerStatHits != null) {
            trainerStatHits.setText(String.format(Locale.US, "%d/%d",
                    trainingSession.correct(), trainingSession.attempts()));
        }
        if (trainerStatAccuracy != null) {
            int acc = trainingSession.accuracyPercent();
            trainerStatAccuracy.setText(acc + "%");
            trainerStatAccuracy.setTextColor(acc >= 80 ? Ui.MINT : acc >= 50 ? Ui.AMBER : Ui.FG_0);
        }
        updateModeButtons();
    }

    private void updateModeButtons() {
        TrainingSession.Mode active = trainingSession.mode();
        for (TrainingSession.Mode m : TrainingSession.Mode.values()) {
            TextView btn = trainerModeButtons.get(m.ordinal());
            if (btn != null) styleSegmentedButton(btn, m == active);
        }
    }

    // Top bar (back button, title, optional trailing widget)
    private View buildTopBar(String title, boolean showBack, View trailing) {
        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setGravity(Gravity.CENTER_VERTICAL);
        bar.setBackgroundColor(Ui.BG_0);
        int p = Ui.dp(this, 12);
        // Reserve space at top equal to the system status-bar height so the row of
        // back/title/trailing widgets clears the system clock and notification icons.
        bar.setPadding(p, Ui.dp(this, statusBarHeightDp), p, 0);

        if (showBack) {
            TextView back = new TextView(this);
            back.setText("‹  " + getString(R.string.back_home));
            back.setTextColor(Ui.BLUE);
            back.setTextSize(17);
            back.setTypeface(back.getTypeface(), android.graphics.Typeface.NORMAL);
            int bp = Ui.dp(this, 10);
            back.setPadding(bp, 0, bp, 0);
            back.setGravity(Gravity.CENTER_VERTICAL);
            back.setMinimumHeight(Ui.dp(this, 44));
            back.setClickable(true);
            back.setFocusable(true);
            back.setOnClickListener(v -> showHome());
            bar.addView(back);
        } else {
            bar.addView(spacerHoriz(44));
        }

        TextView t = new TextView(this);
        t.setText(title);
        t.setTextColor(Ui.FG_0);
        t.setTextSize(17);
        t.setTypeface(t.getTypeface(), android.graphics.Typeface.BOLD);
        t.setLetterSpacing(-0.01f);
        t.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bar.addView(t, tp);

        if (trailing != null) {
            FrameLayout wrap = new FrameLayout(this);
            wrap.setMinimumWidth(Ui.dp(this, 80));
            wrap.addView(trailing,
                    new FrameLayout.LayoutParams(
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            FrameLayout.LayoutParams.WRAP_CONTENT,
                            Gravity.END | Gravity.CENTER_VERTICAL));
            bar.addView(wrap);
        } else {
            bar.addView(spacerHoriz(80));
        }
        return bar;
    }

    private FrameLayout.LayoutParams topBarParams() {
        // The toolbar sits flush at the top; pad downward by the system status-bar height
        // so its children render below the system clock/icons.
        FrameLayout.LayoutParams p = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                Ui.dp(this, 50) + Ui.dp(this, statusBarHeightDp));
        return p;
    }

    private int computeStatusBarHeightDp() {
        int resId = getResources().getIdentifier("status_bar_height", "dimen", "android");
        int px = resId > 0 ? getResources().getDimensionPixelSize(resId) : 0;
        if (px <= 0) return 24; // sensible fallback
        return Math.round(px / getResources().getDisplayMetrics().density);
    }

    // ─────────────────────────────────────────────────────────────
    // Tuner (Stimmgerät)
    // ─────────────────────────────────────────────────────────────

    private View buildTuner() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.BG_0);

        ScrollView scroll = createScrollContainer();
        frame.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout col = createScrollColumn(scroll, /*topPadding*/ 56);

        // Eyebrow (string label)
        tunerLabel = Ui.eyebrow(this, getString(R.string.tuner_label_default), Ui.FG_2);
        tunerLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = padHorizontal(Ui.matchWrap(), 20);
        lp.topMargin = Ui.dp(this, 8);
        col.addView(tunerLabel, lp);

        // Big circular indicator
        FrameLayout circle = new FrameLayout(this);
        GradientDrawable cBg = new GradientDrawable();
        cBg.setShape(GradientDrawable.OVAL);
        cBg.setColor(Ui.BG_1);
        cBg.setStroke(Ui.dp(this, 1), Ui.HAIRLINE_STRONG);
        circle.setBackground(cBg);

        FrameLayout.LayoutParams cp = new FrameLayout.LayoutParams(
                Ui.dp(this, 220), Ui.dp(this, 220));
        cp.topMargin = Ui.dp(this, 18);
        cp.leftMargin = cp.rightMargin = Ui.dp(this, 0);
        // Center horizontally
        LinearLayout circleWrap = new LinearLayout(this);
        circleWrap.setGravity(Gravity.CENTER_HORIZONTAL);
        circleWrap.addView(circle, cp);
        col.addView(circleWrap, Ui.matchWrap());

        // Inner ring (color shifts when in tune)
        tunerCircleRing = new View(this);
        GradientDrawable ringBg = new GradientDrawable();
        ringBg.setShape(GradientDrawable.OVAL);
        ringBg.setStroke(Ui.dp(this, 1), 0x00000000);
        tunerCircleRing.setBackground(ringBg);
        FrameLayout.LayoutParams rp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        rp.setMargins(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        circle.addView(tunerCircleRing, rp);

        // Letter + note inside circle
        LinearLayout circleText = new LinearLayout(this);
        circleText.setOrientation(LinearLayout.VERTICAL);
        circleText.setGravity(Gravity.CENTER);
        FrameLayout.LayoutParams ctp = new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT);
        ctp.gravity = Gravity.CENTER;
        circle.addView(circleText, ctp);

        tunerLetter = Ui.display(this, "—", 96, Ui.FG_2);
        tunerLetter.setGravity(Gravity.CENTER);
        circleText.addView(tunerLetter);

        tunerNoteOctave = Ui.body(this, "E A D G B E", 14, Ui.FG_2);
        tunerNoteOctave.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nop = Ui.matchWrap();
        nop.topMargin = Ui.dp(this, 2);
        circleText.addView(tunerNoteOctave, nop);

        // Cents reading row
        tunerCents = Ui.body(this, getString(R.string.tuner_waiting), 17, Ui.FG_2);
        tunerCents.setGravity(Gravity.CENTER);
        tunerCents.setTypeface(tunerCents.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams cep = padHorizontal(Ui.matchWrap(), 20);
        cep.topMargin = Ui.dp(this, 18);
        col.addView(tunerCents, cep);

        // Strip meter
        tunerMeterView = new TunerMeterView(this);
        LinearLayout.LayoutParams mp = padHorizontal(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 94)), 20);
        mp.topMargin = Ui.dp(this, 16);
        col.addView(tunerMeterView, mp);

        // Detail card (Frequenz / Ziel)
        LinearLayout detailCard = new LinearLayout(this);
        detailCard.setOrientation(LinearLayout.HORIZONTAL);
        detailCard.setBackground(Ui.card(this, Ui.BG_2, 22, Ui.HAIRLINE));
        int dp = Ui.dp(this, 18);
        detailCard.setPadding(dp, Ui.dp(this, 14), dp, Ui.dp(this, 14));

        LinearLayout dl = new LinearLayout(this);
        dl.setOrientation(LinearLayout.VERTICAL);
        dl.addView(Ui.eyebrow(this, getString(R.string.tuner_freq_label), Ui.FG_3));
        tunerFreq = Ui.body(this, "—", 17, Ui.FG_0);
        LinearLayout.LayoutParams tfp = Ui.matchWrap();
        tfp.topMargin = Ui.dp(this, 2);
        dl.addView(tunerFreq, tfp);
        detailCard.addView(dl, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout dr = new LinearLayout(this);
        dr.setOrientation(LinearLayout.VERTICAL);
        dr.setGravity(Gravity.END);
        TextView re = Ui.eyebrow(this, getString(R.string.tuner_target_label), Ui.FG_3);
        re.setGravity(Gravity.END);
        dr.addView(re);
        tunerTarget = Ui.body(this, "—", 17, Ui.FG_1);
        tunerTarget.setGravity(Gravity.END);
        LinearLayout.LayoutParams ttp2 = Ui.matchWrap();
        ttp2.topMargin = Ui.dp(this, 2);
        dr.addView(tunerTarget, ttp2);
        detailCard.addView(dr, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        LinearLayout.LayoutParams dcp = padHorizontal(Ui.matchWrap(), 20);
        dcp.topMargin = Ui.dp(this, 18);
        col.addView(detailCard, dcp);

        // String row
        TextView strHeader = Ui.eyebrow(this, getString(R.string.tuner_section_strings), Ui.FG_2);
        LinearLayout.LayoutParams shp = padHorizontal(Ui.matchWrap(), 20);
        shp.topMargin = Ui.dp(this, 22);
        shp.bottomMargin = Ui.dp(this, 10);
        col.addView(strHeader, shp);

        LinearLayout strRow = new LinearLayout(this);
        strRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams srp = padHorizontal(Ui.matchWrap(), 20);
        col.addView(strRow, srp);

        String[] stringNotes = {"E2", "A2", "D3", "G3", "B3", "E4"};
        for (int i = 0; i < stringNotes.length; i++) {
            View tile = buildStringTile(stringNotes[i]);
            tunerStringTiles.put(i, tile);
            LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(0, Ui.dp(this, 64), 1f);
            stp.leftMargin = i == 0 ? 0 : Ui.dp(this, 4);
            stp.rightMargin = i == stringNotes.length - 1 ? 0 : Ui.dp(this, 4);
            strRow.addView(tile, stp);
        }

        TextView hint = Ui.body(this, getString(R.string.tuner_tap_string), 12, Ui.FG_3);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hp = padHorizontal(Ui.matchWrap(), 20);
        hp.topMargin = Ui.dp(this, 12);
        col.addView(hint, hp);

        col.addView(spacer(24));

        // Top bar (with "Standard" trailing chip)
        TextView standardChip = new TextView(this);
        standardChip.setText(getString(R.string.tuner_standard));
        standardChip.setTextColor(Ui.FG_2);
        standardChip.setTextSize(13);
        int chipPadX = Ui.dp(this, 10), chipPadY = Ui.dp(this, 6);
        standardChip.setPadding(chipPadX, chipPadY, chipPadX, chipPadY);
        GradientDrawable chipBg = new GradientDrawable();
        chipBg.setShape(GradientDrawable.RECTANGLE);
        chipBg.setCornerRadius(Ui.dp(this, 999));
        chipBg.setColor(0x10FFFFFF);
        standardChip.setBackground(chipBg);
        frame.addView(buildTopBar(getString(R.string.tile_tuner), true, standardChip), topBarParams());

        return frame;
    }

    private View buildStringTile(String note) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER);
        tile.setBackground(Ui.card(this, Ui.BG_2, 14, Ui.HAIRLINE));

        TextView letter = Ui.display(this, String.valueOf(note.charAt(0)), 22, Ui.FG_0);
        letter.setGravity(Gravity.CENTER);
        tile.addView(letter);

        TextView label = Ui.body(this, note, 10, Ui.FG_3);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(this, 2);
        tile.addView(label, lp);

        tile.setTag(note);
        return tile;
    }

    private void updateTuner(DetectedPitch pitch) {
        TunerSession.TuningResult result = tunerSession.evaluate(pitch);
        if (tunerDebugLogger != null) tunerDebugLogger.log(pitch, result);

        if (!result.detected) {
            resetTunerDisplay();
            return;
        }

        boolean inTune = Math.abs(result.cents) <= 5f;
        int color = inTune ? Ui.MINT : Ui.AMBER;
        String direction = inTune ? getString(R.string.tuner_in_tune)
                : (result.cents < 0f ? getString(R.string.tuner_too_low) : getString(R.string.tuner_too_high));

        tunerLetter.setText(result.string.note);
        tunerLetter.setTextColor(color);
        tunerLabel.setText(result.string.label);
        tunerNoteOctave.setText(result.string.note);
        tunerCents.setText(String.format(Locale.US, "%s · %+.0f cents", direction, result.cents));
        tunerCents.setTextColor(color);
        tunerFreq.setText(String.format(Locale.US, "%.2f Hz", result.frequencyHz));
        tunerTarget.setText(String.format(Locale.US, "%.2f Hz", result.string.frequencyHz));
        tunerMeterView.setCents(result.cents, true);
        updateTunerRing(true, inTune);
        highlightStringTile(result.string, inTune);
    }

    private void resetTunerDisplay() {
        if (tunerLetter == null) return;
        // A lone em-dash at 96sp with negative letter-spacing renders as a thin grey bar
        // that reads as "broken". A music-note glyph clearly conveys "waiting for sound".
        tunerLetter.setText("♪");
        tunerLetter.setTextColor(Ui.FG_3);
        tunerLabel.setText(getString(R.string.tuner_label_default));
        tunerNoteOctave.setText("E A D G B E");
        tunerCents.setText(getString(R.string.tuner_waiting));
        tunerCents.setTextColor(Ui.FG_2);
        tunerFreq.setText("—");
        tunerTarget.setText("—");
        tunerMeterView.setCents(0f, false);
        updateTunerRing(false, false);
        clearStringTiles();
    }

    private void updateTunerRing(boolean detected, boolean inTune) {
        GradientDrawable bg = (GradientDrawable) tunerCircleRing.getBackground();
        if (!detected) {
            bg.setStroke(Ui.dp(this, 1), 0x00000000);
        } else {
            bg.setStroke(Ui.dp(this, 1), inTune ? 0x80_30D9A8 : 0x73_FFC857);
        }
    }

    private void highlightStringTile(TunerSession.GuitarString str, boolean inTune) {
        // Map by frequency to the index 0..5 (low to high)
        int idx = -1;
        if (Math.abs(str.frequencyHz - 82.41f) < 1f) idx = 0;
        else if (Math.abs(str.frequencyHz - 110.00f) < 1f) idx = 1;
        else if (Math.abs(str.frequencyHz - 146.83f) < 1f) idx = 2;
        else if (Math.abs(str.frequencyHz - 196.00f) < 1f) idx = 3;
        else if (Math.abs(str.frequencyHz - 246.94f) < 1f) idx = 4;
        else if (Math.abs(str.frequencyHz - 329.63f) < 1f) idx = 5;

        for (int i = 0; i < tunerStringTiles.size(); i++) {
            View tile = tunerStringTiles.valueAt(i);
            int key = tunerStringTiles.keyAt(i);
            boolean active = key == idx;
            int fill = active ? (inTune ? Ui.MINT_DIM : Ui.AMBER_DIM) : Ui.BG_2;
            int border = active ? (inTune ? 0x80_30D9A8 : 0x80_FFC857) : Ui.HAIRLINE;
            tile.setBackground(Ui.card(this, fill, 14, border));
            // Update the letter color
            LinearLayout l = (LinearLayout) tile;
            ((TextView) l.getChildAt(0)).setTextColor(active ? (inTune ? Ui.MINT : Ui.AMBER) : Ui.FG_0);
        }
    }

    private void clearStringTiles() {
        for (int i = 0; i < tunerStringTiles.size(); i++) {
            View tile = tunerStringTiles.valueAt(i);
            tile.setBackground(Ui.card(this, Ui.BG_2, 14, Ui.HAIRLINE));
            LinearLayout l = (LinearLayout) tile;
            ((TextView) l.getChildAt(0)).setTextColor(Ui.FG_0);
        }
    }

    // ─────────────────────────────────────────────────────────────
    // Pentatonik
    // ─────────────────────────────────────────────────────────────

    private View buildPentatonik() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.BG_0);

        ScrollView scroll = createScrollContainer();
        frame.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout col = createScrollColumn(scroll, 56);

        TextView eb = Ui.eyebrow(this, getString(R.string.pent_eyebrow), Ui.AMBER);
        col.addView(eb, padHorizontal(Ui.matchWrap(), 20));

        pentHeadline = Ui.title(this, pentatonikSession.headline(), 28);
        LinearLayout.LayoutParams hp = padHorizontal(Ui.matchWrap(), 20);
        hp.topMargin = Ui.dp(this, 4);
        col.addView(pentHeadline, hp);

        pentNotes = Ui.body(this, pentatonikSession.notesLine(), 14, Ui.FG_2);
        LinearLayout.LayoutParams np = padHorizontal(Ui.matchWrap(), 20);
        np.topMargin = Ui.dp(this, 4);
        col.addView(pentNotes, np);

        // Mode segmented
        LinearLayout modeSeg = createSegmented();
        pentMollBtn = createSegmentedButton(getString(R.string.pent_minor),
                pentatonikSession.shape() == PentatonikSession.Shape.MINOR,
                () -> { pentatonikSession.setShape(PentatonikSession.Shape.MINOR); refreshPentatonik(); });
        pentDurBtn = createSegmentedButton(getString(R.string.pent_major),
                pentatonikSession.shape() == PentatonikSession.Shape.MAJOR,
                () -> { pentatonikSession.setShape(PentatonikSession.Shape.MAJOR); refreshPentatonik(); });
        modeSeg.addView(pentMollBtn, segItemParams());
        modeSeg.addView(pentDurBtn, segItemParams());
        LinearLayout.LayoutParams msp = padHorizontal(Ui.matchWrap(), 20);
        msp.topMargin = Ui.dp(this, 14);
        col.addView(modeSeg, msp);

        // Root header
        TextView rootEb = Ui.eyebrow(this, getString(R.string.pent_root), Ui.FG_2);
        LinearLayout.LayoutParams rhp = padHorizontal(Ui.matchWrap(), 20);
        rhp.topMargin = Ui.dp(this, 14);
        rhp.bottomMargin = Ui.dp(this, 8);
        col.addView(rootEb, rhp);

        // Root grid (2 rows of 6)
        for (int row = 0; row < 2; row++) {
            LinearLayout rr = new LinearLayout(this);
            rr.setOrientation(LinearLayout.HORIZONTAL);
            for (int c = 0; c < 6; c++) {
                final int idx = row * 6 + c;
                final TextView btn = createRootButton(PentatonikSession.ROOT_NAMES[idx], idx == pentatonikSession.rootIdx());
                btn.setOnClickListener(v -> { pentatonikSession.setRoot(idx); refreshPentatonik(); });
                pentRootButtons.put(idx, btn);
                LinearLayout.LayoutParams rrp = new LinearLayout.LayoutParams(0, Ui.dp(this, 36), 1f);
                rrp.leftMargin = c == 0 ? 0 : Ui.dp(this, 3);
                rrp.rightMargin = c == 5 ? 0 : Ui.dp(this, 3);
                rr.addView(btn, rrp);
            }
            LinearLayout.LayoutParams rp = padHorizontal(Ui.matchWrap(), 20);
            rp.topMargin = Ui.dp(this, row == 0 ? 0 : 6);
            col.addView(rr, rp);
        }

        // Fretboard
        pentFretboard = new FretboardView(this);
        LinearLayout.LayoutParams fp = padHorizontal(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, 200)), 20);
        fp.topMargin = Ui.dp(this, 18);
        col.addView(pentFretboard, fp);

        // Position + BPM
        LinearLayout posRow = new LinearLayout(this);
        posRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams prp = padHorizontal(Ui.matchWrap(), 20);
        prp.topMargin = Ui.dp(this, 14);
        col.addView(posRow, prp);

        LinearLayout posCard = miniInfoCard(getString(R.string.pent_position), "1/5");
        pentPositionValue = (TextView) ((LinearLayout) posCard).getChildAt(1);
        posRow.addView(posCard, marginRight(Ui.weight1(), 5));
        LinearLayout bpmMini = miniInfoCard(getString(R.string.pent_bpm), pentBpm + "");
        pentBpmValue = (TextView) ((LinearLayout) bpmMini).getChildAt(1);
        posRow.addView(bpmMini, marginLeft(Ui.weight1(), 5));

        LinearLayout stepCard = new LinearLayout(this);
        stepCard.setOrientation(LinearLayout.VERTICAL);
        stepCard.setBackground(Ui.card(this, Ui.BG_2, 22, Ui.HAIRLINE));
        int scp = Ui.dp(this, 16);
        stepCard.setPadding(scp, scp, scp, scp);
        stepCard.addView(Ui.eyebrow(this, "Nächster Ton", Ui.FG_2));
        pentStepValue = Ui.display(this, pentatonikSession.currentNoteName(), 42, Ui.AMBER);
        LinearLayout.LayoutParams psvp = Ui.matchWrap();
        psvp.topMargin = Ui.dp(this, 6);
        stepCard.addView(pentStepValue, psvp);
        LinearLayout.LayoutParams stepP = padHorizontal(Ui.matchWrap(), 20);
        stepP.topMargin = Ui.dp(this, 12);
        col.addView(stepCard, stepP);

        SeekBar pentTempo = new SeekBar(this);
        pentTempo.setMax(180 - 40);
        pentTempo.setProgress(pentBpm - 40);
        pentTempo.getThumb().setColorFilter(Ui.AMBER, android.graphics.PorterDuff.Mode.SRC_IN);
        pentTempo.getProgressDrawable().setColorFilter(Ui.AMBER, android.graphics.PorterDuff.Mode.SRC_IN);
        pentTempo.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                pentBpm = progress + 40;
                if (pentBpmValue != null) pentBpmValue.setText(String.valueOf(pentBpm));
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        LinearLayout.LayoutParams ptp = padHorizontal(Ui.matchWrap(), 20);
        ptp.topMargin = Ui.dp(this, 10);
        col.addView(pentTempo, ptp);

        // Action row
        LinearLayout actRow = new LinearLayout(this);
        actRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams arp = padHorizontal(Ui.matchWrap(), 20);
        arp.topMargin = Ui.dp(this, 12);
        col.addView(actRow, arp);

        pentStartButton = Ui.button(this, getString(R.string.pent_start), Ui.FG_0, Ui.BG_0, 50);
        pentStartButton.setOnClickListener(v -> togglePentPractice());
        actRow.addView(pentStartButton, marginRight(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 2f), 4));
        TextView posBtn = Ui.ghostButton(this, getString(R.string.pent_position_btn));
        posBtn.setOnClickListener(v -> {
            pentatonikSession.nextPosition();
            refreshPentatonik();
        });
        actRow.addView(posBtn, marginLeft(Ui.weight1(), 4));

        col.addView(spacer(24));

        frame.addView(buildTopBar(getString(R.string.tile_pentatonik), true, null), topBarParams());

        // Initialize fretboard
        refreshPentatonik();
        return frame;
    }

    private void refreshPentatonik() {
        if (pentHeadline != null) pentHeadline.setText(pentatonikSession.headline());
        if (pentNotes != null) pentNotes.setText(pentatonikSession.notesLine());
        if (pentPositionValue != null) pentPositionValue.setText((pentatonikSession.position() + 1) + "/5");
        if (pentBpmValue != null) pentBpmValue.setText(String.valueOf(pentBpm));
        if (pentStepValue != null) pentStepValue.setText(pentatonikSession.currentNoteName());
        if (pentFretboard != null) {
            pentFretboard.setPractice(
                    pentatonikSession.rootIdx(),
                    pentatonikSession.shape().intervals,
                    pentatonikSession.positionStartFret(),
                    pentatonikSession.activePitchClass());
        }
        styleSegmentedButton(pentMollBtn, pentatonikSession.shape() == PentatonikSession.Shape.MINOR);
        styleSegmentedButton(pentDurBtn, pentatonikSession.shape() == PentatonikSession.Shape.MAJOR);
        for (int i = 0; i < 12; i++) {
            TextView btn = pentRootButtons.get(i);
            if (btn != null) styleRootButton(btn, i == pentatonikSession.rootIdx());
        }
    }

    private void togglePentPractice() {
        pentRunning = !pentRunning;
        if (pentRunning) {
            pentStartButton.setText(getString(R.string.pent_pause));
            refreshPentatonik();
            mainHandler.postDelayed(pentStepRunnable, Math.round(60000f / pentBpm));
        } else {
            stopPentPractice();
        }
    }

    private void stopPentPractice() {
        if (!pentRunning && pentStartButton == null) return;
        pentRunning = false;
        mainHandler.removeCallbacks(pentStepRunnable);
        if (pentStartButton != null) pentStartButton.setText(getString(R.string.pent_start));
    }

    private TextView createRootButton(String label, boolean active) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(14);
        btn.setTypeface(btn.getTypeface(), android.graphics.Typeface.BOLD);
        btn.setGravity(Gravity.CENTER);
        btn.setMinimumHeight(Ui.dp(this, 36));
        btn.setClickable(true);
        btn.setFocusable(true);
        styleRootButton(btn, active);
        return btn;
    }

    private void styleRootButton(TextView btn, boolean active) {
        if (active) {
            btn.setBackground(Ui.card(this, Ui.AMBER, 10, Color.TRANSPARENT));
            btn.setTextColor(0xFF1A1300);
        } else {
            btn.setBackground(Ui.card(this, Ui.BG_2, 10, Ui.HAIRLINE));
            btn.setTextColor(Ui.FG_0);
        }
    }

    private LinearLayout miniInfoCard(String label, String value) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(this, Ui.BG_2, 22, Ui.HAIRLINE));
        int p = Ui.dp(this, 14);
        card.setPadding(p, p, p, p);
        card.addView(Ui.eyebrow(this, label, Ui.FG_2));
        TextView val = Ui.display(this, value, 28, Ui.FG_0);
        LinearLayout.LayoutParams vp = Ui.matchWrap();
        vp.topMargin = Ui.dp(this, 4);
        card.addView(val, vp);
        return card;
    }

    // ─────────────────────────────────────────────────────────────
    // Fingerübungen
    // ─────────────────────────────────────────────────────────────

    private View buildFinger() {
        FrameLayout frame = new FrameLayout(this);
        frame.setBackgroundColor(Ui.BG_0);

        ScrollView scroll = createScrollContainer();
        frame.addView(scroll, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT));

        LinearLayout col = createScrollColumn(scroll, 56);

        fingerEyebrow = Ui.eyebrow(this, "ÜBUNG 1 VON " + FingerExercise.ALL.length, Ui.VIOLET);
        col.addView(fingerEyebrow, padHorizontal(Ui.matchWrap(), 20));

        fingerExerciseTitle = Ui.title(this, FingerExercise.ALL[0].name, 26);
        LinearLayout.LayoutParams ftp = padHorizontal(Ui.matchWrap(), 20);
        ftp.topMargin = Ui.dp(this, 4);
        col.addView(fingerExerciseTitle, ftp);

        fingerExerciseSub = Ui.body(this, FingerExercise.ALL[0].detail, 14, Ui.FG_2);
        LinearLayout.LayoutParams fsp = padHorizontal(Ui.matchWrap(), 20);
        fsp.topMargin = Ui.dp(this, 4);
        col.addView(fingerExerciseSub, fsp);

        // Carousel
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        fingerCarousel = new LinearLayout(this);
        fingerCarousel.setOrientation(LinearLayout.HORIZONTAL);
        int pad = Ui.dp(this, 20);
        fingerCarousel.setPadding(pad, 0, pad, 0);
        for (int i = 0; i < FingerExercise.ALL.length; i++) {
            final int idx = i;
            FingerExercise ex = FingerExercise.ALL[i];
            View card = buildExerciseCard(idx, ex, idx == fingerExerciseIdx);
            card.setOnClickListener(v -> setFingerExercise(idx));
            LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(Ui.dp(this, 172),
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            cp.rightMargin = Ui.dp(this, 10);
            fingerCarousel.addView(card, cp);
        }
        hsv.addView(fingerCarousel, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams hsvp = Ui.matchWrap();
        hsvp.topMargin = Ui.dp(this, 14);
        col.addView(hsv, hsvp);

        // Pattern card
        LinearLayout patternCard = new LinearLayout(this);
        patternCard.setOrientation(LinearLayout.VERTICAL);
        patternCard.setBackground(Ui.card(this, Ui.BG_2, 22, Ui.HAIRLINE));
        int pp = Ui.dp(this, 14);
        patternCard.setPadding(pp, Ui.dp(this, 18), pp, Ui.dp(this, 18));

        TextView pe = Ui.eyebrow(this, getString(R.string.finger_pattern), Ui.FG_2);
        LinearLayout.LayoutParams pep = Ui.matchWrap();
        pep.bottomMargin = Ui.dp(this, 10);
        patternCard.addView(pe, pep);

        fingerPatternGrid = new PatternGridView(this);
        fingerPatternGrid.setPattern(FingerExercise.ALL[0].pattern);
        patternCard.addView(fingerPatternGrid, Ui.matchWrap());

        // Legend
        LinearLayout legend = new LinearLayout(this);
        legend.setOrientation(LinearLayout.HORIZONTAL);
        legend.setGravity(Gravity.CENTER);
        for (int n = 1; n <= 4; n++) {
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.HORIZONTAL);
            item.setGravity(Gravity.CENTER_VERTICAL);
            View dot = new View(this);
            GradientDrawable db = new GradientDrawable();
            db.setShape(GradientDrawable.RECTANGLE);
            db.setColor(PatternGridView.fingerColor(n));
            db.setCornerRadius(Ui.dp(this, 4));
            dot.setBackground(db);
            LinearLayout.LayoutParams dp = new LinearLayout.LayoutParams(Ui.dp(this, 8), Ui.dp(this, 8));
            dp.rightMargin = Ui.dp(this, 6);
            item.addView(dot, dp);
            item.addView(Ui.body(this, "Finger " + n, 11, Ui.FG_3));
            LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(0,
                    LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            ip.gravity = Gravity.CENTER;
            item.setGravity(Gravity.CENTER);
            legend.addView(item, ip);
        }
        LinearLayout.LayoutParams lp = Ui.matchWrap();
        lp.topMargin = Ui.dp(this, 14);
        patternCard.addView(legend, lp);

        LinearLayout.LayoutParams pcp = padHorizontal(Ui.matchWrap(), 20);
        pcp.topMargin = Ui.dp(this, 18);
        col.addView(patternCard, pcp);

        // BPM card
        LinearLayout bpmCard = new LinearLayout(this);
        bpmCard.setOrientation(LinearLayout.VERTICAL);
        bpmCard.setBackground(Ui.card(this, Ui.BG_2, 22, Ui.HAIRLINE));
        int bp = Ui.dp(this, 18);
        bpmCard.setPadding(bp, bp, bp, bp);

        LinearLayout bpmHeader = new LinearLayout(this);
        bpmHeader.setOrientation(LinearLayout.HORIZONTAL);
        bpmHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView bpmEyebrow = Ui.eyebrow(this, getString(R.string.finger_tempo), Ui.FG_2);
        LinearLayout.LayoutParams bep = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        bpmHeader.addView(bpmEyebrow, bep);

        fingerBpmText = Ui.display(this, "80 BPM", 24, Ui.FG_0);
        bpmHeader.addView(fingerBpmText);
        bpmCard.addView(bpmHeader);

        fingerTempoSeek = new SeekBar(this);
        fingerTempoSeek.setMax(180 - 40);
        fingerTempoSeek.setProgress(fingerBpm - 40);
        fingerTempoSeek.getThumb().setColorFilter(Ui.VIOLET, android.graphics.PorterDuff.Mode.SRC_IN);
        fingerTempoSeek.getProgressDrawable().setColorFilter(Ui.VIOLET, android.graphics.PorterDuff.Mode.SRC_IN);
        fingerTempoSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override public void onProgressChanged(SeekBar bar, int progress, boolean fromUser) {
                fingerBpm = progress + 40;
                fingerBpmText.setText(fingerBpm + " BPM");
            }
            @Override public void onStartTrackingTouch(SeekBar bar) {}
            @Override public void onStopTrackingTouch(SeekBar bar) {}
        });
        LinearLayout.LayoutParams sp = Ui.matchWrap();
        sp.topMargin = Ui.dp(this, 12);
        bpmCard.addView(fingerTempoSeek, sp);

        LinearLayout scaleRow = new LinearLayout(this);
        scaleRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView l40 = Ui.body(this, "40", 11, Ui.FG_3);
        TextView l110 = Ui.body(this, "110", 11, Ui.FG_3);
        l110.setGravity(Gravity.CENTER);
        TextView l180 = Ui.body(this, "180", 11, Ui.FG_3);
        l180.setGravity(Gravity.END);
        scaleRow.addView(l40, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        scaleRow.addView(l110, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        scaleRow.addView(l180, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams srp = Ui.matchWrap();
        srp.topMargin = Ui.dp(this, 4);
        bpmCard.addView(scaleRow, srp);

        LinearLayout.LayoutParams bcp = padHorizontal(Ui.matchWrap(), 20);
        bcp.topMargin = Ui.dp(this, 14);
        col.addView(bpmCard, bcp);

        LinearLayout progRow = new LinearLayout(this);
        progRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout stepInfo = miniInfoCard("Schritt", "1/16");
        fingerStepText = (TextView) stepInfo.getChildAt(1);
        progRow.addView(stepInfo, marginRight(Ui.weight1(), 5));
        LinearLayout roundInfo = miniInfoCard("Runde", "1");
        fingerRoundText = (TextView) roundInfo.getChildAt(1);
        progRow.addView(roundInfo, marginLeft(Ui.weight1(), 5));
        LinearLayout.LayoutParams prp2 = padHorizontal(Ui.matchWrap(), 20);
        prp2.topMargin = Ui.dp(this, 14);
        col.addView(progRow, prp2);

        LinearLayout navRow = new LinearLayout(this);
        navRow.setOrientation(LinearLayout.HORIZONTAL);
        TextView prevBtn = Ui.ghostButton(this, "Zurück");
        prevBtn.setOnClickListener(v -> setFingerExercise((fingerExerciseIdx + FingerExercise.ALL.length - 1) % FingerExercise.ALL.length));
        navRow.addView(prevBtn, marginRight(Ui.weight1(), 5));
        TextView nextBtn = Ui.ghostButton(this, "Weiter");
        nextBtn.setOnClickListener(v -> setFingerExercise((fingerExerciseIdx + 1) % FingerExercise.ALL.length));
        navRow.addView(nextBtn, marginLeft(Ui.weight1(), 5));
        LinearLayout.LayoutParams nrp = padHorizontal(Ui.matchWrap(), 20);
        nrp.topMargin = Ui.dp(this, 12);
        col.addView(navRow, nrp);

        // Start / Pause button
        fingerStartButton = Ui.button(this, getString(R.string.finger_start), Ui.FG_0, Ui.BG_0, 54);
        fingerStartButton.setOnClickListener(v -> toggleFingerRunning());
        LinearLayout.LayoutParams stp = padHorizontal(Ui.matchWrap(), 20);
        stp.topMargin = Ui.dp(this, 14);
        col.addView(fingerStartButton, stp);

        col.addView(spacer(24));

        frame.addView(buildTopBar(getString(R.string.tile_finger), true, null), topBarParams());
        return frame;
    }

    private View buildExerciseCard(int idx, FingerExercise ex, boolean active) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.card(this,
                active ? Ui.BG_ELEV : Ui.BG_2,
                14, active ? 0x80_BF7BFF : Ui.HAIRLINE));
        int p = Ui.dp(this, 14);
        card.setPadding(p, Ui.dp(this, 12), p, Ui.dp(this, 12));
        card.setClickable(true);
        card.setFocusable(true);

        TextView idxLbl = Ui.body(this, "#" + (idx + 1), 11, Ui.FG_3);
        idxLbl.setTypeface(idxLbl.getTypeface(), android.graphics.Typeface.BOLD);
        card.addView(idxLbl);

        TextView nm = Ui.body(this, ex.name, 14, Ui.FG_0);
        nm.setTypeface(nm.getTypeface(), android.graphics.Typeface.BOLD);
        LinearLayout.LayoutParams np = Ui.matchWrap();
        np.topMargin = Ui.dp(this, 2);
        card.addView(nm, np);

        TextView sb = Ui.body(this, ex.subtitle, 11, Ui.FG_2);
        LinearLayout.LayoutParams sbp = Ui.matchWrap();
        sbp.topMargin = Ui.dp(this, 2);
        card.addView(sb, sbp);

        return card;
    }

    private void setFingerExercise(int idx) {
        fingerExerciseIdx = idx;
        FingerExercise ex = FingerExercise.ALL[idx];
        fingerEyebrow.setText("ÜBUNG " + (idx + 1) + " VON " + FingerExercise.ALL.length);
        fingerExerciseTitle.setText(ex.name);
        fingerExerciseSub.setText(ex.detail);
        fingerPatternGrid.setPattern(ex.pattern);
        fingerBpm = ex.recommendedBpm;
        if (fingerBpmText != null) fingerBpmText.setText(fingerBpm + " BPM");
        if (fingerTempoSeek != null) fingerTempoSeek.setProgress(fingerBpm - 40);
        fingerTick = 0;
        fingerRound = 1;
        fingerPatternGrid.setActive(fingerRunning ? 0 : -1);
        refreshFingerProgress();
        // Refresh carousel highlight
        for (int i = 0; i < fingerCarousel.getChildCount(); i++) {
            View card = fingerCarousel.getChildAt(i);
            boolean active = i == idx;
            card.setBackground(Ui.card(this,
                    active ? Ui.BG_ELEV : Ui.BG_2,
                    14, active ? 0x80_BF7BFF : Ui.HAIRLINE));
        }
    }

    private void toggleFingerRunning() {
        fingerRunning = !fingerRunning;
        if (fingerRunning) {
            fingerStartButton.setText(getString(R.string.finger_pause));
            fingerTick = 0;
            fingerRound = 1;
            fingerPatternGrid.setActive(0);
            refreshFingerProgress();
            mainHandler.postDelayed(fingerStepRunnable, Math.round(60000f / fingerBpm));
        } else {
            fingerStartButton.setText(getString(R.string.finger_start));
            mainHandler.removeCallbacks(fingerStepRunnable);
            fingerPatternGrid.setActive(-1);
            refreshFingerProgress();
        }
    }

    private void refreshFingerProgress() {
        if (fingerStepText != null) fingerStepText.setText((fingerTick + 1) + "/16");
        if (fingerRoundText != null) fingerRoundText.setText(String.valueOf(fingerRound));
    }

    // ─────────────────────────────────────────────────────────────
    // Segmented control helpers
    // ─────────────────────────────────────────────────────────────

    private LinearLayout createSegmented() {
        LinearLayout seg = new LinearLayout(this);
        seg.setOrientation(LinearLayout.HORIZONTAL);
        seg.setBackground(Ui.card(this, Ui.BG_2, 14, Ui.HAIRLINE));
        int p = Ui.dp(this, 4);
        seg.setPadding(p, p, p, p);
        return seg;
    }

    private TextView createSegmentedButton(String text, boolean active, Runnable onClick) {
        TextView btn = new TextView(this);
        btn.setText(text);
        btn.setTextSize(14);
        btn.setGravity(Gravity.CENTER);
        btn.setMinimumHeight(Ui.dp(this, 36));
        btn.setClickable(true);
        btn.setFocusable(true);
        if (onClick != null) btn.setOnClickListener(v -> onClick.run());
        styleSegmentedButton(btn, active);
        return btn;
    }

    private void styleSegmentedButton(TextView btn, boolean active) {
        if (btn == null) return;
        if (active) {
            btn.setBackground(Ui.card(this, Ui.BG_ELEV, 10, Color.TRANSPARENT));
            btn.setTextColor(Ui.FG_0);
            btn.setTypeface(btn.getTypeface(), android.graphics.Typeface.BOLD);
        } else {
            btn.setBackground(null);
            btn.setTextColor(Ui.FG_1);
            btn.setTypeface(btn.getTypeface(), android.graphics.Typeface.NORMAL);
        }
    }

    private LinearLayout.LayoutParams segItemParams() {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.leftMargin = Ui.dp(this, 2);
        lp.rightMargin = Ui.dp(this, 2);
        return lp;
    }

    // ─────────────────────────────────────────────────────────────
    // Layout helpers
    // ─────────────────────────────────────────────────────────────

    private ScrollView createScrollContainer() {
        ScrollView sv = new ScrollView(this);
        sv.setFillViewport(true);
        sv.setBackgroundColor(Ui.BG_0);
        sv.setClipToPadding(false);
        return sv;
    }

    private LinearLayout createScrollColumn(ScrollView sv, int topPaddingDp) {
        LinearLayout col = new LinearLayout(this);
        col.setOrientation(LinearLayout.VERTICAL);
        col.setBackgroundColor(Ui.BG_0);
        // Add the status-bar height so the first content (large title or first card)
        // doesn't render under the system clock / pill / icons.
        col.setPadding(0, Ui.dp(this, topPaddingDp + statusBarHeightDp), 0, Ui.dp(this, 24));
        sv.addView(col, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT, ScrollView.LayoutParams.WRAP_CONTENT));
        return col;
    }

    private View spacer(int heightDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, Ui.dp(this, heightDp)));
        return v;
    }

    private View spacerHoriz(int widthDp) {
        View v = new View(this);
        v.setLayoutParams(new LinearLayout.LayoutParams(
                Ui.dp(this, widthDp), LinearLayout.LayoutParams.WRAP_CONTENT));
        return v;
    }

    private LinearLayout.LayoutParams padHorizontal(LinearLayout.LayoutParams lp, int dp) {
        lp.leftMargin = Ui.dp(this, dp);
        lp.rightMargin = Ui.dp(this, dp);
        return lp;
    }

    private LinearLayout.LayoutParams marginRight(LinearLayout.LayoutParams lp, int dp) {
        lp.rightMargin = Ui.dp(this, dp);
        return lp;
    }

    private LinearLayout.LayoutParams marginLeft(LinearLayout.LayoutParams lp, int dp) {
        lp.leftMargin = Ui.dp(this, dp);
        return lp;
    }

    private LinearLayout.LayoutParams marginHoriz(LinearLayout.LayoutParams lp, int l, int r) {
        lp.leftMargin = Ui.dp(this, l);
        lp.rightMargin = Ui.dp(this, r);
        return lp;
    }
}
