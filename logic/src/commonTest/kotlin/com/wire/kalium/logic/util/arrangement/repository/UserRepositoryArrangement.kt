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
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.SelfUser
import com.wire.kalium.logic.data.user.User
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.given
import io.mockative.matchers.Matcher
import io.mockative.mock
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOf

@Suppress("INAPPLICABLE_JVM_NAME")
internal interface UserRepositoryArrangement {
    val userRepository: UserRepository
    fun withDefederateUser(result: Either<CoreFailure, Unit>, userId: Matcher<UserId> = any())
    fun withObserveUser(result: Flow<User?> = flowOf(TestUser.OTHER), userId: Matcher<UserId> = any())

    fun withUpdateUserSuccess()

    fun withUpdateUserFailure(coreFailure: CoreFailure)

    fun withMarkUserAsDeletedAndRemoveFromGroupConversationsSuccess(
        result: List<ConversationId>,
        userIdMatcher: Matcher<UserId> = any()
    )

    fun withSelfUserReturning(selfUser: SelfUser?)

    fun withObservingSelfUserReturning(selfUserFlow: Flow<SelfUser>)

    fun withUserByIdReturning(result: Either<CoreFailure, OtherUser>)

    fun withUpdateOneOnOneConversationReturning(result: Either<CoreFailure, Unit>)

    fun withGetKnownUserReturning(result: Flow<OtherUser?>)

    fun withGetUsersWithOneOnOneConversationReturning(result: List<OtherUser>)

    fun withFetchAllOtherUsersReturning(result: Either<CoreFailure, Unit>)

    fun withFetchUserInfoReturning(result: Either<CoreFailure, Unit>)

    fun withFetchUsersByIdReturning(
        result: Either<CoreFailure, Unit>,
        userIdList: Matcher<Set<UserId>> = any()
    )

    fun withFetchUsersIfUnknownByIdsReturning(
        result: Either<CoreFailure, Unit>,
        userIdList: Matcher<Set<UserId>> = any()
    )

    fun withIsAtLeastOneUserATeamMember(
        result: Either<StorageFailure, Boolean>,
        userIdList: Matcher<List<UserId>> = any()
    )

    fun withMarkAsDeleted(result: Either<StorageFailure, Unit>, userId: Matcher<List<UserId>>)
    fun withOneOnOnConversationId(result: Either<StorageFailure, ConversationId>, userId: Matcher<UserId> = any())
}

@Suppress("INAPPLICABLE_JVM_NAME")
internal open class UserRepositoryArrangementImpl : UserRepositoryArrangement {
    @Mock
    override val userRepository: UserRepository = mock(UserRepository::class)

    override fun withDefederateUser(
        result: Either<CoreFailure, Unit>,
        userId: Matcher<UserId>
    ) {
        given(userRepository)
            .suspendFunction(userRepository::defederateUser)
            .whenInvokedWith(userId)
            .thenReturn(result)
    }

    override fun withObserveUser(result: Flow<User?>, userId: Matcher<UserId>) {
        given(userRepository)
            .suspendFunction(userRepository::observeUser)
            .whenInvokedWith(userId)
            .thenReturn(result)
    }

    override fun withUpdateUserSuccess() {
        given(userRepository).suspendFunction(userRepository::updateUserFromEvent).whenInvokedWith(any())
            .thenReturn(Either.Right(Unit))
    }

    override fun withUpdateUserFailure(coreFailure: CoreFailure) {
        given(userRepository).suspendFunction(userRepository::updateUserFromEvent)
            .whenInvokedWith(any()).thenReturn(Either.Left(coreFailure))
    }

    override fun withMarkUserAsDeletedAndRemoveFromGroupConversationsSuccess(
        result: List<ConversationId>,
        userIdMatcher: Matcher<UserId>
    ) {
        given(userRepository).suspendFunction(
            userRepository::markUserAsDeletedAndRemoveFromGroupConversations
        ).whenInvokedWith(userIdMatcher)
            .thenReturn(Either.Right(result))
    }

    override fun withSelfUserReturning(selfUser: SelfUser?) {
        given(userRepository)
            .suspendFunction(userRepository::getSelfUser)
            .whenInvoked()
            .thenReturn(selfUser)
    }

    override fun withObservingSelfUserReturning(selfUserFlow: Flow<SelfUser>) {
        given(userRepository)
            .suspendFunction(userRepository::observeSelfUser)
            .whenInvoked()
            .thenReturn(selfUserFlow)
    }

    override fun withUserByIdReturning(result: Either<CoreFailure, OtherUser>) {
        given(userRepository)
            .suspendFunction(userRepository::userById)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withUpdateOneOnOneConversationReturning(result: Either<CoreFailure, Unit>) {
        given(userRepository)
            .suspendFunction(userRepository::updateActiveOneOnOneConversation)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withGetKnownUserReturning(result: Flow<OtherUser?>) {
        given(userRepository)
            .suspendFunction(userRepository::getKnownUser)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withGetUsersWithOneOnOneConversationReturning(result: List<OtherUser>) {
        given(userRepository)
            .suspendFunction(userRepository::getUsersWithOneOnOneConversation)
            .whenInvoked()
            .thenReturn(result)
    }

    override fun withFetchAllOtherUsersReturning(result: Either<CoreFailure, Unit>) {
        given(userRepository)
            .suspendFunction(userRepository::fetchAllOtherUsers)
            .whenInvoked()
            .thenReturn(result)
    }

    override fun withFetchUserInfoReturning(result: Either<CoreFailure, Unit>) {
        given(userRepository)
            .suspendFunction(userRepository::fetchUserInfo)
            .whenInvokedWith(any())
            .thenReturn(result)
    }

    override fun withFetchUsersByIdReturning(
        result: Either<CoreFailure, Unit>,
        userIdList: Matcher<Set<UserId>>
    ) {
        given(userRepository)
            .suspendFunction(userRepository::fetchUsersByIds)
            .whenInvokedWith(userIdList)
            .thenReturn(result)
    }

    override fun withFetchUsersIfUnknownByIdsReturning(result: Either<CoreFailure, Unit>, userIdList: Matcher<Set<UserId>>) {
        given(userRepository)
            .suspendFunction(userRepository::fetchUsersIfUnknownByIds)
            .whenInvokedWith(userIdList)
            .thenReturn(result)
    }

    override fun withIsAtLeastOneUserATeamMember(result: Either<StorageFailure, Boolean>, userIdList: Matcher<List<UserId>>) {
        given(userRepository)
            .suspendFunction(userRepository::isAtLeastOneUserATeamMember)
            .whenInvokedWith(userIdList)
            .thenReturn(result)
    }

    override fun withMarkAsDeleted(result: Either<StorageFailure, Unit>, userId: Matcher<List<UserId>>) {
        given(userRepository)
            .suspendFunction(userRepository::markAsDeleted)
            .whenInvokedWith(userId)
            .thenReturn(result)
    }

    override fun withOneOnOnConversationId(result: Either<StorageFailure, ConversationId>, userId: Matcher<UserId>) {
        given(userRepository)
            .suspendFunction(userRepository::getOneOnOnConversationId)
            .whenInvokedWith(userId)
            .thenReturn(result)
    }
}
