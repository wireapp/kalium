/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logic.feature.user.IsMLSEnabledUseCase
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/**
 * Observes the "Notes to self" conversation, which is the MLS self conversation.
 *
 * Emits the conversation details only while Notes is available to the user, which means:
 * - the feature is enabled in `KaliumConfigs.notesToSelfEnabled`;
 * - the user chose to show Notes (see `ObserveShowNotesEnabledUseCase`);
 * - MLS is enabled for the user;
 * - the MLS self conversation exists locally and its MLS group is established, so messages can be sent.
 *
 * Emits `null` in every other case, so clients can hide the Notes entry.
 */
public interface ObserveNotesConversationUseCase {
    public operator fun invoke(): Flow<ConversationDetails.Self?>
}

internal class ObserveNotesConversationUseCaseImpl(
    private val notesToSelfEnabled: Boolean,
    private val userPropertyRepository: UserPropertyRepository,
    private val conversationRepository: ConversationRepository,
    private val isMLSEnabled: IsMLSEnabledUseCase,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl,
) : ObserveNotesConversationUseCase {

    @OptIn(ExperimentalCoroutinesApi::class)
    override fun invoke(): Flow<ConversationDetails.Self?> =
        if (!notesToSelfEnabled) {
            flowOf(null)
        } else {
            userPropertyRepository.observeShowNotesStatus()
                .map { result -> result.fold({ false }, { it }) }
                .distinctUntilChanged()
                .flatMapLatest { showNotes -> if (showNotes) observeEstablishedMlsSelfConversation() else flowOf(null) }
                // MLS can't be observed, so it's checked on every emission. Establishing the self group already
                // depends on MLS being enabled, so this only filters out the case of MLS being disabled afterwards.
                .map { details -> details?.takeIf { isMLSEnabled() } }
                .distinctUntilChanged()
                .flowOn(dispatcher.io)
        }

    @OptIn(ExperimentalCoroutinesApi::class)
    private fun observeEstablishedMlsSelfConversation(): Flow<ConversationDetails.Self?> =
        conversationRepository.observeMLSSelfConversationId().flatMapLatest { conversationId ->
            if (conversationId == null) {
                flowOf(null)
            } else {
                conversationRepository.observeConversationDetailsById(conversationId).map { result ->
                    (result.getOrNull() as? ConversationDetails.Self)?.takeIf { it.isMlsGroupEstablished() }
                }
            }
        }

    private fun ConversationDetails.Self.isMlsGroupEstablished(): Boolean =
        (conversation.protocol as? Conversation.ProtocolInfo.MLS)?.groupState ==
                Conversation.ProtocolInfo.MLSCapable.GroupState.ESTABLISHED
}
