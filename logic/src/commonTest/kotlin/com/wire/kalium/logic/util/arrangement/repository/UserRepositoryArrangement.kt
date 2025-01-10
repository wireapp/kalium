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

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.mls.NameAndHandle
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.fake.valueOf
import io.mockative.matchers.AnyMatcher
import io.mockative.matchers.Matcher
import io.mockative.matches
import io.mockative.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Suppress("INAPPLICABLE_JVM_NAME")
internal interface UserRepositoryArrangement {
    val userRepository: UserRepository
    suspend fun withDefederateUser(result: Either<CoreFailure, Unit>, userId: Matcher<UserId> = AnyMatcher(valueOf()))
    suspend fun withObserveUser(result: Flow<User?> = flowOf(TestUser.OTHER), userId: Matcher<UserId> = AnyMatcher(valueOf()))

    suspend fun withUpdateUserSuccess()

    suspend fun withUpdateUserFailure(coreFailure: CoreFailure)

    suspend fun withMarkUserAsDeletedAndRemoveFromGroupConversationsSuccess(
        result: List<ConversationId>,
        userIdMatcher: Matcher<UserId> = AnyMatcher(valueOf())
    )

    suspend fun withSelfUserReturning(selfUser: SelfUser?)

    suspend fun withObservingSelfUserReturning(selfUserFlow: Flow<SelfUser>)

    suspend fun withUserByIdReturning(result: Either<CoreFailure, OtherUser>)

    suspend fun withUpdateOneOnOneConversationReturning(result: Either<CoreFailure, Unit>)

    suspend fun withGetKnownUserReturning(result: Flow<OtherUser?>)

    suspend fun withGetUsersWithOneOnOneConversationReturning(result: List<OtherUser>)

    suspend fun withFetchAllOtherUsersReturning(result: Either<CoreFailure, Unit>)

    suspend fun withFetchUserInfoReturning(result: Either<CoreFailure, Unit>)

    suspend fun withFetchUsersByIdReturning(
        result: Either<CoreFailure, Boolean>,
        userIdList: Matcher<Set<UserId>> = AnyMatcher(valueOf())
    )

    suspend fun withFetchUsersIfUnknownByIdsReturning(
        result: Either<CoreFailure, Unit>,
        userIdList: Matcher<Set<UserId>> = AnyMatcher(valueOf())
    )

    suspend fun withIsAtLeastOneUserATeamMember(
        result: Either<StorageFailure, Boolean>,
        userIdList: Matcher<List<UserId>> = AnyMatcher(valueOf())
    )

    suspend fun withMarkAsDeleted(result: Either<StorageFailure, Unit>, userId: Matcher<List<UserId>>)
    suspend fun withOneOnOnConversationId(result: Either<StorageFailure, ConversationId>, userId: Matcher<UserId> = AnyMatcher(valueOf()))
    suspend fun withUpdateActiveOneOnOneConversationIfNotSet(
        result: Either<CoreFailure, Unit>,
        userId: Matcher<UserId> = AnyMatcher(valueOf()),
        conversationId: Matcher<ConversationId> = AnyMatcher(valueOf())
    )

    suspend fun withNameAndHandle(result: Either<StorageFailure, NameAndHandle>, userId: Matcher<UserId> = AnyMatcher(valueOf()))

    suspend fun withIsClientMlsCapable(
        result: Either<StorageFailure, Boolean>,
        userId: Matcher<UserId> = AnyMatcher(valueOf()),
        clientId: Matcher<ClientId> = AnyMatcher(valueOf())
    )
}

@Suppress("INAPPLICABLE_JVM_NAME")
internal open class UserRepositoryArrangementImpl : UserRepositoryArrangement {
    @Mock
    override val userRepository: UserRepository = mock(UserRepository::class)

    override suspend fun withDefederateUser(
        result: Either<CoreFailure, Unit>,
        userId: Matcher<UserId>
    ) {
        coEvery {
            userRepository.defederateUser(matches { userId.matches(it) })
        }.returns(result)
    }

    override suspend fun withObserveUser(result: Flow<User?>, userId: Matcher<UserId>) {
        coEvery {
            userRepository.observeUser(matches { userId.matches(it) })
        }.returns(result)
    }

    override suspend fun withUpdateUserSuccess() {
        coEvery {
            userRepository.updateUserFromEvent(any())
        }.returns(Either.Right(Unit))
    }

    override suspend fun withUpdateUserFailure(coreFailure: CoreFailure) {
        coEvery {
            userRepository.updateUserFromEvent(any())
        }.returns(Either.Left(coreFailure))
    }

    override suspend fun withMarkUserAsDeletedAndRemoveFromGroupConversationsSuccess(
        result: List<ConversationId>,
        userIdMatcher: Matcher<UserId>
    ) {
        coEvery { userRepository.markUserAsDeletedAndRemoveFromGroupConversations(matches { userIdMatcher.matches(it) }) }
            .returns(Either.Right(result))
    }

    override suspend fun withSelfUserReturning(selfUser: SelfUser?) {
        coEvery {
            userRepository.getSelfUser()
        }.returns(selfUser)
    }

    override suspend fun withObservingSelfUserReturning(selfUserFlow: Flow<SelfUser>) {
        coEvery {
            userRepository.observeSelfUser()
        }.returns(selfUserFlow)
    }

    override suspend fun withUserByIdReturning(result: Either<CoreFailure, OtherUser>) {
        coEvery {
            userRepository.userById(any())
        }.returns(result)
    }

    override suspend fun withUpdateOneOnOneConversationReturning(result: Either<CoreFailure, Unit>) {
        coEvery {
            userRepository.updateActiveOneOnOneConversation(any(), any())
        }.returns(result)
    }

    override suspend fun withGetKnownUserReturning(result: Flow<OtherUser?>) {
        coEvery {
            userRepository.getKnownUser(any())
        }.returns(result)
    }

    override suspend fun withGetUsersWithOneOnOneConversationReturning(result: List<OtherUser>) {
        coEvery {
            userRepository.getUsersWithOneOnOneConversation()
        }.returns(result)
    }

    override suspend fun withFetchAllOtherUsersReturning(result: Either<CoreFailure, Unit>) {
        coEvery {
            userRepository.fetchAllOtherUsers()
        }.returns(result)
    }

    override suspend fun withFetchUserInfoReturning(result: Either<CoreFailure, Unit>) {
        coEvery {
            userRepository.fetchUserInfo(any())
        }.returns(result)
    }

    override suspend fun withFetchUsersByIdReturning(
        result: Either<CoreFailure, Boolean>,
        userIdList: Matcher<Set<UserId>>
    ) {
        coEvery {
            userRepository.fetchUsersByIds(matches { userIdList.matches(it) })
        }.returns(result)
    }

    override suspend fun withFetchUsersIfUnknownByIdsReturning(result: Either<CoreFailure, Unit>, userIdList: Matcher<Set<UserId>>) {
        coEvery {
            userRepository.fetchUsersIfUnknownByIds(matches { userIdList.matches(it) })
        }.returns(result)
    }

    override suspend fun withIsAtLeastOneUserATeamMember(result: Either<StorageFailure, Boolean>, userIdList: Matcher<List<UserId>>) {
        coEvery {
            userRepository.isAtLeastOneUserATeamMember(matches { userIdList.matches(it) }, any())
        }.returns(result)
    }

    override suspend fun withMarkAsDeleted(result: Either<StorageFailure, Unit>, userId: Matcher<List<UserId>>) {
        coEvery {
            userRepository.markAsDeleted(matches { userId.matches(it) })
        }.returns(result)
    }

    override suspend fun withOneOnOnConversationId(result: Either<StorageFailure, ConversationId>, userId: Matcher<QualifiedID>) {
        coEvery { userRepository.getOneOnOnConversationId(matches { userId.matches(it) }) }
            .returns(result)
    }

    override suspend fun withUpdateActiveOneOnOneConversationIfNotSet(
        result: Either<CoreFailure, Unit>,
        userId: Matcher<UserId>,
        conversationId: Matcher<ConversationId>
    ) {
        coEvery {
            userRepository.updateActiveOneOnOneConversationIfNotSet(
                matches { userId.matches(it) },
                matches { conversationId.matches(it) })
        }
            .returns(result)
    }

    override suspend fun withNameAndHandle(result: Either<StorageFailure, NameAndHandle>, userId: Matcher<UserId>) {
        coEvery { userRepository.getNameAndHandle(matches { userId.matches(it) }) }.returns(result)
    }

    override suspend fun withIsClientMlsCapable(result: Either<StorageFailure, Boolean>, userId: Matcher<UserId>, clientId: Matcher<ClientId>) {
        coEvery {
            userRepository.isClientMlsCapable(
                userId = matches { userId.matches(it) },
                clientId = matches { clientId.matches(it) }
            )
        }.returns(result)
    }
}
