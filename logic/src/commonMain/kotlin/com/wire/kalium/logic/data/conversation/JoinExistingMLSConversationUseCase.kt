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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrElse
import com.wire.kalium.common.functional.isLeft
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.common.logger.logStructuredJson
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.logger.KaliumLogLevel
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureHandler
import com.wire.kalium.logic.sync.receiver.conversation.message.MLSMessageFailureResolution
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsMissingGroupInfo
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.withContext
import kotlin.jvm.JvmInline

/**
 * Send an external commit to join an MLS conversation for which the user is a member,
 * but has not yet joined the corresponding MLS group.
 */
@Mockable
internal interface JoinExistingMLSConversationUseCase {
    suspend operator fun invoke(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        mlsPublicKeys: MLSPublicKeys? = null
    ): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList")
internal class JoinExistingMLSConversationUseCaseImpl(
    private val featureSupport: FeatureSupport,
    private val conversationApi: ConversationApi,
    private val clientRepository: ClientRepository,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val fetchMLSOneToOneConversation: FetchMLSOneToOneConversationUseCase,
    private val fetchConversation: FetchConversationUseCase,
    private val resetMLSConversation: ResetMLSConversationUseCase,
    private val selfUserId: UserId,
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : JoinExistingMLSConversationUseCase {
    private val dispatcher = kaliumDispatcher.io
    private val logger = kaliumLogger.withTextTag("JoinExistingMLSConversationUseCase")

    override suspend operator fun invoke(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId,
        mlsPublicKeys: MLSPublicKeys?
    ): Either<CoreFailure, Unit> =
        if (!featureSupport.isMLSSupported ||
            !clientRepository.hasRegisteredMLSClient().getOrElse(false)
        ) {
            logger.d("Skip re-join existing MLS conversation, since MLS is not supported.")
            Either.Right(Unit)
        } else {
            conversationRepository.getConversationById(conversationId).fold({
                Either.Left(StorageFailure.DataNotFound)
            }, { conversation ->
                withContext(dispatcher) {
                    joinOrEstablishMLSGroupAndRetry(transactionContext, conversation, mlsPublicKeys)
                }
            })
        }

    private suspend fun joinOrEstablishMLSGroupAndRetry(
        transactionContext: CryptoTransactionContext,
        conversation: Conversation,
        mlsPublicKeys: MLSPublicKeys?
    ): Either<CoreFailure, Unit> =
        joinOrEstablishMLSGroup(transactionContext, conversation, mlsPublicKeys)
            .flatMapLeft { failure ->
                if (failure is NetworkFailure.ServerMiscommunication && failure.kaliumException is KaliumException.InvalidRequestError) {
                    if ((failure.kaliumException as KaliumException.InvalidRequestError).isMlsStaleMessage()) {
                        logger.logStructuredJson(
                            level = KaliumLogLevel.WARN,
                            leadingMessage = "Join-Establish MLS Group Stale",
                            jsonStringKeyValues = conversation.logData(failure)
                        )
                        // Re-fetch current epoch and try again
                        if (conversation.type == Conversation.Type.OneOnOne) {
                            conversationRepository.getConversationMembers(conversation.id).flatMap {
                                fetchMLSOneToOneConversation(transactionContext, it.first()).map {
                                    it.mlsPublicKeys
                                }
                            }
                        } else {
                            fetchConversation(transactionContext, conversation.id)
                        }.flatMap {
                            conversationRepository.getConversationById(conversation.id).flatMap { conversation ->
                                joinOrEstablishMLSGroup(transactionContext, conversation, null)
                            }
                        }
                    } else if ((failure.kaliumException as KaliumException.InvalidRequestError).isMlsMissingGroupInfo()) {
                        logger.w("Conversation has no group info, ignoring...")
                        Either.Right(Unit)
                    } else {
                        Either.Left(failure)
                    }
                } else {
                    Either.Left(failure)
                }
            }

    @Suppress("LongMethod")
    private suspend fun joinOrEstablishMLSGroup(
        transactionContext: CryptoTransactionContext,
        conversation: Conversation,
        publicKeys: MLSPublicKeys?
    ): Either<CoreFailure, Unit> {
        val protocol = conversation.protocol

        if (protocol !is Conversation.ProtocolInfo.MLSCapable) {
            kaliumLogger.d("calling join or establish MLS on proteus conversation")
            Either.Right(Unit)
        }

        val remoteEpoch: Either<NetworkFailure, Long?> = wrapApiRequest {
            conversationApi.fetchGroupInfo(conversation.id.toApi())
        }.map {
            GroupInfo(it).extractEpoch().also {
                kaliumLogger.d("cccc: fetched group info epoch=$it for conversation ${conversation.id.toLogString()}")
            }
        }
        if (remoteEpoch.isLeft()) {
            logger.logStructuredJson(
                level = KaliumLogLevel.WARN,
                leadingMessage = "Join-Establish MLS Group Missing GroupInfo",
                jsonStringKeyValues = conversation.logData(remoteEpoch.value)
            )
            return remoteEpoch
        }

        val type = conversation.type
        return when {
            remoteEpoch.value == null -> {
                logger.logStructuredJson(
                    level = KaliumLogLevel.WARN,
                    leadingMessage = "Join-Establish MLS Group Null GroupInfo",
                    jsonStringKeyValues = conversation.logData()
                )
                return Either.Right(Unit)
            }

            remoteEpoch.value!!.toULong() != 0UL -> {
                // TODO(refactor): don't use conversationAPI directly
                //                 we could use mlsConversationRepository to solve this
                logger.d("Joining group by external commit ${conversation.id.toLogString()}")
                wrapApiRequest {
                    conversationApi.fetchGroupInfo(conversation.id.toApi())
                }.flatMap { groupInfo ->
                    transactionContext.wrapInMLSContext { mlsContext ->
                        mlsConversationRepository.joinGroupByExternalCommit(
                            mlsContext,
                            (protocol as Conversation.ProtocolInfo.MLSCapable).groupId,
                            groupInfo
                        )
                    }.flatMapLeft { failure ->
                        when (MLSMessageFailureHandler.handleFailure(failure)) {
                            is MLSMessageFailureResolution.Ignore -> {
                                logger.logStructuredJson(
                                    level = KaliumLogLevel.WARN,
                                    leadingMessage = "Join Group external commit Ignored",
                                    jsonStringKeyValues = conversation.logData(failure)
                                )
                                Either.Right(Unit)
                            }

                            is MLSMessageFailureResolution.ResetConversation -> {
                                logger.logStructuredJson(
                                    level = KaliumLogLevel.WARN,
                                    leadingMessage = "Reset Conversation after join group failure",
                                    jsonStringKeyValues = conversation.logData(failure)
                                )
                                resetMLSConversation(conversation.id, transactionContext)
                            }

                            else -> {
                                logger.logStructuredJson(
                                    level = KaliumLogLevel.ERROR,
                                    leadingMessage = "Join Group external commit Failure",
                                    jsonStringKeyValues = conversation.logData(failure)
                                )
                                Either.Left(failure)
                            }
                        }
                    }
                }.onSuccess {
                    logger.logStructuredJson(
                        level = KaliumLogLevel.INFO,
                        leadingMessage = "Join Group external commit Success",
                        jsonStringKeyValues = conversation.logData()
                    )
                }
            }

            type == Conversation.Type.Self -> {
                logger.d("Establish Self MLS Conversation ${conversation.id.toLogString()}")
                transactionContext.wrapInMLSContext { mlsContext ->
                    mlsConversationRepository.establishMLSGroup(
                        mlsContext,
                        (protocol as Conversation.ProtocolInfo.MLSCapable).groupId,
                        listOf(selfUserId)
                    )
                }
                    .onSuccess {
                        logger.logStructuredJson(
                            level = KaliumLogLevel.INFO,
                            leadingMessage = "Establish Group",
                            jsonStringKeyValues = conversation.logData()
                        )
                    }.map { Unit }
            }

            type == Conversation.Type.OneOnOne || type is Conversation.Type.Group -> {
                logger.d("Establish Group/1:1 MLS Conversation ${conversation.id.toLogString()}")
                conversationRepository.getConversationMembers(conversation.id).flatMap { members ->
                    transactionContext.wrapInMLSContext { mlsContext ->
                        mlsConversationRepository.establishMLSGroup(
                            mlsContext,
                            (protocol as Conversation.ProtocolInfo.MLSCapable).groupId,
                            members,
                            publicKeys
                        )
                    }
                }.onSuccess {
                    logger.logStructuredJson(
                        level = KaliumLogLevel.INFO,
                        leadingMessage = "Establish Group",
                        jsonStringKeyValues = conversation.logData()
                    )
                }.map { Unit }
            }

            else -> {
                logger.w("SKIPPING due to unknown conversation type $type")
                Either.Right(Unit)
            }
        }
    }

    private fun Conversation.logData(
        failure: CoreFailure? = null
    ): Map<String, Any> = buildMap {
        "conversationId" to id.toLogString()
        "conversationType" to type
        "protocol" to CreateConversationParam.Protocol.MLS.name
        "protocolInfo" to protocol.toLogMap()
        failure?.run { "errorInfo" to "$failure" }
    }
}

@JvmInline
value class GroupInfo(val value: ByteArray) {

    /**
     * Extracts the epoch value from the GroupInfo TLS-encoded structure.
     *
     * According to RFC 9420, an epoch represents a state of a group in which a specific set
     * of authenticated clients hold shared cryptographic state. Each epoch has a distinct
     * ratchet tree and secret tree, and epochs progress linearly in sequence.
     *
     * The GroupInfo structure begins with a GroupContext containing:
     * - version (2 bytes)
     * - cipher_suite (2 bytes)
     * - group_id (variable length, prefixed with MLS varint)
     * - epoch (8 bytes, uint64 in big-endian format)
     *
     * @return The epoch value as a Long, or null if parsing fails due to malformed data
     * @see <a href="https://datatracker.ietf.org/doc/html/rfc9420#name-group-context">RFC 9420 - Group Context</a>
     */
    fun extractEpoch(): Long? {
        var p = 0

        fun need(n: Int): Boolean = value.size - p >= n

        /** Reads an unsigned 8-bit integer and advances position by 1 byte */
        fun u8(): Int {
            if (!need(1)) throw IndexOutOfBoundsException()
            return value[p++].toInt() and 0xFF
        }

        /** Reads an unsigned 16-bit integer in big-endian format and advances position by 2 bytes */
        fun u16(): Int {
            if (!need(2)) throw IndexOutOfBoundsException()
            val v = ((value[p].toInt() and 0xFF) shl 8) or (value[p + 1].toInt() and 0xFF)
            p += 2
            return v
        }

        /** Reads an unsigned 64-bit integer in big-endian format and advances position by 8 bytes */
        fun u64(): Long {
            if (!need(8)) throw IndexOutOfBoundsException()
            var v = 0L
            repeat(8) { v = (v shl 8) or (value[p + it].toLong() and 0xFF) }
            p += 8
            return v
        }

        /**
         * Reads an MLS variable-length integer (similar to QUIC varint encoding).
         * - Prefix 00 (bits 7-6): 1 byte total, value in bits 5-0
         * - Prefix 01 (bits 7-6): 2 bytes total, value in bits 5-0 of first byte + 8 bits of second byte
         * - Prefix 10 (bits 7-6): 4 bytes total, value in bits 5-0 of first byte + 24 bits from remaining bytes
         * - Prefix 11 (bits 7-6): Invalid
         * Minimal encoding is required (throws IllegalArgumentException if not minimal)
         */
        fun mlsVarInt(): Int {
            val b0 = u8()
            val prefix = b0 ushr 6
            return when (prefix) {
                0 -> b0 and 0x3F
                1 -> {
                    if (!need(1)) throw IndexOutOfBoundsException()
                    val b1 = u8()
                    val v = ((b0 and 0x3F) shl 8) or b1
                    if (v < 64) throw IllegalArgumentException("Non-minimal varint")
                    v
                }

                2 -> {
                    if (!need(3)) throw IndexOutOfBoundsException()
                    val b1 = u8();
                    val b2 = u8();
                    val b3 = u8()
                    val v = ((b0 and 0x3F) shl 24) or (b1 shl 16) or (b2 shl 8) or b3
                    if (v < 16384) throw IllegalArgumentException("Non-minimal varint")
                    v
                }

                else -> throw IllegalArgumentException("Invalid MLS varint (prefix 11)")
            }
        }

        return try {
            // GroupInfo starts with GroupContext:
            // version(2) | cipher_suite(2) | group_id<V> | epoch(8) | ...
            if (!need(2)) return null
            u16() // version
            if (!need(2)) return null
            u16() // cipher suite

            // group_id<V>
            val groupIdLen = mlsVarInt()
            if (!need(groupIdLen)) return null
            p += groupIdLen

            // epoch (uint64, big-endian)
            if (!need(8)) return null
            u64()
        } catch (_: Exception) {
            null
        }
    }
}
