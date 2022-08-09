package com.wire.kalium.network

import io.ktor.http.content.OutgoingContent
import io.ktor.util.copyToBoth
import io.ktor.utils.io.ByteWriteChannel
import io.ktor.utils.io.close
import io.ktor.utils.io.ByteChannel
import io.ktor.utils.io.ByteReadChannel
import io.ktor.utils.io.writer
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.Dispatchers

internal suspend fun OutgoingContent.observe(log: ByteWriteChannel): OutgoingContent = when (this) {
    is OutgoingContent.ByteArrayContent -> {
        log.writeFully(bytes(), 0, bytes().size)
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
