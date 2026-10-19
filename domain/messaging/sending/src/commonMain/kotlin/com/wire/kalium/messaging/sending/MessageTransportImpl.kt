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

package com.wire.kalium.messaging.sending

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapNetworkMlsFailureIfApplicable
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.NetworkQualifiedId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.message.BroadcastMessageOption
import com.wire.kalium.logic.data.message.MessageEnvelope
import com.wire.kalium.logic.data.message.MessageSent
import com.wire.kalium.logic.data.message.SendMessageFailureMapper
import com.wire.kalium.logic.data.message.SendMessageFailureMapperImpl
import com.wire.kalium.logic.data.message.SendMessagePartialFailureMapper
import com.wire.kalium.logic.data.message.SendMessagePartialFailureMapperImpl
import com.wire.kalium.network.api.authenticated.message.MessagePriority
import com.wire.kalium.network.api.authenticated.message.Parameters
import com.wire.kalium.network.api.authenticated.message.QualifiedMessageOption
import com.wire.kalium.network.api.authenticated.message.QualifiedSendMessageResponse
import com.wire.kalium.network.api.authenticated.message.SendMLSMessageResponse
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.message.MessageApi
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
import com.wire.kalium.network.utils.NetworkResponse
import kotlin.time.Instant

/** Sends envelopes and MLS messages, mapping a Proteus 412 to [com.wire.kalium.logic.failure.ProteusSendMessageFailure]. */
class MessageTransportImpl(
    private val messageApi: MessageApi,
    private val mlsMessageApi: MLSMessageApi,
    private val sendMessageFailureMapper: SendMessageFailureMapper = SendMessageFailureMapperImpl(),
    private val sendMessagePartialFailureMapper: SendMessagePartialFailureMapper = SendMessagePartialFailureMapperImpl(),
) : MessageTransport {

    override suspend fun sendEnvelope(
        conversationId: ConversationId,
        envelope: MessageEnvelope,
        messageTarget: MessageTarget
    ): Either<CoreFailure, MessageSent> {
        val recipientMap: Map<NetworkQualifiedId, Map<String, ByteArray>> =
            envelope.recipients.associate { recipientEntry ->
                recipientEntry.userId.toApi() to recipientEntry.clientPayloads.associate { clientPayload ->
                    clientPayload.clientId.value to clientPayload.payload.data
                }
            }

        return wrapApiRequest {
            messageApi.qualifiedSendMessage(
                // TODO(messaging): Handle other MessageOptions, native push, transient and priorities
                Parameters.QualifiedDefaultParameters(
                    envelope.senderClientId.value,
                    recipientMap,
                    true,
                    MessagePriority.HIGH,
                    false,
                    envelope.dataBlob?.data,
                    messageTarget.toOption()
                ),
                conversationId.toApi(),
            )
        }.fold({ networkFailure ->
            val failure = when {
                (networkFailure is NetworkFailure.ServerMiscommunication && networkFailure.rootCause is ProteusClientsChangedError) -> {
                    sendMessageFailureMapper.fromDTO(networkFailure.rootCause as ProteusClientsChangedError)
                }

                else -> networkFailure
            }
            Either.Left(failure)
        }, { response: QualifiedSendMessageResponse ->
            Either.Right(sendMessagePartialFailureMapper.fromDTO(response))
        })
    }

    private fun MessageTarget.toOption() = when (this) {
        is MessageTarget.Client -> QualifiedMessageOption.IgnoreAll

        is MessageTarget.Conversation -> if (this.usersToIgnore.isNotEmpty()) {
            QualifiedMessageOption.IgnoreSome(this.usersToIgnore.map { it.toApi() })
        } else {
            QualifiedMessageOption.ReportAll
        }

        is MessageTarget.Users -> QualifiedMessageOption.ReportSome(this.userId.map { it.toApi() })
    }

    override suspend fun broadcastEnvelope(
        envelope: MessageEnvelope,
        messageOption: BroadcastMessageOption
    ): Either<CoreFailure, Instant> {
        val recipientMap: Map<NetworkQualifiedId, Map<String, ByteArray>> =
            envelope.recipients.associate { recipientEntry ->
                recipientEntry.userId.toApi() to recipientEntry.clientPayloads.associate { clientPayload ->
                    clientPayload.clientId.value to clientPayload.payload.data
                }
            }

        val option = when (messageOption) {
            is BroadcastMessageOption.IgnoreSome -> QualifiedMessageOption.IgnoreSome(messageOption.userIDs.map { it.toApi() })
            is BroadcastMessageOption.ReportSome -> QualifiedMessageOption.ReportSome(messageOption.userIDs.map { it.toApi() })
            is BroadcastMessageOption.ReportAll -> QualifiedMessageOption.ReportAll
            is BroadcastMessageOption.IgnoreAll -> QualifiedMessageOption.IgnoreAll
        }

        return wrapApiRequest {
            messageApi.qualifiedBroadcastMessage(
                Parameters.QualifiedDefaultParameters(
                    envelope.senderClientId.value,
                    recipientMap,
                    true,
                    MessagePriority.HIGH,
                    false,
                    envelope.dataBlob?.data,
                    option
                ),
            )
        }.fold({ networkFailure ->
            val failure = when {
                (networkFailure is NetworkFailure.ServerMiscommunication && networkFailure.rootCause is ProteusClientsChangedError) -> {
                    sendMessageFailureMapper.fromDTO(networkFailure.rootCause as ProteusClientsChangedError)
                }

                else -> networkFailure
            }
            Either.Left(failure)
        }, {
            Either.Right(it.time)
        })
    }

    override suspend fun sendMLSMessage(
        message: MLSMessageApi.Message
    ): Either<CoreFailure, MessageSent> =
        wrapApiRequest {
            mlsMessageApi.sendMessage(message.value)
        }.flatMapLeft { networkFailure ->
            Either.Left(networkFailure.wrapNetworkMlsFailureIfApplicable())
        }.flatMap { response ->
            Either.Right(sendMessagePartialFailureMapper.fromMlsDTO(response))
        }

    override suspend fun sendMLSTargetedMessage(
        batch: MLSMessageApi.TargetedMessageBatch
    ): Either<CoreFailure, MessageSent> = sendMLS { mlsMessageApi.sendTargetedMessage(batch) }

    override suspend fun sendMLSTransientMessage(
        message: MLSMessageApi.TransientMessage
    ): Either<CoreFailure, MessageSent> = sendMLS { mlsMessageApi.sendTransientMessage(message) }

    override suspend fun sendMLSTransientTargetedMessage(
        batch: MLSMessageApi.TargetedMessageBatch
    ): Either<CoreFailure, MessageSent> = sendMLS { mlsMessageApi.sendTransientTargetedMessage(batch) }

    private suspend fun sendMLS(
        request: suspend () -> NetworkResponse<SendMLSMessageResponse>
    ): Either<CoreFailure, MessageSent> =
        wrapApiRequest {
            request()
        }.flatMapLeft { networkFailure ->
            Either.Left(networkFailure.wrapNetworkMlsFailureIfApplicable())
        }.flatMap { response ->
            Either.Right(sendMessagePartialFailureMapper.fromMlsDTO(response))
        }
}
