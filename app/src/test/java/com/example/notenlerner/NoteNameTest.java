package com.example.notenlerner;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class NoteNameTest {
    @Test
    public void convertsReferencePitchesToNotes() {
        assertEquals("A4", NoteName.fromFrequency(440f).displayName());
        assertEquals("E2", NoteName.fromFrequency(82.41f).displayName());
        assertEquals("G3", NoteName.fromFrequency(196f).displayName());
    }

    @Test
    public void yinDetectsSyntheticGuitarRangePitch() {
        int sampleRate = 44100;
        float frequency = 110f;
        float[] audio = sine(sampleRate, frequency, 4096, 0.6f);

        DetectedPitch pitch = new YinPitchDetector(sampleRate, 70, 1200).detect(audio, audio.length);

        assertTrue(pitch.pitched);
        assertEquals(frequency, pitch.frequencyHz, 1.0f);
        assertEquals("A2", NoteName.fromFrequency(pitch.frequencyHz).displayName());
    }

    @Test
    public void yinDetectsQuietGuitarRangePitch() {
        int sampleRate = 44100;
        float frequency = 110f;
        float[] audio = sine(sampleRate, frequency, 4096, 0.012f);

        DetectedPitch pitch = new YinPitchDetector(sampleRate, 70, 1200).detect(audio, audio.length);

        assertTrue(pitch.pitched);
        assertEquals(frequency, pitch.frequencyHz, 1.0f);
    }

    private float[] sine(int sampleRate, float frequency, int size, float amplitude) {
        float[] data = new float[size];
        for (int i = 0; i < size; i++) {
            data[i] = (float) (amplitude * Math.sin(2.0 * Math.PI * frequency * i / sampleRate));
        }
        return data;
    }
}
