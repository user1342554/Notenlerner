package com.example.notenlerner;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.Locale;
import java.util.Random;

public final class MainActivity extends Activity {
    private static final int REQUEST_RECORD_AUDIO = 1001;

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final TrainingSession trainingSession = new TrainingSession(new Random());
    private final TunerSession tunerSession = new TunerSession();

    private PitchTracker pitchTracker;
    private TunerDebugLogger tunerDebugLogger;
    private ScrollView homeView;
    private ScrollView trainerView;
    private ScrollView tunerView;
    private StaffNoteView staffNoteView;
    private TunerMeterView tunerMeterView;
    private TextView targetText;
    private TextView noteText;
    private TextView frequencyText;
    private TextView detailText;
    private TextView statsText;
    private TextView statusText;
    private Button toggleButton;
    private Button nextButton;
    private Button resetButton;
    private Button beginnerButton;
    private Button mediumButton;
    private Button hardButton;
    private boolean userWantsTracking = true;
    private boolean trainerVisible;
    private boolean tunerVisible;
    private boolean resumed;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        buildHomeUi();
        buildTrainerUi();
        buildTunerUi();
        showHome();
        tunerDebugLogger = new TunerDebugLogger(this);
        pitchTracker = new PitchTracker(this, new PitchTracker.Listener() {
            @Override
            public void onPitch(DetectedPitch pitch) {
                mainHandler.post(() -> {
                    if (trainerVisible) {
                        updatePitch(pitch);
                    } else if (tunerVisible) {
                        updateTuner(pitch);
                    }
                });
            }

            @Override
            public void onError(String message) {
                mainHandler.post(() -> {
                    statusText.setText(message);
                    toggleButton.setText("Start");
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
        if (pitchTracker != null && pitchTracker.isRunning()) {
            pitchTracker.stop();
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        if (pitchTracker != null) {
            pitchTracker.stop();
        }
        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_RECORD_AUDIO
                && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            if (resumed && userWantsTracking && (trainerVisible || tunerVisible)) {
                startTracking();
            }
        } else {
            statusText.setText("Mikrofonberechtigung benoetigt");
        }
    }

    private void buildHomeUi() {
        homeView = new ScrollView(this);
        homeView.setFillViewport(true);
        homeView.setBackgroundColor(0xFF101418);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(72), dp(24), dp(24));
        root.setBackgroundColor(0xFF101418);
        homeView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Notenlerner");
        title.setTextColor(0xFFEAF0F4);
        title.setTextSize(30);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth());

        TextView subtitle = new TextView(this);
        subtitle.setText("Waehle eine Uebung");
        subtitle.setTextColor(0xFF93A3AD);
        subtitle.setTextSize(17);
        subtitle.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams subtitleParams = fullWidth();
        subtitleParams.topMargin = dp(8);
        root.addView(subtitle, subtitleParams);

        LinearLayout.LayoutParams firstTileParams = fullWidth();
        firstTileParams.topMargin = dp(42);
        root.addView(homeTile("Notenlehrer", "Noten lesen und direkt auf der Gitarre pruefen", true), firstTileParams);

        LinearLayout.LayoutParams tileParams = fullWidth();
        tileParams.topMargin = dp(14);
        root.addView(homeTile("Pentatonische Tonleiter", "Platzhalter", false), tileParams);

        LinearLayout.LayoutParams tileParams2 = fullWidth();
        tileParams2.topMargin = dp(14);
        root.addView(homeTile("Fingeruebungen", "Platzhalter", false), tileParams2);

        LinearLayout.LayoutParams tileParams3 = fullWidth();
        tileParams3.topMargin = dp(14);
        root.addView(homeTile("Stimmgeraet", "Standard-Tuning E A D G B E", true), tileParams3);
    }

    private void buildTrainerUi() {
        ScrollView scrollView = new ScrollView(this);
        scrollView.setFillViewport(true);
        scrollView.setBackgroundColor(0xFF101418);
        trainerView = scrollView;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(62), dp(24), dp(16));
        root.setBackgroundColor(0xFF101418);
        scrollView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Notenlerner");
        title.setTextColor(0xFFEAF0F4);
        title.setTextSize(22);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth());

        Button backButton = new Button(this);
        backButton.setText("Home");
        backButton.setAllCaps(false);
        backButton.setOnClickListener(view -> showHome());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(42)
        );
        backParams.topMargin = dp(12);
        root.addView(backButton, backParams);

        LinearLayout difficultyRow = new LinearLayout(this);
        difficultyRow.setOrientation(LinearLayout.HORIZONTAL);
        difficultyRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams difficultyParams = fullWidth();
        difficultyParams.topMargin = dp(14);
        root.addView(difficultyRow, difficultyParams);

        beginnerButton = difficultyButton(TrainingSession.Difficulty.BEGINNER);
        mediumButton = difficultyButton(TrainingSession.Difficulty.MEDIUM);
        hardButton = difficultyButton(TrainingSession.Difficulty.HARD);
        difficultyRow.addView(beginnerButton, difficultyButtonParams());
        difficultyRow.addView(mediumButton, difficultyButtonParams());
        difficultyRow.addView(hardButton, difficultyButtonParams());

        TextView targetLabel = new TextView(this);
        targetLabel.setText("Zielnote");
        targetLabel.setTextColor(0xFF93A3AD);
        targetLabel.setTextSize(15);
        targetLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams targetLabelParams = fullWidth();
        targetLabelParams.topMargin = dp(14);
        root.addView(targetLabel, targetLabelParams);

        staffNoteView = new StaffNoteView(this);
        LinearLayout.LayoutParams staffParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(158)
        );
        staffParams.topMargin = dp(6);
        root.addView(staffNoteView, staffParams);

        targetText = new TextView(this);
        targetText.setText("--");
        targetText.setTextColor(0xFF6EE7B7);
        targetText.setTextSize(22);
        targetText.setGravity(Gravity.CENTER);
        targetText.setIncludeFontPadding(false);
        LinearLayout.LayoutParams targetParams = fullWidth();
        targetParams.topMargin = dp(0);
        root.addView(targetText, targetParams);

        TextView playedLabel = new TextView(this);
        playedLabel.setText("Gespielt");
        playedLabel.setTextColor(0xFF93A3AD);
        playedLabel.setTextSize(15);
        playedLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams playedLabelParams = fullWidth();
        playedLabelParams.topMargin = dp(16);
        root.addView(playedLabel, playedLabelParams);

        noteText = new TextView(this);
        noteText.setText("--");
        noteText.setTextColor(0xFFFFFFFF);
        noteText.setTextSize(48);
        noteText.setGravity(Gravity.CENTER);
        noteText.setIncludeFontPadding(false);
        LinearLayout.LayoutParams noteParams = fullWidth();
        noteParams.topMargin = dp(4);
        root.addView(noteText, noteParams);

        frequencyText = new TextView(this);
        frequencyText.setText("Spiele einen einzelnen Ton");
        frequencyText.setTextColor(0xFFC8D3DA);
        frequencyText.setTextSize(17);
        frequencyText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams freqParams = fullWidth();
        freqParams.topMargin = dp(10);
        root.addView(frequencyText, freqParams);

        detailText = new TextView(this);
        detailText.setText("Frequenz und Abweichung erscheinen hier");
        detailText.setTextColor(0xFF93A3AD);
        detailText.setTextSize(14);
        detailText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams detailParams = fullWidth();
        detailParams.topMargin = dp(6);
        root.addView(detailText, detailParams);

        statsText = new TextView(this);
        statsText.setText("Treffer 0/0 | Accuracy 0%");
        statsText.setTextColor(0xFFEAF0F4);
        statsText.setTextSize(16);
        statsText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statsParams = fullWidth();
        statsParams.topMargin = dp(20);
        root.addView(statsText, statsParams);

        statusText = new TextView(this);
        statusText.setText("Mikrofon wird vorbereitet");
        statusText.setTextColor(0xFF6EE7B7);
        statusText.setTextSize(15);
        statusText.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = fullWidth();
        statusParams.topMargin = dp(10);
        root.addView(statusText, statusParams);

        LinearLayout actionRow = new LinearLayout(this);
        actionRow.setOrientation(LinearLayout.HORIZONTAL);
        actionRow.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams actionRowParams = fullWidth();
        actionRowParams.topMargin = dp(16);
        root.addView(actionRow, actionRowParams);

        toggleButton = new Button(this);
        toggleButton.setText("Stop");
        toggleButton.setAllCaps(false);
        toggleButton.setOnClickListener(this::toggleTracking);
        actionRow.addView(toggleButton, weightedButtonParams());

        nextButton = new Button(this);
        nextButton.setText("Weiter");
        nextButton.setAllCaps(false);
        nextButton.setOnClickListener(view -> {
            trainingSession.skip();
            refreshTargetAndStats();
            statusText.setText("Neue Zielnote");
        });
        actionRow.addView(nextButton, weightedButtonParams());

        resetButton = new Button(this);
        resetButton.setText("Reset");
        resetButton.setAllCaps(false);
        resetButton.setOnClickListener(view -> {
            trainingSession.reset();
            refreshTargetAndStats();
            noteText.setText("--");
            frequencyText.setText("Spiele die Zielnote");
            detailText.setText("Statistik zurueckgesetzt");
            statusText.setText("Bereit");
        });
        LinearLayout.LayoutParams resetParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(48)
        );
        resetParams.topMargin = dp(8);
        root.addView(resetButton, resetParams);

    }

    private void buildTunerUi() {
        tunerView = new ScrollView(this);
        tunerView.setFillViewport(true);
        tunerView.setBackgroundColor(0xFF101418);

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER_HORIZONTAL);
        root.setPadding(dp(24), dp(72), dp(24), dp(20));
        root.setBackgroundColor(0xFF101418);
        tunerView.addView(root, new ScrollView.LayoutParams(
                ScrollView.LayoutParams.MATCH_PARENT,
                ScrollView.LayoutParams.WRAP_CONTENT
        ));

        TextView title = new TextView(this);
        title.setText("Stimmgeraet");
        title.setTextColor(0xFFEAF0F4);
        title.setTextSize(28);
        title.setGravity(Gravity.CENTER);
        root.addView(title, fullWidth());

        Button backButton = new Button(this);
        backButton.setText("Home");
        backButton.setAllCaps(false);
        backButton.setOnClickListener(view -> showHome());
        LinearLayout.LayoutParams backParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(44)
        );
        backParams.topMargin = dp(22);
        root.addView(backButton, backParams);

        TextView tuningLabel = new TextView(this);
        tuningLabel.setText("Standard: E A D G B E");
        tuningLabel.setTextColor(0xFF93A3AD);
        tuningLabel.setTextSize(17);
        tuningLabel.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams tuningParams = fullWidth();
        tuningParams.topMargin = dp(34);
        root.addView(tuningLabel, tuningParams);

        TextView stringText = taggedText("tunerString", "--", 0xFFFFFFFF, 82);
        stringText.setIncludeFontPadding(false);
        LinearLayout.LayoutParams stringParams = fullWidth();
        stringParams.topMargin = dp(42);
        root.addView(stringText, stringParams);

        TextView stringLabelText = taggedText("tunerStringLabel", "Spiele eine leere Saite", 0xFF93A3AD, 20);
        LinearLayout.LayoutParams stringLabelParams = fullWidth();
        stringLabelParams.topMargin = dp(8);
        root.addView(stringLabelText, stringLabelParams);

        tunerMeterView = new TunerMeterView(this);
        LinearLayout.LayoutParams meterParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                dp(96)
        );
        meterParams.topMargin = dp(28);
        root.addView(tunerMeterView, meterParams);

        TextView centsText = taggedText("tunerCents", "Warte auf Ton", 0xFF6EE7B7, 24);
        LinearLayout.LayoutParams centsParams = fullWidth();
        centsParams.topMargin = dp(22);
        root.addView(centsText, centsParams);

        TextView detailText = taggedText("tunerDetail", "Stimme jeweils eine einzelne Saite", 0xFF93A3AD, 15);
        LinearLayout.LayoutParams detailParams = fullWidth();
        detailParams.topMargin = dp(10);
        root.addView(detailText, detailParams);
    }

    private View homeTile(String title, String subtitle, boolean enabled) {
        LinearLayout tile = new LinearLayout(this);
        tile.setOrientation(LinearLayout.VERTICAL);
        tile.setGravity(Gravity.CENTER_VERTICAL);
        tile.setPadding(dp(18), dp(16), dp(18), dp(16));
        tile.setBackgroundColor(enabled ? 0xFF26313A : 0xFF1A2229);
        tile.setMinimumHeight(dp(92));
        tile.setOnClickListener(view -> {
            if ("Notenlehrer".equals(title)) {
                showTrainer();
            } else if ("Stimmgeraet".equals(title)) {
                showTuner();
            } else {
                Toast.makeText(this, title + " kommt spaeter", Toast.LENGTH_SHORT).show();
            }
        });

        TextView titleView = new TextView(this);
        titleView.setText(title);
        titleView.setTextColor(enabled ? 0xFFEAF0F4 : 0xFF93A3AD);
        titleView.setTextSize(22);
        tile.addView(titleView, fullWidth());

        TextView subtitleView = new TextView(this);
        subtitleView.setText(subtitle);
        subtitleView.setTextColor(0xFF93A3AD);
        subtitleView.setTextSize(14);
        LinearLayout.LayoutParams subtitleParams = fullWidth();
        subtitleParams.topMargin = dp(4);
        tile.addView(subtitleView, subtitleParams);
        return tile;
    }

    private void showHome() {
        if (pitchTracker != null && pitchTracker.isRunning()) {
            pitchTracker.stop();
        }
        trainerVisible = false;
        tunerVisible = false;
        setContentView(homeView);
    }

    private void showTrainer() {
        trainerVisible = true;
        tunerVisible = false;
        setContentView(trainerView);
        startTrackingIfNeeded();
    }

    private void showTuner() {
        trainerVisible = false;
        tunerVisible = true;
        userWantsTracking = true;
        if (tunerDebugLogger != null) {
            tunerDebugLogger.reset();
        }
        setContentView(tunerView);
        resetTunerDisplay();
        startTrackingIfNeeded();
    }

    private void startTrackingIfNeeded() {
        if (userWantsTracking
                && resumed
                && pitchTracker != null
                && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
            startTracking();
        }
    }

    private void toggleTracking(View ignored) {
        if (pitchTracker.isRunning()) {
            userWantsTracking = false;
            pitchTracker.stop();
            noteText.setText("--");
            frequencyText.setText("Gestoppt");
            detailText.setText("");
            statusText.setText("Pausiert");
            toggleButton.setText("Start");
        } else {
            userWantsTracking = true;
            startTracking();
        }
    }

    private void startTracking() {
        if (!resumed) {
            return;
        }
        pitchTracker.start();
        if (trainerVisible) {
            statusText.setText("Spiele die Zielnote");
            statusText.setTextColor(0xFF6EE7B7);
            toggleButton.setText("Stop");
        }
    }

    private void updatePitch(DetectedPitch pitch) {
        if (!pitch.pitched) {
            noteText.setText("--");
            frequencyText.setText("Kein klarer Ton");
            detailText.setText(String.format(Locale.US, "Pegel %.3f", pitch.rms));
            trainingSession.evaluate(pitch);
            return;
        }

        TrainingSession.Evaluation evaluation = trainingSession.evaluate(pitch);
        if (evaluation.type == TrainingSession.Evaluation.Type.SCORED) {
            updatePlayedNote(evaluation.played, pitch);
            if (evaluation.correct) {
                statusText.setText("Richtig");
                statusText.setTextColor(0xFF6EE7B7);
            } else {
                statusText.setText("Falsch, versuch " + evaluation.expected.displayName() + " nochmal");
                statusText.setTextColor(0xFFFF8A80);
            }
            refreshTargetAndStats();
        } else if (evaluation.type == TrainingSession.Evaluation.Type.DETECTED) {
            updatePlayedNote(evaluation.played, pitch);
            statusText.setText("Halte den Ton kurz stabil");
            statusText.setTextColor(0xFF93A3AD);
        } else {
            noteText.setText("--");
            frequencyText.setText(String.format(Locale.US, "%.1f Hz", pitch.frequencyHz));
            detailText.setText("Pruefe Stabilitaet");
            statusText.setText("Spiele die Zielnote");
            statusText.setTextColor(0xFF6EE7B7);
        }
    }

    private void updatePlayedNote(NoteName note, DetectedPitch pitch) {
        noteText.setText(note.displayName());
        frequencyText.setText(String.format(Locale.US, "%.1f Hz", pitch.frequencyHz));
        detailText.setText(String.format(
                Locale.US,
                "%s | Referenz %.1f Hz | Sicherheit %.0f%%",
                note.centsText(),
                note.targetFrequencyHz,
                pitch.confidence * 100f
        ));
    }

    private void updateTuner(DetectedPitch pitch) {
        TunerSession.TuningResult result = tunerSession.evaluate(pitch);
        if (tunerDebugLogger != null) {
            tunerDebugLogger.log(pitch, result);
        }
        if (!result.detected) {
            resetTunerDisplay();
            return;
        }

        boolean inTune = Math.abs(result.cents) <= 5f;
        int color = inTune ? 0xFF6EE7B7 : 0xFFFFC857;
        String direction;
        if (inTune) {
            direction = "Stimmt";
        } else if (result.cents < 0f) {
            direction = "Zu tief";
        } else {
            direction = "Zu hoch";
        }

        setTaggedText("tunerString", result.string.note, color);
        setTaggedText("tunerStringLabel", result.string.label, 0xFF93A3AD);
        setTaggedText("tunerCents", String.format(Locale.US, "%s | %+.0f cents", direction, result.cents), color);
        setTaggedText(
                "tunerDetail",
                tunerDetail(result),
                0xFF93A3AD
        );
        if (tunerMeterView != null) {
            tunerMeterView.setCents(result.cents, true);
        }
    }

    private void resetTunerDisplay() {
        setTaggedText("tunerString", "--", 0xFFFFFFFF);
        setTaggedText("tunerStringLabel", "Spiele eine leere Saite", 0xFF93A3AD);
        setTaggedText("tunerCents", "Warte auf Ton", 0xFF6EE7B7);
        setTaggedText("tunerDetail", "Stimme jeweils eine einzelne Saite", 0xFF93A3AD);
        if (tunerMeterView != null) {
            tunerMeterView.setCents(0f, false);
        }
    }

    private String tunerDetail(TunerSession.TuningResult result) {
        if (result.held) {
            return String.format(Locale.US, "Halte Anzeige | Ziel %.2f Hz", result.string.frequencyHz);
        }
        return String.format(
                Locale.US,
                "%.1f Hz -> %.2f Hz | Sicherheit %.0f%%",
                result.frequencyHz,
                result.string.frequencyHz,
                result.confidence * 100f
        );
    }

    private TextView taggedText(String tag, String text, int color, int textSizeSp) {
        TextView textView = new TextView(this);
        textView.setTag(tag);
        textView.setText(text);
        textView.setTextColor(color);
        textView.setTextSize(textSizeSp);
        textView.setGravity(Gravity.CENTER);
        return textView;
    }

    private void setTaggedText(String tag, String text, int color) {
        if (tunerView == null) {
            return;
        }
        View view = tunerView.findViewWithTag(tag);
        if (view instanceof TextView) {
            TextView textView = (TextView) view;
            textView.setText(text);
            textView.setTextColor(color);
        }
    }

    private void refreshTargetAndStats() {
        if (targetText != null) {
            targetText.setText(trainingSession.target().displayName());
        }
        if (staffNoteView != null) {
            staffNoteView.setNote(trainingSession.target());
        }
        if (statsText != null) {
            statsText.setText(String.format(
                    Locale.US,
                    "Treffer %d/%d | Accuracy %d%%",
                    trainingSession.correct(),
                    trainingSession.attempts(),
                    trainingSession.accuracyPercent()
            ));
        }
        updateDifficultyButtons();
    }

    private LinearLayout.LayoutParams fullWidth() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                dp(48),
                1f
        );
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        return params;
    }

    private LinearLayout.LayoutParams difficultyButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0,
                dp(44),
                1f
        );
        params.leftMargin = dp(4);
        params.rightMargin = dp(4);
        return params;
    }

    private Button difficultyButton(TrainingSession.Difficulty difficulty) {
        Button button = new Button(this);
        button.setText(difficulty.label);
        button.setAllCaps(false);
        button.setTextSize(13);
        button.setOnClickListener(view -> {
            trainingSession.setDifficulty(difficulty);
            noteText.setText("--");
            frequencyText.setText("Spiele die Zielnote");
            detailText.setText("Schwierigkeit: " + difficulty.label);
            statusText.setText("Neue Zielnote");
            statusText.setTextColor(0xFF6EE7B7);
            refreshTargetAndStats();
        });
        return button;
    }

    private void updateDifficultyButtons() {
        setDifficultySelected(beginnerButton, trainingSession.difficulty() == TrainingSession.Difficulty.BEGINNER);
        setDifficultySelected(mediumButton, trainingSession.difficulty() == TrainingSession.Difficulty.MEDIUM);
        setDifficultySelected(hardButton, trainingSession.difficulty() == TrainingSession.Difficulty.HARD);
    }

    private void setDifficultySelected(Button button, boolean selected) {
        if (button == null) {
            return;
        }
        button.setTextColor(selected ? 0xFF101418 : 0xFFEAF0F4);
        button.setBackgroundColor(selected ? 0xFF6EE7B7 : 0xFF26313A);
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density + 0.5f);
    }
}
