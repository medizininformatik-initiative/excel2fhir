package de.uni_leipzig.imise.utils;

import java.io.PrintStream;

/**
 * @author AXS (04.07.2015)
 */
public class Sys {

    /** Number of stacktrace lines to be output */
    public static int maxTraceSteps = 100;

    public static boolean insertBlankLineAfterOutput;

    public static void out1(final Object... message) {
        outInternal(1, true, message);
    }

    public static void err1(final Object... message) {
        errInternal(1, true, message);
    }

    public static void out(final Object... message) {
        outInternal(maxTraceSteps, false, message);
    }

    public static void err(final Object... message) {
        errInternal(maxTraceSteps, false, message);
    }

    public static void outn(final int maxTraceSteps, final Object... message) {
        outInternal(maxTraceSteps, false, message);
    }

    public static void outm(final int maxTraceSteps, final int hideTraceSteps, final Object... message) {
        printInternal(maxTraceSteps, System.out, hideTraceSteps + 3, maxTraceSteps == 1, message);
    }

    public static void errn(final int maxTraceSteps, final Object... message) {
        errInternal(maxTraceSteps, false, message);
    }

    private static void outInternal(final int maxTraceSteps, final boolean appendFistTraceStepToLastMessageLine,
            final Object... message) {
        printInternal(maxTraceSteps, System.out, 4, appendFistTraceStepToLastMessageLine, message);
    }

    private static void errInternal(final int maxTraceSteps, final boolean appendFistTraceStepToLastMessageLine,
            final Object... message) {
        printInternal(maxTraceSteps, System.err, 4, appendFistTraceStepToLastMessageLine, message);
    }

    public static void errm(final int maxTraceSteps, final int hideTraceSteps, final Object... message) {
        printInternal(maxTraceSteps, System.err, hideTraceSteps + 3, maxTraceSteps == 1, message);
    }

    private static void printInternal(final int maxTraceSteps, final PrintStream stream, final int hideTraceSteps,
            final boolean appendFistTraceStepToLastMessageLine, final Object... message) {
        if (message == null) {
            print(null, stream, false, !appendFistTraceStepToLastMessageLine);
        } else if (message.length == 0) {
            print("", stream, false, !appendFistTraceStepToLastMessageLine);
        } else if (message.length == 1) { //ich denke das ist der häufigste Fall und der sollte ohne init einer for-Schleife gehen -> daher extra
            print(message[0], stream, false, !appendFistTraceStepToLastMessageLine);
        } else {
            for (int i = 0; i < message.length; i++) {
                print(message[i], stream, false, !appendFistTraceStepToLastMessageLine);
            }
        }
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        for (int i = hideTraceSteps; i < maxTraceSteps + hideTraceSteps && i < stackTrace.length; i++) {
            print(stackTrace[i], stream, !(appendFistTraceStepToLastMessageLine && i == hideTraceSteps), true);
        }
        if (insertBlankLineAfterOutput) {
            stream.println();
        }
    }

    private static final void print(final Object o, final PrintStream stream, final boolean indent, final boolean newLine) {
        String s = indent ? "      " + o : String.valueOf(o); // nicht über toString() gehen, weil es null sein kann
        if (newLine) {
            stream.println(s);
        } else {
            stream.print(s + "      ");
        }
    }

    public static boolean stackTraceContains(final Class<?> clazz) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        String className = clazz.getCanonicalName();
        for (StackTraceElement traceElement : stackTrace) {
            if (traceElement.toString().startsWith(className)) {
                return true;
            }
        }
        return false;
    }

    /**
     * @return the stack trace
     */
    public static final String getStackTrace() {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i < stackTrace.length; i++) {
            sb.append(stackTrace[i]);
            sb.append('\n');
        }
        int length = sb.length();
        if (length > 0 && sb.charAt(length - 1) == '\n') {
            sb.setLength(length - 1);
        }
        return sb.toString();
    }

    /**
     * @return the stack trace
     */
    public static final String getStackTraceStep(int stepIndex) {
        StackTraceElement[] stackTrace = Thread.currentThread().getStackTrace();
        return String.valueOf(stackTrace[stepIndex]);
    }

}
