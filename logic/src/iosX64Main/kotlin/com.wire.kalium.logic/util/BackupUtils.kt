package com.wire.kalium.logic.util

actual const val CLIENT_PLATFORM: String = "iOS"

expect fun createCompressedFile(filesSourcesList: List<Pair<Source, String>>, outputSink: Sink) {
    // TODO: Implement own iOS compression method
}
