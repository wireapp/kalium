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

package com.wire.kalium.logic.sync.receiver

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.map
import com.wire.kalium.logic.data.conversation.GroupConversationMembers
import com.wire.kalium.logic.data.conversation.OneOnOneMembers
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.member.MemberDAO
import com.wire.kalium.persistence.dao.ConnectionDAO
import com.wire.kalium.util.InternalKaliumApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** Minimal connection data used while applying federation events. */
@InternalKaliumApi
public data class FederationConnection(
    public val conversationId: ConversationId,
    public val userId: UserId,
    public val otherUserDomain: String?,
)

@InternalKaliumApi
public interface FederationConnectionRepository {
    public fun getFederationConnections(): Either<StorageFailure, Flow<List<FederationConnection>>>
    public suspend fun deleteFederationConnection(connection: FederationConnection): Either<StorageFailure, Unit>
}

@InternalKaliumApi
public class FederationConnectionRepositoryImpl public constructor(
    private val connectionDAO: ConnectionDAO,
    private val userDAO: UserDAO,
) : FederationConnectionRepository {
    override fun getFederationConnections(): Either<StorageFailure, Flow<List<FederationConnection>>> = wrapStorageRequest {
        connectionDAO.getConnectionRequests().map { connections ->
            connections.map { connection ->
                FederationConnection(
                    conversationId = connection.qualifiedConversationId.toModel(),
                    userId = connection.qualifiedToId.toModel(),
                    otherUserDomain = connection.otherUser?.id?.domain,
                )
            }
        }
    }

    override suspend fun deleteFederationConnection(connection: FederationConnection): Either<StorageFailure, Unit> =
        wrapStorageRequest {
            connectionDAO.deleteConnectionDataAndConversation(connection.conversationId.toDao())
            userDAO.upsertConnectionStatuses(mapOf(connection.userId.toDao() to ConnectionEntity.State.CANCELLED))
        }
}

@InternalKaliumApi
public interface FederationConversationRepository {
    public suspend fun getGroupConversationsWithMembersWithBothDomains(
        firstDomain: String,
        secondDomain: String,
    ): Either<CoreFailure, GroupConversationMembers>

    public suspend fun getOneOnOneConversationsWithFederatedMembers(
        domain: String,
    ): Either<CoreFailure, OneOnOneMembers>

    public suspend fun deleteFederatedMembers(
        userIds: List<UserId>,
        conversationId: ConversationId,
    ): Either<CoreFailure, Unit>
}

@InternalKaliumApi
public class FederationConversationRepositoryImpl public constructor(
    private val memberDAO: MemberDAO,
) : FederationConversationRepository {
    override suspend fun getGroupConversationsWithMembersWithBothDomains(
        firstDomain: String,
        secondDomain: String,
    ): Either<CoreFailure, GroupConversationMembers> = wrapStorageRequest {
        memberDAO.getGroupConversationWithUserIdsWithBothDomains(firstDomain, secondDomain)
            .mapKeys { it.key.toModel() }
            .mapValues { it.value.map { userId -> userId.toModel() } }
    }

    override suspend fun getOneOnOneConversationsWithFederatedMembers(
        domain: String,
    ): Either<CoreFailure, OneOnOneMembers> = wrapStorageRequest {
        memberDAO.getOneOneConversationWithFederatedMembers(domain)
            .mapKeys { it.key.toModel() }
            .mapValues { it.value.toModel() }
    }

    override suspend fun deleteFederatedMembers(
        userIds: List<UserId>,
        conversationId: ConversationId,
    ): Either<CoreFailure, Unit> = wrapStorageRequest {
        memberDAO.deleteMembersByQualifiedID(
            userIds.map { it.toDao() },
            conversationId.toDao(),
        )
    }.map { }
}

@InternalKaliumApi
public fun interface FederationUserRepository {
    public suspend fun defederateUser(userId: UserId): Either<CoreFailure, Unit>
}

@InternalKaliumApi
public class FederationUserRepositoryImpl public constructor(
    private val userDAO: UserDAO,
) : FederationUserRepository {
    override suspend fun defederateUser(userId: UserId): Either<CoreFailure, Unit> = wrapStorageRequest {
        userDAO.markUserAsDefederated(userId.toDao())
    }
}
