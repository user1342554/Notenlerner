package com.example.notenlerner;

import java.util.Locale;

final class NoteName {
    private static final String[] NAMES = {
            "C", "C#", "D", "D#", "E", "F", "F#", "G", "G#", "A", "A#", "B"
    };

    final String name;
    final int octave;
    final int midiNumber;
    final float cents;
    final float targetFrequencyHz;

    private NoteName(String name, int octave, int midiNumber, float cents, float targetFrequencyHz) {
        this.name = name;
        this.octave = octave;
        this.midiNumber = midiNumber;
        this.cents = cents;
        this.targetFrequencyHz = targetFrequencyHz;
    }

    static NoteName fromFrequency(float frequencyHz) {
        double midi = 69.0 + 12.0 * log2(frequencyHz / 440.0);
        int nearest = (int) Math.round(midi);
        int index = Math.floorMod(nearest, 12);
        int octave = nearest / 12 - 1;
        double target = 440.0 * Math.pow(2.0, (nearest - 69) / 12.0);
        float cents = (float) (1200.0 * log2(frequencyHz / target));
        return new NoteName(NAMES[index], octave, nearest, cents, (float) target);
    }

    static NoteName fromMidi(int midiNumber) {
        return fromMidi(midiNumber, 0f);
    }

    static NoteName fromMidi(int midiNumber, float cents) {
        int index = Math.floorMod(midiNumber, 12);
        int octave = midiNumber / 12 - 1;
        double target = 440.0 * Math.pow(2.0, (midiNumber - 69) / 12.0);
        return new NoteName(NAMES[index], octave, midiNumber, cents, (float) target);
    }

    String displayName() {
        return name + octave;
    }

    String centsText() {
        if (Math.abs(cents) < 0.5f) {
            return "genau";
        }
        return String.format(Locale.US, "%+.0f cents", cents);
    }

    private static double log2(double value) {
        return Math.log(value) / Math.log(2.0);
    }
}
