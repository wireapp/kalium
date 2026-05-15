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
package com.wire.kalium.logic.util.arrangement.repository

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.mls.NameAndHandle
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestUser
import dev.mokkery.matcher.any
import dev.mokkery.everySuspend
import dev.mokkery.matcher.matches
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

private val MOKKERY_USER_ID = UserId("mokkery-user", "mokkery.test")
private val MOKKERY_CONVERSATION_ID = ConversationId("mokkery-conversation", "mokkery.test")
private val MOKKERY_CLIENT_ID = ClientId("mokkery-client")
private val MOKKERY_TEAM_ID = TeamId("mokkery-team")
private val MOKKERY_USER_UPDATE_EVENT = Event.User.Update(
    id = "mokkery-event",
    userId = MOKKERY_USER_ID,
    accentId = null,
    ssoIdDeleted = null,
    name = null,
    handle = null,
    email = null,
    previewAssetId = null,
    completeAssetId = null,
    supportedProtocols = null
)

@Suppress("INAPPLICABLE_JVM_NAME")
internal interface UserRepositoryArrangement {
    val userRepository: UserRepository
    suspend fun withDefederateUser(result: Either<CoreFailure, Unit>, userId: (UserId) -> Boolean = { true })
    suspend fun withObserveUser(result: Flow<User?> = flowOf(TestUser.OTHER), userId: (UserId) -> Boolean = { true })

    suspend fun withUpdateUserSuccess()

    suspend fun withUpdateUserFailure(coreFailure: CoreFailure)

    suspend fun withMarkUserAsDeletedAndRemoveFromGroupConversationsSuccess(
        result: List<ConversationId>,
        userIdMatcher: (UserId) -> Boolean = { true }
    )

    suspend fun withSelfUserReturning(result: Either<StorageFailure, SelfUser>)

    suspend fun withObservingSelfUserReturning(selfUserFlow: Flow<SelfUser>)

    suspend fun withUserByIdReturning(result: Either<CoreFailure, OtherUser>)

    suspend fun withUpdateOneOnOneConversationReturning(result: Either<CoreFailure, Unit>)

    suspend fun withGetKnownUserReturning(result: Flow<OtherUser?>)

    suspend fun withGetUsersWithOneOnOneConversationReturning(result: List<OtherUser>)

    suspend fun withFetchAllOtherUsersReturning(result: Either<CoreFailure, Unit>)

    suspend fun withFetchUserInfoReturning(result: Either<CoreFailure, Unit>)

    suspend fun withFetchUsersByIdReturning(
        result: Either<CoreFailure, Boolean>,
        userIdList: (Set<UserId>) -> Boolean = { true }
    )

    suspend fun withFetchUsersIfUnknownByIdsReturning(
        result: Either<CoreFailure, Unit>,
        userIdList: (Set<UserId>) -> Boolean = { true }
    )

    suspend fun withIsAtLeastOneUserATeamMember(
        result: Either<StorageFailure, Boolean>,
        userIdList: (List<UserId>) -> Boolean = { true }
    )

    suspend fun withMarkAsDeleted(result: Either<StorageFailure, Unit>, userId: (List<UserId>) -> Boolean)
    suspend fun withOneOnOnConversationId(result: Either<StorageFailure, ConversationId>, userId: (UserId) -> Boolean = { true })
    suspend fun withUpdateActiveOneOnOneConversationIfNotSet(
        result: Either<CoreFailure, Unit>,
        userId: (UserId) -> Boolean = { true },
        conversationId: (ConversationId) -> Boolean = { true }
    )

    suspend fun withNameAndHandle(result: Either<StorageFailure, NameAndHandle>, userId: (UserId) -> Boolean = { true })

    suspend fun withIsClientMlsCapable(
        result: Either<StorageFailure, Boolean>,
        userId: (UserId) -> Boolean = { true },
        clientId: (ClientId) -> Boolean = { true }
    )

    suspend fun withFetchSelfUser(result: Either<CoreFailure, Unit>)
}

@Suppress("INAPPLICABLE_JVM_NAME")
internal open class UserRepositoryArrangementImpl : UserRepositoryArrangement {

    override val userRepository: UserRepository = mock<UserRepository>(mode = MockMode.autoUnit)

    override suspend fun withFetchSelfUser(result: Either<CoreFailure, Unit>) {
        everySuspend {
            userRepository.fetchSelfUser()
        }.returns(result)
    }

    override suspend fun withDefederateUser(
        result: Either<CoreFailure, Unit>,
        userId: (UserId) -> Boolean
    ) {
        everySuspend {
            userRepository.defederateUser(matches { userId(it) })
        }.returns(result)
    }

    override suspend fun withObserveUser(result: Flow<User?>, userId: (UserId) -> Boolean) {
        everySuspend {
            userRepository.observeUser(matches { userId(it) })
        }.returns(result)
    }

    override suspend fun withUpdateUserSuccess() {
        everySuspend {
            userRepository.updateUserFromEvent(any())
        }.returns(Either.Right(Unit))
    }

    override suspend fun withUpdateUserFailure(coreFailure: CoreFailure) {
        everySuspend {
            userRepository.updateUserFromEvent(any())
        }.returns(Either.Left(coreFailure))
    }

    override suspend fun withMarkUserAsDeletedAndRemoveFromGroupConversationsSuccess(
        result: List<ConversationId>,
        userIdMatcher: (UserId) -> Boolean
    ) {
        everySuspend {
            userRepository.markUserAsDeletedAndRemoveFromGroupConversations(matches { userIdMatcher(it) })
        }
            .returns(Either.Right(result))
    }

    override suspend fun withSelfUserReturning(result: Either<StorageFailure, SelfUser>) {
        everySuspend {
            userRepository.getSelfUser()
        }.returns(result)
    }

    override suspend fun withObservingSelfUserReturning(selfUserFlow: Flow<SelfUser>) {
        everySuspend {
            userRepository.observeSelfUser()
        }.returns(selfUserFlow)
    }

    override suspend fun withUserByIdReturning(result: Either<CoreFailure, OtherUser>) {
        everySuspend {
            userRepository.userById(any())
        }.returns(result)
    }

    override suspend fun withUpdateOneOnOneConversationReturning(result: Either<CoreFailure, Unit>) {
        everySuspend {
            userRepository.updateActiveOneOnOneConversation(any(), any())
        }.returns(result)
    }

    override suspend fun withGetKnownUserReturning(result: Flow<OtherUser?>) {
        everySuspend {
            userRepository.getKnownUser(any())
        }.returns(result)
    }

    override suspend fun withGetUsersWithOneOnOneConversationReturning(result: List<OtherUser>) {
        everySuspend {
            userRepository.getUsersWithOneOnOneConversation()
        }.returns(result)
    }

    override suspend fun withFetchAllOtherUsersReturning(result: Either<CoreFailure, Unit>) {
        everySuspend {
            userRepository.fetchAllOtherUsers()
        }.returns(result)
    }

    override suspend fun withFetchUserInfoReturning(result: Either<CoreFailure, Unit>) {
        everySuspend {
            userRepository.fetchUserInfo(any())
        }.returns(result)
    }

    override suspend fun withFetchUsersByIdReturning(
        result: Either<CoreFailure, Boolean>,
        userIdList: (Set<UserId>) -> Boolean
    ) {
        everySuspend {
            userRepository.fetchUsersByIds(matches { userIdList(it) })
        }.returns(result)
    }

    override suspend fun withFetchUsersIfUnknownByIdsReturning(result: Either<CoreFailure, Unit>, userIdList: (Set<UserId>) -> Boolean) {
        everySuspend {
            userRepository.fetchUsersIfUnknownByIds(matches { userIdList(it) })
        }.returns(result)
    }

    override suspend fun withIsAtLeastOneUserATeamMember(result: Either<StorageFailure, Boolean>, userIdList: (List<UserId>) -> Boolean) {
        everySuspend {
            userRepository.isAtLeastOneUserATeamMember(matches { userIdList(it) }, any())
        }.returns(result)
    }

    override suspend fun withMarkAsDeleted(result: Either<StorageFailure, Unit>, userId: (List<UserId>) -> Boolean) {
        everySuspend {
            userRepository.markAsDeleted(matches { userId(it) })
        }.returns(result)
    }

    override suspend fun withOneOnOnConversationId(result: Either<StorageFailure, ConversationId>, userId: (QualifiedID) -> Boolean) {
        everySuspend { userRepository.getOneOnOnConversationId(matches { userId(it) }) }
            .returns(result)
    }

    override suspend fun withUpdateActiveOneOnOneConversationIfNotSet(
        result: Either<CoreFailure, Unit>,
        userId: (UserId) -> Boolean,
        conversationId: (ConversationId) -> Boolean
    ) {
        everySuspend {
            userRepository.updateActiveOneOnOneConversationIfNotSet(
                matches { userId(it) },
                matches { conversationId(it) })
        }
            .returns(result)
    }

    override suspend fun withNameAndHandle(result: Either<StorageFailure, NameAndHandle>, userId: (UserId) -> Boolean) {
        everySuspend { userRepository.getNameAndHandle(matches { userId(it) }) }.returns(result)
    }

    override suspend fun withIsClientMlsCapable(
        result: Either<StorageFailure, Boolean>,
        userId: (UserId) -> Boolean,
        clientId: (ClientId) -> Boolean
    ) {
        everySuspend {
            userRepository.isClientMlsCapable(
                userId = matches { userId(it) },
                clientId = matches { clientId(it) }
            )
        }.returns(result)
    }
}
