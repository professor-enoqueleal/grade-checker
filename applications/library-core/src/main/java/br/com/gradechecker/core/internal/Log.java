package br.com.gradechecker.core.internal;

/**
 * Logging of the library over {@link System.Logger} (JDK only, capability core-boundaries), with the "{}" messages of
 * Log4j, so the log calls of the code moved from the applications stay the same. The applications route
 * System.Logger to Log4j 2 with org.apache.logging.log4j:log4j-jpl. A Throwable as the last extra argument is logged
 * as the exception, as in Log4j. Internal: not part of the API of the library.
 */
public final class Log {

    private final System.Logger logger;

    private Log(System.Logger logger) {
        this.logger = logger;
    }

    public static Log of(Class<?> type) {
        return new Log(System.getLogger(type.getName()));
    }

    public void debug(String message, Object... args) { log(System.Logger.Level.DEBUG, message, args); }
    public void info(String message, Object... args) { log(System.Logger.Level.INFO, message, args); }
    public void warn(String message, Object... args) { log(System.Logger.Level.WARNING, message, args); }
    public void error(String message, Object... args) { log(System.Logger.Level.ERROR, message, args); }

    private void log(System.Logger.Level level, String message, Object[] args) {
        if (!logger.isLoggable(level)) return;
        StringBuilder text = new StringBuilder();
        int next = 0;
        int from = 0;
        int at;
        while ((at = message.indexOf("{}", from)) >= 0) {
            text.append(message, from, at);
            text.append(next < args.length ? String.valueOf(args[next++]) : "{}");
            from = at + 2;
        }
        text.append(message.substring(from));
        if (next < args.length && args[args.length - 1] instanceof Throwable) {
            logger.log(level, text.toString(), (Throwable) args[args.length - 1]);
        } else {
            logger.log(level, text.toString());
        }
    }
}
