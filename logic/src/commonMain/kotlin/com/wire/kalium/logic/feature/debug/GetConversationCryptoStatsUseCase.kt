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
package com.wire.kalium.logic.feature.debug

import com.wire.kalium.common.error.CommonizedMLSException
import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.commonizeMLSException
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.fold
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.ConversationWithOtherUserName
import com.wire.kalium.logic.data.id.ConversationId
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first

/**
 * Retrieves crypto statistics for all conversations.
 * Returns counts of Proteus, MLS, and Mixed conversations,
 * and checks which MLS/Mixed conversations are established in core crypto.
 */
public interface GetConversationCryptoStatsUseCase {
    public suspend operator fun invoke(): GetConversationCryptoStatsResult
}

internal class GetConversationCryptoStatsUseCaseImpl(
    private val conversationRepository: ConversationRepository,
    private val transactionProvider: CryptoTransactionProvider,
) : GetConversationCryptoStatsUseCase {

    override suspend fun invoke(): GetConversationCryptoStatsResult =
        conversationRepository.getConversationListWithOtherUserName().fold(
            fnL = { GetConversationCryptoStatsResult.Failure(it) },
            fnR = { conversationFlow ->
                val conversationsWithOtherUserName = conversationFlow.first()
                val mlsCapable = conversationsWithOtherUserName.mapNotNull { conversationWithOtherUserName ->
                    (conversationWithOtherUserName.conversation.protocol as? Conversation.ProtocolInfo.MLSCapable)
                        ?.let { conversationWithOtherUserName.conversation to it }
                }

                val ccEpochs = fetchCCEpochs(mlsCapable)

                val details = conversationsWithOtherUserName.map { conversationWithOtherUserName ->
                    val conversation = conversationWithOtherUserName.conversation
                    val mlsCapableInfo = conversation.protocol as? Conversation.ProtocolInfo.MLSCapable
                    val ccResult = mlsCapableInfo?.groupId?.value?.let { ccEpochs[it] }
                    ConversationCryptoDetail(
                        conversationId = conversation.id,
                        conversationName = conversationWithOtherUserName.displayName(),
                        protocolType = conversation.protocol.toProtocolType(),
                        groupId = mlsCapableInfo?.groupId?.value,
                        dbGroupState = mlsCapableInfo?.groupState?.toDetailGroupState(),
                        dbEpoch = mlsCapableInfo?.epoch,
                        ccEpoch = ccResult?.epoch,
                        ccLookupFailed = ccResult?.lookupFailed ?: false,
                        selfIsMember = conversationWithOtherUserName.selfIsMember,
                    )
                }

                GetConversationCryptoStatsResult.Success(
                    ConversationCryptoStats(
                        totalConversations = conversationsWithOtherUserName.size,
                        proteusCount = details.count { it.protocolType == ConversationCryptoProtocolType.PROTEUS },
                        mlsCount = details.count { it.protocolType == ConversationCryptoProtocolType.MLS },
                        mixedCount = details.count { it.protocolType == ConversationCryptoProtocolType.MIXED },
                        mlsDriftCount = details.count { it.isDrift(ConversationCryptoProtocolType.MLS) },
                        mixedDriftCount = details.count { it.isDrift(ConversationCryptoProtocolType.MIXED) },
                        mlsLeftCount = details.count { it.isLeft(ConversationCryptoProtocolType.MLS) },
                        mixedLeftCount = details.count { it.isLeft(ConversationCryptoProtocolType.MIXED) },
                        ccLookupFailedCount = details.count { it.ccLookupFailed },
                        conversationDetails = details
                    )
                )
            }
        )

    private suspend fun fetchCCEpochs(
        mlsCapableConversations: List<Pair<Conversation, Conversation.ProtocolInfo.MLSCapable>>
    ): Map<String, CCEpochResult> {
        if (mlsCapableConversations.isEmpty()) return emptyMap()

        return transactionProvider.mlsTransaction("GetConversationCryptoStats") { mlsContext ->
            val result = mlsCapableConversations.associate { (_, protocolInfo) ->
                protocolInfo.groupId.value to runCatching {
                    mlsContext.conversationEpoch(protocolInfo.groupId.value)
                }.fold(
                    onSuccess = { CCEpochResult(epoch = it, lookupFailed = false) },
                    onFailure = { it.toCCEpochResult() },
                )
            }
            Either.Right(result)
        }.fold(
            fnL = {
                mlsCapableConversations.associate { (_, protocolInfo) ->
                    protocolInfo.groupId.value to CCEpochResult.lookupFailed()
                }
            },
            fnR = { it }
        )
    }

    private data class CCEpochResult(val epoch: ULong?, val lookupFailed: Boolean) {
        companion object {
            fun lookupFailed(): CCEpochResult = CCEpochResult(epoch = null, lookupFailed = true)
        }
    }

    private fun Throwable.toCCEpochResult(): CCEpochResult {
        if (this is CancellationException) throw this
        val failure = when (this) {
            is CommonizedMLSException -> failure
            is Exception -> commonizeMLSException(this).failure
            else -> commonizeMLSException(Exception(this)).failure
        }
        return when (failure) {
            MLSFailure.ConversationNotFound -> CCEpochResult(epoch = null, lookupFailed = false)
            else -> CCEpochResult.lookupFailed()
        }
    }

    private fun ConversationWithOtherUserName.displayName(): String? = when {
        !conversation.name.isNullOrBlank() -> conversation.name
        !otherUserName.isNullOrBlank() -> otherUserName
        conversation.type == Conversation.Type.Self -> SELF_CONVERSATION_NAME
        else -> null
    }

    private fun ConversationCryptoDetail.isDrift(protocolType: ConversationCryptoProtocolType): Boolean =
        this.protocolType == protocolType &&
            selfIsMember &&
            dbGroupState == DetailGroupState.ESTABLISHED &&
            ccEpoch == null &&
            !ccLookupFailed

    private fun ConversationCryptoDetail.isLeft(protocolType: ConversationCryptoProtocolType): Boolean =
        this.protocolType == protocolType && !selfIsMember

    private fun Conversation.ProtocolInfo.toProtocolType(): ConversationCryptoProtocolType = when (this) {
        is Conversation.ProtocolInfo.Proteus -> ConversationCryptoProtocolType.PROTEUS
        is Conversation.ProtocolInfo.MLS -> ConversationCryptoProtocolType.MLS
        is Conversation.ProtocolInfo.Mixed -> ConversationCryptoProtocolType.MIXED
    }
}

public data class ConversationCryptoStats(
    val totalConversations: Int,
    val proteusCount: Int,
    val mlsCount: Int,
    val mixedCount: Int,
    val mlsDriftCount: Int,
    val mixedDriftCount: Int,
    val mlsLeftCount: Int,
    val mixedLeftCount: Int,
    val ccLookupFailedCount: Int,
    val conversationDetails: List<ConversationCryptoDetail>,
)

public data class ConversationCryptoDetail(
    val conversationId: ConversationId,
    val conversationName: String?,
    val protocolType: ConversationCryptoProtocolType,
    val groupId: String?,
    val dbGroupState: DetailGroupState?,
    val dbEpoch: ULong?,
    val ccEpoch: ULong?,
    val ccLookupFailed: Boolean,
    val selfIsMember: Boolean,
)

public enum class DetailGroupState {
    PENDING_CREATION,
    PENDING_JOIN,
    PENDING_WELCOME_MESSAGE,
    ESTABLISHED,
    PENDING_AFTER_RESET,
}

private fun Conversation.ProtocolInfo.MLSCapable.GroupState.toDetailGroupState(): DetailGroupState = when (this) {
    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_CREATION -> DetailGroupState.PENDING_CREATION
    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_JOIN -> DetailGroupState.PENDING_JOIN
    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_WELCOME_MESSAGE -> DetailGroupState.PENDING_WELCOME_MESSAGE
    Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED -> DetailGroupState.ESTABLISHED
    Conversation.ProtocolInfo.MLSCapable.GroupState.PENDING_AFTER_RESET -> DetailGroupState.PENDING_AFTER_RESET
}

public enum class ConversationCryptoProtocolType {
    PROTEUS, MLS, MIXED
}

public sealed class GetConversationCryptoStatsResult {
    public data class Success(val stats: ConversationCryptoStats) : GetConversationCryptoStatsResult()
    public data class Failure(val coreFailure: CoreFailure) : GetConversationCryptoStatsResult()
}

private const val SELF_CONVERSATION_NAME = "self"
