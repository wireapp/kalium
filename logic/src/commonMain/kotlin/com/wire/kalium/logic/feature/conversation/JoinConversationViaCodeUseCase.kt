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
@file:Suppress("konsist.useCasesShouldNotAccessNetworkLayerDirectly")

package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.common.functional.fold
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberAddedResponse
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isWrongConversationPassword

/**
 * Use case for joining a conversation via a code invite code.
 * the param can be obtained from the deep link
 * @param code The code of the conversation to join.
 * @param key The key of the conversation to join.
 * @param domain optional domain of the conversation to join.
 */
class JoinConversationViaCodeUseCase internal constructor(
    private val conversionsGroupRepository: ConversationGroupRepository,
    private val selfUserId: UserId
) {
    suspend operator fun invoke(
        code: String,
        key: String,
        domain: String?,
        password: String?
    ): Result =
    // the swagger docs say that the URI is optional, without explaining what uri need to be used
        // nevertheless the request works fine without the uri, so we are not going to use it
        conversionsGroupRepository.joinViaInviteCode(
            code,
            key,
            null,
            password
        )
            .fold({
                onError(it)
            }, { response ->
                when (response) {
                    is ConversationMemberAddedResponse.Changed -> onConversationChanged(response)
                    ConversationMemberAddedResponse.Unchanged -> onConversationUnChanged(code, key, domain)
                }
            })

    private fun onError(networkFailure: NetworkFailure): Result.Failure = when (networkFailure) {
        is NetworkFailure.ServerMiscommunication -> {
            if (networkFailure.kaliumException is KaliumException.InvalidRequestError &&
                (networkFailure.kaliumException as KaliumException.InvalidRequestError).isWrongConversationPassword()
            ) {
                Result.Failure.IncorrectPassword
            } else {
                Result.Failure.Generic(networkFailure)
            }
        }

        else -> Result.Failure.Generic(networkFailure)
    }

    private fun onConversationChanged(response: ConversationMemberAddedResponse.Changed): Result =
        Result.Success.Changed(response.event.qualifiedConversation.toModel())

    private suspend fun onConversationUnChanged(
        code: String,
        key: String,
        domain: String?
    ): Result =
        conversionsGroupRepository.fetchLimitedInfoViaInviteCode(code, key)
            .fold({
                Result.Success.Unchanged(null)
            }, {
                ConversationId(it.nonQualifiedId, domain ?: selfUserId.domain).let { conversationId ->
                    Result.Success.Unchanged(conversationId)
                }
            })

    sealed interface Result {
        sealed interface Success : Result {
            val conversationId: ConversationId?

            data class Changed(
                override val conversationId: ConversationId,
            ) : Success

            data class Unchanged(
                override val conversationId: ConversationId?,
            ) : Success
        }

        sealed interface Failure : Result {

            data object IncorrectPassword : Failure
            data class Generic(
                val failure: NetworkFailure
            ) : Failure
        }
    }
}
