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

package com.wire.kalium.logic.sync.receiver.conversation

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoTransactionContext
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.WelcomeBundle
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.client.wrapInMLSContext
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationIfUnknownUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.feature.conversation.mls.OneOnOneResolver
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesResult
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.util.createEventProcessingLogger
import io.mockative.Mockable
import kotlinx.coroutines.flow.first
import kotlin.io.encoding.Base64

@Mockable
internal interface MLSWelcomeEventHandler {
    suspend fun handle(transactionContext: CryptoTransactionContext, event: Event.Conversation.MLSWelcome): Either<CoreFailure, Unit>
}

@Suppress("LongParameterList", "LongMethod")
internal class MLSWelcomeEventHandlerImpl(
    private val conversationRepository: ConversationRepository,
    private val oneOnOneResolver: OneOnOneResolver,
    private val refillKeyPackages: RefillKeyPackagesUseCase,
    private val revocationListChecker: RevocationListChecker,
    private val joinExistingMLSConversation: JoinExistingMLSConversationUseCase,
    private val fetchConversationIfUnknown: FetchConversationIfUnknownUseCase,
    private val certificateRevocationListRepository: CertificateRevocationListRepository
) : MLSWelcomeEventHandler {
    override suspend fun handle(
        transactionContext: CryptoTransactionContext,
        event: Event.Conversation.MLSWelcome
    ): Either<CoreFailure, Unit> {
        val eventLogger = kaliumLogger.createEventProcessingLogger(event)
        val mlsContext = transactionContext.mls
        if (mlsContext == null) {
            kaliumLogger.w("$TAG: mls is disabled to handle welcome message")
            return Unit.right()
        }

        var welcomeOutcome = OUTCOME_PROCESSED_DIRECTLY

        return fetchConversationIfUnknown(transactionContext, event.conversationId)
            .flatMap {
                kaliumLogger.d("$TAG: Processing MLS welcome message")
                processWelcomeMessageWithRecovery(mlsContext, event.conversationId, event.message) { recovered ->
                    if (recovered) welcomeOutcome = OUTCOME_RECOVERED_AFTER_WIPE
                }
            }
            .flatMap { welcomeBundle ->
                welcomeBundle.crlNewDistributionPoints?.let {
                    kaliumLogger.d("$TAG: checking revocation list")
                    checkRevocationList(mlsContext, it)
                }
                kaliumLogger.d("$TAG: Marking conversation as established ${welcomeBundle.groupId.obfuscateId()}")
                markConversationAsEstablished(GroupID(welcomeBundle.groupId))
            }.flatMap {
                kaliumLogger.d("$TAG: Resolving conversation if one-on-one ${event.conversationId.toLogString()}")
                resolveConversationIfOneOnOne(transactionContext, event.conversationId)
            }
            .flatMapLeft { failure ->
                when (failure) {
                    is MLSFailure.OrphanWelcome -> {
                        if (isAlreadyEstablishedLocally(mlsContext, event.conversationId)) {
                            welcomeOutcome = OUTCOME_SKIPPED_ALREADY_ESTABLISHED
                            kaliumLogger.w(
                                "$TAG: OrphanWelcome for already established local MLS group; " +
                                    "treating as duplicate welcome and skipping external commit rejoin " +
                                    "[conversationId=${event.conversationId.toLogString()}]"
                            )
                            Unit.right()
                        } else {
                            kaliumLogger.w(
                                "$TAG: OrphanWelcome, discarding welcome and joining existing conversation by external commit " +
                                    "[conversationId=${event.conversationId.toLogString()}]"
                            )
                            joinExistingMLSConversation(
                                transactionContext = transactionContext,
                                conversationId = event.conversationId,
                            ).onSuccess {
                                welcomeOutcome = OUTCOME_RECOVERED_VIA_EXTERNAL_COMMIT
                            }.onFailure { joinFailure ->
                                welcomeOutcome = OUTCOME_EXTERNAL_COMMIT_REJOIN_FAILED
                                kaliumLogger.w(
                                    "$TAG: External-commit fallback for OrphanWelcome failed " +
                                        "[conversationId=${event.conversationId.toLogString()}, cause=$joinFailure]"
                                )
                            }.map { }
                        }
                    }

                    else -> {
                        welcomeOutcome = OUTCOME_FAILED
                        Either.Left(failure)
                    }
                }
            }
            .onSuccess {
                transactionContext.wrapInMLSContext { mlsContext ->
                    val didSucceedRefillingKeyPackages = when (val refillResult = refillKeyPackages(mlsContext)) {
                        is RefillKeyPackagesResult.Failure -> {
                            val exception = (refillResult.failure as? CoreFailure.Unknown)?.rootCause
                            kaliumLogger.w("$TAG: Failed to refill key packages; Failure: ${refillResult.failure}", exception)
                            false
                        }

                        RefillKeyPackagesResult.Success -> {
                            true
                        }
                    }
                    eventLogger.logSuccess(
                        "info" to "Established mls conversation from welcome message",
                        "welcomeOutcome" to welcomeOutcome,
                        "didSucceedRefillingKeypackages" to didSucceedRefillingKeyPackages
                    )
                    Unit.right()
                }
            }
            .onFailure { eventLogger.logFailure(it, "welcomeOutcome" to welcomeOutcome) }
    }

    private suspend fun isAlreadyEstablishedLocally(
        mlsContext: MlsCoreCryptoContext,
        conversationId: ConversationId
    ): Boolean = conversationRepository.getConversationProtocolInfo(conversationId)
        .map { it as? Conversation.ProtocolInfo.MLSCapable }
        .flatMap { mlsProtocol ->
            when {
                mlsProtocol == null -> Either.Right(false)
                mlsProtocol.groupState != Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED -> Either.Right(false)
                else -> wrapMLSRequest { mlsContext.conversationExists(mlsProtocol.groupId.toCrypto()) }
            }
        }
        .fold({ false }, { it })

    private suspend fun processWelcomeMessageWithRecovery(
        mlsContext: MlsCoreCryptoContext,
        conversationId: ConversationId,
        base64Message: String,
        onRecoveredAfterWipe: (recovered: Boolean) -> Unit
    ): Either<CoreFailure, WelcomeBundle> {
        return wrapMLSRequest { mlsContext.processWelcomeMessage(Base64.decode(base64Message)) }
            .flatMapLeft { failure ->
                if (failure is MLSFailure.ConversationAlreadyExists) {
                    kaliumLogger.w(
                        "$TAG: Welcome processing hit ConversationAlreadyExists, wiping local group and retrying " +
                            "[conversationId=${conversationId.toLogString()}]"
                    )
                    wipeLocalConversation(mlsContext, conversationId)
                        .onFailure { wipeFailure ->
                            kaliumLogger.w(
                                "$TAG: Failed to wipe local group before welcome retry " +
                                    "[conversationId=${conversationId.toLogString()}, cause=$wipeFailure]"
                            )
                        }
                        .flatMap {
                            wrapMLSRequest {
                                mlsContext.processWelcomeMessage(Base64.decode(base64Message))
                            }
                                .onSuccess {
                                    onRecoveredAfterWipe(true)
                                    kaliumLogger.i(
                                        "$TAG: Welcome recovered after wiping local group " +
                                            "[conversationId=${conversationId.toLogString()}]"
                                    )
                                }
                                .onFailure { retryFailure ->
                                    kaliumLogger.w(
                                        "$TAG: Welcome retry after wipe failed; local group has been wiped " +
                                            "[conversationId=${conversationId.toLogString()}, cause=$retryFailure]"
                                    )
                                }
                        }
                } else {
                    kaliumLogger.w(
                        "$TAG: Welcome processing failed " +
                            "[conversationId=${conversationId.toLogString()}, cause=$failure]"
                    )
                    Either.Left(failure)
                }
            }
    }

    private suspend fun wipeLocalConversation(
        mlsContext: MlsCoreCryptoContext,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> =
        conversationRepository.getConversationProtocolInfo(conversationId)
            .flatMap { protocol ->
                if (protocol is Conversation.ProtocolInfo.MLSCapable) {
                    wrapMLSRequest { mlsContext.wipeConversation(protocol.groupId.toCrypto()) }
                } else {
                    Either.Left(CoreFailure.Unknown(IllegalStateException("Conversation is not MLS capable")))
                }
            }

    private suspend fun markConversationAsEstablished(groupID: GroupID): Either<CoreFailure, Unit> =
        conversationRepository.updateConversationGroupState(groupID, Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED)

    private suspend fun checkRevocationList(mlsContext: MlsCoreCryptoContext, crlNewDistributionPoints: List<String>) {
        crlNewDistributionPoints.forEach { url ->
            revocationListChecker.check(mlsContext, url).map { newExpiration ->
                newExpiration?.let {
                    certificateRevocationListRepository.addOrUpdateCRL(url, it)
                }
            }
        }
    }

    private suspend fun resolveConversationIfOneOnOne(
        transactionContext: CryptoTransactionContext,
        conversationId: ConversationId
    ): Either<CoreFailure, Unit> =
        conversationRepository.observeConversationDetailsById(conversationId)
            .first()
            .flatMap {
                if (it is ConversationDetails.OneOne) {
                    oneOnOneResolver.resolveOneOnOneConversationWithUser(
                        transactionContext = transactionContext,
                        user = it.otherUser,
                        invalidateCurrentKnownProtocols = true
                    ).map { Unit }
                } else {
                    Either.Right(Unit)
                }
            }

    companion object {
        private const val TAG = "[MLSWelcomeEventHandler]"

        // Values for the structured "welcomeOutcome" field on the event log. Kept short and
        // stable so they can be used as filters/aggregations in log-analysis tooling.
        private const val OUTCOME_PROCESSED_DIRECTLY = "processed-directly"
        private const val OUTCOME_RECOVERED_AFTER_WIPE = "recovered-after-wipe"
        private const val OUTCOME_RECOVERED_VIA_EXTERNAL_COMMIT = "recovered-via-external-commit"
        private const val OUTCOME_SKIPPED_ALREADY_ESTABLISHED = "skipped-already-established"
        private const val OUTCOME_EXTERNAL_COMMIT_REJOIN_FAILED = "external-commit-rejoin-failed"
        private const val OUTCOME_FAILED = "failed"
    }

}
