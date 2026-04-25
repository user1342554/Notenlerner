package com.example.notenlerner;

import java.util.Random;

final class TrainingSession {
    private static final int VISIBLE_FRAMES_REQUIRED = 2;
    private static final int STABLE_FRAMES_REQUIRED = 3;
    private static final float MAX_CORRECT_CENTS = 35f;
    private static final float MIN_CONFIDENCE = 0.72f;
    private static final int[] TARGET_HARMONICS = {1, 2, 3, 4};

    private final Random random;

    private Difficulty difficulty = Difficulty.BEGINNER;
    private NoteName target;
    private int correct;
    private int attempts;
    private int lastMidi = -1;
    private int stableFrames;
    private boolean armed = true;

    TrainingSession(Random random) {
        this.random = random;
        nextTarget();
    }

    NoteName target() {
        return target;
    }

    int correct() {
        return correct;
    }

    int attempts() {
        return attempts;
    }

    int accuracyPercent() {
        if (attempts == 0) {
            return 0;
        }
        return Math.round(correct * 100f / attempts);
    }

    void reset() {
        correct = 0;
        attempts = 0;
        lastMidi = -1;
        stableFrames = 0;
        armed = true;
        nextTarget();
    }

    Difficulty difficulty() {
        return difficulty;
    }

    void setDifficulty(Difficulty difficulty) {
        if (this.difficulty == difficulty) {
            return;
        }
        this.difficulty = difficulty;
        reset();
    }

    Evaluation evaluate(DetectedPitch pitch) {
        if (!pitch.pitched || pitch.confidence < MIN_CONFIDENCE) {
            lastMidi = -1;
            stableFrames = 0;
            armed = true;
            return Evaluation.listening();
        }

        NoteName played = correctedNoteForTarget(pitch.frequencyHz);
        if (played.midiNumber == lastMidi) {
            stableFrames++;
        } else {
            lastMidi = played.midiNumber;
            stableFrames = 1;
        }

        if (stableFrames < VISIBLE_FRAMES_REQUIRED) {
            return Evaluation.listening();
        }

        if (!armed || stableFrames < STABLE_FRAMES_REQUIRED) {
            return Evaluation.detected(played);
        }

        boolean correctNow = played.midiNumber == target.midiNumber
                && Math.abs(played.cents) <= MAX_CORRECT_CENTS;
        attempts++;
        if (correctNow) {
            correct++;
        }

        NoteName previousTarget = target;
        if (correctNow) {
            nextTarget();
        }
        armed = false;
        stableFrames = 0;
        lastMidi = played.midiNumber;
        return Evaluation.scored(played, previousTarget, correctNow);
    }

    void skip() {
        lastMidi = -1;
        stableFrames = 0;
        armed = true;
        nextTarget();
    }

    private void nextTarget() {
        int[] notes = difficulty.allowedMidiNotes;
        int midi = notes[random.nextInt(notes.length)];
        if (target != null && notes.length > 1 && midi == target.midiNumber) {
            int startIndex = indexOf(notes, midi);
            midi = notes[(startIndex + 1) % notes.length];
        }
        target = NoteName.fromMidi(midi);
    }

    private NoteName correctedNoteForTarget(float frequencyHz) {
        for (int harmonic : TARGET_HARMONICS) {
            float harmonicTargetHz = target.targetFrequencyHz * harmonic;
            float harmonicCents = cents(frequencyHz, harmonicTargetHz);
            if (Math.abs(harmonicCents) <= MAX_CORRECT_CENTS) {
                return NoteName.fromMidi(target.midiNumber, harmonicCents);
            }
        }
        return NoteName.fromFrequency(frequencyHz);
    }

    private float cents(float frequencyHz, float targetHz) {
        return (float) (1200.0 * Math.log(frequencyHz / targetHz) / Math.log(2.0));
    }

    private int indexOf(int[] notes, int midi) {
        for (int i = 0; i < notes.length; i++) {
            if (notes[i] == midi) {
                return i;
            }
        }
        return 0;
    }

    enum Difficulty {
        BEGINNER("Anfaenger", new int[]{40, 41, 43, 45, 47, 48, 50, 52, 53, 55}),
        MEDIUM("Mittel", range(40, 64)),
        HARD("Schwer", range(40, 76));

        final String label;
        final int[] allowedMidiNotes;

        Difficulty(String label, int[] allowedMidiNotes) {
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

    static final class Evaluation {
        enum Type {
            LISTENING,
            DETECTED,
            SCORED
        }

        final Type type;
        final NoteName played;
        final NoteName expected;
        final boolean correct;

        private Evaluation(Type type, NoteName played, NoteName expected, boolean correct) {
            this.type = type;
            this.played = played;
            this.expected = expected;
            this.correct = correct;
        }

        static Evaluation listening() {
            return new Evaluation(Type.LISTENING, null, null, false);
        }

        static Evaluation detected(NoteName played) {
            return new Evaluation(Type.DETECTED, played, null, false);
        }

        static Evaluation scored(NoteName played, NoteName expected, boolean correct) {
            return new Evaluation(Type.SCORED, played, expected, correct);
        }
    }
}
