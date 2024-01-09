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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * @return the [Boolean] for if the user's team has conference calling enabled in its plan.
 */
interface IsEligibleToStartCallUseCase {
    suspend operator fun invoke(conversationId: ConversationId, conversationType: Conversation.Type): ConferenceCallingResult
}

internal class IsEligibleToStartCallUseCaseImpl(
    private val userConfigRepository: UserConfigRepository,
    private val callRepository: CallRepository,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : IsEligibleToStartCallUseCase {

    override suspend fun invoke(conversationId: ConversationId, conversationType: Conversation.Type): ConferenceCallingResult =
        withContext(dispatcher.io) {
            val establishedCallConversationId = callRepository.establishedCallConversationId()

            val canStartCall = (conversationType == Conversation.Type.ONE_ON_ONE ||
                    (conversationType == Conversation.Type.GROUP && isConferenceCallingEnabled()))

            establishedCallConversationId?.let {
                callIsEstablished(it, conversationId, canStartCall)
            } ?: run {
                if (canStartCall) ConferenceCallingResult.Enabled else ConferenceCallingResult.Disabled.Unavailable
            }
        }

    private fun callIsEstablished(
        establishedCallConversationId: ConversationId,
        conversationId: ConversationId,
        canStartCall: Boolean
    ): ConferenceCallingResult = when {
        establishedCallConversationId == conversationId -> ConferenceCallingResult.Disabled.Established
        canStartCall -> ConferenceCallingResult.Disabled.OngoingCall
        !canStartCall -> ConferenceCallingResult.Disabled.Unavailable
        else -> ConferenceCallingResult.Disabled.Unavailable
    }

    private fun isConferenceCallingEnabled(): Boolean = userConfigRepository
        .isConferenceCallingEnabled()
        .fold({
            DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE
        }, {
            it
        })

    private companion object {
        const val DEFAULT_CONFERENCE_CALLING_ENABLED_VALUE = false
    }
}

sealed interface ConferenceCallingResult {
    /**
     * Eligible to start conference/one on one calls
     */
    data object Enabled : ConferenceCallingResult

    /**
     * Not eligible to start a conference/one on one calls
     */
    sealed interface Disabled : ConferenceCallingResult {
        /**
         * Established call is ongoing
         */
        data object Established : Disabled

        /**
         * There is an Ongoing Call
         */
        data object OngoingCall : Disabled

        /**
         * Conference Calling is unavailable due to team not having the paid plan
         */
        data object Unavailable : Disabled
    }
}
