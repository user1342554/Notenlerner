package com.example.notenlerner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class TunerSessionTest {
    @Test
    public void detectsNearestStandardString() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(110f, 0.9f, 0.2f), 1_000L);
        TunerSession.TuningResult result = session
                .evaluate(DetectedPitch.pitched(110f, 0.9f, 0.2f), 1_000L);

        assertTrue(result.detected);
        assertEquals("A", result.string.note);
        assertEquals("5. Saite", result.string.label);
        assertEquals(0f, result.cents, 0.5f);
    }

    @Test
    public void reportsCentsAgainstNearestString() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(112f, 0.9f, 0.2f), 1_000L);
        TunerSession.TuningResult result = session
                .evaluate(DetectedPitch.pitched(112f, 0.9f, 0.2f), 1_000L);

        assertTrue(result.detected);
        assertEquals("A", result.string.note);
        assertTrue(result.cents > 0f);
    }

    @Test
    public void acceptsModerateConfidencePitchForTuning() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(110f, 0.5f, 0.2f), 1_000L);
        TunerSession.TuningResult result = session
                .evaluate(DetectedPitch.pitched(110f, 0.5f, 0.2f), 1_000L);

        assertTrue(result.detected);
    }

    @Test
    public void acceptsLowerConfidencePitchForTuning() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(110f, 0.46f, 0.2f), 1_000L);
        TunerSession.TuningResult result = session
                .evaluate(DetectedPitch.pitched(110f, 0.46f, 0.2f), 1_000L);

        assertTrue(result.detected);
        assertEquals("A", result.string.note);
    }

    @Test
    public void ignoresVeryLowConfidencePitch() {
        TunerSession.TuningResult result = new TunerSession()
                .evaluate(DetectedPitch.pitched(110f, 0.2f, 0.2f));

        assertFalse(result.detected);
    }

    @Test
    public void handlesLowEOctaveOvertoneAsLowEString() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(164.82f, 0.8f, 0.2f), 1_000L);
        TunerSession.TuningResult result = session
                .evaluate(DetectedPitch.pitched(164.82f, 0.8f, 0.2f), 1_000L);

        assertTrue(result.detected);
        assertEquals("E", result.string.note);
        assertEquals("6. Saite", result.string.label);
        assertEquals(0f, result.cents, 1.0f);
    }

    @Test
    public void holdsLastStringAcrossShortDropout() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(110f, 0.9f, 0.2f), 900L);
        TunerSession.TuningResult detected = session
                .evaluate(DetectedPitch.pitched(110f, 0.9f, 0.2f), 1_000L);

        TunerSession.TuningResult held = session
                .evaluate(DetectedPitch.silence(0.0f), 1_500L);

        assertTrue(detected.detected);
        assertTrue(held.detected);
        assertTrue(held.held);
        assertEquals("A", held.string.note);
    }

    @Test
    public void releasesHeldStringAfterLongDropout() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(110f, 0.9f, 0.2f), 900L);
        session.evaluate(DetectedPitch.pitched(110f, 0.9f, 0.2f), 1_000L);

        TunerSession.TuningResult result = session
                .evaluate(DetectedPitch.silence(0.0f), 3_000L);

        assertFalse(result.detected);
    }

    @Test
    public void ignoresLargeAttackCentOutlierBeforeStableString() {
        TunerSession session = new TunerSession();

        TunerSession.TuningResult attack = session
                .evaluate(DetectedPitch.pitched(191.24f, 0.97f, 0.12f), 1_000L);
        session.evaluate(DetectedPitch.pitched(195.60f, 0.97f, 0.03f), 1_100L);
        TunerSession.TuningResult stable = session
                .evaluate(DetectedPitch.pitched(195.60f, 0.97f, 0.03f), 1_100L);

        assertFalse(attack.detected);
        assertTrue(stable.detected);
        assertEquals("G", stable.string.note);
        assertTrue(Math.abs(stable.cents) < 5f);
    }

    @Test
    public void ignoresRunningOctaveOutlierOnSameString() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(82.40f, 0.99f, 0.02f), 900L);
        TunerSession.TuningResult stable = session
                .evaluate(DetectedPitch.pitched(82.40f, 0.99f, 0.02f), 1_000L);
        TunerSession.TuningResult outlier = session
                .evaluate(DetectedPitch.pitched(166.46f, 0.94f, 0.02f), 1_100L);

        assertTrue(stable.detected);
        assertTrue(outlier.detected);
        assertTrue(outlier.held);
        assertEquals("6. Saite", outlier.string.label);
        assertEquals(stable.cents, outlier.cents, 0.1f);
    }

    @Test
    public void doesNotHoldLowEAcrossFourthHarmonic() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(82.40f, 0.99f, 0.02f), 900L);
        session.evaluate(DetectedPitch.pitched(82.40f, 0.99f, 0.02f), 1_000L);

        TunerSession.TuningResult firstHighEFrame = session
                .evaluate(DetectedPitch.pitched(329.63f, 0.99f, 0.02f), 1_100L);
        TunerSession.TuningResult confirmedHighEFrame = session
                .evaluate(DetectedPitch.pitched(329.63f, 0.99f, 0.02f), 1_200L);

        assertFalse(firstHighEFrame.detected);
        assertTrue(confirmedHighEFrame.detected);
        assertFalse(confirmedHighEFrame.held);
        assertEquals("1. Saite", confirmedHighEFrame.string.label);
    }

    @Test
    public void ignoresSingleWrongStringFrameDuringSwitch() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(246.94f, 0.99f, 0.02f), 900L);
        session.evaluate(DetectedPitch.pitched(246.94f, 0.99f, 0.02f), 1_000L);

        TunerSession.TuningResult wrongSubharmonic = session
                .evaluate(DetectedPitch.pitched(164.82f, 0.99f, 0.02f), 1_100L);

        assertFalse(wrongSubharmonic.detected);
    }

    @Test
    public void doesNotShowOldStringWhileConfirmingNewString() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(82.40f, 0.99f, 0.02f), 900L);
        session.evaluate(DetectedPitch.pitched(82.40f, 0.99f, 0.02f), 1_000L);

        TunerSession.TuningResult firstA = session
                .evaluate(DetectedPitch.pitched(110.00f, 0.99f, 0.02f), 1_100L);
        TunerSession.TuningResult confirmedA = session
                .evaluate(DetectedPitch.pitched(110.00f, 0.99f, 0.02f), 1_200L);

        assertFalse(firstA.detected);
        assertTrue(confirmedA.detected);
        assertEquals("5. Saite", confirmedA.string.label);
    }

    @Test
    public void holdsStableAStringAcrossThirdHarmonic() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(110.00f, 0.99f, 0.03f), 900L);
        session.evaluate(DetectedPitch.pitched(110.00f, 0.99f, 0.03f), 1_000L);
        session.evaluate(DetectedPitch.pitched(110.05f, 0.99f, 0.02f), 1_400L);

        TunerSession.TuningResult harmonic = session
                .evaluate(DetectedPitch.pitched(330.00f, 0.96f, 0.01f), 1_800L);

        assertTrue(harmonic.detected);
        assertTrue(harmonic.held);
        assertEquals("5. Saite", harmonic.string.label);
    }

    @Test
    public void allowsEarlyCorrectionFromFalseLowEToBString() {
        TunerSession session = new TunerSession();
        session.evaluate(DetectedPitch.pitched(82.30f, 0.90f, 0.01f), 1_000L);

        TunerSession.TuningResult firstB = session
                .evaluate(DetectedPitch.pitched(246.94f, 0.93f, 0.01f), 1_200L);
        TunerSession.TuningResult confirmedB = session
                .evaluate(DetectedPitch.pitched(246.94f, 0.93f, 0.01f), 1_300L);

        assertFalse(firstB.detected);
        assertTrue(confirmedB.detected);
        assertFalse(confirmedB.held);
        assertEquals("2. Saite", confirmedB.string.label);
    }
}
