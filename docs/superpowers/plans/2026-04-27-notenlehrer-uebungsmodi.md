# Notenlehrer Übungsmodi Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the three Schwierigkeit buttons (Anfänger/Mittel/Schwer) on the Notenlehrer screen with six content-based modes (Leere Saiten, C-Dur Anfang, C-Dur komplett, Häufige Noten, Bünde 0–5, Bünde 0–12) and lay them out as two rows of three pill buttons.

**Architecture:** Mechanical rename `TrainingSession.Difficulty` → `TrainingSession.Mode` (incl. `setDifficulty`/`difficulty()` accessors), swap the enum constants, double the segmented-control row in MainActivity, swap the strings.xml entries. No changes to scoring logic, pitch detection, or session flow.

**Tech Stack:** Java 17, framework Android (no AndroidX), JUnit 4. Programmatic UI built in `MainActivity`. Tests under `app/src/test/java`.

**Spec:** `docs/superpowers/specs/2026-04-27-notenlehrer-uebungsmodi-design.md`

---

## File Structure

- **Modify** `app/src/main/java/com/example/notenlerner/TrainingSession.java` — swap nested enum and accessor names.
- **Modify** `app/src/main/java/com/example/notenlerner/MainActivity.java` — rename selector field/methods, build two pill rows, route through new string IDs.
- **Modify** `app/src/main/res/values/strings.xml` — drop `diff_*`, add `mode_*`.
- **Modify** `app/src/test/java/com/example/notenlerner/TrainingSessionTest.java` — update existing test to use new enum, add open-strings draw test.

No new files are created. The change does not introduce new abstractions or helpers — the existing `createSegmented()` / `createSegmentedButton()` / `segItemParams()` / `styleSegmentedButton()` helpers are reused as-is for the second row.

---

## Task 1: Update `TrainingSessionTest` to drive the new `Mode` enum

This task lands the failing tests first. The production code still has `Difficulty` at this point, so the test file will not compile after this step — that's the expected "red" state.

**Files:**
- Modify: `app/src/test/java/com/example/notenlerner/TrainingSessionTest.java`

- [ ] **Step 1: Replace the existing difficulty test and add the open-strings test**

Replace the body of `difficultyChangesTargetPoolAndResetsScore` (lines 72–80 in the file as it stands today) and add a new test below it. The full updated file (everything from `package` to the end) should look like this:

```java
package com.example.notenlerner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.HashSet;
import java.util.Random;
import java.util.Set;

public final class TrainingSessionTest {
    @Test
    public void scoresCorrectStableNoteAndAdvancesTarget() {
        TrainingSession session = new TrainingSession(new FixedRandom(5, 6));
        assertEquals("C3", session.target().displayName());

        DetectedPitch pitch = DetectedPitch.pitched(130.81f, 0.9f, 0.2f);
        session.evaluate(pitch);
        session.evaluate(pitch);
        TrainingSession.Evaluation result = session.evaluate(pitch);

        assertEquals(TrainingSession.Evaluation.Type.SCORED, result.type);
        assertEquals(1, session.correct());
        assertEquals(1, session.attempts());
        assertEquals(100, session.accuracyPercent());
        assertEquals("D3", session.target().displayName());
    }

    @Test
    public void hidesSingleFrameBeforeShowingDetectedNote() {
        TrainingSession session = new TrainingSession(new FixedRandom(5, 6));

        TrainingSession.Evaluation first = session.evaluate(DetectedPitch.pitched(130.81f, 0.9f, 0.2f));
        TrainingSession.Evaluation second = session.evaluate(DetectedPitch.pitched(130.81f, 0.9f, 0.2f));

        assertEquals(TrainingSession.Evaluation.Type.LISTENING, first.type);
        assertEquals(TrainingSession.Evaluation.Type.DETECTED, second.type);
        assertEquals("C3", second.played.displayName());
    }

    @Test
    public void scoresTargetWhenDetectorReportsTargetHarmonic() {
        TrainingSession session = new TrainingSession(new FixedRandom(5, 6));
        assertEquals("C3", session.target().displayName());

        DetectedPitch harmonic = DetectedPitch.pitched(261.62f, 0.95f, 0.2f);
        session.evaluate(harmonic);
        session.evaluate(harmonic);
        TrainingSession.Evaluation result = session.evaluate(harmonic);

        assertEquals(TrainingSession.Evaluation.Type.SCORED, result.type);
        assertEquals("C3", result.played.displayName());
        assertEquals(1, session.correct());
        assertEquals(1, session.attempts());
    }

    @Test
    public void scoresWrongStableNoteWithoutAdvancingTarget() {
        TrainingSession session = new TrainingSession(new FixedRandom(5, 6));
        assertEquals("C3", session.target().displayName());

        DetectedPitch pitch = DetectedPitch.pitched(196f, 0.9f, 0.2f);
        session.evaluate(pitch);
        session.evaluate(pitch);
        TrainingSession.Evaluation result = session.evaluate(pitch);

        assertEquals(TrainingSession.Evaluation.Type.SCORED, result.type);
        assertEquals(0, session.correct());
        assertEquals(1, session.attempts());
        assertEquals(0, session.accuracyPercent());
        assertEquals("C3", session.target().displayName());
    }

    @Test
    public void modeChangesTargetPoolAndResetsScore() {
        TrainingSession session = new TrainingSession(new FixedRandom(0));
        session.setMode(TrainingSession.Mode.FRETS_0_12);

        // Constructor seeded target to MIDI 40 (E2). setMode triggers reset → nextTarget,
        // which sees the duplicate and bumps to notes[1] = 41 (F2) under FRETS_0_12.
        assertEquals("F2", session.target().displayName());
        assertEquals(0, session.correct());
        assertEquals(0, session.attempts());
    }

    @Test
    public void openStringsModeOnlyDrawsOpenStringNotes() {
        TrainingSession session = new TrainingSession(new Random(42));
        session.setMode(TrainingSession.Mode.OPEN_STRINGS);

        Set<Integer> allowed = new HashSet<>();
        allowed.add(40); // E2
        allowed.add(45); // A2
        allowed.add(50); // D3
        allowed.add(55); // G3
        allowed.add(59); // B3
        allowed.add(64); // E4

        for (int i = 0; i < 30; i++) {
            int midi = session.target().midiNumber;
            assertTrue("target MIDI " + midi + " not in open-strings set", allowed.contains(midi));
            session.skip();
        }
    }

    private static final class FixedRandom extends Random {
        private final int[] values;
        private int index;

        FixedRandom(int... values) {
            this.values = values;
        }

        @Override
        public int nextInt(int bound) {
            return values[index++ % values.length] % bound;
        }
    }
}
```

- [ ] **Step 2: Verify the test file does not yet compile**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: compile failure mentioning `cannot find symbol: setMode` and `Mode.FRETS_0_12` / `Mode.OPEN_STRINGS`. This proves the tests are exercising the new API.

- [ ] **Step 3: Commit the failing tests**

```bash
git add app/src/test/java/com/example/notenlerner/TrainingSessionTest.java
git commit -m "test(trainer): switch difficulty test to Mode and add open-strings draw test"
```

---

## Task 2: Replace `Difficulty` with `Mode` in `TrainingSession`

Mechanical rename plus swap of enum constants. After this task the unit tests should compile and pass; `MainActivity` will not yet compile (it still references `Difficulty`) — that's fixed in Task 4.

**Files:**
- Modify: `app/src/main/java/com/example/notenlerner/TrainingSession.java`

- [ ] **Step 1: Rename the field, accessor, and mutator**

In `TrainingSession.java`:

- Line 14: `private Difficulty difficulty = Difficulty.BEGINNER;` → `private Mode mode = Mode.OPEN_STRINGS;`
- Lines 61–63 (`Difficulty difficulty() { return difficulty; }`) → `Mode mode() { return mode; }`
- Lines 65–71 (`setDifficulty`): rename to `setMode`, parameter `Mode mode`, body becomes:

```java
void setMode(Mode mode) {
    if (this.mode == mode) {
        return;
    }
    this.mode = mode;
    reset();
}
```

- Line 125 inside `nextTarget()`: `int[] notes = difficulty.allowedMidiNotes;` → `int[] notes = mode.allowedMidiNotes;`

- [ ] **Step 2: Replace the nested `Difficulty` enum with `Mode`**

Lines 158–178 currently define `enum Difficulty`. Replace that entire enum block with:

```java
enum Mode {
    OPEN_STRINGS("Leere Saiten", new int[]{40, 45, 50, 55, 59, 64}),
    C_MAJOR_START("C-Dur (Anfang)", new int[]{48, 50, 52, 53}),
    C_MAJOR_FULL("C-Dur (komplett)", new int[]{48, 50, 52, 53, 55, 57, 59}),
    COMMON_NOTES("Häufige Noten", new int[]{40, 41, 43, 45, 47, 48, 50, 52, 53, 55}),
    FRETS_0_5("Bünde 0–5", range(40, 53)),
    FRETS_0_12("Bünde 0–12", range(40, 64));

    final String label;
    final int[] allowedMidiNotes;

    Mode(String label, int[] allowedMidiNotes) {
        this.label = label;
        this.allowedMidiNotes = allowedMidiNotes;
    }

    private static int[] range(int minMidi, int maxMidi) {
        int[] result = new int[maxMidi - minMidi + 1];
        for (int i = 0; i < result.length; i++) {
            result[i] = minMidi + i;
        }
        return result;
    }
}
```

- [ ] **Step 3: Run the unit tests**

Run: `./gradlew.bat :app:testDebugUnitTest`
Expected: all `TrainingSessionTest` tests pass, including the two updated/added ones. (The full app build will still fail because `MainActivity` references `Difficulty` — that's fine; we only ran the test task.)

If a test fails, do not proceed; diagnose against the spec table of MIDI notes (modes section).

- [ ] **Step 4: Commit**

```bash
git add app/src/main/java/com/example/notenlerner/TrainingSession.java
git commit -m "feat(trainer): replace Difficulty with content-based Mode enum"
```

---

## Task 3: Swap difficulty strings for mode strings

**Files:**
- Modify: `app/src/main/res/values/strings.xml`

- [ ] **Step 1: Remove the three `diff_*` entries**

Delete the three lines in `strings.xml`:

```xml
<string name="diff_beginner">Anfänger</string>
<string name="diff_medium">Mittel</string>
<string name="diff_hard">Schwer</string>
```

- [ ] **Step 2: Add six `mode_*` entries in their place**

Insert (in the same location):

```xml
<string name="mode_open_strings">Leere Saiten</string>
<string name="mode_cmajor_start">C-Dur (Anfang)</string>
<string name="mode_cmajor_full">C-Dur (komplett)</string>
<string name="mode_common">Häufige Noten</string>
<string name="mode_frets_0_5">Bünde 0–5</string>
<string name="mode_frets_0_12">Bünde 0–12</string>
```

- [ ] **Step 3: Don't commit yet**

The new strings are unreferenced and the old ones being removed are still referenced by `MainActivity`. Committing here would leave the build red. Stage them for the Task 4 commit.

---

## Task 4: Wire the six-pill, two-row selector into `MainActivity`

**Files:**
- Modify: `app/src/main/java/com/example/notenlerner/MainActivity.java`

- [ ] **Step 1: Rename the SparseArray field**

Line 56: rename `trainerDifficultyButtons` → `trainerModeButtons`. Remember to update every reference in this file (there are three, at lines 56, 471, 716 in the current file).

- [ ] **Step 2: Replace the segmented-control build block (lines 452–472) with two rows**

Find the existing block:

```java
// Difficulty segmented
LinearLayout seg = createSegmented();
LinearLayout.LayoutParams segP = padHorizontal(Ui.matchWrap(), 20);
col.addView(seg, segP);

TrainingSession.Difficulty[] diffs = {
        TrainingSession.Difficulty.BEGINNER,
        TrainingSession.Difficulty.MEDIUM,
        TrainingSession.Difficulty.HARD,
};
String[] diffLabels = {
        getString(R.string.diff_beginner),
        getString(R.string.diff_medium),
        getString(R.string.diff_hard),
};
for (int i = 0; i < diffs.length; i++) {
    final TrainingSession.Difficulty d = diffs[i];
    TextView btn = createSegmentedButton(diffLabels[i], false, () -> onDifficultyClicked(d));
    seg.addView(btn, segItemParams());
    trainerDifficultyButtons.put(d.ordinal(), btn);
}
```

Replace with:

```java
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
```

- [ ] **Step 3: Rename `onDifficultyClicked` → `onModeClicked`**

At lines 632–638 the method:

```java
private void onDifficultyClicked(TrainingSession.Difficulty d) {
    trainingSession.setDifficulty(d);
    if (staffNoteView != null) staffNoteView.clearPlayedNote();
    trainerStatusText.setText(getString(R.string.trainer_status_play));
    trainerStatusText.setTextColor(Ui.MINT);
    refreshTargetAndStats();
}
```

becomes:

```java
private void onModeClicked(TrainingSession.Mode m) {
    trainingSession.setMode(m);
    if (staffNoteView != null) staffNoteView.clearPlayedNote();
    trainerStatusText.setText(getString(R.string.trainer_status_play));
    trainerStatusText.setTextColor(Ui.MINT);
    refreshTargetAndStats();
}
```

- [ ] **Step 4: Rename `updateDifficultyButtons` → `updateModeButtons`**

Line 710 (call site):

```java
updateDifficultyButtons();
```

becomes:

```java
updateModeButtons();
```

Lines 713–719 (definition):

```java
private void updateDifficultyButtons() {
    TrainingSession.Difficulty active = trainingSession.difficulty();
    for (TrainingSession.Difficulty d : TrainingSession.Difficulty.values()) {
        TextView btn = trainerDifficultyButtons.get(d.ordinal());
        if (btn != null) styleSegmentedButton(btn, d == active);
    }
}
```

becomes:

```java
private void updateModeButtons() {
    TrainingSession.Mode active = trainingSession.mode();
    for (TrainingSession.Mode m : TrainingSession.Mode.values()) {
        TextView btn = trainerModeButtons.get(m.ordinal());
        if (btn != null) styleSegmentedButton(btn, m == active);
    }
}
```

- [ ] **Step 5: Run the full debug build + tests**

Run: `./gradlew.bat testDebugUnitTest assembleDebug`
Expected: BUILD SUCCESSFUL, 0 test failures.

If `padHorizontal` / `Ui.matchWrap` / `Ui.dp` symbols fail to resolve, they're already used elsewhere in this file — search to confirm spelling matches the existing call sites at line 454 and 730.

- [ ] **Step 6: Commit MainActivity + strings.xml together**

```bash
git add app/src/main/java/com/example/notenlerner/MainActivity.java app/src/main/res/values/strings.xml
git commit -m "feat(trainer): six-mode selector in two pill rows on Notenlehrer"
```

---

## Task 5: Install on a connected device and visually verify

The repo has no instrumented test suite — visual regressions are caught only on device. CLAUDE.md mandates ImageMagick re-encoding for screenshots.

**Files:** none (verification only)

- [ ] **Step 1: Install the freshly-built debug APK**

Run: `./gradlew.bat :app:installDebug`
Expected: `INSTALLED` line in the Gradle log.

- [ ] **Step 2: Launch the Notenlehrer screen and capture a screenshot**

Run on the connected device:

```bash
adb shell monkey -p com.example.notenlerner -c android.intent.category.LAUNCHER 1
```

Then have the user tap the "Notenlehrer" tile. Capture:

```bash
adb exec-out screencap -p | magick - -resize 1920x1920\> -strip -quality 85 /tmp/android_screenshot.jpg
```

Read `/tmp/android_screenshot.jpg`.

- [ ] **Step 3: Visual checklist**

Confirm by reading the screenshot:

- Two rows of three pill buttons appear above the staff card.
- All six labels render in full without truncation: Leere Saiten · C-Dur (Anfang) · C-Dur (komplett) · Häufige Noten · Bünde 0–5 · Bünde 0–12.
- One pill is highlighted (the default `OPEN_STRINGS`).
- The staff card and the pinned bottom action bar are both still visible.

If any label truncates, the fix is to drop the font size from 14 to 13 in `createSegmentedButton` *only for this row*; flag it back to the user before changing the helper, since pentatonic also uses it.

- [ ] **Step 4: Tap each mode and confirm**

For each of the six modes, tap it and verify the target on the staff changes to a note from that mode's pool (e.g. tapping Leere Saiten cycles only between E/A/D/G/B/E). No commit needed — this is a runtime check.

---

## Self-review notes

- **Spec coverage:** every spec section maps to a task: enum rename → Task 2; UI two-row selector → Task 4; strings → Task 3; tests (existing rename + new open-strings test) → Task 1; visual verification (CLAUDE.md mandate) → Task 5.
- **No placeholders:** all code blocks are concrete.
- **Type/name consistency:** `Mode`, `setMode`, `mode()`, `trainerModeButtons`, `onModeClicked`, `updateModeButtons` — single naming used end-to-end. String IDs `mode_open_strings` / `mode_cmajor_start` / `mode_cmajor_full` / `mode_common` / `mode_frets_0_5` / `mode_frets_0_12` are consistent across strings.xml and MainActivity references.
