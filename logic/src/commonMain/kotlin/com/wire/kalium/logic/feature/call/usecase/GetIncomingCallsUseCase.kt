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

import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.CALLING
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.data.call.CallRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.MutedConversationStatus
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.call.Call
import com.wire.kalium.logic.functional.nullableFold
import com.wire.kalium.logic.kaliumLogger
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach

/**
 * Use case that is responsible for observing the incoming calls.
 */
interface GetIncomingCallsUseCase {

    /**
     * That Flow emits everytime when the list is changed
     * @return a [Flow] of incoming calls List that should be shown to the user.
     */
    suspend operator fun invoke(): Flow<List<Call>>
}

internal class GetIncomingCallsUseCaseImpl internal constructor(
    private val callRepository: CallRepository,
    private val userRepository: UserRepository,
    private val conversationRepository: ConversationRepository,
) : GetIncomingCallsUseCase {

    private val logger
        get() = kaliumLogger.withFeatureId(CALLING)

    override suspend operator fun invoke(): Flow<List<Call>> {
        return observeIncomingCallsIfUserStatusAllows()
            .onlyCallsInNotMutedConversations()
            .distinctUntilChanged()
            .onEach { calls ->
                val callIds = calls.map { call -> call.conversationId }.joinToString()
                logger.d("$TAG; Emitting result calls: $callIds")
            }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun observeIncomingCallsIfUserStatusAllows(): Flow<List<Call>> =
        userRepository.observeSelfUser()
            .flatMapLatest {

                // if user is AWAY we don't show any IncomingCalls
                if (it.availabilityStatus == UserAvailabilityStatus.AWAY) {
                    logger.d("$TAG; Ignoring possible calls based user's status")
                    flowOf(listOf())
                } else callRepository.incomingCallsFlow()
                    .onEach { calls ->
                        val callIds = calls.map { call -> call.conversationId }.joinToString()
                        logger.d("$TAG; Found calls: $callIds")
                    }
                    .distinctUntilChanged { old, new ->
                        old.firstOrNull()?.conversationId == new.firstOrNull()?.conversationId
                    }
            }

    private fun Flow<List<Call>>.onlyCallsInNotMutedConversations(): Flow<List<Call>> =
        map { calls ->
            logger.d("$TAG; Filtering not muted conversations")

            // Filter calls if ConversationDetails for it were not found
            val allowedConversations = calls.mapNotNull { call ->
                conversationRepository.getConversationById(call.conversationId).nullableFold({ null }, { it })
            }.filter { conversationDetails ->
                // Don't display call if that Conversation is muted
                conversationDetails.mutedStatus != MutedConversationStatus.AllMuted
            }.map { it.id }

            calls.filter { allowedConversations.contains(it.conversationId) }
        }.onEach { calls ->
            val callIds = calls.map { call -> call.conversationId }.joinToString()
            logger.d("$TAG; Filtered calls: $callIds")
        }

    private fun List<ConversationId>.joinToString(): String = joinToString(prefix = "(", postfix = ")") {
        it.toString().obfuscateId()
    }

    private companion object {
        const val TAG = "IncomingCallsUseCase"
    }
}
