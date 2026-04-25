package com.example.notenlerner;

final class TunerSession {
    private static final float MIN_CONFIDENCE = 0.45f;
    private static final float MAX_ACCEPTED_CENTS = 70f;
    private static final float MAX_ATTACK_CENTS = 35f;
    private static final long HOLD_MILLIS = 1800L;
    private static final float CENT_SMOOTHING = 0.24f;
    private static final float MAX_RUNNING_CENT_STEP = 14f;
    private static final long STABLE_STRING_HARMONIC_HOLD_MILLIS = 650L;
    private static final int REQUIRED_NEW_STRING_FRAMES = 2;
    private static final int REQUIRED_STRING_SWITCH_FRAMES = 2;

    private static final GuitarString[] STANDARD_TUNING = {
            new GuitarString("E", "6. Saite", 82.41f),
            new GuitarString("A", "5. Saite", 110.00f),
            new GuitarString("D", "4. Saite", 146.83f),
            new GuitarString("G", "3. Saite", 196.00f),
            new GuitarString("B", "2. Saite", 246.94f),
            new GuitarString("E", "1. Saite", 329.63f)
    };

    private TuningResult lastStableResult = TuningResult.listening();
    private long lastDetectedAtMillis;
    private long currentStringStartedAtMillis;
    private float smoothedCents;
    private GuitarString pendingString;
    private int pendingFrames;

    TuningResult evaluate(DetectedPitch pitch) {
        return evaluate(pitch, System.currentTimeMillis());
    }

    TuningResult evaluate(DetectedPitch pitch, long nowMillis) {
        if (!pitch.pitched || pitch.confidence < MIN_CONFIDENCE) {
            return heldOrListening(nowMillis);
        }

        GuitarString nearest = null;
        float nearestCents = 0f;
        float nearestScore = Float.MAX_VALUE;
        for (int i = 0; i < STANDARD_TUNING.length; i++) {
            GuitarString candidate = STANDARD_TUNING[i];
            float candidateCents = playableCents(pitch.frequencyHz, candidate.frequencyHz);
            if (Float.isNaN(candidateCents)) {
                continue;
            }
            float score = Math.abs(candidateCents);
            if (score < nearestScore) {
                nearest = candidate;
                nearestCents = candidateCents;
                nearestScore = score;
            }
        }

        if (lastStableResult.detected && nowMillis - lastDetectedAtMillis <= HOLD_MILLIS) {
            float activeStringCents = playableCents(pitch.frequencyHz, lastStableResult.string.frequencyHz);
            if (!Float.isNaN(activeStringCents) && Math.abs(activeStringCents) <= MAX_ACCEPTED_CENTS) {
                nearest = lastStableResult.string;
                nearestCents = activeStringCents;
            }
        }

        if (nearest == null || Math.abs(nearestCents) > MAX_ACCEPTED_CENTS) {
            return heldOrListening(nowMillis);
        }

        if (isStableStringHarmonic(nearest, pitch.frequencyHz, nowMillis)) {
            lastDetectedAtMillis = nowMillis;
            return TuningResult.detected(
                    lastStableResult.string,
                    lastStableResult.frequencyHz,
                    smoothedCents,
                    lastStableResult.confidence,
                    true
            );
        }

        if (isUnconfirmedAttack(nearest, nearestCents, nowMillis)) {
            if (lastStableResult.detected && lastStableResult.string != nearest) {
                return TuningResult.listening();
            }
            return heldOrListening(nowMillis);
        }

        if (lastStableResult.detected
                && lastStableResult.string == nearest
                && nowMillis - lastDetectedAtMillis <= HOLD_MILLIS) {
            if (Math.abs(nearestCents - smoothedCents) > MAX_RUNNING_CENT_STEP) {
                lastDetectedAtMillis = nowMillis;
                return TuningResult.detected(
                        lastStableResult.string,
                        lastStableResult.frequencyHz,
                        smoothedCents,
                        lastStableResult.confidence,
                        true
                );
            }
            smoothedCents = smoothedCents + (nearestCents - smoothedCents) * CENT_SMOOTHING;
        } else {
            smoothedCents = nearestCents;
        }

        lastDetectedAtMillis = nowMillis;
        if (!lastStableResult.detected || lastStableResult.string != nearest) {
            currentStringStartedAtMillis = nowMillis;
        }
        lastStableResult = TuningResult.detected(nearest, pitch.frequencyHz, smoothedCents, pitch.confidence, false);
        pendingString = null;
        pendingFrames = 0;
        return lastStableResult;
    }

    private boolean isStableStringHarmonic(GuitarString nearest, float frequencyHz, long nowMillis) {
        if (!lastStableResult.detected
                || lastStableResult.string == nearest
                || nowMillis - lastDetectedAtMillis > HOLD_MILLIS
                || nowMillis - currentStringStartedAtMillis < STABLE_STRING_HARMONIC_HOLD_MILLIS) {
            return false;
        }

        return Math.abs(cents(frequencyHz, lastStableResult.string.frequencyHz * 2f)) <= MAX_ACCEPTED_CENTS
                || Math.abs(cents(frequencyHz, lastStableResult.string.frequencyHz * 3f)) <= MAX_ACCEPTED_CENTS
                || Math.abs(cents(frequencyHz, lastStableResult.string.frequencyHz * 4f)) <= MAX_ACCEPTED_CENTS;
    }

    private boolean isUnconfirmedAttack(GuitarString nearest, float cents, long nowMillis) {
        boolean hasCurrentString = lastStableResult.detected
                && nowMillis - lastDetectedAtMillis <= HOLD_MILLIS
                && lastStableResult.string == nearest;
        if (hasCurrentString) {
            return false;
        }

        if (Math.abs(cents) > MAX_ATTACK_CENTS) {
            pendingString = null;
            pendingFrames = 0;
            return true;
        }

        if (pendingString == nearest) {
            pendingFrames++;
        } else {
            pendingString = nearest;
            pendingFrames = 1;
        }

        int requiredFrames = hasRecentDifferentString(nowMillis) ? REQUIRED_STRING_SWITCH_FRAMES : REQUIRED_NEW_STRING_FRAMES;
        return pendingFrames < requiredFrames;
    }

    private boolean hasRecentDifferentString(long nowMillis) {
        return lastStableResult.detected
                && nowMillis - lastDetectedAtMillis <= HOLD_MILLIS
                && lastStableResult.string != pendingString;
    }

    private TuningResult heldOrListening(long nowMillis) {
        if (lastStableResult.detected && nowMillis - lastDetectedAtMillis <= HOLD_MILLIS) {
            return TuningResult.detected(
                    lastStableResult.string,
                    lastStableResult.frequencyHz,
                    lastStableResult.cents,
                    lastStableResult.confidence,
                    true
            );
        }
        return TuningResult.listening();
    }

    private float foldedCents(float frequencyHz, float targetHz) {
        float raw = cents(frequencyHz, targetHz);
        while (raw > 600f) {
            raw -= 1200f;
        }
        while (raw < -600f) {
            raw += 1200f;
        }
        return raw;
    }

    private float playableCents(float frequencyHz, float targetHz) {
        float fundamentalCents = cents(frequencyHz, targetHz);
        if (Math.abs(fundamentalCents) <= MAX_ACCEPTED_CENTS) {
            return fundamentalCents;
        }

        float firstOctaveCents = cents(frequencyHz, targetHz * 2f);
        if (Math.abs(firstOctaveCents) <= MAX_ACCEPTED_CENTS) {
            return firstOctaveCents;
        }

        return Float.NaN;
    }

    @SuppressWarnings("unused")
    private TuningResult evaluateStrictlyByFundamental(DetectedPitch pitch) {
        GuitarString nearest = STANDARD_TUNING[0];
        float nearestCents = cents(pitch.frequencyHz, nearest.frequencyHz);
        for (int i = 1; i < STANDARD_TUNING.length; i++) {
            GuitarString candidate = STANDARD_TUNING[i];
            float candidateCents = cents(pitch.frequencyHz, candidate.frequencyHz);
            if (Math.abs(candidateCents) < Math.abs(nearestCents)) {
                nearest = candidate;
                nearestCents = candidateCents;
            }
        }

        return TuningResult.detected(nearest, pitch.frequencyHz, nearestCents, pitch.confidence);
    }

    private float cents(float frequencyHz, float targetHz) {
        return (float) (1200.0 * Math.log(frequencyHz / targetHz) / Math.log(2.0));
    }

    static final class GuitarString {
        final String note;
        final String label;
        final float frequencyHz;

        GuitarString(String note, String label, float frequencyHz) {
            this.note = note;
            this.label = label;
            this.frequencyHz = frequencyHz;
        }
    }

    static final class TuningResult {
        final boolean detected;
        final GuitarString string;
        final float frequencyHz;
        final float cents;
        final float confidence;
        final boolean held;

        private TuningResult(
                boolean detected,
                GuitarString string,
                float frequencyHz,
                float cents,
                float confidence,
                boolean held
        ) {
            this.detected = detected;
            this.string = string;
            this.frequencyHz = frequencyHz;
            this.cents = cents;
            this.confidence = confidence;
            this.held = held;
        }

        static TuningResult listening() {
            return new TuningResult(false, null, 0f, 0f, 0f, false);
        }

        static TuningResult detected(
                GuitarString string,
                float frequencyHz,
                float cents,
                float confidence
        ) {
            return detected(string, frequencyHz, cents, confidence, false);
        }

        static TuningResult detected(
                GuitarString string,
                float frequencyHz,
                float cents,
                float confidence,
                boolean held
        ) {
            return new TuningResult(true, string, frequencyHz, cents, confidence, held);
        }
    }
}
