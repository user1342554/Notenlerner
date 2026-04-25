package com.example.notenlerner;

final class YinPitchDetector {
    private static final float SILENCE_RMS = 0.006f;
    private static final float YIN_THRESHOLD = 0.14f;

    private final int sampleRate;
    private final int minTau;
    private final int maxTau;
    private final float[] difference;
    private final float[] cumulativeMeanNormalizedDifference;

    YinPitchDetector(int sampleRate, int minFrequencyHz, int maxFrequencyHz) {
        this.sampleRate = sampleRate;
        this.minTau = Math.max(2, sampleRate / maxFrequencyHz);
        this.maxTau = Math.max(minTau + 1, sampleRate / minFrequencyHz);
        this.difference = new float[maxTau + 1];
        this.cumulativeMeanNormalizedDifference = new float[maxTau + 1];
    }

    DetectedPitch detect(float[] audio, int length) {
        float rms = calculateRms(audio, length);
        if (rms < SILENCE_RMS || length < maxTau * 2) {
            return DetectedPitch.silence(rms);
        }

        removeDcOffset(audio, length);
        calculateDifference(audio, length);
        calculateCumulativeMeanNormalizedDifference();

        int tau = absoluteThreshold();
        if (tau == -1) {
            return DetectedPitch.silence(rms);
        }

        float betterTau = parabolicInterpolation(tau);
        float frequency = sampleRate / betterTau;
        float probability = 1f - cumulativeMeanNormalizedDifference[tau];
        return DetectedPitch.pitched(frequency, clamp(probability, 0f, 1f), rms);
    }

    private float calculateRms(float[] audio, int length) {
        double sum = 0.0;
        for (int i = 0; i < length; i++) {
            sum += audio[i] * audio[i];
        }
        return (float) Math.sqrt(sum / length);
    }

    private void removeDcOffset(float[] audio, int length) {
        float mean = 0f;
        for (int i = 0; i < length; i++) {
            mean += audio[i];
        }
        mean /= length;
        for (int i = 0; i < length; i++) {
            audio[i] -= mean;
        }
    }

    private void calculateDifference(float[] audio, int length) {
        for (int tau = 0; tau <= maxTau; tau++) {
            difference[tau] = 0f;
        }

        int window = Math.min(length - maxTau, maxTau * 2);
        for (int tau = minTau; tau <= maxTau; tau++) {
            float sum = 0f;
            for (int i = 0; i < window; i++) {
                float delta = audio[i] - audio[i + tau];
                sum += delta * delta;
            }
            difference[tau] = sum;
        }
    }

    private void calculateCumulativeMeanNormalizedDifference() {
        cumulativeMeanNormalizedDifference[0] = 1f;
        float runningSum = 0f;
        for (int tau = 1; tau <= maxTau; tau++) {
            runningSum += difference[tau];
            cumulativeMeanNormalizedDifference[tau] =
                    runningSum == 0f ? 1f : difference[tau] * tau / runningSum;
        }
    }

    private int absoluteThreshold() {
        for (int tau = minTau; tau <= maxTau; tau++) {
            if (cumulativeMeanNormalizedDifference[tau] < YIN_THRESHOLD) {
                while (tau + 1 <= maxTau
                        && cumulativeMeanNormalizedDifference[tau + 1]
                        < cumulativeMeanNormalizedDifference[tau]) {
                    tau++;
                }
                return tau;
            }
        }
        return -1;
    }

    private float parabolicInterpolation(int tau) {
        int left = Math.max(minTau, tau - 1);
        int right = Math.min(maxTau, tau + 1);
        if (left == tau || right == tau) {
            return tau;
        }

        float s0 = cumulativeMeanNormalizedDifference[left];
        float s1 = cumulativeMeanNormalizedDifference[tau];
        float s2 = cumulativeMeanNormalizedDifference[right];
        float denominator = 2f * (2f * s1 - s2 - s0);
        if (Math.abs(denominator) < 0.000001f) {
            return tau;
        }
        return tau + (s2 - s0) / denominator;
    }

    private float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }
}
