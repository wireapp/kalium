/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.network.api.v4.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.message.EnvelopeProtoMapper
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.api.base.authenticated.message.QualifiedSendMessageResponse
import com.wire.kalium.network.api.base.model.ConversationId
import com.wire.kalium.network.api.v3.authenticated.MessageApiV3
import com.wire.kalium.network.exceptions.SendMessageError
import com.wire.kalium.network.serialization.XProtoBuf
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapKaliumResponse
import io.ktor.client.call.body
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType

internal open class MessageApiV4 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    private val envelopeProtoMapper: EnvelopeProtoMapper
) : MessageApiV3(authenticatedNetworkClient, envelopeProtoMapper) {

    override suspend fun qualifiedSendMessage(
        parameters: MessageApi.Parameters.QualifiedDefaultParameters,
        conversationId: ConversationId
    ): NetworkResponse<QualifiedSendMessageResponse> {

        suspend fun performRequest(
            queryParameter: String?,
            queryParameterValue: Any?,
            body: ByteArray
        ): NetworkResponse<QualifiedSendMessageResponse> = wrapKaliumResponse<QualifiedSendMessageResponse.MessageSent>({
            if (it.status != STATUS_CLIENTS_HAVE_CHANGED) null
            else NetworkResponse.Error(kException = SendMessageError.MissingDeviceError(errorBody = it.body()))
        }) {
            httpClient.post("$PATH_CONVERSATIONS/${conversationId.domain}/${conversationId.value}/$PATH_PROTEUS_MESSAGE") {
                if (queryParameter != null) {
                    parameter(queryParameter, queryParameterValue)
                }
                setBody(body)
                contentType(ContentType.Application.XProtoBuf)
            }
        }

        return when (parameters.messageOption) {
            is MessageApi.QualifiedMessageOption.IgnoreAll -> {
                val body = envelopeProtoMapper.encodeToProtobuf(parameters)
                performRequest(QUERY_IGNORE_MISSING, true, body)
            }

            is MessageApi.QualifiedMessageOption.IgnoreSome -> {
                val body = envelopeProtoMapper.encodeToProtobuf(parameters)
                val commaSeparatedList = parameters.messageOption.userIDs.joinToString(",")
                performRequest(QUERY_IGNORE_MISSING, commaSeparatedList, body)
            }

            is MessageApi.QualifiedMessageOption.ReportAll -> {
                val body = envelopeProtoMapper.encodeToProtobuf(parameters)
                performRequest(QUERY_REPORT_MISSING, true, body)
            }

            is MessageApi.QualifiedMessageOption.ReportSome -> {
                val body = envelopeProtoMapper.encodeToProtobuf(parameters)
                val commaSeparatedList = parameters.messageOption.userIDs.joinToString(",")
                performRequest(QUERY_REPORT_MISSING, commaSeparatedList, body)
            }
        }
    }
}
