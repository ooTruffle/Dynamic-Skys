package com.ootruffle.dynamicskies.audio;

import com.ootruffle.dynamicskies.Log;
import com.ootruffle.dynamicskies.config.DynamicSkiesConfig;

import javax.sound.sampled.Mixer;
import java.util.ArrayList;
import java.util.List;

/**
 * Owns the capture thread and hands smoothed audio values to the sky renderer.
 * {@link #update(float)} runs once per rendered frame on the render thread.
 */
public final class AudioManager {

    public static final int BANDS = AudioAnalyzer.BANDS;

    private static final int SOURCE_OFF = 0;
    private static final int SOURCE_DEVICE = 1;
    private static final int SOURCE_FAKE = 2;
    private static final int SOURCE_SYSTEM = 3;

    private static AudioAnalyzer analyzer;
    private static String runningFilter;
    private static boolean restartRequested;
    private static String reportedError;
    /** When the current capture started; errors wait a moment so typing a device name doesn't spam chat. */
    private static long startedAt;

    private static float bass, mid, high, level, beat;
    private static long seenBeat;
    private static long seenDrop;
    private static float build;
    private static float dropAge = 100f;
    private static boolean beatThisFrame, dropThisFrame;
    private static double fakePhase;
    private static final float[] spectrum = new float[BANDS];

    private AudioManager() {
    }

    public static float bass() { return bass; }
    public static float mid() { return mid; }
    public static float high() { return high; }
    public static float level() { return level; }
    public static float beat() { return beat; }

    /** 0..1 how far into a build-up the music is. */
    public static float build() { return build; }

    /** Seconds since the last drop (large when there hasn't been one). */
    public static float dropAge() { return dropAge; }

    public static boolean beatThisFrame() { return beatThisFrame; }

    public static boolean dropThisFrame() { return dropThisFrame; }

    /** Smoothed 64-band spectrum, 0..1 each. */
    public static float[] spectrum() { return spectrum; }

    public static void restart() {
        restartRequested = true;
    }

    public static void update(float dt) {
        DynamicSkiesConfig cfg = DynamicSkiesConfig.INSTANCE;
        int source = cfg.enabled ? cfg.audioSource : SOURCE_OFF;

        // Start, stop or switch the capture thread to match the settings.
        boolean system = source == SOURCE_SYSTEM;
        boolean wantCapture = source == SOURCE_DEVICE || system;
        String filter = system ? "<system>" : cfg.audioDevice == null ? "" : cfg.audioDevice.trim();
        if (analyzer != null && (!wantCapture || !filter.equals(runningFilter) || restartRequested)) {
            analyzer.stop();
            analyzer = null;
        }
        if (wantCapture && analyzer == null) {
            analyzer = new AudioAnalyzer(system ? "" : filter, system);
            runningFilter = filter;
            reportedError = null;
            startedAt = System.nanoTime();
            seenBeat = 0L;
            seenDrop = 0L;
            analyzer.start();
            Log.info("Starting audio capture: " + (system ? "system audio" : filter.isEmpty() ? "auto" : filter));
        }
        restartRequested = false;

        float tBass = 0f, tMid = 0f, tHigh = 0f, tLevel = 0f;
        float tBuild = 0f;
        boolean newBeat = false;
        boolean newDrop = false;
        float sens = cfg.sensitivity;

        if (wantCapture && analyzer != null) {
            if (analyzer.error != null && !analyzer.error.equals(reportedError)
                    && System.nanoTime() - startedAt > 2_000_000_000L) {
                reportedError = analyzer.error;
                Log.warn("Audio: " + reportedError);
                DynamicSkiesConfig.chat("§cAudio: " + reportedError);
                DynamicSkiesConfig.guideLink();
            }
            tBass = Math.min(1f, analyzer.bass * sens);
            tMid = Math.min(1f, analyzer.mid * sens);
            tHigh = Math.min(1f, analyzer.high * sens);
            tLevel = Math.min(1f, analyzer.level * sens);
            long b = analyzer.lastBeatNanos;
            if (b != seenBeat) {
                seenBeat = b;
                newBeat = true;
            }
            analyzer.dropSensitivity = cfg.dropSensitivity;
            tBuild = analyzer.buildUp;
            long d = analyzer.lastDropNanos;
            if (d != seenDrop) {
                seenDrop = d;
                newDrop = true;
            }
            float[] src = analyzer.spectrum;
            for (int i = 0; i < BANDS; i++) {
                spectrum[i] = Math.min(1f, src[i] * sens);
            }
        } else if (source == SOURCE_FAKE) {
            double prev = fakePhase;
            fakePhase += dt * cfg.fakeBpm / 60.0;
            if (Math.floor(prev) != Math.floor(fakePhase)) {
                newBeat = true;
            }
            float p = (float) (fakePhase - Math.floor(fakePhase));
            float t = (float) fakePhase;
            // 32-beat phrases: the bass drops out for a 16-beat build-up, then the drop hits.
            double phrase = fakePhase % 32.0;
            boolean building = phrase >= 16.0;
            if (Math.floor(prev / 32.0) != Math.floor(fakePhase / 32.0)) {
                newDrop = true;
            }
            tBuild = building ? (float) ((phrase - 16.0) / 16.0) : 0f;
            tBass = building ? 0f : (float) Math.exp(-p * 5.0);
            tMid = 0.45f + 0.35f * (float) Math.sin(t * 0.5 * Math.PI);
            tHigh = 0.3f + 0.3f * (float) Math.sin(t * Math.PI * 2.0 + 1.0) * (float) Math.exp(-((p * 2f) % 1f) * 4f);
            tLevel = (tBass + tMid + tHigh) / 2.2f;
            for (int i = 0; i < BANDS; i++) {
                float x = i / (float) BANDS;
                float shape = (float) (Math.exp(-x * 3.0) * tBass + 0.4 * tMid * Math.sin(x * 9.0 + t * 1.7) * Math.sin(x * 9.0 + t * 1.7)
                        + 0.25 * tHigh * x);
                spectrum[i] = Math.max(0f, Math.min(1f, shape));
            }
        } else {
            for (int i = 0; i < BANDS; i++) {
                spectrum[i] *= Math.max(0f, 1f - dt * 4f);
            }
        }

        // Fast attack, release controlled by "Smoothing".
        float release = 22f + (2.5f - 22f) * cfg.smoothing;
        bass = approach(bass, tBass, dt, release);
        mid = approach(mid, tMid, dt, release);
        high = approach(high, tHigh, dt, release);
        level = approach(level, tLevel, dt, release);
        beat = newBeat ? 1f : beat * (float) Math.exp(-dt * 7.0);

        boolean detect = cfg.dropDetection && source != SOURCE_OFF;
        if (!detect) {
            tBuild = 0f;
            newDrop = false;
        }
        build += (tBuild - build) * (1f - (float) Math.exp(-dt * (tBuild > build ? 3.0 : 8.0)));
        dropAge = newDrop ? 0f : Math.min(100f, dropAge + dt);
        beatThisFrame = newBeat;
        dropThisFrame = newDrop;
        if (newDrop) {
            Log.info("Drop detected");
        }
    }

    private static float approach(float cur, float target, float dt, float release) {
        float rate = target > cur ? 35f : release;
        return cur + (target - cur) * (1f - (float) Math.exp(-dt * rate));
    }

    public static List<String> describeDevices() {
        List<String> lines = new ArrayList<>();
        if (DynamicSkiesConfig.INSTANCE.audioSource == SOURCE_SYSTEM) {
            lines.add(analyzer != null
                    ? "Listening to: " + analyzer.deviceName + (analyzer.error != null ? " (" + analyzer.error + ")" : "")
                    : "System audio isn't running.");
            lines.add("It follows whatever Windows uses as the default output.");
            return lines;
        }
        List<Mixer.Info> mixers = AudioAnalyzer.captureMixers();
        if (mixers.isEmpty()) {
            lines.add("No recording devices found.");
        } else {
            lines.add("Recording devices (type part of a name into \"Audio Device\"):");
            for (Mixer.Info info : mixers) {
                lines.add(" - " + info.getName());
            }
        }
        if (analyzer != null) {
            lines.add("Currently listening to: " + analyzer.deviceName
                    + (analyzer.error != null ? " (" + analyzer.error + ")" : ""));
        }
        lines.add("Tip: Audio Source \"System Audio\" reacts to whatever Windows is playing, with no setup.");
        return lines;
    }
}
