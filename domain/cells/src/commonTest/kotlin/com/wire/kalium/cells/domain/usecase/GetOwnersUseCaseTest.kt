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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellUsersRepository
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserAvailabilityStatusEntity
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs

class GetOwnersUseCaseTest {

    private companion object {
        private const val CONVERSATION_ID = "user1@wire.com"
        private const val USER_ID_1 = "user1"
        private const val USER_DOMAIN_1 = "wire.com"
        private const val USER_ID_2 = "user2"
        private const val USER_DOMAIN_2 = "wire.com"
        private const val USER_NAME_1 = "John Doe"
        private const val USER_NAME_2 = "Jane Smith"
        private const val USER_HANDLE_1 = "@johndoe"
        private const val USER_HANDLE_2 = "@janesmith"
    }

    @Test
    fun given_ConversationWithUsers_whenInvoked_thenReturnOwnersSuccess() = runTest {
        val (_, useCase) = Arrangement()
            .withConversationMembers(
                listOf(
                    createUserDetails(USER_ID_1, USER_DOMAIN_1, USER_NAME_1, USER_HANDLE_1),
                    createUserDetails(USER_ID_2, USER_DOMAIN_2, USER_NAME_2, USER_HANDLE_2)
                )
            )
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(2, result.owners.size)
        assertEquals(USER_NAME_1, result.owners[0].name)
        assertEquals(USER_NAME_2, result.owners[1].name)
    }

    @Test
    fun given_AllUsers_whenInvokedWithNullConversationId_thenReturnAllOwnersSuccess() = runTest {
        val (_, useCase) = Arrangement()
            .withAllUsers(
                listOf(
                    createUserDetails(USER_ID_1, USER_DOMAIN_1, USER_NAME_1, USER_HANDLE_1),
                    createUserDetails(USER_ID_2, USER_DOMAIN_2, USER_NAME_2, USER_HANDLE_2)
                )
            )
            .arrange()

        val result = useCase(null)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(2, result.owners.size)
        assertEquals(USER_NAME_1, result.owners[0].name)
        assertEquals(USER_NAME_2, result.owners[1].name)
    }

    @Test
    fun given_ConversationRepositoryFails_whenInvoked_thenReturnFailure() = runTest {
        val storageFailure: StorageFailure = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement()
            .withConversationMembersError(storageFailure)
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertIs<GetOwnersUseCaseResult.Failure>(result)
        assertIs<StorageFailure>(result.failure)
    }

    @Test
    fun given_AllUsersRepositoryFails_whenInvokedWithNullConversationId_thenReturnFailure() = runTest {
        val storageFailure: StorageFailure = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement()
            .withAllUsersError(storageFailure)
            .arrange()

        val result = useCase(null)

        assertIs<GetOwnersUseCaseResult.Failure>(result)
        assertIs<StorageFailure>(result.failure)
    }

    @Test
    fun given_OwnersWithTeamId_whenInvoked_thenPreserveTeamId() = runTest {
        val teamId = "team123"
        val userWithTeam = createUserDetails(USER_ID_1, USER_DOMAIN_1, USER_NAME_1, USER_HANDLE_1)
            .copy(team = teamId)

        val (_, useCase) = Arrangement()
            .withConversationMembers(listOf(userWithTeam))
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(1, result.owners.size)
        assertEquals(TeamId(teamId), result.owners[0].teamId)
    }

    @Test
    fun given_OwnersWithAssets_whenInvoked_thenPreserveAssetIds() = runTest {
        val previewAssetId = QualifiedIDEntity("preview123", USER_DOMAIN_1)
        val completeAssetId = QualifiedIDEntity("complete123", USER_DOMAIN_1)
        val userWithAssets = createUserDetails(USER_ID_1, USER_DOMAIN_1, USER_NAME_1, USER_HANDLE_1)
            .copy(
                previewAssetId = previewAssetId,
                completeAssetId = completeAssetId
            )

        val (_, useCase) = Arrangement()
            .withConversationMembers(listOf(userWithAssets))
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(1, result.owners.size)
        assertEquals(
            UserAssetId("preview123", USER_DOMAIN_1),
            result.owners[0].previewPicture
        )
        assertEquals(
            UserAssetId("complete123", USER_DOMAIN_1),
            result.owners[0].completePicture
        )
    }

    @Test
    fun given_EmptyUsers_whenInvoked_thenReturnEmptyList() = runTest {
        val (_, useCase) = Arrangement()
            .withConversationMembers(emptyList())
            .arrange()

        val result = useCase(CONVERSATION_ID)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(0, result.owners.size)
    }

    @Test
    fun given_EmptyAllUsers_whenInvokedWithNullConversationId_thenReturnEmptyList() = runTest {
        val (_, useCase) = Arrangement()
            .withAllUsers(emptyList())
            .arrange()

        val result = useCase(null)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(0, result.owners.size)
    }

    private fun createUserDetails(
        userId: String,
        domain: String,
        name: String,
        handle: String
    ): UserDetailsEntity {
        return UserDetailsEntity(
            id = QualifiedIDEntity(userId, domain),
            name = name,
            handle = handle,
            email = "$userId@$domain",
            phone = null,
            accentId = 0,
            team = null,
            previewAssetId = null,
            completeAssetId = null,
            availabilityStatus = UserAvailabilityStatusEntity.AVAILABLE,
            userType = UserTypeEntity.STANDARD,
            botService = null,
            deleted = false,
            expiresAt = null,
            defederated = false,
            isProteusVerified = false,
            supportedProtocols = null,
            activeOneOnOneConversationId = null,
            isUnderLegalHold = false
        )
    }

    private class Arrangement {
        private val usersRepository = mock(CellUsersRepository::class)

        private var conversationMembersResult: Either<StorageFailure, List<UserDetailsEntity>> = Either.Right(emptyList())
        private var allUsersResult: Either<StorageFailure, List<UserDetailsEntity>> = Either.Right(emptyList())

        fun withConversationMembers(users: List<UserDetailsEntity>) = apply {
            conversationMembersResult = users.right()
        }

        fun withAllUsers(users: List<UserDetailsEntity>) = apply {
            allUsersResult = users.right()
        }

        fun withConversationMembersError(failure: StorageFailure) = apply {
            conversationMembersResult = failure.left()
        }

        fun withAllUsersError(failure: StorageFailure) = apply {
            allUsersResult = failure.left()
        }

        suspend fun arrange(): Pair<Arrangement, GetOwnersUseCase> {
            coEvery {
                usersRepository.getConversationMemberDetails(any())
            }.returns(conversationMembersResult)

            coEvery {
                usersRepository.getUsers()
            }.returns(allUsersResult)

            return this to GetOwnersUseCaseImpl(usersRepository)
        }
    }
}

