package de.uni_leipzig.imise.utils;

import org.slf4j.Logger;

import com.google.common.base.Stopwatch;

/**
 * @author AXS (19.11.2021)
 */
public class LogUtils {

    /**
     * Logs the default message "Start..." as info and returns a startet
     * {@link Stopwatch}.
     *
     * @param logger
     * @param message
     * @return
     */
    public static Stopwatch infoStarted(Logger logger) {
        return infoStarted(logger, "Start...");
    }

    /**
     * Logs the info message and return a startet {@link Stopwatch}
     *
     * @param logger
     * @param message
     * @return
     */
    public static Stopwatch infoStarted(Logger logger, String message) {
        logger.info(message);
        return Stopwatch.createStarted();
    }

    /**
     * Stop the stopwatch and log the default message "Finished in " with the
     * appended stopwatch time string as info.
     *
     * @param logger
     * @return
     */
    public static void infoFinished(Logger logger, Stopwatch stopwatch) {
        infoFinished(logger, "Finished in ", stopwatch, "");
    }

    /**
     * Stop the stopwatch and log the message with the appended stopwatch time
     * string as info.
     *
     * @param logger
     * @param message
     * @return
     */
    public static void infoFinished(Logger logger, String message, Stopwatch stopwatch) {
        infoFinished(logger, message, stopwatch, "");
    }

    /**
     * Stop the stopwatch and log the message as info. The full message consists
     * of <code>messagePrefix + stopwatch time string + messagePostfix</code>
     *
     * @param logger
     * @param messagePrefix
     * @param stopwatch
     * @param messagePostfix
     * @return
     */
    public static void infoFinished(Logger logger, String messagePrefix, Stopwatch stopwatch, String messagePostfix) {
        stopwatch.stop();
        logger.info(messagePrefix + stopwatch + messagePostfix);
    }

}
