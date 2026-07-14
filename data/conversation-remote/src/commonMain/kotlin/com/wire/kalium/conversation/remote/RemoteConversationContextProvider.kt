@file:OptIn(com.wire.kalium.conversation.ExperimentalConversationApi::class)
@file:Suppress("TooGenericExceptionCaught")

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

package com.wire.kalium.conversation.remote

import com.wire.kalium.conversation.CallClient
import com.wire.kalium.conversation.CallConversationContext
import com.wire.kalium.conversation.CallConversationProtocol
import com.wire.kalium.conversation.CallConversationType
import com.wire.kalium.conversation.CallMember
import com.wire.kalium.conversation.ConversationContextFailure
import com.wire.kalium.conversation.ConversationContextProvider
import com.wire.kalium.conversation.ConversationContextResult
import com.wire.kalium.conversation.ExperimentalConversationApi
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.network.api.authenticated.conversation.ConvProtocol
import com.wire.kalium.network.api.authenticated.conversation.ConversationMemberDTO
import com.wire.kalium.network.api.authenticated.conversation.ConversationResponse
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.api.model.QualifiedID as NetworkQualifiedId
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isAccessDenied
import com.wire.kalium.network.exceptions.isConversationNotFound
import com.wire.kalium.network.exceptions.isNotFound
import com.wire.kalium.network.utils.NetworkResponse
import kotlin.coroutines.cancellation.CancellationException

/** Fetches only the conversation/member/client data required for a live call. */
@ExperimentalConversationApi
public class RemoteConversationContextProvider(
    private val conversationApi: ConversationApi,
    private val clientApi: ClientApi,
    private val selfUserTeamId: String?,
) : ConversationContextProvider {
    override suspend fun getForCall(conversationId: ConversationId): ConversationContextResult = try {
        when (val response = conversationApi.fetchConversationDetails(conversationId.toNetwork())) {
            is NetworkResponse.Error -> ConversationContextResult.Failure(response.kException.toContextFailure())
            is NetworkResponse.Success -> response.value.toContext()
        }
    } catch (cancellation: CancellationException) {
        throw cancellation
    } catch (failure: Throwable) {
        ConversationContextResult.Failure(
            ConversationContextFailure.Remote("Remote conversation context lookup failed", failure),
        )
    }

    @Suppress("ReturnCount")
    private suspend fun ConversationResponse.toContext(): ConversationContextResult {
        val callProtocol = when (protocol) {
            ConvProtocol.PROTEUS -> CallConversationProtocol.Proteus
            ConvProtocol.MLS -> groupId?.let { CallConversationProtocol.Mls(GroupID(it), epoch) }
            ConvProtocol.MIXED -> groupId?.let { CallConversationProtocol.Mixed(GroupID(it), epoch) }
        } ?: return ConversationContextResult.Failure(
            ConversationContextFailure.Invalid("MLS-capable conversation is missing its group ID"),
        )
        val callMembers = buildList {
            members.self?.let { add(it.toCallMember(isSelf = true)) }
            addAll(members.otherMembers.map { it.toCallMember(isSelf = false) })
        }
        val clients = when (val response = clientApi.listClientsOfUsers(callMembers.map { it.userId.toNetwork() })) {
            is NetworkResponse.Error -> return ConversationContextResult.Failure(
                ConversationContextFailure.Remote("Conversation client lookup failed", response.kException),
            )
            is NetworkResponse.Success -> response.value.flatMap { (userId, userClients) ->
                userClients.map { CallClient(userId.toModel(), it.id) }
            }
        }
        return ConversationContextResult.Success(
            CallConversationContext(
                conversationId = id.toModel(),
                type = toCallType(),
                protocol = callProtocol,
                members = callMembers,
                clients = clients,
                teamId = teamId,
            ),
        )
    }

    @Suppress("DEPRECATION", "ComplexCondition")
    private fun ConversationResponse.toCallType(): CallConversationType = when (type) {
        ConversationResponse.Type.SELF -> CallConversationType.SELF
        ConversationResponse.Type.ONE_TO_ONE -> CallConversationType.ONE_TO_ONE
        ConversationResponse.Type.WAIT_FOR_CONNECTION -> CallConversationType.CONNECTION_PENDING
        ConversationResponse.Type.GROUP -> {
            val isFakeTeamOneToOne = members.otherMembers.size == 1 &&
                    name.isNullOrBlank() && selfUserTeamId != null && selfUserTeamId == teamId
            if (isFakeTeamOneToOne) {
                CallConversationType.ONE_TO_ONE
            } else {
                when (conversationGroupType) {
                    ConversationResponse.GroupType.Channel -> CallConversationType.CHANNEL
                    ConversationResponse.GroupType.Meeting -> CallConversationType.MEETING
                    else -> CallConversationType.GROUP
                }
            }
        }
    }

    private fun ConversationMemberDTO.toCallMember(isSelf: Boolean): CallMember = CallMember(
        userId = id.toModel(),
        role = conversationRole,
        isSelf = isSelf,
        isService = service != null,
    )

    private fun QualifiedID.toNetwork(): NetworkQualifiedId = NetworkQualifiedId(value, domain)

    private fun NetworkQualifiedId.toModel(): QualifiedID = QualifiedID(value, domain)

    private fun KaliumException.toContextFailure(): ConversationContextFailure = when {
        this is KaliumException.InvalidRequestError && (isNotFound() || isConversationNotFound()) ->
            ConversationContextFailure.NotFound
        this is KaliumException.InvalidRequestError && isAccessDenied() -> ConversationContextFailure.AccessDenied
        else -> ConversationContextFailure.Remote("Conversation lookup failed", this)
    }
}
