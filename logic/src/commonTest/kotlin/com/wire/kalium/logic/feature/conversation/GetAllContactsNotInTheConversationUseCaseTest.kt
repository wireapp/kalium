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

package com.wire.kalium.logic.feature.conversation

import app.cash.turbine.test
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.user.ConnectionState
import com.wire.kalium.logic.data.user.OtherUser
import com.wire.kalium.logic.data.user.UserAvailabilityStatus
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.data.user.type.UserType
import com.wire.kalium.common.functional.Either
import io.mockative.Mock
import io.mockative.any
import io.mockative.every
import io.mockative.mock
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertIs
import kotlin.test.assertTrue

class GetAllContactsNotInTheConversationUseCaseTest {

    @Test
    fun givenSuccessFullResult_whenGettingUsersNotPartofTheConversation_ThenReturnTheResult() = runTest {
        // given
        val (_, getAllContactsNotInTheConversation) = Arrangement()
            .withSuccessFullGetUsersNotPartOfConversation()
            .arrange()

        // when
        getAllContactsNotInTheConversation(ConversationId("someValue", "someDomain")).test {
            // then
            val result = awaitItem()
            assertIs<Result.Success>(result)
            assertTrue { result.contactsNotInConversation == Arrangement.mockAllContacts }
            cancelAndIgnoreRemainingEvents()
        }
    }

    @Test
    fun givenFailure_whenGettingUsersNotPartofTheConversation_ThenReturnTheResult() = runTest {
        // given
        val (_, getAllContactsNotInTheConversation) = Arrangement()
            .withFailureGetUsersNotPartOfConversation()
            .arrange()

        // when
        getAllContactsNotInTheConversation(ConversationId("someValue", "someDomain")).test {
            // then
            val result = awaitItem()
            assertIs<Result.Failure>(result)
            cancelAndIgnoreRemainingEvents()
        }
    }

    private class Arrangement {
        companion object {
            val mockAllContacts = listOf(
                OtherUser(
                    id = QualifiedID("someAllContactsValue", "someAllContactsDomain"),
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = 0,
                    teamId = null,
                    connectionStatus = ConnectionState.ACCEPTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                    userType = UserType.INTERNAL,
                    botService = null,
                    deleted = false,
                    defederated = false,
                    isProteusVerified = false,
                    supportedProtocols = null
                ),
                OtherUser(
                    id = QualifiedID("someAllContactsValue1", "someAllContactsDomain1"),
                    name = null,
                    handle = null,
                    email = null,
                    phone = null,
                    accentId = 0,
                    teamId = null,
                    connectionStatus = ConnectionState.ACCEPTED,
                    previewPicture = null,
                    completePicture = null,
                    availabilityStatus = UserAvailabilityStatus.AVAILABLE,
                    userType = UserType.INTERNAL,
                    botService = null,
                    deleted = false,
                    defederated = false,
                    isProteusVerified = false,
                    supportedProtocols = null
                )
            )
        }

        @Mock
        val conversationRepository = mock(ConversationRepository::class)

        @Mock
        val userRepository = mock(UserRepository::class)

        fun withSuccessFullGetUsersNotPartOfConversation(allContacts: List<OtherUser> = mockAllContacts): Arrangement {
            every {
                userRepository.observeAllKnownUsersNotInConversation(any())
            }.returns(
                    flowOf(
                        Either.Right(
                            allContacts
                        )
                    )
                )
            return this
        }

        fun withFailureGetUsersNotPartOfConversation(): Arrangement {
            every {
                userRepository.observeAllKnownUsersNotInConversation(any())
            }.returns(flowOf(Either.Left(StorageFailure.DataNotFound)))

            return this
        }

        fun arrange() = this to GetAllContactsNotInConversationUseCase(userRepository)
    }

}
