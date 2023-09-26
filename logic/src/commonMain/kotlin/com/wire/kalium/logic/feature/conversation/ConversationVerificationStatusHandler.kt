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
package com.wire.kalium.logic.feature.conversation

import com.benasher44.uuid.uuid4
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.Conversation.VerificationStatus
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.getOrElse
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onlyRight
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emptyFlow
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.onCompletion
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.flow.shareIn
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

/**
 * Observes Conversation Verification status.
 * Notify user (by adding System message in conversation) if needed about changes.
 * @param conversationId [ConversationId]
 * @param shouldShareFlow [Boolean] flag if the resulted [Flow] should be shared in IO [CoroutineScope], or not.
 * Basically needed only for unit-tests cause it fails if flow is shared (Flow never completes).
 */
// internal interface ConversationVerificationStatusHandler {
//     suspend operator fun invoke(conversationId: ConversationId, shouldShareFlow: Boolean = true): Flow<Unit>
// }
//
// internal class ConversationVerificationStatusHandlerImpl(
//     private val conversationRepository: ConversationRepository,
//     private val persistMessage: PersistMessageUseCase,
//     private val mlsConversationRepository: MLSConversationRepository,
//     private val selfUserId: UserId,
//     kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
// ) : ConversationVerificationStatusHandler {
//     private val dispatcher = kaliumDispatcher.io
//
//     private val lock = Mutex()
//     private val observeFlows = hashMapOf<ConversationId, Flow<Unit>>()
//
//     override suspend fun invoke(conversationId: ConversationId, shouldShareFlow: Boolean): Flow<Unit> = withContext(dispatcher) {
//         lock.withLock {
//             val currentFlow = observeFlows[conversationId]
//             if (currentFlow != null) return@withContext currentFlow
//         }
//
//         val flow = conversationRepository.getConversationProtocolInfo(conversationId)
//             .map { protocol ->
//                 fetchVerificationStatusFlow(conversationId, protocol)
//                     .mapLatest { status -> notifyUserIfNeeded(conversationId, protocol, status) }
//                     .onCompletion { observeFlows.remove(conversationId) }
//             }
//             .getOrElse(emptyFlow())
//             .apply {
//                 if (shouldShareFlow) shareIn(this@withContext, SharingStarted.WhileSubscribed(1))
//             }
//
//         lock.withLock { observeFlows[conversationId] = flow }
//
//         flow
//     }
//
//     /**
//      * Get conversation verification status and save it locally.
//      */
//     private suspend fun fetchVerificationStatusFlow(
//         conversationId: ConversationId,
//         protocol: Conversation.ProtocolInfo
//     ): Flow<VerificationStatus> =
//         observerVerificationStatus(protocol, conversationId)
//             .distinctUntilChanged()
//             .onlyRight()
//             .mapLatest { newStatus ->
//                 val currentStatus = conversationRepository.getConversationVerificationStatus(conversationId)
//                     .getOrElse(VerificationStatus.NOT_VERIFIED)
//
//                 // Current CoreCrypto implementation returns only a boolean flag "if conversation is verified or not".
//                 // So we need to calculate if status was degraded on our side by comparing it to the previous status.
//                 if (newStatus == currentStatus) {
//                     currentStatus
//                 } else if (newStatus == VerificationStatus.NOT_VERIFIED && currentStatus == VerificationStatus.VERIFIED) {
//                     conversationRepository.updateVerificationStatus(VerificationStatus.DEGRADED, conversationId)
//                     VerificationStatus.DEGRADED
//                 } else if (newStatus == VerificationStatus.NOT_VERIFIED && currentStatus == VerificationStatus.DEGRADED) {
//                     currentStatus
//                 } else {
//                     conversationRepository.updateVerificationStatus(newStatus, conversationId)
//                     newStatus
//                 }
//             }
//
//     private suspend fun observerVerificationStatus(protocol: Conversation.ProtocolInfo, conversationId: ConversationId) =
//         if (protocol is Conversation.ProtocolInfo.MLS) {
//             observeMLSVerificationStatus(protocol)
//         } else {
//             observeProteusVerificationStatus(conversationId)
//         }
//
//     private suspend fun observeMLSVerificationStatus(
//         protocol: Conversation.ProtocolInfo.MLS
//     ): Flow<Either<CoreFailure, VerificationStatus>> =
//         mlsConversationRepository.observeEpochChanges()
//             .filter { it == protocol.groupId }
//             .onStart { emit(protocol.groupId) }
//             .mapLatest { mlsConversationRepository.getConversationVerificationStatus(protocol.groupId) }
//
//     private suspend fun observeProteusVerificationStatus(
//         conversationId: ConversationId
//     ): Flow<Either<CoreFailure, VerificationStatus>> {
//         // TODO needs to be handled for Proteus conversation that is not implemented yet
//         return flowOf(Either.Right(VerificationStatus.NOT_VERIFIED))
//     }
//
//     /**
//      * Add a SystemMessage into a conversation, to inform user when the conversation verification status becomes DEGRADED.
//      */
//     private suspend fun notifyUserIfNeeded(
//         conversationId: ConversationId,
//         protocol: Conversation.ProtocolInfo,
//         updatedStatus: VerificationStatus
//     ) {
//         if (shouldNotifyUser(conversationId, protocol, updatedStatus)) {
//             val content = when (protocol) {
//                 is Conversation.ProtocolInfo.MLS -> MessageContent.ConversationDegradedMLS
//                 is Conversation.ProtocolInfo.Proteus -> MessageContent.ConversationDegradedProteus
//             }
//             val conversationDegradedMessage = Message.System(
//                 id = uuid4().toString(),
//                 content = content,
//                 conversationId = conversationId,
//                 date = DateTimeUtil.currentIsoDateTimeString(),
//                 senderUserId = selfUserId,
//                 status = Message.Status.Sent,
//                 visibility = Message.Visibility.VISIBLE,
//                 expirationData = null
//             )
//
//             persistMessage(conversationDegradedMessage)
//                 .flatMap { conversationRepository.setInformedAboutDegradedMLSVerificationFlag(conversationId, true) }
//         } else if (updatedStatus != VerificationStatus.DEGRADED) {
//             conversationRepository.setInformedAboutDegradedMLSVerificationFlag(conversationId, false)
//         }
//     }
//
//     private suspend fun shouldNotifyUser(
//         conversationId: ConversationId,
//         protocol: Conversation.ProtocolInfo,
//         status: VerificationStatus
//     ): Boolean =
//         if (status == VerificationStatus.DEGRADED) {
//             if (protocol is Conversation.ProtocolInfo.MLS) {
//                 !conversationRepository.isInformedAboutDegradedMLSVerification(conversationId).getOrElse(true)
//             } else {
//                 // TODO check flag for Proteus after implementing it.
//                 false
//             }
//         } else {
//             false
//         }
// }
