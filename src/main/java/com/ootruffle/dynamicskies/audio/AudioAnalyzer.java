package com.ootruffle.dynamicskies.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.Line;
import javax.sound.sampled.Mixer;
import javax.sound.sampled.TargetDataLine;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Reads a recording device on its own thread and turns it into bass / mid / high
 * levels, a beat flag and a 64-band spectrum, each roughly 0..1 (auto-gained).
 */
final class AudioAnalyzer implements Runnable {

    static final int BANDS = 64;

    private static final int FFT_SIZE = 2048;
    private static final int HOP = 1024;
    private static final float MIN_PEAK = 0.0035f;
    private static final float SILENCE_RMS = 0.0006f;

    /** Name fragments of devices that capture what's playing, not a microphone. */
    private static final String[] LOOPBACK_HINTS = {
            "stereo mix", "loopback", "what u hear", "wave out", "cable output", "vb-audio", "voicemeeter",
            "monitor of", "blackhole", "soundflower", "stereomix"
    };

    private final String deviceFilter;
    private volatile boolean running = true;
    private Thread thread;

    // Results, read by the render thread.
    volatile float bass, mid, high, level;
    volatile long lastBeatNanos;
    volatile String deviceName = "none";
    volatile String error;
    final float[] spectrum = new float[BANDS];

    // Build-up / drop detection. A build-up is the bass cutting out after a
    // stretch with bass; the drop is the bass coming back hard afterwards.
    volatile float buildUp;
    volatile long lastDropNanos;
    volatile float dropSensitivity = 1f;
    private float bassShort, bassLong, highShort, energyShort;
    private boolean inBreak;
    private boolean breakWasSilent;
    private long breakStartNanos;
    private float preBreakBass, highAtBreak;

    // Analysis state (analyzer thread only).
    private final float[] window = new float[FFT_SIZE];
    private final float[] samples = new float[FFT_SIZE];
    private final float[] re = new float[FFT_SIZE];
    private final float[] im = new float[FFT_SIZE];
    private final float[] bandPeak = new float[BANDS];
    private float bassPeak, midPeak, highPeak;
    private float bassAverage;
    private float sampleRate;
    private final boolean systemAudio;
    private final float[] pending = new float[HOP];
    private int pendingCount;

    AudioAnalyzer(String deviceFilter, boolean systemAudio) {
        this.deviceFilter = deviceFilter == null ? "" : deviceFilter.trim().toLowerCase(Locale.ROOT);
        this.systemAudio = systemAudio;
        for (int i = 0; i < FFT_SIZE; i++) {
            window[i] = (float) (0.5 - 0.5 * Math.cos(2 * Math.PI * i / (FFT_SIZE - 1)));
        }
    }

    void start() {
        thread = new Thread(this, "DynamicSkies-Audio");
        thread.setDaemon(true);
        thread.start();
    }

    void stop() {
        running = false;
        if (thread != null) {
            thread.interrupt();
        }
    }

    @Override
    public void run() {
        try {
            if (systemAudio) {
                runSystemAudio();
            } else {
                runInputDevice();
            }
        } catch (Throwable t) {
            error = "Audio capture stopped: " + t.getMessage();
        } finally {
            bass = mid = high = level = 0f;
        }
    }

    /** Adds one mono sample; every HOP samples the newest window gets analyzed. */
    private void feed(float sample) {
        pending[pendingCount++] = sample;
        if (pendingCount == HOP) {
            System.arraycopy(samples, HOP, samples, 0, FFT_SIZE - HOP);
            System.arraycopy(pending, 0, samples, FFT_SIZE - HOP, HOP);
            pendingCount = 0;
            analyze();
        }
    }

    private void runInputDevice() throws Exception {
        TargetDataLine line = open();
        if (line == null) {
            error = deviceFilter.isEmpty()
                    ? "No recording device found."
                    : "No recording device matching \"" + deviceFilter + "\".";
            return;
        }
        try {
            AudioFormat fmt = line.getFormat();
            sampleRate = fmt.getSampleRate();
            int channels = fmt.getChannels();
            int frameBytes = fmt.getFrameSize();
            boolean bigEndian = fmt.isBigEndian();
            byte[] buf = new byte[HOP * frameBytes];
            line.start();

            while (running) {
                int n = line.read(buf, 0, buf.length);
                if (n <= 0) continue;
                int frames = n / frameBytes;
                for (int f = 0; f < frames; f++) {
                    float v = 0f;
                    for (int c = 0; c < channels; c++) {
                        int o = f * frameBytes + c * 2;
                        int s = bigEndian
                                ? (buf[o] << 8) | (buf[o + 1] & 0xFF)
                                : (buf[o + 1] << 8) | (buf[o] & 0xFF);
                        v += s / 32768f;
                    }
                    feed(v / channels);
                }
            }
        } finally {
            try {
                line.stop();
                line.close();
            } catch (Throwable ignored) {
            }
        }
    }

    /**
     * Captures whatever Windows is playing. Follows the default output if it
     * changes, and retries if the device disappears (e.g. headphones unplugged).
     */
    private void runSystemAudio() throws InterruptedException {
        if (!System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win")) {
            error = "System Audio only works on Windows. Use Input Device instead.";
            return;
        }
        while (running) {
            WasapiLoopback capture = new WasapiLoopback();
            try {
                capture.open();
                sampleRate = capture.sampleRate;
                deviceName = "System audio (" + capture.deviceName + ")";
                error = null;

                long lastData = System.nanoTime();
                long lastDeviceCheck = lastData;
                while (running) {
                    int got = capture.read(this::feedBlock);
                    long now = System.nanoTime();
                    if (got > 0) {
                        lastData = now;
                    } else if (now - lastData > 50_000_000L) {
                        // Nothing playing: Windows sends no packets at all, so feed silence to let the sky settle.
                        int frames = (int) Math.min(sampleRate, (now - lastData) / 1e9 * sampleRate);
                        for (int i = 0; i < frames; i++) feed(0f);
                        lastData = now;
                    }
                    if (now - lastDeviceCheck > 2_000_000_000L) {
                        lastDeviceCheck = now;
                        if (capture.defaultDeviceChanged()) {
                            break; // reopen on the new default output
                        }
                    }
                    Thread.sleep(10);
                }
            } catch (InterruptedException e) {
                throw e;
            } catch (Throwable t) {
                error = t.getMessage();
                Thread.sleep(2000); // e.g. no output device right now; try again shortly
            } finally {
                capture.close();
            }
        }
    }

    private void feedBlock(float[] mono, int count) {
        for (int i = 0; i < count; i++) {
            feed(mono[i]);
        }
    }

    private TargetDataLine open() throws Exception {
        AudioFormat[] formats = {
                new AudioFormat(44100f, 16, 2, true, false),
                new AudioFormat(48000f, 16, 2, true, false),
                new AudioFormat(44100f, 16, 1, true, false),
                new AudioFormat(48000f, 16, 1, true, false)
        };
        Mixer.Info chosen = pickMixer(formats);
        for (AudioFormat fmt : formats) {
            DataLine.Info info = new DataLine.Info(TargetDataLine.class, fmt);
            try {
                TargetDataLine line;
                if (chosen != null) {
                    Mixer mixer = AudioSystem.getMixer(chosen);
                    if (!mixer.isLineSupported(info)) continue;
                    line = (TargetDataLine) mixer.getLine(info);
                    deviceName = chosen.getName();
                } else if (deviceFilter.isEmpty()) {
                    if (!AudioSystem.isLineSupported(info)) continue;
                    line = (TargetDataLine) AudioSystem.getLine(info);
                    deviceName = "Default recording device";
                } else {
                    return null;
                }
                line.open(fmt, HOP * fmt.getFrameSize() * 4);
                return line;
            } catch (Exception ignored) {
                // Try the next format.
            }
        }
        return null;
    }

    /** The user's device if they named one, otherwise the first loopback-looking device. */
    private Mixer.Info pickMixer(AudioFormat[] formats) {
        List<Mixer.Info> capture = captureMixers(formats);
        if (!deviceFilter.isEmpty()) {
            for (Mixer.Info info : capture) {
                if (info.getName().toLowerCase(Locale.ROOT).contains(deviceFilter)) {
                    return info;
                }
            }
            return null;
        }
        for (String hint : LOOPBACK_HINTS) {
            for (Mixer.Info info : capture) {
                if (info.getName().toLowerCase(Locale.ROOT).contains(hint)) {
                    return info;
                }
            }
        }
        return null;
    }

    static List<Mixer.Info> captureMixers(AudioFormat[] formats) {
        List<Mixer.Info> out = new ArrayList<>();
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            for (AudioFormat fmt : formats) {
                Line.Info li = new DataLine.Info(TargetDataLine.class, fmt);
                if (mixer.isLineSupported(li)) {
                    out.add(info);
                    break;
                }
            }
        }
        return out;
    }

    static List<Mixer.Info> captureMixers() {
        return captureMixers(new AudioFormat[]{
                new AudioFormat(44100f, 16, 2, true, false),
                new AudioFormat(44100f, 16, 1, true, false),
                new AudioFormat(48000f, 16, 2, true, false)
        });
    }

    private void analyze() {
        double sumSq = 0;
        for (int i = 0; i < FFT_SIZE; i++) {
            float s = samples[i];
            sumSq += s * s;
            re[i] = s * window[i];
            im[i] = 0f;
        }
        float rms = (float) Math.sqrt(sumSq / FFT_SIZE);
        fft(re, im);

        float binHz = sampleRate / FFT_SIZE;
        float norm = 4f / FFT_SIZE;

        boolean silent = rms < SILENCE_RMS;
        float b = silent ? 0f : bandEnergy(20f, 150f, binHz, norm);
        float m = silent ? 0f : bandEnergy(150f, 2000f, binHz, norm);
        float h = silent ? 0f : bandEnergy(2000f, 10000f, binHz, norm);

        bassPeak = Math.max(Math.max(bassPeak * 0.997f, b), MIN_PEAK);
        midPeak = Math.max(Math.max(midPeak * 0.997f, m), MIN_PEAK * 0.6f);
        highPeak = Math.max(Math.max(highPeak * 0.997f, h), MIN_PEAK * 0.3f);

        bass = shape(b / bassPeak);
        mid = shape(m / midPeak);
        high = shape(h / highPeak);
        level = silent ? 0f : Math.min(1f, (bass + mid + high) / 2.2f);

        // Beat: bass jumps clearly above its recent average.
        if (b > bassAverage * 1.45f && b > MIN_PEAK && System.nanoTime() - lastBeatNanos > 220_000_000L) {
            lastBeatNanos = System.nanoTime();
        }
        bassAverage = bassAverage * 0.93f + b * 0.07f;

        detectBuildUpAndDrop(b, m, h, silent);

        // 64 log-spaced bands, 30 Hz .. 14 kHz.
        double lo = Math.log(30), hi = Math.log(14000);
        for (int band = 0; band < BANDS; band++) {
            float f0 = (float) Math.exp(lo + (hi - lo) * band / BANDS);
            float f1 = (float) Math.exp(lo + (hi - lo) * (band + 1) / BANDS);
            float e = silent ? 0f : bandEnergy(f0, f1, binHz, norm);
            bandPeak[band] = Math.max(Math.max(bandPeak[band] * 0.996f, e), MIN_PEAK * 0.5f);
            float target = shape(e / bandPeak[band]);
            float cur = spectrum[band];
            spectrum[band] = target > cur ? target : cur * 0.82f + target * 0.18f;
        }
    }

    private void detectBuildUpAndDrop(float b, float m, float h, boolean silent) {
        float hop = HOP / sampleRate;
        bassShort += (b - bassShort) * ema(hop, 0.15f);
        bassLong += (b - bassLong) * ema(hop, 6f);
        highShort += (h - highShort) * ema(hop, 0.4f);
        energyShort += ((b + m + h) - energyShort) * ema(hop, 0.3f);

        float s = Math.max(0.5f, Math.min(2f, dropSensitivity));
        long now = System.nanoTime();

        if (!inBreak) {
            // Bass falls well below what it has been: a breakdown / build-up starts.
            if (bassLong > MIN_PEAK * 1.5f && bassShort < bassLong * Math.min(0.6f, 0.3f * s)) {
                inBreak = true;
                breakWasSilent = false;
                breakStartNanos = now;
                preBreakBass = bassLong;
                highAtBreak = Math.max(highShort, 1e-5f);
            } else {
                buildUp = Math.max(0f, buildUp - hop * 2f);
            }
            return;
        }

        float secs = (now - breakStartNanos) / 1e9f;
        if (silent || energyShort < MIN_PEAK * 0.5f) {
            // A gap between songs isn't a build-up.
            breakWasSilent = true;
        }

        // Builds over time, faster when the highs climb (risers, snare rolls).
        float riser = Math.max(0f, Math.min(1f, (highShort / highAtBreak - 1f) * 0.6f));
        float target = breakWasSilent ? 0f : Math.min(1f, secs / 10f) * 0.65f + riser * 0.35f;
        buildUp += (target - buildUp) * ema(hop, 0.5f);

        if (bassShort > preBreakBass * (0.75f / s) && secs > 1.2f) {
            if (!breakWasSilent) {
                lastDropNanos = now;
            }
            inBreak = false;
            buildUp = 0f;
            bassLong = Math.max(bassLong, preBreakBass);
        } else if (secs > 40f) {
            inBreak = false;
        }
    }

    private static float ema(float dt, float tau) {
        return 1f - (float) Math.exp(-dt / tau);
    }

    private static float shape(float x) {
        x = Math.max(0f, Math.min(1f, x));
        return x * x * (3f - 2f * x);
    }

    private float bandEnergy(float fromHz, float toHz, float binHz, float norm) {
        int from = Math.max(1, (int) (fromHz / binHz));
        int to = Math.min(FFT_SIZE / 2 - 1, Math.max(from, (int) Math.ceil(toHz / binHz)));
        float sum = 0f;
        for (int i = from; i <= to; i++) {
            sum += (float) Math.sqrt(re[i] * re[i] + im[i] * im[i]) * norm;
        }
        return sum / (to - from + 1);
    }

    /** In-place iterative radix-2 FFT. */
    private static void fft(float[] re, float[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                float t = re[i]; re[i] = re[j]; re[j] = t;
                t = im[i]; im[i] = im[j]; im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            float wRe = (float) Math.cos(ang), wIm = (float) Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                float curRe = 1f, curIm = 0f;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k, b = a + len / 2;
                    float tRe = re[b] * curRe - im[b] * curIm;
                    float tIm = re[b] * curIm + im[b] * curRe;
                    re[b] = re[a] - tRe;
                    im[b] = im[a] - tIm;
                    re[a] += tRe;
                    im[a] += tIm;
                    float nRe = curRe * wRe - curIm * wIm;
                    curIm = curRe * wIm + curIm * wRe;
                    curRe = nRe;
                }
            }
        }
    }
}
