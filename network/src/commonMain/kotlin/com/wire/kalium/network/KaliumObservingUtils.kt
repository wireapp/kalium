package com.wire.kalium.network


import io.ktor.http.content.*
import io.ktor.util.*
import io.ktor.utils.io.*
import kotlinx.coroutines.*

internal suspend fun OutgoingContent.observe(log: ByteWriteChannel): OutgoingContent = when (this) {
    is OutgoingContent.ByteArrayContent -> {
        log.writeFully(bytes())
        log.close()
        this
    }
    is OutgoingContent.ReadChannelContent -> {
        val responseChannel = ByteChannel()
        val content = readFrom()

        content.copyToBoth(log, responseChannel)
        KaliumLoggedContent(this, responseChannel)
    }
    is OutgoingContent.WriteChannelContent -> {
        val responseChannel = ByteChannel()
        val content = toReadChannel()
        content.copyToBoth(log, responseChannel)
        KaliumLoggedContent(this, responseChannel)
    }
    else -> {
        log.close()
        this
    }
}

@OptIn(DelicateCoroutinesApi::class)
private fun OutgoingContent.WriteChannelContent.toReadChannel():
        ByteReadChannel = GlobalScope.writer(Dispatchers.Unconfined) { writeTo(channel) }.channel
