# Notenlehrer: Übungsmodi statt Schwierigkeitsstufen

**Date:** 2026-04-27
**Screen affected:** Notenlehrer (note trainer)

## Motivation

The current Notenlehrer offers three "Schwierigkeit" buttons — *Anfänger*, *Mittel*, *Schwer*. The user objects to the framing: these aren't really difficulty levels, they're arbitrary chromatic ranges. The user wants the selector reframed as **content categories** that describe *which notes are being practiced*, with a clearer pedagogical progression from "just open strings" up to "every note across many frets."

## Goals

- Replace the three-way Difficulty selector with a content-driven mode selector.
- Order modes from narrowest/easiest to broadest, so a learner can pick exactly the slice they're studying.
- Keep the existing UI style (programmatic Java, no XML layouts, Ui.java tokens, segmented pill buttons).
- Preserve the existing harmonic-aware scoring logic in `TrainingSession` — only the *target pool* and the *selector UI* change.

## Non-goals

- No change to pitch detection, scoring rules, or the `Evaluation` flow.
- No persistence of the selected mode across app restarts (current code doesn't persist Difficulty either).
- No tracking of per-mode statistics. Score still resets when the mode changes (mirrors today's `setDifficulty` reset).
- No instrumented/UI tests (the project has none) — coverage stays at the existing JVM unit-test level.

## Modes

The selector replaces the existing `Difficulty` enum entirely. Six modes, in order:

| # | German label        | MIDI notes                                            | Notes (display)                |
|---|---------------------|-------------------------------------------------------|--------------------------------|
| 1 | Leere Saiten        | 40, 45, 50, 55, 59, 64                                | E2 A2 D3 G3 B3 E4              |
| 2 | C-Dur (Anfang)      | 48, 50, 52, 53                                        | C4 D4 E4 F4                    |
| 3 | C-Dur (komplett)    | 48, 50, 52, 53, 55, 57, 59                            | C4 D4 E4 F4 G4 A4 B4           |
| 4 | Häufige Noten       | 40, 41, 43, 45, 47, 48, 50, 52, 53, 55                | (today's "Anfänger" set)       |
| 5 | Bünde 0–5           | 40–53 chromatic (14 notes)                            | E2 through F3                  |
| 6 | Bünde 0–12          | 40–64 chromatic (25 notes)                            | E2 through E4                  |

Notes:

- **Mode 4** intentionally equals today's BEGINNER pool — gives continuity for users who liked that range.
- **Mode 6** intentionally equals today's MEDIUM pool — same range, just relabeled to describe what it is (every note up to the 12th fret on the low E string).
- The previous HARD pool (E2–E5) is dropped. Rationale: it extended above the open-position fretboard into territory not covered by any of the new content-based labels, and the user did not ask for an "extra-wide" mode in brainstorming.

## Architecture

### Renaming `Difficulty` → `Mode`

Inside `TrainingSession`, the nested `enum Difficulty` is replaced with `enum Mode`. The replacement is mechanical:

- Field rename: `difficulty` → `mode`.
- Method rename: `difficulty()` → `mode()`, `setDifficulty(...)` → `setMode(...)`.
- The reset-on-change behavior in `setMode` is preserved.
- `Mode` keeps the same shape: `(label, allowedMidiNotes)` constructor, plus the existing `range(min, max)` helper.

This is a breaking rename of a package-private API. There are no external consumers — `MainActivity` and `TrainingSessionTest` are the only callers, and both are updated in the same change.

### Mode definition

```java
enum Mode {
    OPEN_STRINGS("Leere Saiten", new int[]{40, 45, 50, 55, 59, 64}),
    C_MAJOR_START("C-Dur (Anfang)", new int[]{48, 50, 52, 53}),
    C_MAJOR_FULL("C-Dur (komplett)", new int[]{48, 50, 52, 53, 55, 57, 59}),
    COMMON_NOTES("Häufige Noten", new int[]{40, 41, 43, 45, 47, 48, 50, 52, 53, 55}),
    FRETS_0_5("Bünde 0–5", range(40, 53)),
    FRETS_0_12("Bünde 0–12", range(40, 64));
    // ...same constructor + range() helper as before
}
```

The `label` field stays for parity with today's Difficulty, but for display we will route through `strings.xml` resources (see UI section) so translation/typography stays under one roof.

### UI selector

Currently: a single horizontal `LinearLayout` (`createSegmented`) containing three pill `TextView`s. With six items that row would either become unreadable or overflow.

Proposed layout: **two rows of three pill buttons**, stacked vertically inside a `LinearLayout(VERTICAL)`. Each row uses the existing `createSegmented()` + `segItemParams()` helpers unchanged, so styling stays consistent with the current design tokens.

Row split:
- Row 1: Leere Saiten · C-Dur (Anfang) · C-Dur (komplett)
- Row 2: Häufige Noten · Bünde 0–5 · Bünde 0–12

Active-button styling (background, text color) reuses the existing logic in `updateDifficultyButtons` — renamed to `updateModeButtons`, looking up by `Mode.ordinal()` in a `SparseArray<TextView>`.

### Strings

`strings.xml`:

- Remove: `diff_beginner`, `diff_medium`, `diff_hard`.
- Add: `mode_open_strings`, `mode_cmajor_start`, `mode_cmajor_full`, `mode_common`, `mode_frets_0_5`, `mode_frets_0_12` with the German labels listed in the table above.

Header label: the eyebrow/section title above the selector (currently absent — buttons sit directly in the column) gets no change. If a heading is later wanted, it's a separate task.

### MainActivity wiring

- Field: `trainerDifficultyButtons` → `trainerModeButtons`.
- Method: `onDifficultyClicked(Mode)` replaces `onDifficultyClicked(Difficulty)`.
- Method: `updateDifficultyButtons()` → `updateModeButtons()`.
- Build loop: iterates the `Mode[]` array in display order and lays out two `createSegmented()` rows.

## Testing

Existing JUnit tests in `TrainingSessionTest` reference `Difficulty.HARD`. The plan:

- Update `difficultyChangesTargetPoolAndResetsScore` to use `Mode.FRETS_0_12` (replacement for HARD's role of "wide pool"). With `FixedRandom(0)`, the constructor first picks `notes[0]` from the default mode (E2 = MIDI 40), then `setMode(FRETS_0_12)` calls `reset` → `nextTarget`, which sees `midi == target.midiNumber` and bumps to `notes[1]` = MIDI 41 = F2. So the existing `"F2"` assertion remains correct — only the enum constant changes.
- Rename the test method to `modeChangesTargetPoolAndResetsScore` for clarity.
- Add one new test: `openStringsModeOnlyDrawsOpenStringNotes` — sets `Mode.OPEN_STRINGS`, draws the next target several times via a non-fixed `Random`, asserts every drawn target's MIDI is in `{40, 45, 50, 55, 59, 64}`. Catches regressions where someone leaves a stray non-open MIDI in the array.

Other tests (scoring, harmonic detection, listening/detected/scored states) don't reference Difficulty and stay as-is.

## Risk / Migration

- **No persisted state**: Difficulty isn't saved anywhere, so removing the enum doesn't orphan any preference data.
- **Visual regression risk**: the second pill row adds vertical space above the staff card. Visual check on device after the change confirms staff card still has room and the bottom action bar isn't pushed off-screen.
- **String resource removal**: `diff_beginner`/`medium`/`hard` are removed from `strings.xml`. A grep confirms `MainActivity` is the only consumer — no other layout/code references them.

## Out of scope (mentioned but explicitly deferred)

- Persisting the selected mode across launches.
- Per-mode high-score tracking.
- A "custom" mode where the user picks arbitrary notes.
- Splitting C-Dur further into more sub-stages (e.g. "C D E", "C D E F G", "full") — the user raised this in brainstorming but on follow-up gave us latitude; we ship two stages (Anfang + komplett) and revisit if more granularity is wanted.
