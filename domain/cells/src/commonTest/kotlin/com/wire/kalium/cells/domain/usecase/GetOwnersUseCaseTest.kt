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
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.user.UserAssetId
import com.wire.kalium.logic.data.user.UserId
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
        private const val CONVERSATION_ID = "conversation123"
        private const val SEARCH_QUERY = ""
        private const val USER_ID_1 = "user1"
        private const val USER_DOMAIN_1 = "wire.com"
        private const val USER_ID_2 = "user2"
        private const val USER_DOMAIN_2 = "wire.com"
        private const val USER_NAME_1 = "John Doe"
        private const val USER_NAME_2 = "Jane Smith"
        private const val USER_HANDLE_1 = "@johndoe"
        private const val USER_HANDLE_2 = "@janesmith"
        private const val NODE_ID_1 = "node1"
        private const val NODE_ID_2 = "node2"
        private const val NODE_ID_3 = "node3"
    }

    @Test
    fun given_NodesWithOwners_whenInvoked_thenReturnOwnersSuccess() = runTest {
        val (_, useCase) = Arrangement()
            .withNodes(
                listOf(
                    createCellNode(NODE_ID_1, UserId(USER_ID_1, USER_DOMAIN_1).toString()),
                    createCellNode(NODE_ID_2, UserId(USER_ID_2, USER_DOMAIN_2).toString())
                )
            )
            .withUsers(
                listOf(
                    createUserDetails(USER_ID_1, USER_DOMAIN_1, USER_NAME_1, USER_HANDLE_1),
                    createUserDetails(USER_ID_2, USER_DOMAIN_2, USER_NAME_2, USER_HANDLE_2)
                )
            )
            .arrange()

        val result = useCase(CONVERSATION_ID, SEARCH_QUERY)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(2, result.owners.size)
        assertEquals(USER_NAME_1, result.owners[0].name)
        assertEquals(USER_NAME_2, result.owners[1].name)
    }

    @Test
    fun given_NodesWithDuplicateOwners_whenInvoked_thenReturnUniqueOwners() = runTest {
        val (_, useCase) = Arrangement()
            .withNodes(
                listOf(
                    createCellNode(NODE_ID_1, UserId(USER_ID_1, USER_DOMAIN_1).toString()),
                    createCellNode(NODE_ID_2, UserId(USER_ID_1, USER_DOMAIN_1).toString()),
                    createCellNode(NODE_ID_3, UserId(USER_ID_2, USER_DOMAIN_2).toString())
                )
            )
            .withUsers(
                listOf(
                    createUserDetails(USER_ID_1, USER_DOMAIN_1, USER_NAME_1, USER_HANDLE_1),
                    createUserDetails(USER_ID_2, USER_DOMAIN_2, USER_NAME_2, USER_HANDLE_2)
                )
            )
            .arrange()

        val result = useCase(CONVERSATION_ID, SEARCH_QUERY)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(2, result.owners.size)
    }

    @Test
    fun given_NodesWithoutMatches_whenInvoked_thenReturnEmptyList() = runTest {
        val (_, useCase) = Arrangement()
            .withNodes(
                listOf(
                    createCellNode(NODE_ID_1, "unknownUserId")
                )
            )
            .withUsers(
                listOf(
                    createUserDetails(USER_ID_1, USER_DOMAIN_1, USER_NAME_1, USER_HANDLE_1)
                )
            )
            .arrange()

        val result = useCase(CONVERSATION_ID, SEARCH_QUERY)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(0, result.owners.size)
    }

    @Test
    fun given_NodesWithNullOwnerIds_whenInvoked_thenFilterThemOut() = runTest {
        val (_, useCase) = Arrangement()
            .withNodes(
                listOf(
                    createCellNode(NODE_ID_1, UserId(USER_ID_1, USER_DOMAIN_1).toString()),
                    createCellNode(NODE_ID_2, null),
                    createCellNode(NODE_ID_3, UserId(USER_ID_2, USER_DOMAIN_2).toString())
                )
            )
            .withUsers(
                listOf(
                    createUserDetails(USER_ID_1, USER_DOMAIN_1, USER_NAME_1, USER_HANDLE_1),
                    createUserDetails(USER_ID_2, USER_DOMAIN_2, USER_NAME_2, USER_HANDLE_2)
                )
            )
            .arrange()

        val result = useCase(CONVERSATION_ID, SEARCH_QUERY)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(2, result.owners.size)
    }

    @Test
    fun given_NodesRepositoryFails_whenInvoked_thenReturnFailure() = runTest {
        val networkFailure: NetworkFailure = NetworkFailure.ServerMiscommunication(Exception("Test"))
        val (_, useCase) = Arrangement()
            .withNodesError(networkFailure)
            .arrange()

        val result = useCase(CONVERSATION_ID, SEARCH_QUERY)

        assertIs<GetOwnersUseCaseResult.Failure>(result)
        assertEquals(networkFailure, result.failure)
    }

    @Test
    fun given_UsersRepositoryFails_whenInvoked_thenReturnFailure() = runTest {
        val storageFailure: StorageFailure = StorageFailure.DataNotFound
        val (_, useCase) = Arrangement()
            .withNodes(
                listOf(
                    createCellNode(NODE_ID_1, UserId(USER_ID_1, USER_DOMAIN_1).toString())
                )
            )
            .withUsersError(storageFailure)
            .arrange()

        val result = useCase(CONVERSATION_ID, SEARCH_QUERY)

        assertIs<GetOwnersUseCaseResult.Failure>(result)
        assertIs<StorageFailure>(result.failure)
    }

    @Test
    fun given_NullConversationId_whenInvoked_thenUseEmptyString() = runTest {
        val (_, useCase) = Arrangement()
            .withNodes(
                listOf(
                    createCellNode(NODE_ID_1, UserId(USER_ID_1, USER_DOMAIN_1).toString())
                )
            )
            .withUsers(
                listOf(
                    createUserDetails(USER_ID_1, USER_DOMAIN_1, USER_NAME_1, USER_HANDLE_1)
                )
            )
            .arrange()

        val result = useCase(null, SEARCH_QUERY)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(1, result.owners.size)
    }

    @Test
    fun given_OwnersWithTeamId_whenInvoked_thenPreserveTeamId() = runTest {
        val teamId = "team123"
        val userWithTeam = createUserDetails(USER_ID_1, USER_DOMAIN_1, USER_NAME_1, USER_HANDLE_1)
            .copy(team = teamId)

        val (_, useCase) = Arrangement()
            .withNodes(
                listOf(
                    createCellNode(NODE_ID_1, UserId(USER_ID_1, USER_DOMAIN_1).toString())
                )
            )
            .withUsers(listOf(userWithTeam))
            .arrange()

        val result = useCase(CONVERSATION_ID, SEARCH_QUERY)

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
            .withNodes(
                listOf(
                    createCellNode(NODE_ID_1, UserId(USER_ID_1, USER_DOMAIN_1).toString())
                )
            )
            .withUsers(listOf(userWithAssets))
            .arrange()

        val result = useCase(CONVERSATION_ID, SEARCH_QUERY)

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
    fun given_EmptyNodes_whenInvoked_thenReturnEmptyList() = runTest {
        val (_, useCase) = Arrangement()
            .withNodes(emptyList())
            .withUsers(
                listOf(
                    createUserDetails(USER_ID_1, USER_DOMAIN_1, USER_NAME_1, USER_HANDLE_1)
                )
            )
            .arrange()

        val result = useCase(CONVERSATION_ID, SEARCH_QUERY)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(0, result.owners.size)
    }

    @Test
    fun given_EmptyUsers_whenInvoked_thenReturnEmptyList() = runTest {
        val (_, useCase) = Arrangement()
            .withNodes(
                listOf(
                    createCellNode(NODE_ID_1, UserId(USER_ID_1, USER_DOMAIN_1).toString())
                )
            )
            .withUsers(emptyList())
            .arrange()

        val result = useCase(CONVERSATION_ID, SEARCH_QUERY)

        assertIs<GetOwnersUseCaseResult.Success>(result)
        assertEquals(0, result.owners.size)
    }

    private fun createCellNode(nodeId: String, ownerUserId: String?): CellNode {
        return CellNode(
            uuid = nodeId,
            versionId = "v1",
            path = "/path/to/$nodeId",
            ownerUserId = ownerUserId,
            mimeType = "text/plain"
        )
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
        val cellsRepository = mock(CellsRepository::class)
        val usersRepository = mock(CellUsersRepository::class)

        private var nodesResult: Either<NetworkFailure, List<CellNode>> = Either.Right(emptyList())
        private var usersResult: Either<StorageFailure, List<UserDetailsEntity>> = Either.Right(emptyList())

        fun withNodes(nodes: List<CellNode>) = apply {
            nodesResult = nodes.right()
        }

        fun withUsers(users: List<UserDetailsEntity>) = apply {
            usersResult = users.right()
        }

        fun withNodesError(failure: NetworkFailure) = apply {
            nodesResult = failure.left()
        }

        fun withUsersError(failure: StorageFailure) = apply {
            usersResult = failure.left()
        }

        suspend fun arrange(): Pair<Arrangement, GetOwnersUseCase> {
            coEvery {
                cellsRepository.getNodesByPath(any(), any(), any(), any())
            }.returns(nodesResult)

            coEvery {
                usersRepository.getUsers()
            }.returns(usersResult)

            return this to GetOwnersUseCaseImpl(cellsRepository, usersRepository)
        }
    }
}

