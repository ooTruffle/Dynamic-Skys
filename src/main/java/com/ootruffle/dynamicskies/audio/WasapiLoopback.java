package com.ootruffle.dynamicskies.audio;

import java.lang.foreign.Arena;
import java.lang.foreign.FunctionDescriptor;
import java.lang.foreign.Linker;
import java.lang.foreign.MemorySegment;
import java.lang.foreign.SymbolLookup;
import java.lang.invoke.MethodHandle;
import java.nio.charset.StandardCharsets;

import static java.lang.foreign.ValueLayout.ADDRESS;
import static java.lang.foreign.ValueLayout.JAVA_BYTE;
import static java.lang.foreign.ValueLayout.JAVA_FLOAT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_INT;
import static java.lang.foreign.ValueLayout.JAVA_INT_UNALIGNED;
import static java.lang.foreign.ValueLayout.JAVA_LONG;
import static java.lang.foreign.ValueLayout.JAVA_SHORT_UNALIGNED;

/**
 * Captures whatever Windows is playing on the default output device (WASAPI
 * loopback), called straight from Java through the Foreign Function API, so no
 * extra app, driver or DLL is needed.
 *
 * Everything here must run on one thread: the one that called {@link #open()}.
 */
final class WasapiLoopback implements AutoCloseable {

    interface Sink {
        void accept(float[] mono, int count);
    }

    private static final String CLSID_MMDEVICE_ENUMERATOR = "BCDE0395-E52F-467C-8E3D-C4579291692E";
    private static final String IID_IMMDEVICE_ENUMERATOR = "A95664D2-9614-4F35-A746-DE8DB63617E6";
    private static final String IID_IAUDIO_CLIENT = "1CB9AD4C-DBFA-4C32-B178-C2F568A703B2";
    private static final String IID_IAUDIO_CAPTURE_CLIENT = "C8ADBD64-E71E-48A0-A4DE-185C395CD317";
    private static final String PKEY_DEVICE_FRIENDLY_NAME = "A45C254E-DF1C-4EFD-8020-67D146A850E0";

    private static final int CLSCTX_ALL = 0x17;
    private static final int E_RENDER = 0;
    private static final int E_CONSOLE = 0;
    private static final int SHAREMODE_SHARED = 0;
    private static final int STREAMFLAGS_LOOPBACK = 0x00020000;
    private static final int BUFFERFLAGS_SILENT = 0x2;
    private static final int WAVE_FORMAT_IEEE_FLOAT = 3;
    private static final int WAVE_FORMAT_EXTENSIBLE = 0xFFFE;
    private static final short VT_LPWSTR = 31;

    // COM vtable slots (IUnknown takes 0-2 everywhere).
    private static final int RELEASE = 2;
    private static final int ENUM_GET_DEFAULT_ENDPOINT = 4;
    private static final int DEVICE_ACTIVATE = 3;
    private static final int DEVICE_OPEN_PROPERTY_STORE = 4;
    private static final int DEVICE_GET_ID = 5;
    private static final int PROPSTORE_GET_VALUE = 5;
    private static final int CLIENT_INITIALIZE = 3;
    private static final int CLIENT_GET_MIX_FORMAT = 8;
    private static final int CLIENT_START = 10;
    private static final int CLIENT_STOP = 11;
    private static final int CLIENT_GET_SERVICE = 14;
    private static final int CAPTURE_GET_BUFFER = 3;
    private static final int CAPTURE_RELEASE_BUFFER = 4;
    private static final int CAPTURE_GET_NEXT_PACKET_SIZE = 5;

    private static final Linker LINKER = Linker.nativeLinker();

    // Call shapes. Each takes the function pointer first, then `this`, then the arguments; all return an HRESULT.
    private static final MethodHandle CALL_0 = LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS));
    private static final MethodHandle CALL_P = LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle CALL_I = LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
    private static final MethodHandle CALL_PP = LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS));
    private static final MethodHandle CALL_IP = LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, ADDRESS));
    private static final MethodHandle CALL_IIP = LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, ADDRESS));
    private static final MethodHandle CALL_PIPP = LINKER.downcallHandle(FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
    private static final MethodHandle CALL_INITIALIZE = LINKER.downcallHandle(
            FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT, JAVA_INT, JAVA_LONG, JAVA_LONG, ADDRESS, ADDRESS));
    private static final MethodHandle CALL_PPPPP = LINKER.downcallHandle(
            FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS, ADDRESS));

    private static MethodHandle coInitializeEx, coCreateInstance, coTaskMemFree, coUninitialize, propVariantClear;

    private final Arena arena = Arena.ofConfined();
    private boolean comInitialized;
    private MemorySegment enumerator = MemorySegment.NULL;
    private MemorySegment device = MemorySegment.NULL;
    private MemorySegment client = MemorySegment.NULL;
    private MemorySegment capture = MemorySegment.NULL;
    private MemorySegment mixFormat = MemorySegment.NULL;
    private boolean started;

    private MemorySegment out, packetSize, dataPtr, frameCount, flags;
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

    private static synchronized void loadOle32() {
        if (coInitializeEx != null) return;
        SymbolLookup ole32 = SymbolLookup.libraryLookup("ole32", Arena.global());
        coInitializeEx = LINKER.downcallHandle(ole32.find("CoInitializeEx").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, JAVA_INT));
        coCreateInstance = LINKER.downcallHandle(ole32.find("CoCreateInstance").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS, ADDRESS, JAVA_INT, ADDRESS, ADDRESS));
        coTaskMemFree = LINKER.downcallHandle(ole32.find("CoTaskMemFree").orElseThrow(),
                FunctionDescriptor.ofVoid(ADDRESS));
        coUninitialize = LINKER.downcallHandle(ole32.find("CoUninitialize").orElseThrow(),
                FunctionDescriptor.ofVoid());
        propVariantClear = LINKER.downcallHandle(ole32.find("PropVariantClear").orElseThrow(),
                FunctionDescriptor.of(JAVA_INT, ADDRESS));
    }

    /** Starts capturing the current default output device. Throws with a readable message on failure. */
    void open() throws Throwable {
        loadOle32();
        out = arena.allocate(ADDRESS);
        packetSize = arena.allocate(JAVA_INT);
        dataPtr = arena.allocate(ADDRESS);
        frameCount = arena.allocate(JAVA_INT);
        flags = arena.allocate(JAVA_INT);

        // 0 = COINIT_MULTITHREADED. S_FALSE (already initialized) is fine too.
        int hr = (int) coInitializeEx.invoke(MemorySegment.NULL, 0);
        comInitialized = hr >= 0;

        check((int) coCreateInstance.invoke(guid(CLSID_MMDEVICE_ENUMERATOR), MemorySegment.NULL, CLSCTX_ALL,
                guid(IID_IMMDEVICE_ENUMERATOR), out), "creating the device enumerator");
        enumerator = out.get(ADDRESS, 0);

        check((int) CALL_IIP.invoke(slot(enumerator, ENUM_GET_DEFAULT_ENDPOINT), enumerator, E_RENDER, E_CONSOLE, out),
                "finding the default output device");
        device = out.get(ADDRESS, 0);
        deviceId = readId(device);
        try {
            deviceName = readFriendlyName(device);
        } catch (Throwable ignored) {
            // Name is only for display.
        }

        check((int) CALL_PIPP.invoke(slot(device, DEVICE_ACTIVATE), device, guid(IID_IAUDIO_CLIENT), CLSCTX_ALL,
                MemorySegment.NULL, out), "opening the audio client");
        client = out.get(ADDRESS, 0);

        check((int) CALL_P.invoke(slot(client, CLIENT_GET_MIX_FORMAT), client, out), "reading the output format");
        mixFormat = out.get(ADDRESS, 0).reinterpret(40);
        int tag = mixFormat.get(JAVA_SHORT_UNALIGNED, 0) & 0xFFFF;
        channels = mixFormat.get(JAVA_SHORT_UNALIGNED, 2) & 0xFFFF;
        sampleRate = mixFormat.get(JAVA_INT_UNALIGNED, 4);
        blockAlign = mixFormat.get(JAVA_SHORT_UNALIGNED, 12) & 0xFFFF;
        bits = mixFormat.get(JAVA_SHORT_UNALIGNED, 14) & 0xFFFF;
        isFloat = tag == WAVE_FORMAT_IEEE_FLOAT
                || (tag == WAVE_FORMAT_EXTENSIBLE && mixFormat.get(JAVA_INT_UNALIGNED, 24) == WAVE_FORMAT_IEEE_FLOAT);
        if (channels <= 0 || (isFloat ? bits != 32 : bits != 16 && bits != 24 && bits != 32)) {
            throw new IllegalStateException("Unsupported output format (" + bits + "-bit, " + channels + " channels)");
        }

        // 200 ms buffer; loopback must use the device's own mix format.
        check((int) CALL_INITIALIZE.invoke(slot(client, CLIENT_INITIALIZE), client, SHAREMODE_SHARED, STREAMFLAGS_LOOPBACK,
                2_000_000L, 0L, mixFormat, MemorySegment.NULL), "starting loopback capture");
        check((int) CALL_PP.invoke(slot(client, CLIENT_GET_SERVICE), client, guid(IID_IAUDIO_CAPTURE_CLIENT), out),
                "opening the capture stream");
        capture = out.get(ADDRESS, 0);
        check((int) CALL_0.invoke(slot(client, CLIENT_START), client), "starting the capture stream");
        started = true;
    }

    /** Hands every waiting packet to the sink as mono samples. Returns how many frames were read. */
    int read(Sink sink) throws Throwable {
        int total = 0;
        while (true) {
            check((int) CALL_P.invoke(slot(capture, CAPTURE_GET_NEXT_PACKET_SIZE), capture, packetSize), "reading audio");
            if (packetSize.get(JAVA_INT, 0) == 0) {
                return total;
            }
            check((int) CALL_PPPPP.invoke(slot(capture, CAPTURE_GET_BUFFER), capture, dataPtr, frameCount, flags,
                    MemorySegment.NULL, MemorySegment.NULL), "reading audio");
            int frames = frameCount.get(JAVA_INT, 0);
            boolean silent = (flags.get(JAVA_INT, 0) & BUFFERFLAGS_SILENT) != 0;
            if (mono.length < frames) {
                mono = new float[frames];
            }
            if (silent) {
                java.util.Arrays.fill(mono, 0, frames, 0f);
            } else {
                MemorySegment data = dataPtr.get(ADDRESS, 0).reinterpret((long) frames * blockAlign);
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
            check((int) CALL_I.invoke(slot(capture, CAPTURE_RELEASE_BUFFER), capture, frames), "reading audio");
            sink.accept(mono, frames);
            total += frames;
        }
    }

    private float sample(MemorySegment data, long offset) {
        if (isFloat) {
            return data.get(JAVA_FLOAT_UNALIGNED, offset);
        }
        switch (bits) {
            case 16:
                return data.get(JAVA_SHORT_UNALIGNED, offset) / 32768f;
            case 24: {
                int v = (data.get(JAVA_BYTE, offset) & 0xFF)
                        | (data.get(JAVA_BYTE, offset + 1) & 0xFF) << 8
                        | data.get(JAVA_BYTE, offset + 2) << 16;
                return v / 8388608f;
            }
            default:
                return data.get(JAVA_INT_UNALIGNED, offset) / 2147483648f;
        }
    }

    /** True when Windows' default output is no longer the device being captured. */
    boolean defaultDeviceChanged() throws Throwable {
        int hr = (int) CALL_IIP.invoke(slot(enumerator, ENUM_GET_DEFAULT_ENDPOINT), enumerator, E_RENDER, E_CONSOLE, out);
        if (hr < 0) {
            return false; // e.g. no output devices at all right now
        }
        MemorySegment current = out.get(ADDRESS, 0);
        try {
            return !readId(current).equals(deviceId);
        } finally {
            release(current);
        }
    }

    private String readId(MemorySegment dev) throws Throwable {
        check((int) CALL_P.invoke(slot(dev, DEVICE_GET_ID), dev, out), "reading the device id");
        MemorySegment str = out.get(ADDRESS, 0);
        try {
            return wide(str);
        } finally {
            coTaskMemFree.invoke(str);
        }
    }

    private String readFriendlyName(MemorySegment dev) throws Throwable {
        check((int) CALL_IP.invoke(slot(dev, DEVICE_OPEN_PROPERTY_STORE), dev, 0 /* STGM_READ */, out), "reading the device name");
        MemorySegment store = out.get(ADDRESS, 0);
        try {
            MemorySegment key = arena.allocate(20);
            MemorySegment.copy(guid(PKEY_DEVICE_FRIENDLY_NAME), 0, key, 0, 16);
            key.set(JAVA_INT_UNALIGNED, 16, 14);
            MemorySegment value = arena.allocate(24, 8);
            check((int) CALL_PP.invoke(slot(store, PROPSTORE_GET_VALUE), store, key, value), "reading the device name");
            try {
                if (value.get(JAVA_SHORT_UNALIGNED, 0) == VT_LPWSTR) {
                    return wide(value.get(ADDRESS, 8));
                }
                return "Default output";
            } finally {
                int ignored = (int) propVariantClear.invoke(value);
            }
        } finally {
            release(store);
        }
    }

    /** Reads a null-terminated UTF-16 string. */
    private static String wide(MemorySegment ptr) {
        if (ptr.equals(MemorySegment.NULL)) return "";
        MemorySegment s = ptr.reinterpret(Long.MAX_VALUE);
        int len = 0;
        while (len < 2048 && s.get(JAVA_SHORT_UNALIGNED, len * 2L) != 0) {
            len++;
        }
        byte[] raw = new byte[len * 2];
        MemorySegment.copy(s, JAVA_BYTE, 0, raw, 0, raw.length);
        return new String(raw, StandardCharsets.UTF_16LE);
    }

    @Override
    public void close() {
        try {
            if (started) {
                int ignored = (int) CALL_0.invoke(slot(client, CLIENT_STOP), client);
            }
        } catch (Throwable ignored) {
        }
        release(capture);
        release(client);
        release(device);
        release(enumerator);
        capture = client = device = enumerator = MemorySegment.NULL;
        try {
            if (!mixFormat.equals(MemorySegment.NULL)) coTaskMemFree.invoke(mixFormat);
            if (comInitialized) coUninitialize.invoke();
        } catch (Throwable ignored) {
        }
        mixFormat = MemorySegment.NULL;
        comInitialized = false;
        arena.close();
    }

    private static void release(MemorySegment obj) {
        if (obj == null || obj.equals(MemorySegment.NULL)) return;
        try {
            int ignored = (int) CALL_0.invoke(slot(obj, RELEASE), obj);
        } catch (Throwable ignored) {
        }
    }

    /** The function pointer at the given slot of a COM object's vtable. */
    private static MemorySegment slot(MemorySegment obj, int index) {
        MemorySegment vtable = obj.reinterpret(ADDRESS.byteSize()).get(ADDRESS, 0);
        return vtable.reinterpret(ADDRESS.byteSize() * (index + 1)).getAtIndex(ADDRESS, index);
    }

    private MemorySegment guid(String text) {
        String h = text.replace("-", "");
        MemorySegment g = arena.allocate(16, 4);
        g.set(JAVA_INT_UNALIGNED, 0, (int) Long.parseLong(h.substring(0, 8), 16));
        g.set(JAVA_SHORT_UNALIGNED, 4, (short) Integer.parseInt(h.substring(8, 12), 16));
        g.set(JAVA_SHORT_UNALIGNED, 6, (short) Integer.parseInt(h.substring(12, 16), 16));
        for (int i = 0; i < 8; i++) {
            g.set(JAVA_BYTE, 8 + i, (byte) Integer.parseInt(h.substring(16 + i * 2, 18 + i * 2), 16));
        }
        return g;
    }

    private static void check(int hr, String what) {
        if (hr < 0) {
            String hint = hr == 0x88890004 ? " (the output device was removed or changed)" : "";
            throw new IllegalStateException(String.format("Windows audio error while %s: 0x%08X%s", what, hr, hint));
        }
    }
}
