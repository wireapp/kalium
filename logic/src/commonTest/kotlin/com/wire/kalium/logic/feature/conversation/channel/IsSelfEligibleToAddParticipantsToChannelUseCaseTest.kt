/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.conversation.channel

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.conversation.ConversationDetails.Group.Channel.ChannelAddPermission
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.logic.feature.user.GetSelfUserUseCase
import com.wire.kalium.logic.framework.TestUser
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.mock
import io.mockative.once
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class IsSelfEligibleToAddParticipantsToChannelUseCaseTest {

    @Test
    fun givenErrorOccurs_whenRetrievingPermission_thenResultShouldBeFalse() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withGetChannelAddPermissionReturning(Either.Left(StorageFailure.DataNotFound))
            .arrange()

        val result = useCase(conversationId)

        assertEquals(false, result)
        coVerify { arrangement.conversationRepository.getChannelAddPermission(any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserIsAdminAndPermissionIsADMINS_whenCheckingEligibility_thenResultShouldBeTrue() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withGetChannelAddPermissionReturning(Either.Right(ChannelAddPermission.ADMINS))
            .withGetUserTypeReturning(UserType.ADMIN)
            .arrange()

        val result = useCase(conversationId)

        assertEquals(true, result)
        coVerify { arrangement.conversationRepository.getChannelAddPermission(any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserIsMemberAndPermissionIsADMINS_whenCheckingEligibility_thenResultShouldBeFalse() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withGetChannelAddPermissionReturning(Either.Right(ChannelAddPermission.ADMINS))
            .withGetUserTypeReturning(UserType.INTERNAL)
            .arrange()

        val result = useCase(conversationId)

        assertEquals(false, result)
        coVerify { arrangement.conversationRepository.getChannelAddPermission(any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserIsMemberAndPermissionIsEVERYONE_whenCheckingEligibility_thenResultShouldBeTrue() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withGetChannelAddPermissionReturning(Either.Right(ChannelAddPermission.EVERYONE))
            .withGetUserTypeReturning(UserType.INTERNAL)
            .arrange()

        val result = useCase(conversationId)

        assertEquals(true, result)
        coVerify { arrangement.conversationRepository.getChannelAddPermission(any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserIsOwnerAndPermissionIsEVERYONE_whenCheckingEligibility_thenResultShouldBeTrue() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withGetChannelAddPermissionReturning(Either.Right(ChannelAddPermission.EVERYONE))
            .withGetUserTypeReturning(UserType.OWNER)
            .arrange()

        val result = useCase(conversationId)

        assertEquals(true, result)
        coVerify { arrangement.conversationRepository.getChannelAddPermission(any()) }.wasInvoked(exactly = once)
    }

    @Test
    fun givenUserIsInternalAndPermissionIsADMINS_whenCheckingEligibility_thenResultShouldBeFalse() = runTest {
        val (arrangement, useCase) = Arrangement()
            .withGetChannelAddPermissionReturning(Either.Right(ChannelAddPermission.ADMINS))
            .withGetUserTypeReturning(UserType.INTERNAL)
            .arrange()

        val result = useCase(conversationId)

        assertEquals(false, result)
        coVerify { arrangement.conversationRepository.getChannelAddPermission(any()) }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val getSelfUser = mock(GetSelfUserUseCase::class)

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        private val isSelfEligibleToAddParticipantsToChannel =
            IsSelfEligibleToAddParticipantsToChannelUseCaseImpl(getSelfUser, conversationRepository)

        suspend fun withGetChannelAddPermissionReturning(result: Either<StorageFailure, ChannelAddPermission>) = apply {
            coEvery {
                conversationRepository.getChannelAddPermission(any())
            }.returns(result)
        }

        suspend fun withGetUserTypeReturning(userType: UserType) = apply {
            coEvery {
                getSelfUser()
            }.returns(TestUser.SELF.copy(userType = userType))
        }

        fun arrange(block: Arrangement.() -> Unit = { }) = apply(block).let { this to isSelfEligibleToAddParticipantsToChannel }
    }

    companion object {
        private val conversationId = ConversationId("value", "domain")
    }
}
