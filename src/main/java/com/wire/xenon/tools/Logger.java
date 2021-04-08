//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//

package com.wire.xenon.tools;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;

public class Logger {
    public static java.util.logging.Logger getLOGGER() {
        return LOGGER;
    }

    private final static java.util.logging.Logger LOGGER = java.util.logging.Logger.getLogger("com.wire.bots.logger");
    private static final AtomicInteger errorCount = new AtomicInteger();
    private static final AtomicInteger warningCount = new AtomicInteger();

    static {
        for (Handler handler : LOGGER.getHandlers()) {
            handler.setFormatter(new BotFormatter());
        }
    }

    public static void debug(String msg) {
        LOGGER.fine(msg);
    }

    public static void debug(String format, Object... args) {
        LOGGER.fine(String.format(format, args));
    }

    public static void info(String msg) {
        LOGGER.info(msg);
    }

    public static void info(String format, Object... args) {
        LOGGER.info(String.format(format, args));
    }

    public static void error(String msg) {
        errorCount.incrementAndGet();
        LOGGER.severe(msg);
    }

    public static void error(String format, Object... args) {
        // check if there's an exception in the objects
        for (Object arg : args) {
            if (arg instanceof Throwable) {
                // if so log it as exception instead of a common format
                exception(format, (Throwable) arg, args);
                // break as counter and logger is handled in the exception
                return;
            }
        }

        errorCount.incrementAndGet();
        LOGGER.severe(String.format(format, args));
    }

    public static void exception(String message, Throwable throwable, Object... args) {
        errorCount.incrementAndGet();
        LOGGER.log(Level.SEVERE, String.format(message, args), throwable);
    }

    public static void warning(String msg) {
        warningCount.incrementAndGet();
        LOGGER.warning(msg);
    }

    public static void warning(String format, Object... args) {
        warningCount.incrementAndGet();
        LOGGER.warning(String.format(format, args));
    }

    public static int getErrorCount() {
        return errorCount.get();
    }

    public static int getWarningCount() {
        return warningCount.get();
    }

    public static Level getLevel() {
        return LOGGER.getLevel();
    }

    static class BotFormatter extends Formatter {
        private static final DateFormat df = new SimpleDateFormat("dd/MM/yyyy HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            StringBuilder builder = new StringBuilder();
            builder.append(df.format(new Date(record.getMillis()))).append(" - ");
            // builder.append("[").append(record.getSourceClassName()).append(".");
            // builder.append(record.getSourceMethodName()).append("] - ");
            builder.append("[").append(record.getLevel()).append("] - ");
            builder.append(formatMessage(record));
            builder.append("\n");
            return builder.toString();
        }

        @Override
        public String getHead(Handler h) {
            return super.getHead(h);
        }

        @Override
        public String getTail(Handler h) {
            return super.getTail(h);
        }
    }
}
