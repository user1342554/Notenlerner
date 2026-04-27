package com.example.notenlerner;

/** Bundled finger exercises shown on the Fingerübungen screen. */
final class FingerExercise {
    final String name;
    final String subtitle;
    final String detail;
    final int recommendedBpm;
    final int[] pattern;

    private FingerExercise(String name, String subtitle, String detail, int recommendedBpm, int[] pattern) {
        this.name = name;
        this.subtitle = subtitle;
        this.detail = detail;
        this.recommendedBpm = recommendedBpm;
        this.pattern = pattern;
    }

    static final FingerExercise[] ALL = {
            new FingerExercise("Spider 1-2-3-4",
                    "Chromatisch · jede Saite",
                    "Vier Finger sauber nacheinander. Nach 16 Schritten zur nächsten Saite wechseln.",
                    80,
                    new int[]{1,2,3,4,1,2,3,4,1,2,3,4,1,2,3,4}),
            new FingerExercise("1-3-2-4 Permutation",
                    "Unabhängigkeit",
                    "Finger bleiben nah am Griffbrett. Jeder Klick ist ein Anschlag.",
                    70,
                    new int[]{1,3,2,4,1,3,2,4,1,3,2,4,1,3,2,4}),
            new FingerExercise("String Skipping",
                    "Saite überspringen",
                    "Wechsle nach jedem Zweierblock die Saite und halte das Tempo stabil.",
                    65,
                    new int[]{1,3,1,3,2,4,2,4,1,3,1,3,2,4,2,4}),
            new FingerExercise("Trill 1-2",
                    "Hammer-on · Pull-off",
                    "Nur der erste Ton wird angeschlagen, danach sauber binden.",
                    90,
                    new int[]{1,2,1,2,1,2,1,2,1,2,1,2,1,2,1,2}),
            new FingerExercise("2-4 Kraft",
                    "Ringfinger · kleiner Finger",
                    "Langsam starten. Beide Finger klingen lassen, bevor der nächste Schritt kommt.",
                    55,
                    new int[]{2,4,2,4,3,4,3,4,2,4,2,4,3,4,3,4}),
            new FingerExercise("Reverse Spider",
                    "4-3-2-1 Kontrolle",
                    "Rückwärts spielen, ohne die Hand zu verspannen.",
                    75,
                    new int[]{4,3,2,1,4,3,2,1,4,3,2,1,4,3,2,1}),
    };
}
