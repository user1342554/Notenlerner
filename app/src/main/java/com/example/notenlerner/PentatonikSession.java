package com.example.notenlerner;

/** State for the Pentatonik (pentatonic-scale) screen. */
final class PentatonikSession {
    enum Shape {
        MINOR(new int[]{0, 3, 5, 7, 10}),
        MAJOR(new int[]{0, 2, 4, 7, 9});

        final int[] intervals;
        Shape(int[] intervals) { this.intervals = intervals; }
    }

    static final String[] ROOT_NAMES = {
            "C", "C\u266F", "D", "D\u266F", "E", "F",
            "F\u266F", "G", "G\u266F", "A", "A\u266F", "B"
    };

    private int rootIdx = 9; // A
    private Shape shape = Shape.MINOR;
    private int position = 0;
    private int step = 0;

    int rootIdx() { return rootIdx; }
    Shape shape() { return shape; }
    int position() { return position; }
    int step() { return step; }

    void setRoot(int rootIdx) {
        this.rootIdx = ((rootIdx % 12) + 12) % 12;
        step = 0;
    }

    void setShape(Shape shape) {
        this.shape = shape;
        step = 0;
    }

    void nextPosition() {
        position = (position + 1) % 5;
        step = 0;
    }

    void nextStep() {
        step = (step + 1) % shape.intervals.length;
    }

    String currentNoteName() {
        return ROOT_NAMES[((rootIdx + shape.intervals[step]) % 12 + 12) % 12];
    }

    int activePitchClass() {
        return ((rootIdx + shape.intervals[step]) % 12 + 12) % 12;
    }

    int positionStartFret() {
        int[] starts = {0, 3, 5, 7, 10};
        return starts[position];
    }

    String rootName() { return ROOT_NAMES[rootIdx]; }

    String headline() {
        return rootName() + " " + (shape == Shape.MINOR ? "Moll" : "Dur") + "-Pentatonik";
    }

    String notesLine() {
        StringBuilder sb = new StringBuilder("Fünf Töne pro Oktave · ");
        int[] iv = shape.intervals;
        for (int i = 0; i < iv.length; i++) {
            if (i > 0) sb.append(" · ");
            sb.append(ROOT_NAMES[((rootIdx + iv[i]) % 12 + 12) % 12]);
        }
        return sb.toString();
    }
}
