package de.uni_leipzig.imise.utils;

import java.io.File;

import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.core.Appender;
import org.apache.logging.log4j.core.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.apache.logging.log4j.core.appender.FileAppender;
import org.apache.logging.log4j.core.config.AppenderRef;
import org.apache.logging.log4j.core.config.Configuration;
import org.apache.logging.log4j.core.config.LoggerConfig;
import org.apache.logging.log4j.core.layout.PatternLayout;

/**
 * @author AXS (19.11.2021)
 */
public class FileLogger {

    /**
     * @author AXS (19.11.2021)
     */
    public static enum LogContentLayout {
        //https://howtodoinjava.com/log4j2/useful-conversion-pattern-examples/

        /**
         * @see PatternLayout#DEFAULT_CONVERSION_PATTERN
         */
        MESSANGE_ONLY {
            @Override
            public String getPattern() {
                return PatternLayout.DEFAULT_CONVERSION_PATTERN;
            }
        },
        /**
         * Pattern:<br>
         * <code>%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n</code><br>
         * Example:<br>
         * <code>11:06:35.437 [main] INFO de.uni_leipzig.imise.Excel2Fhir -
         * Hello world!</code>
         */
        DATE_LEVEL_SOURCE {
            @Override
            public String getPattern() {
                return "%d{HH:mm:ss.SSS} [%t] %-5level %logger{36} - %msg%n";
            }
        },
        /**
         * Pattern:<br>
         * <code>%sn %d{yyyy/MM/dd HH:mm:ss,SSS} %r [%-6p] [%t] %C{3}.%M(%F:%L) – %m%n</code><br>
         * Example:<br>
         * <code>1 2021/11/19 11:06:35,437 431 [INFO  ] [main] uni_leipzig.imise.Excel2Fhir.main(Excel2Fhir.java:62) -
         * Hello world!</code>
         */
        DATE_LEVEL_SOURCE_LINENUMBER {
            @Override
            public String getPattern() {
                return "%sn %d{yyyy/MM/dd HH:mm:ss,SSS} %r [%-6p] [%t] %C{36}.%M(%F:%L) – %m%n";
            }
        },
        ;

        /**
         * @return the pattern
         */
        public abstract String getPattern();
    }

    /**
     * @param relativeOrAbsoluteFile
     * @param logContentLayout
     */
    public static void addRootFileLogger(File relativeOrAbsoluteFile, LogContentLayout logContentLayout) {
        addRootFileLogger(relativeOrAbsoluteFile, logContentLayout.getPattern());
    }

    /**
     * @param relativeOrAbsoluteFile
     * @param pattern
     */
    public static void addRootFileLogger(File relativeOrAbsoluteFile, String pattern) {
        String absolutePath = relativeOrAbsoluteFile.getAbsolutePath();
        addRootFileLogger(absolutePath, pattern);
    }

    /**
     * @param relativeOrAbsoluteFile
     * @param level
     * @param logContentLayout
     */
    public static void addFileLogger(File relativeOrAbsoluteFile, Level level, LogContentLayout logContentLayout) {
        addFileLogger(relativeOrAbsoluteFile, level, logContentLayout.getPattern());
    }

    /**
     * @param relativeOrAbsoluteFile
     * @param level
     * @param pattern
     */
    public static void addFileLogger(File relativeOrAbsoluteFile, Level level, String pattern) {
        String absolutePath = relativeOrAbsoluteFile.getAbsolutePath();
        addFileLogger(absolutePath, level, pattern);
    }

    /**
     * @param relativeOrAbsolutePathToFile
     * @param pattern
     */
    public static void addRootFileLogger(String relativeOrAbsolutePathToFile, String pattern) {
        //ignore 'potencial resource leak' compiler warning and don't surround the LoggerContext with try() !
        LoggerContext logContext = (LoggerContext) LogManager.getContext(false);
        Configuration logContextConfig = logContext.getConfiguration();
        Appender fileAppender = getFileAppender(relativeOrAbsolutePathToFile, logContextConfig, pattern);
        Logger rootLogger = logContext.getRootLogger();
        rootLogger.addAppender(fileAppender);
        logContext.updateLoggers();
    }

    /**
     * @param relativeOrAbsolutePathToFile
     * @param level
     * @param pattern
     */
    public static void addFileLogger(String relativeOrAbsolutePathToFile, Level level, String pattern) {
        //ignore 'potencial resource leak' compiler warning and don't surround the LoggerContext with try() !
        LoggerContext logContext = (LoggerContext) LogManager.getContext(false);
        Configuration logContextConfig = logContext.getConfiguration();
        Appender fileAppender = getFileAppender(relativeOrAbsolutePathToFile, logContextConfig, pattern);
        logContextConfig.addAppender(fileAppender);
        AppenderRef appenderRef = AppenderRef.createAppenderRef("File", null, null);
        AppenderRef[] refs = new AppenderRef[] {appenderRef};
        LoggerConfig loggerConfig = LoggerConfig.createLogger(false, level, "de", "true", refs, null, logContextConfig, null);
        loggerConfig.addAppender(fileAppender, null, null);
        logContextConfig.addLogger("de", loggerConfig);
        logContext.updateLoggers();
    }

    /**
     * @param relativeOrAbsolutePathToFile
     * @param logContextConfig
     * @param pattern
     * @return
     */
    private static Appender getFileAppender(String relativeOrAbsolutePathToFile, Configuration logContextConfig, String pattern) {
        PatternLayout logLayout = getLayout(logContextConfig, pattern);
        Appender fileAppender = FileAppender.createAppender(relativeOrAbsolutePathToFile, "false", "false", "File", "false", "false", "true", "4000", logLayout, null, "false", null, logContextConfig);
        fileAppender.start();
        return fileAppender;
    }

    /**
     * @param logContextConfig
     * @param pattern
     * @return Layout with the given pattern
     * @see PatternLayout#createLayout(String,
     *      org.apache.logging.log4j.core.layout.PatternSelector, Configuration,
     *      org.apache.logging.log4j.core.pattern.RegexReplacement,
     *      java.nio.charset.Charset, boolean, boolean, String, String)
     */
    private static PatternLayout getLayout(Configuration logContextConfig, String pattern) {
        //                @PluginAttribute(value = "pattern", defaultString = DEFAULT_CONVERSION_PATTERN) final String pattern,
        //                @PluginElement("PatternSelector") final PatternSelector patternSelector,
        //                @PluginConfiguration final Configuration config,
        //                @PluginElement("Replace") final RegexReplacement replace,
        //                // LOG4J2-783 use platform default by default, so do not specify defaultString for charset
        //                @PluginAttribute(value = "charset") final Charset charset,
        //                @PluginAttribute(value = "alwaysWriteExceptions", defaultBoolean = true) final boolean alwaysWriteExceptions,
        //                @PluginAttribute(value = "noConsoleNoAnsi") final boolean noConsoleNoAnsi,
        //                @PluginAttribute("header") final String headerPattern,
        //                @PluginAttribute("footer") final String footerPattern) {
        return PatternLayout.newBuilder()
                .withPattern(pattern)
                .withPatternSelector(null)
                .withConfiguration(logContextConfig)
                .withRegexReplacement(null)
                .withCharset(null)
                .withAlwaysWriteExceptions(true)
                .withNoConsoleNoAnsi(false)
                .withHeader(null)
                .withFooter(null)
                .build();
    }

}
