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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.api.authenticated.conversation.model.ConversationCodeInfo
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isAccessDenied
import com.wire.kalium.network.exceptions.isConversationHasNoCode
import com.wire.kalium.network.exceptions.isConversationNotFound
import com.wire.kalium.network.exceptions.isGuestLinkDisabled
import com.wire.kalium.network.exceptions.isNotTeamMember
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.flow.first

/**
 * Checks if the conversation invite code is valid
 * @param code the invite code
 * @param key the key of the conversation
 * @param domain optional domain of the conversation
 *
 */
class CheckConversationInviteCodeUseCase internal constructor(
    private val conversationGroupRepository: ConversationGroupRepository,
    private val conversationRepository: ConversationRepository,
    private val selfUserId: UserId
) {
    suspend operator fun invoke(code: String, key: String, domain: String?) =
        conversationGroupRepository.fetchLimitedInfoViaInviteCode(code, key).fold(
            { failure ->
                when (failure) {
                    is NetworkFailure.NoNetworkConnection,
                    is NetworkFailure.FederatedBackendFailure,
                    is NetworkFailure.FeatureNotSupported,
                    is NetworkFailure.ProxyError -> Result.Failure.Generic(failure)

                    is NetworkFailure.ServerMiscommunication -> handleServerMissCommunicationError(failure)
                }
            },
            { response ->
                handleSuccess(response, domain)
            }
        )

    private suspend fun handleSuccess(response: ConversationCodeInfo, domain: String?): Result.Success {
        val conversationId = ConversationId(
            response.nonQualifiedId,
            domain ?: selfUserId.domain
        )
        val isSelfMember = conversationRepository.observeIsUserMember(
            conversationId,
            selfUserId
        ).first().fold({ false }, { it })
        return Result.Success(response.name, conversationId, isSelfMember, response.hasPassword)
    }

    private fun handleServerMissCommunicationError(error: NetworkFailure.ServerMiscommunication): Result.Failure =
        when (error.kaliumException) {

            is KaliumException.InvalidRequestError -> {
                with(error.kaliumException) {
                    when {
                        // for more info about error codes see
                        // https://staging-nginz-https.zinfra.io/api/swagger-ui/#/default/get_conversations_join
                        errorResponse.code == HttpStatusCode.BadRequest.value -> Result.Failure.InvalidCodeOrKey
                        isNotTeamMember() -> Result.Failure.RequestingUserIsNotATeamMember
                        isAccessDenied() -> Result.Failure.AccessDenied
                        isConversationNotFound() || isConversationHasNoCode() -> Result.Failure.ConversationNotFound
                        isGuestLinkDisabled() -> Result.Failure.GuestLinksDisabled
                        else -> Result.Failure.Generic(error)
                    }
                }
            }

            else -> Result.Failure.Generic(error)
        }

    sealed interface Result {
        data class Success(
            val name: String?,
            val conversationId: ConversationId,
            val isSelfMember: Boolean,
            val isPasswordProtected: Boolean
        ) : Result

        sealed interface Failure : Result {
            data object InvalidCodeOrKey : Failure
            data object RequestingUserIsNotATeamMember : Failure
            data object AccessDenied : Failure
            data object ConversationNotFound : Failure
            data object GuestLinksDisabled : Failure
            data class Generic(val failure: CoreFailure) : Failure
        }
    }
}
