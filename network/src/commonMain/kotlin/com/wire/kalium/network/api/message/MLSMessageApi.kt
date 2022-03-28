package com.wire.kalium.network.api.message

import com.wire.kalium.network.utils.NetworkResponse
import kotlin.jvm.JvmInline

interface MLSMessageApi {

    /**
     * A welcome message allows an MLS client to join an existing MLS group.
     */
    @JvmInline
    value class WelcomeMessage(val value: ByteArray)

    /**
     * An MLS message can either be an application message containing user content or
     * a handshake message which updates the group state (adding, removing clients).
     */
    @JvmInline
    value class Message(val value: ByteArray)

    /**
     * Send an MLS message to a MLS group. The destination group is encoded into the message itself.
     *
     * @param message MLS Message
     */
    suspend fun sendMessage(message: Message): NetworkResponse<Unit>

    /**
     * Send an MLS welcome message to a client(s) which you've added to a MLS group.
     *
     * @param message MLS welcome Message
     */
    suspend fun sendWelcomeMessage(message: WelcomeMessage): NetworkResponse<Unit>

}
