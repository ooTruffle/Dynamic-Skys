package com.ootruffle.dynamicskies;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Writes to the game's log (logs/latest.log). Minecraft 1.8.9's log format
 * doesn't show logger names, so every line gets a "[Dynamic Skies]" prefix.
 */
public final class Log {

    private static final Logger LOGGER = LogManager.getLogger("Dynamic Skies");
    private static final String PREFIX = "[Dynamic Skies] ";

    private Log() {
    }

    public static void info(String message) {
        LOGGER.info(PREFIX + message);
    }

    public static void warn(String message) {
        LOGGER.warn(PREFIX + message);
    }

    public static void error(String message) {
        LOGGER.error(PREFIX + message);
    }

    public static void error(String message, Throwable t) {
        LOGGER.error(PREFIX + message, t);
    }
}
