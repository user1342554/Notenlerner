package com.example.notenlerner;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.os.Process;

final class PitchTracker {
    interface Listener {
        void onPitch(DetectedPitch pitch);
        void onError(String message);
    }

    private static final int SAMPLE_RATE = 44100;
    private static final int FRAME_SIZE = 4096;

    private final Context context;
    private final Listener listener;
    private final YinPitchDetector detector = new YinPitchDetector(SAMPLE_RATE, 70, 1200);

    private volatile boolean running;
    private Thread thread;
    private AudioRecord audioRecord;

    PitchTracker(Context context, Listener listener) {
        this.context = context.getApplicationContext();
        this.listener = listener;
    }

    boolean isRunning() {
        return running;
    }

    void start() {
        if (running) {
            return;
        }
        if (context.checkSelfPermission(Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            listener.onError("Mikrofonberechtigung fehlt");
            return;
        }

        running = true;
        thread = new Thread(this::recordLoop, "PitchTracker");
        thread.start();
    }

    void stop() {
        running = false;
        AudioRecord record = audioRecord;
        if (record != null) {
            try {
                record.stop();
            } catch (IllegalStateException ignored) {
            }
        }
    }

    private void recordLoop() {
        Process.setThreadPriority(Process.THREAD_PRIORITY_AUDIO);

        AudioRecord record = createAudioRecord();
        if (record == null) {
            running = false;
            listener.onError("AudioRecord konnte nicht gestartet werden");
            return;
        }

        audioRecord = record;
        short[] shortBuffer = new short[FRAME_SIZE];
        float[] floatBuffer = new float[FRAME_SIZE];

        try {
            record.startRecording();
            while (running) {
                int read = record.read(shortBuffer, 0, shortBuffer.length, AudioRecord.READ_BLOCKING);
                if (read <= 0) {
                    continue;
                }
                for (int i = 0; i < read; i++) {
                    floatBuffer[i] = shortBuffer[i] / 32768f;
                }
                listener.onPitch(detector.detect(floatBuffer, read));
            }
        } catch (IllegalStateException e) {
            listener.onError("Mikrofonaufnahme ist abgebrochen: " + e.getMessage());
        } finally {
            record.release();
            audioRecord = null;
            running = false;
        }
    }

    private AudioRecord createAudioRecord() {
        int minBufferBytes = AudioRecord.getMinBufferSize(
                SAMPLE_RATE,
                AudioFormat.CHANNEL_IN_MONO,
                AudioFormat.ENCODING_PCM_16BIT
        );
        if (minBufferBytes <= 0) {
            return null;
        }

        int bufferBytes = Math.max(minBufferBytes * 2, FRAME_SIZE * 2);
        int[] sources = {
                MediaRecorder.AudioSource.UNPROCESSED,
                MediaRecorder.AudioSource.VOICE_RECOGNITION,
                MediaRecorder.AudioSource.MIC
        };

        for (int source : sources) {
            AudioRecord record = new AudioRecord(
                    source,
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferBytes
            );
            if (record.getState() == AudioRecord.STATE_INITIALIZED) {
                return record;
            }
            record.release();
        }
        return null;
    }
}
