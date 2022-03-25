package com.wire.kalium.network.api.message

import com.wire.kalium.network.utils.NetworkResponse
import kotlin.jvm.JvmInline

interface MLSMessageApi {

    @JvmInline
    value class Message(val value: ByteArray)
    @JvmInline
    value class WelcomeMessage(val value: ByteArray)

    suspend fun sendMessage(message: Message): NetworkResponse<Unit>
    suspend fun sendWelcomeMessage(message: WelcomeMessage): NetworkResponse<Unit>

}
