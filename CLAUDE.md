# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project

Notenlerner is a single-Activity Android app (Java) for learning notes on the guitar. UI strings are in German. The app drives five screens — Home, Notenlehrer (note trainer), Stimmgerät (tuner), Pentatonik, Fingerübungen — switched by `MainActivity` view-state.

## Build & test

```bash
./gradlew.bat testDebugUnitTest assembleDebug   # full check
./gradlew.bat assembleDebug                     # debug APK only
./gradlew.bat testDebugUnitTest                 # JUnit unit tests
./gradlew.bat :app:testDebugUnitTest --tests 'com.example.notenlerner.TunerSessionTest.detectsNearestStandardString'
```

There is no instrumentation/UI test source set; tests live under `app/src/test/` and run on the JVM. The app needs `RECORD_AUDIO` at runtime — there are no Robolectric/Espresso suites to verify UI changes, so audio/UI regressions surface only on device.

## Important Gradle/Android config

- `android.useAndroidX=false` (gradle.properties). Code uses **framework views directly** (`android.widget.*`, `Activity`, no `androidx.*`, no Material Components, no Jetpack Compose). Do not introduce AndroidX without flipping that flag and migrating.
- `compileSdk 35`, `minSdk 26`, `targetSdk 35`, Java 17 source/target.
- Only dependency is `junit:junit:4.13.2` (test). No third-party runtime libs — pitch detection, custom views, drawables are all hand-rolled.
- All UI is built **programmatically in Java**. There are no layout XMLs under `res/layout/`; only `values/` (colors, strings, styles). Don't add XML layouts — match the existing programmatic style.

## Architecture

### Audio → detection → session pipeline

```
PitchTracker  ──►  YinPitchDetector  ──►  DetectedPitch  ──►  { TrainingSession  |  TunerSession }
(mic thread)       (YIN algorithm)        (immutable)         (per-screen logic)
```

- `PitchTracker` (`app/src/main/java/com/example/notenlerner/PitchTracker.java`): owns an `AudioRecord` on a background thread (44.1 kHz mono PCM16, 4096-sample frames). Tries `UNPROCESSED` → `VOICE_RECOGNITION` → `MIC` sources. Drops samples to floats and feeds the detector each frame; calls `Listener.onPitch` / `onError` from the audio thread — `MainActivity` posts back to the main thread.
- `YinPitchDetector`: classic YIN with cumulative-mean-normalized difference, parabolic interpolation, RMS gate (`SILENCE_RMS = 0.006`), threshold `0.14`. Constructor takes `(sampleRate, minHz, maxHz)`; default range is 70–1200 Hz.
- `DetectedPitch`: immutable struct (`pitched`, `frequencyHz`, `confidence`, `rms`) with static factories `silence()` / `pitched()`.
- `NoteName`: MIDI ↔ frequency conversion (A4 = 440 Hz). `fromFrequency` snaps to nearest semitone and returns cents offset; `fromMidi(midi, cents)` builds an explicit target.

### Session state holders

- `TrainingSession`: drives the Notenlehrer screen. Picks a target MIDI note from a `Difficulty` set (BEGINNER/MEDIUM/HARD), requires N stable frames, and accepts target **harmonics 1–4** as correct (so a played open string scores even if YIN locks onto an overtone). Returns an `Evaluation` with type LISTENING / DETECTED / SCORED.
- `TunerSession`: drives the Stimmgerät screen with the six standard-tuning strings (E2 A2 D3 G3 B3 E4). Non-trivial state machine: per-string smoothing, hold-last-result for ~1.8 s across dropouts, harmonic suppression so the 2×/3×/4× of a stable string doesn't flip the display, multi-frame confirmation before switching strings, octave folding (`playableCents`). When changing this class, **read `TunerSessionTest` first** — there are 14+ tests covering specific real-world false-trigger scenarios (low-E vs B subharmonic, A-string third harmonic hold, etc.).
- `PentatonikSession`: pure data — root index, MAJOR/MINOR shape, position (1 of 5), step within scale.

### UI layer

- `MainActivity` (~80 KB, intentionally monolithic): builds all five screens programmatically and toggles which root `View` is attached. Holds widget refs as fields, refreshes them from session state on each show. Uses `Handler(Looper.getMainLooper())` for metronome/tick loops on Pentatonik and Fingerübungen.
- `Ui.java`: **single source of design tokens** — color constants (`BG_0`, `MINT`, etc.) mirror the iOS-style dark-mode design bundle CSS variables, plus builder helpers (`card()`, `button()`, `ghostButton()`, `statPill()`, `eyebrow()`, `display()`, `title()`, `body()`, `dp()`). Use these helpers and constants instead of inlining colors/typography. `colors.xml` carries the same values for theme/window backgrounds.
- Custom `View` subclasses do their own `onDraw`:
  - `StaffNoteView` — five-line staff with target note glyph (Notenlehrer)
  - `TunerMeterView` — horizontal cents meter (used by both Tuner and Trainer)
  - `FretboardView` — guitar fretboard with pentatonic-scale highlighting
  - `PatternGridView` — 16-step finger-exercise grid with active-step highlight
  - `TileBackgroundView` — Home-tile gradient background
- `TunerDebugLogger`: writes `tuner_debug_log.csv` into `Context.getFilesDir()` on each pitch frame; gitignored. Useful when triaging tuner regressions — capture a session and inspect the CSV.

### Memory model

The audio thread (`PitchTracker`) calls listeners directly — `MainActivity` re-posts work to the main thread before touching views. Sessions are not thread-safe; only mutate them on the main thread.

## Working in this repo

- The user has explicit guidance recorded in memory about implementing design handoffs **inside the Android app**, not as a parallel HTML/web prototype. The design-bundle HTML is just the medium; redesign the Activities/views/themes to match.
- When a screen needs visual verification, build & install on a connected device. To capture a screenshot, **always re-encode through ImageMagick** before reading — raw `adb screencap` PNGs cause the API to reject the image:
  ```
  adb exec-out screencap -p | magick - -resize 1920x1920\> -strip -quality 85 /tmp/android_screenshot.jpg
  ```
- `*.png` files at the repo root (`nl_home.png`, `nl_check.png`, etc.) and `tuner_debug_log*.csv` are gitignored working artifacts; don't commit them.
