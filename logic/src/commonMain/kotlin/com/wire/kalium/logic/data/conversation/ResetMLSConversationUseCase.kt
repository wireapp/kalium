/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrFail
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse as NetworkConversationResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import com.wire.kalium.util.ConversationPersistenceApi
import io.mockative.Mockable
import kotlin.experimental.ExperimentalObjCRefinement
import kotlin.native.HiddenFromObjC

/**
 * Reset an MLS conversation which cannot be recovered by any other means.
 *
 * This use case will reset the MLS conversation by:
 *  - Calling /mls/reset-conversation API endpoint
 *  - Leaving the MLS group (wipeConversation)
 *  - Fetching the conversation to update group ID
 *  - Re-establishing the MLS group with the updated group ID and current members.
 */
@Mockable
public interface ResetMLSConversationUseCase {
    // TODO(refactor): transactionProvider should be always required to avoid deadlocks.
    //                 Callers of this function should get one if needed.
    @Deprecated("Transaction provider should be provided")
    public suspend operator fun invoke(
        conversationId: ConversationId
    ): ResetMLSConversationResult

    public suspend operator fun invoke(
        conversationId: ConversationId,
        transactionContext: CryptoTransactionContext
    ): ResetMLSConversationResult
}

@Suppress("ReturnCount", "LongParameterList")
internal class ResetMLSConversationUseCaseImpl(
    private val selfUserId: UserId,
    private val userConfig: UserConfigRepository,
    private val transactionProvider: CryptoTransactionProvider,
    private val conversationRepository: ConversationRepository,
    private val mlsConversationRepository: MLSConversationRepository,
    private val fetchConversationUseCase: FetchConversationUseCase,
    private val kaliumConfigs: KaliumConfigs,
) : ResetMLSConversationUseCase {

    private val logger by lazy { kaliumLogger.withTextTag("ResetMLSConversationUseCase") }

    override suspend fun invoke(conversationId: ConversationId): ResetMLSConversationResult {
        return transactionProvider.transaction("ResetMLSConversation") {
            invoke(conversationId, it).toEither()
        }.fold(
            { ResetMLSConversationResult.Failure(it) },
            { ResetMLSConversationResult.Success }
        )
    }

    override suspend operator fun invoke(
        conversationId: ConversationId,
        transactionContext: CryptoTransactionContext
    ): ResetMLSConversationResult {
        if (!kaliumConfigs.isMlsResetEnabled) {
            logger.i("MLS conversation reset feature is disabled via compile time flag.")
            return ResetMLSConversationResult.Success
        }

        if (!userConfig.isMlsConversationsResetEnabled()) {
            logger.i("MLS conversation reset feature is disabled.")
            return ResetMLSConversationResult.Success
        }

        if (selfUserId.domain != conversationId.domain) {
            logger.i("Federated conversation. Do not reset conversation for another backend.")
            return ResetMLSConversationResult.Success
        }

        return transactionContext.resetConversation(conversationId)
            .fold(
                { ResetMLSConversationResult.Failure(it) },
                { ResetMLSConversationResult.Success }
            )
    }

    private suspend fun CryptoTransactionContext.resetConversation(
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> {
        val mlsContext = mls ?: return errorNotMlsConversation()

        return getMlsProtocolInfo(conversationId)
            .flatMap { localProtocolInfo ->
                getLocalResetInfo(mlsContext, localProtocolInfo)
                    .flatMap { epoch ->
                        resetConversationWithRetryOnMlsStaleMessage(
                            conversationId = conversationId,
                            mlsContext = mlsContext,
                            localGroupId = localProtocolInfo.groupId,
                            epoch = epoch
                        )
                    }
            }
            .flatMap {
                fetchConversation(this, conversationId)
            }
            .flatMap { getMlsProtocolInfo(conversationId) }
            .map { updatedProtocolInfo ->
                val members = conversationRepository.getConversationMembers(conversationId).getOrFail {
                    logger.e("Failed to get members for conversation: $it")
                    return it.left()
                }
                mlsConversationRepository.establishMLSGroup(
                    mlsContext = mlsContext,
                    groupID = updatedProtocolInfo.groupId,
                    members = members,
                )
            }.map {}
    }

    private suspend fun fetchConversation(
        transaction: CryptoTransactionContext,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> = fetchConversationUseCase(transaction, conversationId, ConversationSyncReason.ConversationReset)

    private suspend fun getMlsProtocolInfo(conversationId: ConversationId): Either<CoreFailure, Conversation.ProtocolInfo.MLSCapable> {
        return conversationRepository.getConversationById(conversationId)
            .map {
                it.mlsProtocolInfo() ?: return errorNotMlsConversation()
            }
    }

    private suspend fun getLocalResetInfo(
        mlsContext: MlsCoreCryptoContext,
        protocolInfo: Conversation.ProtocolInfo.MLSCapable
    ): Either<CoreFailure, ULong> =
        wrapMLSRequest {
            mlsContext.conversationEpoch(protocolInfo.groupId.toCrypto())
        }.flatMapLeft {
            logger.e("Failed to get local epoch for reset conversation: $it.")
            if (it is MLSFailure.ConversationNotFound) {
                protocolInfo.epoch.right()
            } else {
                it.left()
            }
        }

    private suspend fun resetConversationWithRetryOnMlsStaleMessage(
        conversationId: ConversationId,
        mlsContext: MlsCoreCryptoContext,
        localGroupId: GroupID,
        epoch: ULong,
    ): Either<CoreFailure, Unit> =
        performReset(mlsContext, localGroupId, epoch)
            .flatMapLeft { failure ->
                if (!failure.isMlsStaleMessageConflict()) return@flatMapLeft failure.left()

                logger.w("MLS stale message during reset for ${conversationId.toLogString()}, refetching conversation and retrying.")
                fetchEpoch(conversationId)
                    .flatMap { epoch ->
                        performReset(mlsContext, localGroupId, epoch)
                    }
            }

    private suspend fun performReset(
        mlsContext: MlsCoreCryptoContext,
        localGroupId: GroupID,
        epoch: ULong
    ): Either<CoreFailure, Unit> =
        conversationRepository.resetMlsConversation(localGroupId, epoch)
            .onSuccess {
                // the result of the leave can be ignored
                mlsConversationRepository.leaveGroup(mlsContext, localGroupId)
            }

    @OptIn(ConversationPersistenceApi::class)
    private suspend fun fetchEpoch(conversationId: ConversationId): Either<CoreFailure, ULong> =
        conversationRepository.fetchConversation(conversationId)
            .flatMap { it.mlsResetInfo() }

    private fun errorNotMlsConversation() =
        CoreFailure.Unknown(IllegalStateException("Conversation is not an MLS conversation.")).left()
}

private fun Conversation.mlsProtocolInfo(): Conversation.ProtocolInfo.MLSCapable? {
    return when (this.protocol) {
        is Conversation.ProtocolInfo.MLSCapable -> this.protocol as Conversation.ProtocolInfo.MLSCapable
        else -> null
    }
}

private fun NetworkConversationResponse.mlsResetInfo(): Either<CoreFailure, ULong> = when (protocol) {
    ConvProtocol.MLS, ConvProtocol.MIXED -> {
        epoch?.right() ?: return CoreFailure.Unknown(
            IllegalStateException("Remote conversation is missing MLS epoch.")
        ).left()
    }

    ConvProtocol.PROTEUS -> CoreFailure.Unknown(
        IllegalStateException("Conversation is not an MLS conversation.")
    ).left()
}

private fun CoreFailure.isMlsStaleMessageConflict(): Boolean =
    this is NetworkFailure.ServerMiscommunication && run {
        val exception = kaliumException
        exception is KaliumException.InvalidRequestError && exception.isMlsStaleMessage()
    }

public sealed class ResetMLSConversationResult {
    /**
     * Indicates the reset MLS conversation operation completed successfully or no further action is needed.
     */
    public data object Success : ResetMLSConversationResult()

    /**
     * Indicates the reset MLS conversation operation failed.
     * @param error The error that occurred during the operation.
     */
    public data class Failure(val error: CoreFailure) : ResetMLSConversationResult()

    /**
     * Converts this result to an Either type for internal Kalium use or JVM/Android clients.
     * This function is hidden from iOS/Swift to maintain a clean Swift API.
     *
     * @return Either.Right(Unit) for Success, Either.Left(error) for Failure
     */
    @OptIn(ExperimentalObjCRefinement::class)
    @HiddenFromObjC
    @Suppress("konsist.kaliumLogicModuleShouldNotExposeEitherTypesInPublicAPI")
    public fun toEither(): Either<CoreFailure, Unit> =
        when (this) {
            is Success -> Either.Right(Unit)
            is Failure -> Either.Left(error)
        }
}
