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

package com.wire.kalium.logic.data.user

import app.cash.turbine.test
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.SelfTeamIdProvider
import com.wire.kalium.logic.data.id.TeamId
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.legalhold.ListUsersLegalHoldConsent
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserDataSource.Companion.BATCH_SIZE
import com.wire.kalium.logic.failure.SelfUserDeleted
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestEvent
import com.wire.kalium.logic.framework.TestTeam
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.framework.TestUser.LIST_USERS_DTO
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.getOrNull
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.test_util.TestNetworkException
import com.wire.kalium.logic.test_util.TestNetworkException.federationNotEnabled
import com.wire.kalium.logic.test_util.TestNetworkException.generic
import com.wire.kalium.logic.test_util.TestNetworkResponseError
import com.wire.kalium.logic.util.shouldFail
import com.wire.kalium.logic.util.shouldSucceed
import com.wire.kalium.network.api.authenticated.teams.TeamMemberDTO
import com.wire.kalium.network.api.authenticated.teams.TeamMemberListNonPaginated
import com.wire.kalium.network.api.authenticated.user.CreateUserTeamDTO
import com.wire.kalium.network.api.authenticated.userDetails.ListUserRequest
import com.wire.kalium.network.api.authenticated.userDetails.ListUsersDTO
import com.wire.kalium.network.api.authenticated.userDetails.QualifiedUserIdListRequest
import com.wire.kalium.network.api.authenticated.userDetails.qualifiedIds
import com.wire.kalium.network.api.base.authenticated.TeamsApi
import com.wire.kalium.network.api.base.authenticated.UpgradePersonalToTeamApi
import com.wire.kalium.network.api.base.authenticated.self.SelfApi
import com.wire.kalium.network.api.base.authenticated.userDetails.UserDetailsApi
import com.wire.kalium.network.api.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.PartialUserEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserEntityMinimized
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import io.ktor.http.HttpStatusCode
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.coVerify
import io.mockative.eq
import io.mockative.every
import io.mockative.matches
import io.mockative.mock
import io.mockative.once
import io.mockative.twice
import io.mockative.verify
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class UserRepositoryTest {

    @Test
    fun givenAllUsersAreKnown_whenFetchingUsersIfUnknown_thenShouldNotFetchFromApiAndSucceed() = runTest {
        val requestedUserIds = setOf(
            UserId(value = "id1", domain = "domain1"),
            UserId(value = "id2", domain = "domain2")
        )
        val knownUserEntities = listOf(
            TestUser.DETAILS_ENTITY.copy(id = UserIDEntity(value = "id1", domain = "domain1")),
            TestUser.DETAILS_ENTITY.copy(id = UserIDEntity(value = "id2", domain = "domain2"))
        )
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetUsersByQualifiedIdList(knownUserEntities)
            .arrange()

        coEvery {
            arrangement.userDAO.getUsersDetailsByQualifiedIDList(any())
        }.returns(knownUserEntities)

        userRepository.fetchUsersIfUnknownByIds(requestedUserIds).shouldSucceed()

        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenAUserIsNotKnown_whenFetchingUsersIfUnknown_thenShouldFetchFromAPIAndSucceed() = runTest {
        val missingUserId = UserId(value = "id2", domain = "domain2")
        val requestedUserIds = setOf(UserId(value = "id1", domain = "domain1"), missingUserId)
        val knownUserEntities = listOf(TestUser.DETAILS_ENTITY.copy(id = UserIDEntity(value = "id1", domain = "domain1")))
        val (arrangement, userRepository) = Arrangement()
//             .withGetSelfUserId()
            .withSuccessfulGetUsersByQualifiedIdList(knownUserEntities)
            .withSuccessfulGetMultipleUsersApiRequest(ListUsersDTO(usersFailed = emptyList(), listOf(TestUser.USER_PROFILE_DTO)))
            .arrange()

        userRepository.fetchUsersIfUnknownByIds(requestedUserIds).shouldSucceed()

        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(matches { request: ListUserRequest ->
                (request as QualifiedUserIdListRequest).qualifiedIds.first() == missingUserId.toApi()
            })
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAUserEvent_whenPersistingTheUser_thenShouldSucceed() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUpdateUserReturning()
            .arrange()

        val result = userRepository.updateUserFromEvent(TestEvent.updateUser(userId = SELF_USER.id))

        with(result) {
            shouldSucceed()
            coVerify {
                arrangement.userDAO.updateUser(any<PartialUserEntity>())
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenAnEmptyUserIdList_whenFetchingUsers_thenShouldNotFetchFromApiAndSucceed() = runTest {
        // given
        val requestedUserIds = emptySet<UserId>()
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetMultipleUsersApiRequest(
                ListUsersDTO(
                    usersFailed = emptyList(),
                    usersFound = listOf(TestUser.USER_PROFILE_DTO)
                )
            )
            .arrange()
        // when
        userRepository.fetchUsersByIds(requestedUserIds).shouldSucceed()
        // then
        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenAnUserIdListWithDifferentDomain_whenApiReturnsFederationDisabledError_thenShouldTryToFetchOnlyUsersWithSelfDomain() = runTest {
        // given
        val requestedUserIds = setOf(TestUser.OTHER_USER_ID, TestUser.OTHER_FEDERATED_USER_ID)
        val (arrangement, userRepository) = Arrangement()
            .withGetMultipleUsersApiRequestFederationNotEnabledError()
            .arrange()
        // when
        userRepository.fetchUsersByIds(requestedUserIds).shouldFail()
        // then
        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(eq(QualifiedUserIdListRequest(requestedUserIds.map { it.toApi() }.toList())))
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(eq(QualifiedUserIdListRequest(listOf(TestUser.OTHER_USER_ID.toApi()))))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAnEmptyUserIdListFromSameDomainAsSelf_whenFetchingUsers_thenShouldNotFetchMultipleUsersAndSucceed() = runTest {
        // given
        val requestedUserIds = setOf(
            UserId(value = "id1", domain = "domain1"),
            UserId(value = "id2", domain = "domain2")
        )
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetMultipleUsersApiRequest(ListUsersDTO(usersFailed = emptyList(), listOf(TestUser.USER_PROFILE_DTO)))
            .arrange()
        assertTrue { requestedUserIds.none { it.domain == arrangement.selfUserId.domain } }
        // when
        userRepository.fetchUsersByIds(requestedUserIds).shouldSucceed()
        // then
        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAUserIdListSmallerThanBatchSize_whenFetchingUsers_thenShouldExecuteRequestsOnce() = runTest {
        // given
        val requestedUserIds = buildSet {
            repeat(BATCH_SIZE - 1) { add(UserId(value = "id$it", domain = "domain")) }
        }
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetMultipleUsersApiRequest(
                ListUsersDTO(
                    usersFailed = emptyList(),
                    usersFound = listOf(TestUser.USER_PROFILE_DTO)
                )
            )
            .arrange()
        // when
        userRepository.fetchUsersByIds(requestedUserIds).shouldSucceed()
        // then
        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenAUserIdListLargerThanBatchSize_whenFetchingUsers_thenShouldExecuteRequestsTwice() = runTest {
        // given
        val requestedUserIds = buildSet {
            repeat(BATCH_SIZE + 1) { add(UserId(value = "id$it", domain = "domain")) }
        }
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetMultipleUsersApiRequest(
                ListUsersDTO(
                    usersFailed = emptyList(),
                    usersFound = listOf(TestUser.USER_PROFILE_DTO)
                )
            )
            .arrange()
        // when
        userRepository.fetchUsersByIds(requestedUserIds).shouldSucceed()
        // then
        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }.wasInvoked(exactly = twice)
    }

    @Test
    fun givenARemoteUserIsDeleted_whenFetchingSelfUser_thenShouldFailWithProperError() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withRemoteGetSelfReturningDeletedUser()
            .arrange()
        // when
        val result = userRepository.fetchSelfUser()
        // then
        with(result) {
            shouldFail { it is SelfUserDeleted }
            coVerify {
                arrangement.selfApi.getSelfInfo()
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun whenFetchingKnownUsers_thenShouldFetchFromDatabaseAndApiAndSucceed() = runTest {
        // Given
        val knownUserEntities = listOf(
            UserIDEntity(value = "id1", domain = "domain1"),
            UserIDEntity(value = "id2", domain = "domain2")
        )
        val knownUserIds = knownUserEntities.map { UserId(it.value, it.domain) }.toSet()
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetMultipleUsers()
            .arrange {
                withAllOtherUsersIdSuccess(knownUserEntities)
            }

        // When
        userRepository.fetchAllOtherUsers().shouldSucceed()

        // Then
        coVerify {
            arrangement.userDAO.allOtherUsersId()
        }.wasInvoked(exactly = once)

        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(eq(ListUserRequest.qualifiedIds(knownUserIds.map { userId -> userId.toApi() })))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenThereAreUsersWithoutMetadata_whenSyncingUsers_thenShouldUpdateThem() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withDaoReturningNoMetadataUsers(listOf(TestUser.DETAILS_ENTITY.copy(name = null)))
            .withSuccessfulGetMultipleUsersApiRequest(ListUsersDTO(emptyList(), listOf(TestUser.USER_PROFILE_DTO)))
            .arrange()

        // when
        userRepository.syncUsersWithoutMetadata()
            .shouldSucceed()

        // then
        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.userDAO.upsertUsers(
                matches { it.firstOrNull()?.name != null },
            )
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenThereAreNOUsersWithoutMetadata_whenSyncingUsers_thenShouldNOTUpdateThem() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withDaoReturningNoMetadataUsers(listOf())
            .arrange()

        // when
        userRepository.syncUsersWithoutMetadata()
            .shouldSucceed()

        // then
        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }.wasNotInvoked()
        coVerify {
            arrangement.userDAO.upsertUsers(any())
        }.wasNotInvoked()
    }

    @Test
    fun whenRemovingUserBrokenAsset_thenShouldCallDaoAndSucceed() = runTest {
        // Given
        val qualifiedIdToRemove = QualifiedID(value = "id", domain = "domain")
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulRemoveUserAsset()
            .arrange()

        // When
        userRepository.removeUserBrokenAsset(qualifiedIdToRemove).shouldSucceed()

        // Then
        coVerify {
            arrangement.userDAO.removeUserAsset(any())
        }.wasInvoked()
    }

    @Test
    fun whenObservingKnowUsers_thenShouldReturnUsersThatHaveMetadata() = runTest {
        // Given
        val (arrangement, userRepository) = Arrangement()
//             .withGetSelfUserId()
            .withDaoObservingByConnectionStatusReturning(
                listOf(
                    TestUser.DETAILS_ENTITY.copy(id = QualifiedIDEntity("id-valid", "domain2"), hasIncompleteMetadata = false),
                    TestUser.DETAILS_ENTITY.copy(id = QualifiedIDEntity("id2", "domain2"), hasIncompleteMetadata = true)
                )
            )
            .arrange()

        // When
        userRepository.observeAllKnownUsers().test {
            // Then
            awaitItem().also {
                val users = it.getOrNull()
                assertEquals(1, users!!.size)
                assertTrue { users.first().name == TestUser.ENTITY.name }
            }
            cancelAndIgnoreRemainingEvents()
        }

        coVerify {
            arrangement.userDAO.observeAllUsersDetailsByConnectionStatus(any())
        }.wasInvoked(once)
    }

    @Test
    fun whenObservingKnowUsersNotInConversation_thenShouldReturnUsersThatHaveMetadata() = runTest {
        // Given
        val (arrangement, userRepository) = Arrangement()
            .withDaoObservingNotInConversationReturning(
                listOf(
                    TestUser.DETAILS_ENTITY.copy(id = QualifiedIDEntity("id-valid", "domain2"), hasIncompleteMetadata = false),
                    TestUser.DETAILS_ENTITY.copy(id = QualifiedIDEntity("id2", "domain2"), hasIncompleteMetadata = true)
                )
            )
            .arrange()

        // When
        userRepository.observeAllKnownUsersNotInConversation(TestConversation.ID).test {
            // Then
            awaitItem().also {
                val users = it.getOrNull()
                assertEquals(1, users!!.size)
                assertTrue { users.first().name == TestUser.ENTITY.name }
            }
            cancelAndIgnoreRemainingEvents()
        }

        verify {
            arrangement.userDAO.observeUsersDetailsNotInConversation(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenUserIdWhenDefederateUser_thenShouldMarkUserAsDefederated() = runTest {
        // Given
        val (arrangement, userRepository) = Arrangement()
            .withMarkUserAsDefederated()
            .arrange()

        // When
        userRepository.defederateUser(TestUser.OTHER_FEDERATED_USER_ID).shouldSucceed()

        // Then
        coVerify {
            arrangement.userDAO.markUserAsDefederated(eq(TestUser.OTHER_FEDERATED_USER_ID.toDao()))
        }.wasInvoked(once)
    }

    @Test
    fun givenUserIds_WhenRequestingSummaries_thenShouldSucceed() = runTest {
        // Given
        val requestedUserIds = listOf(
            UserId(value = "id1", domain = "domain1"),
            UserId(value = "id2", domain = "domain2")
        )
        val knownUserEntities = listOf(
            TestUser.DETAILS_ENTITY.copy(id = UserIDEntity(value = "id1", domain = "domain1")),
            TestUser.DETAILS_ENTITY.copy(id = UserIDEntity(value = "id2", domain = "domain2"))
        )
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetUsersByQualifiedIdList(knownUserEntities)
            .arrange()

        // When
        userRepository.getUsersSummaryByIds(requestedUserIds).shouldSucceed()

        // Then
        coVerify {
            arrangement.userDAO.getUsersDetailsByQualifiedIDList(any())
        }.wasInvoked(once)
    }

    @Test
    fun givenANewSupportedProtocols_whenUpdatingOk_thenShouldSucceedAndPersistTheSupportedProtocolsLocally() = runTest {
        val successResponse = NetworkResponse.Success(Unit, mapOf(), HttpStatusCode.OK.value)
        val (arrangement, userRepository) = Arrangement()
            .withUpdateSupportedProtocolsApiRequestResponse(successResponse)
            .arrange()

        val result = userRepository.updateSupportedProtocols(setOf(SupportedProtocol.MLS))

        with(result) {
            shouldSucceed()
            coVerify {
                arrangement.selfApi.updateSupportedProtocols(any())
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.userDAO.updateUserSupportedProtocols(any(), any())
            }.wasInvoked(exactly = once)
        }
    }

    @Test
    fun givenANewSupportedProtocols_whenUpdatingFails_thenShouldNotPersistSupportedProtocolsLocally() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUpdateSupportedProtocolsApiRequestResponse(TestNetworkResponseError.genericResponseError())
            .arrange()

        val result = userRepository.updateSupportedProtocols(setOf(SupportedProtocol.MLS))

        with(result) {
            shouldFail()
            coVerify {
                arrangement.selfApi.updateSupportedProtocols(any())
            }.wasInvoked(exactly = once)
            coVerify {
                arrangement.userDAO.updateUserSupportedProtocols(any(), any())
            }.wasNotInvoked()
        }
    }

    @Test
    fun givenUserIdAndConversationId_whenUpdatingOneOnOneConversation_thenShouldCallDAOWithCorrectArguments() = runTest {
        val userId = TestUser.USER_ID
        val conversationId = TestConversation.CONVERSATION.id

        val (arrangement, userRepository) = Arrangement()
            .withUpdateOneOnOneConversationSuccess()
            .arrange()

        userRepository.updateActiveOneOnOneConversation(
            userId,
            conversationId
        ).shouldSucceed()

        coVerify {
            arrangement.userDAO.updateActiveOneOnOneConversation(eq(userId.toDao()), eq(conversationId.toDao()))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenDAOFails_whenUpdatingOneOnOneConversation_thenShouldPropagateException() = runTest {
        val exception = IllegalStateException("Oopsie Doopsie!")
        val (_, connectionRepository) = Arrangement()
            .withUpdateOneOnOneConversationFailing(exception)
            .arrange()
        val userId = TestUser.USER_ID
        val conversationId = TestConversation.CONVERSATION.id

        connectionRepository.updateActiveOneOnOneConversation(
            userId,
            conversationId
        ).shouldFail {
            assertIs<StorageFailure.Generic>(it)
            assertEquals(exception, it.rootCause)
        }
    }

    @Test
    fun givenUserDAOReturnsFailure_whenCallingGetKnownUserMinimized_thenReturnFailure() = runTest {
        val (_, userRepository) = Arrangement()
            .withUserDAOReturning(null)
            .arrange()

        val result = userRepository.getKnownUserMinimized(TestUser.USER_ID)

        result.shouldFail()
    }

    @Test
    fun givenUserDAOReturnsUserMinimized_whenCallingGetKnownUserMinimized_thenReturnUserMinimized() = runTest {
        val userMinimized = UserEntityMinimized(
            id = QualifiedIDEntity("id", "domain"),
            name = "Max",
            userType = UserTypeEntity.ADMIN,
            completeAssetId = null,
            accentId = 0
        )
        val (arrangement, userRepository) = Arrangement()
            .withUserDAOReturning(userMinimized)
            .arrange()

        val result = userRepository.getKnownUserMinimized(TestUser.USER_ID)

        coVerify {
            arrangement.userDAO.getUserMinimizedByQualifiedID(any())
        }.wasInvoked(exactly = once)
        result.shouldSucceed {
            assertIs<OtherUserMinimized>(it)
        }
    }

    @Test
    fun givenATeamMemberUser_whenFetchingUserInfo_thenItShouldBeUpsertedAsATeamMember() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUserDaoReturning(TestUser.DETAILS_ENTITY.copy(team = TestTeam.TEAM_ID.value))
            .withSuccessfulGetUsersInfo(TestUser.USER_PROFILE_DTO.copy(teamId = TestTeam.TEAM_ID.value))
            .withSuccessfulFetchTeamMembersByIds(listOf(TestTeam.memberDTO((TestUser.USER_PROFILE_DTO.id.value))))
            .arrange()

        val result = userRepository.fetchUserInfo(TestUser.USER_ID)

        assertIs<Either.Right<Unit>>(result)
        coVerify {
            arrangement.userDetailsApi.getUserInfo(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.userDAO.upsertUsers(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.userDAO.upsertConnectionStatuses(any())
        }.wasInvoked(exactly = once)
        coVerify {
            arrangement.userDAO.upsertUsers(any())
        }.wasNotInvoked()
    }

    @Test
    fun givenUserId_whenFetchingUserInfoFailed_thenItShouldInsertIncompleteUserData() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withFailingGetUserInfo()
            .arrange()

        userRepository.fetchUserInfo(TestUser.USER_ID)

        coVerify {
            arrangement.userDAO.insertOrIgnoreIncompleteUsers(any())
        }.wasInvoked()
    }

    @Test
    fun givenApiRequestSucceeds_whenFetchingUsersLegalHoldConsent_thenShouldReturnProperValues() = runTest {
        // given
        val userIdWithConsent = TestUser.OTHER_USER_ID.copy(value = "idWithConsent")
        val userIdWithoutConsent = TestUser.OTHER_USER_ID.copy(value = "idWithoutConsent")
        val userIdFailed = TestUser.OTHER_USER_ID.copy(value = "idFailed")
        val requestedUserIds = setOf(userIdWithConsent, userIdWithoutConsent, userIdFailed)
        val expectedResult = ListUsersLegalHoldConsent(
            usersWithConsent = listOf(userIdWithConsent to TeamId("teamId")),
            usersWithoutConsent = listOf(userIdWithoutConsent),
            usersFailed = listOf(userIdFailed),
        )
        val (arrangement, userRepository) = Arrangement()
            .withSuccessfulGetMultipleUsersApiRequest(
                ListUsersDTO(
                    usersFound = listOf(
                        TestUser.USER_PROFILE_DTO.copy(id = userIdWithConsent.toApi(), legalHoldStatus = LegalHoldStatusDTO.DISABLED),
                        TestUser.USER_PROFILE_DTO.copy(id = userIdWithoutConsent.toApi(), legalHoldStatus = LegalHoldStatusDTO.NO_CONSENT),
                    ),
                    usersFailed = listOf(userIdFailed.toApi()),
                )
            )
            .arrange()
        // when
        val result = userRepository.fetchUsersLegalHoldConsent(requestedUserIds)
        // then
        result.shouldSucceed {
            assertEquals(expectedResult, it)
        }
        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(eq(QualifiedUserIdListRequest(requestedUserIds.map { it.toApi() }.toList())))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestFails_whenFetchingUsersLegalHoldConsent_thenShouldPropagateError() = runTest {
        // given
        val requestedUserIds = setOf(TestUser.OTHER_USER_ID)
        val (arrangement, userRepository) = Arrangement()
            .withGetMultipleUsersApiRequestGenericError()
            .arrange()
        // when
        val result = userRepository.fetchUsersLegalHoldConsent(requestedUserIds)
        // then
        result.shouldFail()
        coVerify {
            arrangement.userDetailsApi.getMultipleUsers(eq(QualifiedUserIdListRequest(requestedUserIds.map { it.toApi() }.toList())))
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestSucceeds_whenPersonalUserUpgradesToTeam_thenShouldSucceed() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withRemoteGetSelfReturningDeletedUser()
            .withMigrateUserToTeamSuccess()
            .arrange()
        // when
        val result = userRepository.migrateUserToTeam("teamName")
        // then
        result.shouldSucceed()
        coVerify {
            arrangement.upgradePersonalToTeamApi.migrateToTeam(any())
        }.wasInvoked(exactly = once)
    }

    @Test
    fun givenApiRequestFails_whenPersonalUserUpgradesToTeam_thenShouldPropagateError() = runTest {
        // given
        val (arrangement, userRepository) = Arrangement()
            .withMigrateUserToTeamFailure()
            .arrange()
        // when
        val result = userRepository.migrateUserToTeam("teamName")
        // then
        result.shouldFail()
        coVerify {
            arrangement.upgradePersonalToTeamApi.migrateToTeam(any())
        }.wasInvoked(exactly = once)
    }

    private class Arrangement {
        @Mock
        val userDAO = mock(UserDAO::class)

        @Mock
        val clientDAO = mock(ClientDAO::class)

        @Mock
        val selfApi = mock(SelfApi::class)

        @Mock
        val userDetailsApi = mock(UserDetailsApi::class)

        @Mock
        val teamsApi = mock(TeamsApi::class)

        @Mock
        val sessionRepository = mock(SessionRepository::class)

        @Mock
        val selfTeamIdProvider: SelfTeamIdProvider = mock(SelfTeamIdProvider::class)

        @Mock
        val legalHoldHandler: LegalHoldHandler = mock(LegalHoldHandler::class)

        @Mock
        val upgradePersonalToTeamApi: UpgradePersonalToTeamApi =
            mock(UpgradePersonalToTeamApi::class)

        val selfUserId = TestUser.SELF.id

        val userRepository: UserRepository by lazy {
            UserDataSource(
                userDAO = userDAO,
                clientDAO = clientDAO,
                selfApi = selfApi,
                userDetailsApi = userDetailsApi,
                teamsApi = teamsApi,
                sessionRepository = sessionRepository,
                selfUserId = selfUserId,
                selfTeamIdProvider = selfTeamIdProvider,
                legalHoldHandler = legalHoldHandler,
                upgradePersonalToTeamApi = upgradePersonalToTeamApi,
            )
        }

        suspend fun withUserDAOReturning(value: UserEntityMinimized?) = apply {
            coEvery {
                userDAO.getUserMinimizedByQualifiedID(any())
            }.returns(value)
        }

        suspend fun withDaoObservingByConnectionStatusReturning(userEntities: List<UserDetailsEntity>) = apply {
            coEvery {
                userDAO.observeAllUsersDetailsByConnectionStatus(any())
            }.returns(flowOf(userEntities))
        }

        fun withDaoObservingNotInConversationReturning(userEntities: List<UserDetailsEntity>) = apply {
            every {
                userDAO.observeUsersDetailsNotInConversation(any())
            }.returns(flowOf(userEntities))
        }

        suspend fun withUpdateUserReturning() = apply {
            coEvery {
                userDAO.updateUser(any<PartialUserEntity>())
            }.returns(Unit)
        }

        suspend fun withSuccessfulGetUsersInfo(result: UserProfileDTO = TestUser.USER_PROFILE_DTO) = apply {
            coEvery {
                userDetailsApi.getUserInfo(any())
            }.returns(NetworkResponse.Success(result, mapOf(), 200))
        }

        suspend fun withFailingGetUserInfo() = apply {
            coEvery {
                userDetailsApi.getUserInfo(any())
            }.returns(NetworkResponse.Error(TestNetworkException.generic))
        }

        suspend fun withSuccessfulFetchTeamMembersByIds(result: List<TeamMemberDTO>) = apply {
            coEvery {
                teamsApi.getTeamMembersByIds(any(), any())
            }.returns(NetworkResponse.Success(TeamMemberListNonPaginated(false, result), mapOf(), 200))
        }

        suspend fun withSuccessfulGetUsersByQualifiedIdList(knownUserEntities: List<UserDetailsEntity>) = apply {
            coEvery {
                userDAO.getUsersDetailsByQualifiedIDList(any())
            }.returns(knownUserEntities)
        }

        suspend fun withUserDaoReturning(userEntity: UserDetailsEntity? = TestUser.DETAILS_ENTITY) = apply {
            coEvery {
                userDAO.observeUserDetailsByQualifiedID(any())
            }.returns(flowOf(userEntity))
        }

        suspend fun withDaoReturningNoMetadataUsers(userEntity: List<UserDetailsEntity> = emptyList()) = apply {
            coEvery {
                userDAO.getUsersDetailsWithoutMetadata()
            }.returns(userEntity)
        }

        suspend fun withRemoteGetSelfReturningDeletedUser(): Arrangement = apply {
            coEvery {
                selfApi.getSelfInfo()
            }.returns(NetworkResponse.Success(TestUser.SELF_USER_DTO.copy(deleted = true), mapOf(), 200))
        }

        suspend fun withSuccessfulGetMultipleUsersApiRequest(result: ListUsersDTO) = apply {
            coEvery {
                userDetailsApi.getMultipleUsers(any())
            }.returns(NetworkResponse.Success(result, mapOf(), HttpStatusCode.OK.value))
        }

        suspend fun withGetMultipleUsersApiRequestFederationNotEnabledError() = apply {
            coEvery {
                userDetailsApi.getMultipleUsers(any())
            }.returns(NetworkResponse.Error(federationNotEnabled))
        }

        suspend fun withGetMultipleUsersApiRequestGenericError() = apply {
            coEvery {
                userDetailsApi.getMultipleUsers(any())
            }.returns(NetworkResponse.Error(generic))
        }

        suspend fun withUpdateDisplayNameApiRequestResponse(response: NetworkResponse<Unit>) = apply {
            coEvery {
                selfApi.updateSelf(any())
            }.returns(response)
        }

        suspend fun withUpdateSupportedProtocolsApiRequestResponse(response: NetworkResponse<Unit>) = apply {
            coEvery {
                selfApi.updateSupportedProtocols(any())
            }.returns(response)
        }

        suspend fun withRemoteUpdateEmail(result: NetworkResponse<Boolean>) = apply {
            coEvery {
                selfApi.updateEmailAddress(any())
            }.returns(result)
        }

        suspend fun withSuccessfulRemoveUserAsset() = apply {
            coEvery {
                userDAO.removeUserAsset(any())
            }.returns(Unit)
        }

        suspend fun withSuccessfulGetAllUsers(userEntities: List<UserDetailsEntity>) = apply {
            coEvery {
                userDAO.getAllUsersDetails()
            }.returns(flowOf(userEntities))
        }

        suspend fun withSuccessfulGetMultipleUsers() = apply {
            coEvery {
                userDetailsApi.getMultipleUsers(any())
            }.returns(NetworkResponse.Success(value = LIST_USERS_DTO, headers = mapOf(), httpCode = 200) )
        }

        suspend fun withAllOtherUsersIdSuccess(
            result: List<UserIDEntity>,
        ) {
            coEvery {
                userDAO.allOtherUsersId()
            }.returns(result)
        }

        suspend fun withMarkUserAsDefederated() = apply {
            coEvery {
                userDAO.markUserAsDefederated(any())
            }.returns(Unit)
        }

        suspend fun withUpdateOneOnOneConversationSuccess() = apply {
            coEvery {
                userDAO.updateActiveOneOnOneConversation(any(), any())
            }.returns(Unit)
        }

        suspend fun withUpdateOneOnOneConversationFailing(exception: Throwable) = apply {
            coEvery {
                userDAO.updateActiveOneOnOneConversation(any(), any())
            }.throws(exception)
        }

        suspend fun withInsertOrIgnoreUsers() {
            coEvery {
                userDAO.insertOrIgnoreIncompleteUsers(any())
            }.returns(Unit)
        }

        suspend fun withGetTeamMemberSuccess(result: TeamMemberDTO) = apply {
            coEvery {
                teamsApi.getTeamMember(any(), any())
            }.returns(NetworkResponse.Success(result, mapOf(), 200))
        }

        suspend fun withMigrateUserToTeamSuccess() = apply {
            coEvery {
                upgradePersonalToTeamApi.migrateToTeam(any())
            }.returns(
                NetworkResponse.Success(
                    CreateUserTeamDTO("teamId", "teamName"),
                    mapOf(),
                    200
                )
            )
        }

        suspend fun withMigrateUserToTeamFailure() = apply {
            coEvery {
                upgradePersonalToTeamApi.migrateToTeam(any())
            }.returns(NetworkResponse.Error(generic))
        }

        suspend inline fun arrange(block: (Arrangement.() -> Unit) = { }): Pair<Arrangement, UserRepository> {
            coEvery {
                userDAO.observeUserDetailsByQualifiedID(any())
            }.returns(flowOf(TestUser.DETAILS_ENTITY))

            coEvery {
                selfTeamIdProvider()
            }.returns(Either.Right(TestTeam.TEAM_ID))
            coEvery {
                sessionRepository.updateSsoIdAndScimInfo(any(), any(), any())
            }.returns(Either.Right(Unit))
            withGetTeamMemberSuccess(TestTeam.memberDTO(selfUserId.value))
            coEvery {
                legalHoldHandler.handleUserFetch(any(), any())
            }.returns(Either.Right(Unit))
            apply(block)
            return this to userRepository
        }
    }

    private companion object {
        val SELF_USER = TestUser.SELF
    }
}
