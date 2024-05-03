/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.network.api.v0.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.message.EnvelopeProtoMapper
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessagePriority
import com.wire.kalium.network.api.base.authenticated.message.QualifiedSendMessageResponse
import com.wire.kalium.network.api.base.authenticated.message.UserToClientToEncMsgMap
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
import com.wire.kalium.network.serialization.XProtoBuf
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.call.body
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.HttpStatusCode
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

internal open class MessageApiV0 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val envelopeProtoMapper: EnvelopeProtoMapper
) : MessageApi {

    protected val httpClient get() = authenticatedNetworkClient.httpClient

    @Serializable
    internal data class RequestBody(
        @SerialName("sender") val sender: String,
        @SerialName("data") val data: String?,
        @SerialName("native_push") val nativePush: Boolean,
        @SerialName("recipients") val recipients: UserToClientToEncMsgMap,
        @SerialName("transient") val transient: Boolean,
        @SerialName("report_missing") var reportMissing: List<String>? = null,
        @SerialName("native_priority") val priority: MessagePriority
    )

    private fun MessageApi.Parameters.DefaultParameters.toRequestBody(): RequestBody = RequestBody(
        sender = this.sender,
        data = this.data,
        nativePush = this.nativePush,
        recipients = this.recipients,
        transient = this.transient,
        priority = this.priority
    )

    override suspend fun qualifiedSendMessage(
        parameters: MessageApi.Parameters.QualifiedDefaultParameters,
        conversationId: ConversationId
    ): NetworkResponse<QualifiedSendMessageResponse> = wrapKaliumResponse<QualifiedSendMessageResponse.MessageSent>({
        if (it.status != STATUS_CLIENTS_HAVE_CHANGED) null
        else NetworkResponse.Error(
            kException = ProteusClientsChangedError(
                errorBody = it.body()
            )
        )
    }) {
        httpClient.post("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_PROTEUS_MESSAGE") {
            setBody(envelopeProtoMapper.encodeToProtobuf(parameters))
            contentType(ContentType.Application.XProtoBuf)
        }
    }

    override suspend fun qualifiedBroadcastMessage(
        parameters: MessageApi.Parameters.QualifiedDefaultParameters
    ): NetworkResponse<QualifiedSendMessageResponse> = wrapKaliumResponse<QualifiedSendMessageResponse.MessageSent>({
        if (it.status != STATUS_CLIENTS_HAVE_CHANGED) null
        else NetworkResponse.Error(kException = ProteusClientsChangedError(errorBody = it.body()))
    }) {
        httpClient.post("$PATH_BROADCAST/$PATH_PROTEUS_MESSAGE") {
            setBody(envelopeProtoMapper.encodeToProtobuf(parameters))
            contentType(ContentType.Application.XProtoBuf)
        }
    }

    protected companion object {
        val STATUS_CLIENTS_HAVE_CHANGED = HttpStatusCode(
            412,
            "Proteus clients have changed"
        )
        const val PATH_PROTEUS_MESSAGE = "proteus/messages"
        const val PATH_CONVERSATIONS = "conversations"
        const val PATH_BROADCAST = "broadcast"
    }
}
