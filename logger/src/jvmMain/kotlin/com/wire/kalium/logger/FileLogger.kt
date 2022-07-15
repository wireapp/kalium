package com.wire.kalium.logger

import co.touchlab.kermit.LogWriter
import co.touchlab.kermit.Severity
import java.io.File
import java.util.Timer
import kotlin.concurrent.scheduleAtFixedRate
import kotlin.time.Duration.Companion.seconds
import kotlin.time.DurationUnit

private var LOG_FILE_FLUSH_PERIOD = 5.seconds.toLong(DurationUnit.MILLISECONDS)

class FileLogger(outputfile: File) : LogWriter() {

    private val writer = outputfile.bufferedWriter()

    init {
        // Attempt to close & flush the output file before the process terminates
        Runtime.getRuntime().addShutdownHook(
            Thread() {
                writer.close()
            }
        )

        // Flush the output file every 5 seconds for the case when there a low log output
        Timer().scheduleAtFixedRate(delay = LOG_FILE_FLUSH_PERIOD, period = LOG_FILE_FLUSH_PERIOD) {
            writer.flush()
        }
    }

    override fun log(severity: Severity, message: String, tag: String, throwable: Throwable?) {
        val str = "$severity: ($tag) $message"
        writer.write(str)
        writer.newLine()

        throwable?.let {
            val thString = it.stackTraceToString()
            writer.write(thString)
            writer.newLine()
        }
    }

}
