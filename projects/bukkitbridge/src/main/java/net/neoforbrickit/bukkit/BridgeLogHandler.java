/*
 * Forbric Bukkit bridge — routes java.util.logging through the platform's log4j.
 */
package net.neoforbrickit.bukkit;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

import net.neoforbrickit.bridge.util.ForbricLog;

/**
 * The handler behind {@code Server.getLogger()}, so plugin logs look like every other line.
 *
 * <h2>What it replaces and why</h2>
 *
 * <p>The bridge used to install a plain {@link java.util.logging.ConsoleHandler} on its logger. That handler
 * formats with java.util.logging's default {@code SimpleFormatter} and writes to {@code System.err}, so a
 * plugin's {@code getLogger().info("...")} came out as:
 *
 * <pre>{@code
 * 信息: 简单的通讯器插件加载完毕 | SimpleCom plugin loaded.
 * }</pre>
 *
 * <p>No timestamp, no thread, no level in English, no logger name — and on a Chinese locale the level itself
 * is localised, which makes the line ungreppable for anyone reading a bug report. Every other line in the
 * same console has the shape the platform chose:
 *
 * <pre>{@code
 * [13:08:03] [Server thread/INFO] [neoforbrickit.bukkit/]: [SimpleCom] 简单的通讯器插件加载完毕
 * }</pre>
 *
 * <p>Two things were wrong, and this class fixes the one that a plugin cannot fix for itself:
 *
 * <ul>
 *   <li>The <b>destination</b> was {@code System.err}, so plugin output never reached {@code latest.log}.
 *       An operator reading the log a bug report ships could see the bridge's own lines and not the plugin's.
 *   <li>The <b>format</b> was JUL's, which is the shape above. Routing through log4j gives the platform's
 *       layout for free, including the timestamp and the thread name.
 * </ul>
 *
 * <p>The missing {@code [SimpleCom]} prefix is NOT fixed here, and deliberately so: that prefix comes from
 * {@link org.bukkit.plugin.PluginLogger}, which every plugin would have to be constructed with, and the
 * bridge hands one logger to all of them. Adding a prefix here would have to guess which plugin is calling,
 * and a wrong prefix is worse than none — it sends the reader to the wrong jar. See the class comment on
 * {@code BridgeBootstrap.createLogger} for what a plugin-own logger would take.
 *
 * <h2>Level mapping</h2>
 *
 * <p>JUL's levels are finer-grained than log4j's, so several collapse onto one. The direction of the
 * collapse is chosen so nothing is silently DROPPED: {@code CONFIG}/{@code FINE}/{@code FINER}/{@code FINEST}
 * all map to {@code INFO} rather than {@code DEBUG}, because the bridge's logger is set to {@code ALL} and a
 * plugin using {@code fine} for detail still expects to see it while the bridge is being diagnosed.
 * {@code WARNING} maps to {@code WARN}, {@code SEVERE} to {@code ERROR}, and {@code OFF} drops the record —
 * that one is a plugin saying "do not log this", not a level to translate.
 */
final class BridgeLogHandler extends Handler {

    /** The one instance, since the logger holds it for the server's life. */
    static final BridgeLogHandler INSTANCE = new BridgeLogHandler();

    private BridgeLogHandler() {
        // ALL: the level decision is made per-record in publish(), so a handler-level filter here would
        // silently drop records the logger above it deliberately accepted.
        setLevel(Level.ALL);
    }

    /**
     * Sends one record to log4j at the level its JUL level maps to.
     *
     * <p>Never throws. This runs inside a plugin's own logging call, and an exception thrown from a handler
     * propagates out of {@code Logger.info} — a plugin that logs would crash where it only meant to say
     * something.
     */
    @Override
    public void publish(LogRecord record) {
        if (record == null) return;
        Level level = record.getLevel();
        if (level == null || level == Level.OFF) return;

        String message = record.getMessage();
        if (message == null) message = "";
        // JUL's own parameter substitution, done here because log4j receives the finished string: the record
        // reaching a Handler has its parameters unsubstituted when the caller used Logger#log(Level,String,Object[]).
        Object[] parameters = record.getParameters();
        if (parameters != null && parameters.length > 0) {
            try {
                message = java.text.MessageFormat.format(message, parameters);
            } catch (RuntimeException notAMessageFormat) {
                // A message with braces that was never a MessageFormat ("{}" from a plugin using another
                // logger's syntax, say). Keeping it as written is right, and dropping the record is not.
                message = record.getMessage() + " " + java.util.Arrays.toString(parameters);
            }
        }

        // The class and method that logged, when the caller asked for them: a per-plugin logger would put the
        // plugin name here, and until every plugin has one this is what identifies the caller.
        String source = record.getSourceClassName();
        String origin = source == null || source.isEmpty() ? "" : "(" + simpleName(source) + ") ";

        // The throwable goes through the same channel so it lands under the same timestamp and thread, rather
        // than being swallowed by a handler that only forwards the message.
        Throwable thrown = record.getThrown();
        String line = "[Forbric/Bukkit] " + origin + message;

        try {
            if (thrown != null) {
                if (level.intValue() >= Level.SEVERE.intValue()) {
                    ForbricLog.error(line, thrown);
                } else if (level.intValue() >= Level.WARNING.intValue()) {
                    ForbricLog.warn(line, thrown);
                } else {
                    ForbricLog.info(line + " - " + thrown);
                }
            } else if (level.intValue() >= Level.SEVERE.intValue()) {
                ForbricLog.error(line);
            } else if (level.intValue() >= Level.WARNING.intValue()) {
                ForbricLog.warn(line);
            } else {
                ForbricLog.info(line);
            }
        } catch (Throwable loggingItselfFailed) {
            // A handler that throws while handling takes the plugin's logging call down with it. Reporting on
            // stderr is the last resort and cannot recurse, because nothing here logs again.
            System.err.println(line);
            if (thrown != null) thrown.printStackTrace(System.err);
        }
    }

    /** Nothing is buffered, so there is nothing to flush. */
    @Override
    public void flush() {
        // no-op
    }

    /**
     * Ignored: the logger owns this handler for the server's life.
     *
     * <p>Closing it would silently send every plugin's logging to a closed handler, and JUL does not forbid
     * re-closing — so a second close would raise nothing and the loss would be invisible until someone
     * noticed the missing lines.
     */
    @Override
    public void close() {
        // no-op
    }

    private static String simpleName(String className) {
        int dot = className.lastIndexOf('.');
        return dot < 0 ? className : className.substring(dot + 1);
    }
}
