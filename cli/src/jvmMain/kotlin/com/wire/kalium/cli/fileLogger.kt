package com.wire.kalium.cli

import co.touchlab.kermit.LogWriter
import com.wire.kalium.logger.FileLogger
import java.io.File

actual fun fileLogger(filePath: String): LogWriter {
    return FileLogger(File(filePath))
}
