package com.ootruffle.dynamicskies.sky;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Keeps the modified times of the custom sky files up to date on a background thread, so
 * the render thread never waits on the disk (slow or network drives made the game stutter).
 * Files nobody has asked about for a while are dropped.
 */
final class FileWatcher {

    private static final long POLL_MILLIS = 500L;
    private static final long FORGET_NANOS = 10_000_000_000L;

    private static final class Entry {
        volatile long modified;
        volatile long askedNanos;
    }

    private static final Map<Path, Entry> FILES = new ConcurrentHashMap<>();
    private static Thread thread;

    private FileWatcher() {
    }

    /**
     * The file's last modified time in milliseconds, or -1 if it isn't a regular file.
     * The first call for a file checks it right away; after that it's at most half a second old.
     */
    static long modified(Path file) {
        Entry entry = FILES.get(file);
        if (entry == null) return refresh(file);
        entry.askedNanos = System.nanoTime();
        return entry.modified;
    }

    /** Checks the file right now (first use, or a Reload button). */
    static long refresh(Path file) {
        Entry entry = FILES.computeIfAbsent(file, f -> new Entry());
        entry.askedNanos = System.nanoTime();
        entry.modified = check(file);
        start();
        return entry.modified;
    }

    private static long check(Path file) {
        try {
            return Files.isRegularFile(file) ? Files.getLastModifiedTime(file).toMillis() : -1L;
        } catch (IOException | SecurityException e) {
            return -1L;
        }
    }

    private static synchronized void start() {
        if (thread != null) return;
        thread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(POLL_MILLIS);
                } catch (InterruptedException e) {
                    return;
                }
                long now = System.nanoTime();
                FILES.entrySet().removeIf(e -> now - e.getValue().askedNanos > FORGET_NANOS);
                FILES.forEach((file, entry) -> entry.modified = check(file));
            }
        }, "DynamicSkies-FileWatcher");
        thread.setDaemon(true);
        thread.start();
    }
}
