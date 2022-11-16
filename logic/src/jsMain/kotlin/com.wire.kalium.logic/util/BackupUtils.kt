package com.wire.kalium.logic.util

actual val clientPlatform: String = "js"

expect fun createCompressedFile(filesSourcesList: List<Pair<Source, String>>, outputSink: Sink) {
    // TODO: Implement own JS compression method
}
