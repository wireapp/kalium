package com.wire.kalium.logic.feature.conversation

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.conversation.ConversationGroupRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.network.api.base.authenticated.conversation.model.LimitedConversationInfo
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.ProteusClientsChangedError
import com.wire.kalium.network.exceptions.SendMessageError
import com.wire.kalium.network.exceptions.isAccessDenied
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
                    is NetworkFailure.ProxyError -> Result.Failure.Generic(failure)

                    is NetworkFailure.ServerMiscommunication -> handleServerMissCommunicationError(failure)
                }
            },
            { response ->
                handleSuccess(response, domain)
            }
        )

    private suspend fun handleSuccess(response: LimitedConversationInfo, domain: String?): Result.Success {
        val conversationId = ConversationId(
            response.nonQualifiedConversationId,
            domain ?: selfUserId.domain
        )
        val isSelfMember = conversationRepository.observeIsUserMember(
            conversationId,
            selfUserId
        ).first().fold({ false }, { it })
        return Result.Success(response.name, conversationId, isSelfMember)
    }

    private fun handleServerMissCommunicationError(error: NetworkFailure.ServerMiscommunication): Result.Failure =
        when (error.kaliumException) {
            is APINotSupported,
            is ProteusClientsChangedError,
            is SendMessageError.MissingDeviceError,
            is KaliumException.GenericError,
            is KaliumException.RedirectError,
            is KaliumException.ServerError,
            is KaliumException.Unauthorized -> Result.Failure.Generic(error)

            is KaliumException.InvalidRequestError -> {
                with(error.kaliumException) {
                    when {
                        // for more info about error codes see
                        // https://staging-nginz-https.zinfra.io/api/swagger-ui/#/default/get_conversations_join
                        errorResponse.code == HttpStatusCode.BadRequest.value -> Result.Failure.InvalidCodeOrKey
                        isNotTeamMember() -> Result.Failure.RequestingUserIsNotATeamMember
                        isAccessDenied() -> Result.Failure.AccessDenied
                        isConversationNotFound() -> Result.Failure.ConversationNotFound
                        isGuestLinkDisabled() -> Result.Failure.GuestLinksDisabled
                        else -> Result.Failure.Generic(error)
                    }
                }
            }
        }

    sealed interface Result {
        data class Success(
            val name: String?,
            val conversationId: ConversationId,
            val isSelfMember: Boolean
        ) : Result

        sealed interface Failure : Result {
            object InvalidCodeOrKey : Failure
            object RequestingUserIsNotATeamMember : Failure
            object AccessDenied : Failure
            object ConversationNotFound : Failure
            object GuestLinksDisabled : Failure
            data class Generic(val failure: CoreFailure) : Failure
        }
    }
}
