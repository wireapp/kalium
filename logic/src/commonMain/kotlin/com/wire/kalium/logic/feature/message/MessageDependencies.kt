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

package com.wire.kalium.logic.feature.message

import com.wire.kalium.cells.domain.MessageAttachmentDraftRepository
import com.wire.kalium.cells.domain.usecase.DeleteMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.GetMessageAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.PublishAttachmentsUseCase
import com.wire.kalium.cells.domain.usecase.RemoveAttachmentDraftsUseCase
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.LegalHoldStatusMapper
import com.wire.kalium.logic.data.message.CompositeMessageRepository
import com.wire.kalium.logic.data.message.MessageMetadataRepository
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.data.message.draft.MessageDraftRepository
import com.wire.kalium.logic.data.message.reaction.ReactionRepository
import com.wire.kalium.logic.data.message.receipt.ReceiptRepository
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandler
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.sync.IncrementalSyncRepository
import com.wire.kalium.logic.di.UserSessionScopedFactory
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.selfDeletingMessages.ObserveSelfDeletionTimerSettingsForConversationUseCase
import com.wire.kalium.logic.feature.user.ObserveFileSharingStatusUseCase
import com.wire.kalium.logic.featureFlags.KaliumConfigs
import com.wire.kalium.logic.sync.receiver.asset.AudioNormalizedLoudnessScheduler
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler

internal fun interface MessageDependencyFactory<T> {
    operator fun invoke(): T
}

/** Feature-specific runtime inputs that have not yet moved into graph-native bindings. */
internal interface MessageDependencies {
    val messageDraftRepositoryFactory: UserSessionScopedFactory<MessageDraftRepository>
    val attachmentsRepositoryFactory: MessageDependencyFactory<MessageAttachmentDraftRepository>
    val clientRepositoryFactory: UserSessionScopedFactory<ClientRepository>
    val clientRemoteRepositoryFactory: UserSessionScopedFactory<ClientRemoteRepository>
    val preKeyRepositoryFactory: UserSessionScopedFactory<PreKeyRepository>
    val reactionRepositoryFactory: UserSessionScopedFactory<ReactionRepository>
    val receiptRepositoryFactory: UserSessionScopedFactory<ReceiptRepository>
    val messageSendingScheduler: MessageSendingScheduler
    val audioNormalizedLoudnessScheduler: AudioNormalizedLoudnessScheduler
    val incrementalSyncRepositoryFactory: UserSessionScopedFactory<IncrementalSyncRepository>
    val protoContentMapper: ProtoContentMapper
    val observeSelfDeletingMessages: ObserveSelfDeletionTimerSettingsForConversationUseCase
    val messageMetadataRepositoryFactory: UserSessionScopedFactory<MessageMetadataRepository>
    val staleEpochVerifier: StaleEpochVerifier
    val legalHoldHandler: LegalHoldHandler
    val observeFileSharingStatusUseCase: ObserveFileSharingStatusUseCase
    val getMessageAttachmentsFactory: MessageDependencyFactory<GetMessageAttachmentsUseCase>
    val publishAttachmentsFactory: MessageDependencyFactory<PublishAttachmentsUseCase>
    val removeAttachmentDraftsFactory: MessageDependencyFactory<RemoveAttachmentDraftsUseCase>
    val deleteMessageAttachmentsFactory: MessageDependencyFactory<DeleteMessageAttachmentsUseCase>
    val fetchConversationUseCase: FetchConversationUseCase
    val compositeMessageRepositoryFactory: UserSessionScopedFactory<CompositeMessageRepository>
    val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder
    val mlsMissingUsersMessageRejectionHandler: MLSMissingUsersMessageRejectionHandler
    val kaliumConfigs: KaliumConfigs
    val legalHoldStatusMapper: LegalHoldStatusMapper
}
