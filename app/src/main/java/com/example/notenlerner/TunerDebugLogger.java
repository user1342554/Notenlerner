package com.example.notenlerner;

import android.content.Context;
import android.util.Log;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Locale;

final class TunerDebugLogger {
    private static final String TAG = "NotenlernerTuner";
    private static final String FILE_NAME = "tuner_debug_log.csv";

    private final File file;

    TunerDebugLogger(Context context) {
        file = new File(context.getFilesDir(), FILE_NAME);
        reset();
    }

    File file() {
        return file;
    }

    void reset() {
        try (FileWriter writer = new FileWriter(file, false)) {
            writer.write("time_ms,pitched,raw_hz,confidence,rms,detected,string,label,target_hz,cents,held\n");
        } catch (IOException e) {
            Log.w(TAG, "Could not reset tuner debug log", e);
        }
    }

    void log(DetectedPitch pitch, TunerSession.TuningResult result) {
        String line = String.format(
                Locale.US,
                "%d,%s,%.2f,%.3f,%.4f,%s,%s,%s,%.2f,%.2f,%s\n",
                System.currentTimeMillis(),
                pitch.pitched,
                pitch.frequencyHz,
                pitch.confidence,
                pitch.rms,
                result.detected,
                result.detected ? result.string.note : "",
                result.detected ? result.string.label : "",
                result.detected ? result.string.frequencyHz : 0f,
                result.detected ? result.cents : 0f,
                result.detected && result.held
        );

        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write(line);
        } catch (IOException e) {
            Log.w(TAG, "Could not write tuner debug log", e);
        }
        Log.d(TAG, line.trim());
    }
}
