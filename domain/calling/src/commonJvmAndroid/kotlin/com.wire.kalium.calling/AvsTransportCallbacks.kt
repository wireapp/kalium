/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.calling

import com.sun.jna.Pointer
import com.wire.kalium.calling.callbacks.CallConfigRequestHandler
import com.wire.kalium.calling.callbacks.SFTRequestHandler
import com.wire.kalium.calling.callbacks.SendHandler
import com.wire.kalium.calling.types.Handle
import com.wire.kalium.calling.types.Size_t
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.coroutines.cancellation.CancellationException

/** Shared asynchronous adapter for AVS call-configuration callbacks. */
class AvsCallConfigRequestHandler(
    private val scope: CoroutineScope,
    private val loadConfig: suspend () -> String?,
    private val respond: (Handle, String?) -> Unit,
    private val callbackResult: Int = 0,
) : CallConfigRequestHandler {
    override fun onConfigRequest(inst: Handle, arg: Pointer?): Int {
        scope.launch { respond(inst, loadConfig()) }
        return callbackResult
    }
}

/**
 * Shared asynchronous adapter for AVS SFT callbacks.
 *
 * AVS supplies a NUL-terminated JSON string; the native length is not the JSON payload length.
 */
class AvsSftRequestHandler(
    private val scope: CoroutineScope,
    private val connect: suspend (url: String, payload: ByteArray) -> ByteArray?,
    private val respond: suspend (context: Pointer?, response: ByteArray?) -> Unit,
    private val callbackResult: Int = 0,
    private val invalidPayloadResult: Int = callbackResult,
) : SFTRequestHandler {
    override fun onSFTRequest(ctx: Pointer?, url: String, data: Pointer?, length: Size_t, arg: Pointer?): Int {
        val payload = data?.getString(0, Charsets.UTF_8.name())?.encodeToByteArray()
            ?: return invalidPayloadResult
        scope.launch { respond(ctx, connect(url, payload)) }
        return callbackResult
    }
}

/** Parsed, ownership-checked request emitted by the native AVS send callback. */
data class AvsSendRequest(
    val context: Pointer?,
    val remoteConversationId: String,
    val remoteSelfUserId: String,
    val remoteSelfClientId: String,
    val recipientsJson: String?,
    val clientDestination: String?,
    val content: String,
    val isTransient: Boolean,
    val myClientsOnly: Boolean,
)

/** Shared validation and payload extraction for outbound AVS signalling callbacks. */
@Suppress("LongParameterList")
class AvsSendRequestHandler(
    private val matchesSelf: (userId: String, clientId: String) -> Boolean,
    private val acceptsPayload: (length: Long, recipientsJson: String?, clientDestination: String?) -> Boolean,
    private val onRequest: (AvsSendRequest) -> Unit,
    private val onFailure: (Throwable) -> Unit = {},
    private val acceptedResult: Int = 0,
    private val invalidArgumentResult: Int,
    private val decodingFailureResult: Int = invalidArgumentResult,
) : SendHandler {
    @Suppress("LongParameterList", "ReturnCount", "TooGenericExceptionCaught")
    override fun onSend(
        context: Pointer?,
        remoteConversationId: String,
        remoteSelfUserId: String,
        remoteClientIdSelf: String,
        targetRecipientsJson: String?,
        clientIdDestination: String?,
        data: Pointer?,
        length: Size_t,
        isTransient: Boolean,
        myClientsOnly: Boolean,
        arg: Pointer?,
    ): Int {
        val payloadLength = length.value
        if (!matchesSelf(remoteSelfUserId, remoteClientIdSelf) || data == null) return invalidArgumentResult
        if (!acceptsPayload(payloadLength, targetRecipientsJson, clientIdDestination)) return invalidArgumentResult
        if (payloadLength !in 1..Int.MAX_VALUE.toLong()) return invalidArgumentResult
        return try {
            onRequest(
                AvsSendRequest(
                    context = context,
                    remoteConversationId = remoteConversationId,
                    remoteSelfUserId = remoteSelfUserId,
                    remoteSelfClientId = remoteClientIdSelf,
                    recipientsJson = targetRecipientsJson,
                    clientDestination = clientIdDestination,
                    content = data.getByteArray(0, payloadLength.toInt()).decodeToString(),
                    isTransient = isTransient,
                    myClientsOnly = myClientsOnly,
                ),
            )
            acceptedResult
        } catch (cancellation: CancellationException) {
            throw cancellation
        } catch (failure: Throwable) {
            onFailure(failure)
            decodingFailureResult
        }
    }
}
