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
package com.wire.kalium.tools

import java.text.DateFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.concurrent.atomic.AtomicInteger
import java.util.logging.Formatter
import java.util.logging.Handler
import java.util.logging.Level
import java.util.logging.LogRecord
import java.util.logging.Logger

object Logger {
    fun getLOGGER(): Logger? {
        return LOGGER
    }

    private val LOGGER = Logger.getLogger("com.wire.bots.logger")
    private val errorCount: AtomicInteger = AtomicInteger()
    private val warningCount: AtomicInteger = AtomicInteger()

    fun debug(msg: String?) = LOGGER.fine(msg)

    fun debug(format: String?, vararg args: Any?) = format?.let { LOGGER.fine(String.format(it, *args)) }

    fun info(msg: String?) = LOGGER.info(msg)

    fun info(format: String?, vararg args: Any?) = format?.let { LOGGER.info(String.format(it, *args)) }

    fun error(msg: String?) {
        errorCount.incrementAndGet()
        LOGGER.severe(msg)
    }

    fun error(format: String?, vararg args: Any?) {
        // check if there's an exception in the objects
        for (arg in args) {
            if (arg is Throwable) {
                // if so log it as exception instead of a common format
                exception(arg as Throwable?, format, *args)
                // break as counter and logger is handled in the exception
                return
            }
        }
        errorCount.incrementAndGet()

        format?.let { LOGGER.severe(String.format(it, *args)) }
    }

    fun exception(throwable: Throwable?, message: String?, vararg args: Any?) {
        errorCount.incrementAndGet()
        message?.let { LOGGER.log(Level.SEVERE, String.format(it, *args), throwable) }
    }

    fun warning(msg: String?) {
        warningCount.incrementAndGet()
        LOGGER.warning(msg)
    }

    fun warning(format: String?, vararg args: Any?) {
        warningCount.incrementAndGet()
        format?.let { LOGGER.warning(String.format(it, *args)) }
    }

    fun getErrorCount(): Int {
        return errorCount.get()
    }

    fun getWarningCount(): Int {
        return warningCount.get()
    }

    fun getLevel(): Level? {
        return LOGGER.level
    }

    internal class BotFormatter : Formatter() {
        override fun format(record: LogRecord): String? {
            val builder = StringBuilder()
            builder.append(df.format(Date(record.millis))).append(" - ")
            // builder.append("[").append(record.getSourceClassName()).append(".");
            // builder.append(record.getSourceMethodName()).append("] - ");
            builder.append("[").append(record.level).append("] - ")
            builder.append(formatMessage(record))
            builder.append("\n")
            return builder.toString()
        }

        override fun getHead(h: Handler?): String? {
            return super.getHead(h)
        }

        override fun getTail(h: Handler?): String? {
            return super.getTail(h)
        }

        companion object {
            private val df: DateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm:ss")
        }
    }

//    init {
//        for (handler in LOGGER.handlers) {
//            com.wire.kalium.tools.handler.setFormatter(BotFormatter())
//        }
//    }
}
