package com.ootruffle.dynamicskies.audio;

import com.sun.jna.Function;
import com.sun.jna.Memory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import com.sun.jna.platform.win32.Guid.GUID;
import com.sun.jna.platform.win32.Ole32;
import com.sun.jna.ptr.PointerByReference;

/** Java 21 WASAPI loopback through the JNA runtime shipped by Minecraft. */
final class WasapiLoopback implements AutoCloseable {

    interface Sink {
        void accept(float[] mono, int count);
    }

    private static final String CLSID_MMDEVICE_ENUMERATOR = "BCDE0395-E52F-467C-8E3D-C4579291692E";
    private static final String IID_IMMDEVICE_ENUMERATOR = "A95664D2-9614-4F35-A746-DE8DB63617E6";
    private static final String IID_IAUDIO_CLIENT = "1CB9AD4C-DBFA-4C32-B178-C2F568A703B2";
    private static final String IID_IAUDIO_CAPTURE_CLIENT = "C8ADBD64-E71E-48A0-A4DE-185C395CD317";

    private static final int CLSCTX_ALL = 0x17;
    private static final int E_RENDER = 0;
    private static final int E_CONSOLE = 0;
    private static final int SHAREMODE_SHARED = 0;
    private static final int STREAMFLAGS_LOOPBACK = 0x00020000;
    private static final int BUFFERFLAGS_SILENT = 0x2;
    private static final int WAVE_FORMAT_IEEE_FLOAT = 3;
    private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;

    // COM vtable slots (IUnknown takes 0-2 everywhere).
    private static final int RELEASE = 2;
    private static final int ENUM_GET_DEFAULT_ENDPOINT = 4;
    private static final int DEVICE_ACTIVATE = 3;
    private static final int DEVICE_GET_ID = 5;
    private static final int CLIENT_INITIALIZE = 3;
    private static final int CLIENT_GET_MIX_FORMAT = 8;
    private static final int CLIENT_START = 10;
    private static final int CLIENT_STOP = 11;
    private static final int CLIENT_GET_SERVICE = 14;
    private static final int CAPTURE_GET_BUFFER = 3;
    private static final int CAPTURE_RELEASE_BUFFER = 4;
    private static final int CAPTURE_GET_NEXT_PACKET_SIZE = 5;

    private boolean comInitialized;
    private Pointer enumerator, device, client, capture, mixFormat;
    private boolean started;
    private final PointerByReference out = new PointerByReference();
    private final Memory packetSize = new Memory(4);
    private final Memory dataPtr = new Memory(Native.POINTER_SIZE);
    private final Memory frameCount = new Memory(4);
    private final Memory flags = new Memory(4);
    private float[] mono = new float[4096];

    float sampleRate;
    int channels;
    String deviceName = "Default output";
    private String deviceId = "";
    private boolean isFloat;
    private int bits;
    private int blockAlign;

    static boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase(java.util.Locale.ROOT).contains("win");
    }

    void open() {
        int hr = Ole32.INSTANCE.CoInitializeEx(null, 0).intValue();
        check(hr, "initializing COM");
        comInitialized = true;
        check(Ole32.INSTANCE.CoCreateInstance(new GUID(CLSID_MMDEVICE_ENUMERATOR), null, CLSCTX_ALL,
                new GUID(IID_IMMDEVICE_ENUMERATOR), out).intValue(), "creating the device enumerator");
        enumerator = out.getValue();
        check(call(enumerator, ENUM_GET_DEFAULT_ENDPOINT, E_RENDER, E_CONSOLE, out), "finding the default output");
        device = out.getValue();
        deviceId = readId(device);
        check(call(device, DEVICE_ACTIVATE, new GUID(IID_IAUDIO_CLIENT), CLSCTX_ALL, null, out), "opening the audio client");
        client = out.getValue();
        check(call(client, CLIENT_GET_MIX_FORMAT, out), "reading the output format");
        mixFormat = out.getValue();
        int tag = mixFormat.getShort(0) & 0xFFFF;
        channels = mixFormat.getShort(2) & 0xFFFF;
        sampleRate = mixFormat.getInt(4);
        blockAlign = mixFormat.getShort(12) & 0xFFFF;
        bits = mixFormat.getShort(14) & 0xFFFF;
        isFloat = tag == WAVE_FORMAT_IEEE_FLOAT
                || (tag == WAVE_FORMAT_EXTENSIBLE && mixFormat.getInt(24) == WAVE_FORMAT_IEEE_FLOAT);
        if (channels <= 0 || (isFloat ? bits != 32 : bits != 16 && bits != 24 && bits != 32)) {
            throw new IllegalStateException("Unsupported output format (" + bits + "-bit, " + channels + " channels)");
        }

        // 200 ms buffer; loopback must use the device's own mix format.
        check(call(client, CLIENT_INITIALIZE, SHAREMODE_SHARED, STREAMFLAGS_LOOPBACK,
                2_000_000L, 0L, mixFormat, null), "starting loopback capture");
        check(call(client, CLIENT_GET_SERVICE, new GUID(IID_IAUDIO_CAPTURE_CLIENT), out),
                "opening the capture stream");
        capture = out.getValue();
        check(call(client, CLIENT_START), "starting the capture stream");
        started = true;
    }

    /** Hands every waiting packet to the sink as mono samples. Returns how many frames were read. */
    int read(Sink sink) throws Throwable {
        int total = 0;
        while (true) {
            check(call(capture, CAPTURE_GET_NEXT_PACKET_SIZE, packetSize), "reading audio");
            if (packetSize.getInt(0) == 0) {
                return total;
            }
            check(call(capture, CAPTURE_GET_BUFFER, dataPtr, frameCount, flags,
                    null, null), "reading audio");
            int frames = frameCount.getInt(0);
            boolean silent = (flags.getInt(0) & BUFFERFLAGS_SILENT) != 0;
            if (mono.length < frames) {
                mono = new float[frames];
            }
            if (silent) {
                java.util.Arrays.fill(mono, 0, frames, 0f);
            } else {
                Pointer data = dataPtr.getPointer(0);
                int bytes = bits / 8;
                for (int f = 0; f < frames; f++) {
                    long base = (long) f * blockAlign;
                    float sum = 0f;
                    for (int c = 0; c < channels; c++) {
                        sum += sample(data, base + (long) c * bytes);
                    }
                    mono[f] = sum / channels;
                }
            }
            check(call(capture, CAPTURE_RELEASE_BUFFER, frames), "reading audio");
            sink.accept(mono, frames);
            total += frames;
        }
    }

    private float sample(Pointer data, long offset) {
        if (isFloat) {
            return data.getFloat(offset);
        }
        switch (bits) {
            case 16:
                return data.getShort(offset) / 32768f;
            case 24: {
                int v = (data.getByte(offset) & 0xFF)
                        | (data.getByte(offset + 1) & 0xFF) << 8
                        | data.getByte(offset + 2) << 16;
                return v / 8388608f;
            }
            default:
                return data.getInt(offset) / 2147483648f;
        }
    }

    /** True when Windows' default output is no longer the device being captured. */
    boolean defaultDeviceChanged() throws Throwable {
        int hr = call(enumerator, ENUM_GET_DEFAULT_ENDPOINT, E_RENDER, E_CONSOLE, out);
        if (hr < 0) {
            return false; // e.g. no output devices at all right now
        }
        Pointer current = out.getValue();
        try {
            return !readId(current).equals(deviceId);
        } finally {
            release(current);
        }
    }

    private String readId(Pointer dev) {
        check(call(dev, DEVICE_GET_ID, out), "reading the device id");
        Pointer str = out.getValue();
        try {
            return str.getWideString(0);
        } finally {
            Ole32.INSTANCE.CoTaskMemFree(str);
        }
    }

    @Override
    public void close() {
        try {
            if (started) call(client, CLIENT_STOP);
        } finally {
            release(capture);
            release(client);
            release(device);
            release(enumerator);
            if (mixFormat != null) Ole32.INSTANCE.CoTaskMemFree(mixFormat);
            if (comInitialized) Ole32.INSTANCE.CoUninitialize();
            packetSize.close();
            dataPtr.close();
            frameCount.close();
            flags.close();
        }
    }

    private static int call(Pointer object, int slot, Object... args) {
        Pointer fn = object.getPointer(0).getPointer((long) slot * Native.POINTER_SIZE);
        Object[] params = new Object[args.length + 1];
        params[0] = object;
        System.arraycopy(args, 0, params, 1, args.length);
        return Function.getFunction(fn, Function.ALT_CONVENTION).invokeInt(params);
    }

    private static void release(Pointer object) {
        if (object != null) call(object, RELEASE);
    }

    private static void check(int hr, String what) {
        if (hr < 0) {
            String hint = hr == 0x88890004 ? " (the output device was removed or changed)" : "";
            throw new IllegalStateException(String.format("Windows audio error while %s: 0x%08X%s", what, hr, hint));
        }
    }
}
