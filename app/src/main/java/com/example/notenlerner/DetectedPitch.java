package com.example.notenlerner;

final class DetectedPitch {
    final boolean pitched;
    final float frequencyHz;
    final float confidence;
    final float rms;

    private DetectedPitch(boolean pitched, float frequencyHz, float confidence, float rms) {
        this.pitched = pitched;
        this.frequencyHz = frequencyHz;
        this.confidence = confidence;
        this.rms = rms;
    }

    static DetectedPitch silence(float rms) {
        return new DetectedPitch(false, 0f, 0f, rms);
    }

    static DetectedPitch pitched(float frequencyHz, float confidence, float rms) {
        return new DetectedPitch(true, frequencyHz, confidence, rms);
    }
}
