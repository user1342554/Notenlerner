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
