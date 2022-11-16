package com.wire.kalium.logic.util

actual val clientPlatform: String = "iOS"

expect fun createCompressedFile(filesSourcesList: List<Pair<Source, String>>, outputSink: Sink) {
    // TODO: Implement own iOS compression method
}
