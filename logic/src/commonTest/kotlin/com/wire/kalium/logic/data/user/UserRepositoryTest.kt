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
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.getOrNull
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
import com.wire.kalium.network.api.model.GenericAPIErrorResponse
import com.wire.kalium.network.api.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.AppDAO
import com.wire.kalium.persistence.dao.PartialUserEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserDetailsEntity
import com.wire.kalium.persistence.dao.UserEntityMinimized
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import com.wire.kalium.persistence.dao.client.ClientDAO
import com.wire.kalium.persistence.dao.member.MemberDAO
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.sequentiallyReturns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.matcher.eq
import dev.mokkery.matcher.matches
import dev.mokkery.mock
import dev.mokkery.verify
import dev.mokkery.verify.VerifyMode
import dev.mokkery.verifySuspend
import io.ktor.http.HttpStatusCode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest

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

        everySuspend {
            arrangement.userDAO.getUsersDetailsByQualifiedIDList(any())
        }.returns(knownUserEntities)

        userRepository.fetchUsersIfUnknownByIds(requestedUserIds).shouldSucceed()

        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDetailsApi.getMultipleUsers(matches { request: ListUserRequest ->
                (request as QualifiedUserIdListRequest).qualifiedIds.first() == missingUserId.toApi()
            })
        }
    }

    @Test
    fun givenAUserEvent_whenPersistingTheUser_thenShouldSucceed() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withUpdateUserReturning()
            .arrange()

        val result = userRepository.updateUserFromEvent(TestEvent.updateUser(userId = SELF_USER.id))

        with(result) {
            shouldSucceed()
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userDAO.updateUser(any<PartialUserEntity>())
            }
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
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDetailsApi.getMultipleUsers(eq(QualifiedUserIdListRequest(requestedUserIds.map { it.toApi() }.toList())))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDetailsApi.getMultipleUsers(eq(QualifiedUserIdListRequest(listOf(TestUser.OTHER_USER_ID.toApi()))))
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }
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
        verifySuspend(VerifyMode.exactly(2)) {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.selfApi.getSelfInfo()
            }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.allOtherUsersId()
        }

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDetailsApi.getMultipleUsers(eq(ListUserRequest.qualifiedIds(knownUserIds.map { userId -> userId.toApi() })))
        }
    }

    @Test
    fun givenMalformedQualifiedIdsInDb_whenListUsersReturnsBadRequest_thenShouldDeleteMalformedUsersAndRetry() = runTest {
        // Given
        val usersInDb = listOf(
            UserIDEntity(value = "id1", domain = "domain1.com"),
            UserIDEntity(value = "id2", domain = ""),
        )
        val expectedRequestIds = listOf(UserId("id1", "domain1.com").toApi())
        val (arrangement, userRepository) = Arrangement()
            .arrange {
                withAllOtherUsersIdSuccess(usersInDb)
            }
        val malformedQualifiedIdsError = KaliumException.InvalidRequestError(
            GenericAPIErrorResponse(
                code = 400,
                message = "Error in \$['qualified_ids'][1].domain: alphanumeric character: not enough input",
                label = "bad-request"
            )
        )
        everySuspend {
            arrangement.userDetailsApi.getMultipleUsers(any())
        } sequentiallyReturns listOf(
            NetworkResponse.Error(malformedQualifiedIdsError),
            NetworkResponse.Success(LIST_USERS_DTO, mapOf(), 200)
        )
        everySuspend {
            arrangement.userDAO.deleteUserByQualifiedID(any())
        }.returns(Unit)

        // When
        userRepository.fetchAllOtherUsers().shouldSucceed()

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.deleteUserByQualifiedID(eq(UserIDEntity(value = "id2", domain = "")))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDetailsApi.getMultipleUsers(eq(ListUserRequest.qualifiedIds(expectedRequestIds)))
        }
    }

    @Test
    fun givenMalformedQualifiedIdsInDb_whenListUsersReturns400WithNonBadRequestLabel_thenShouldDeleteMalformedUsersAndRetry() = runTest {
        // Given
        val usersInDb = listOf(
            UserIDEntity(value = "id1", domain = "domain1.com"),
            UserIDEntity(value = "id2", domain = ""),
        )
        val expectedRequestIds = listOf(UserId("id1", "domain1.com").toApi())
        val (arrangement, userRepository) = Arrangement()
            .arrange {
                withAllOtherUsersIdSuccess(usersInDb)
            }
        val malformedQualifiedIdsError = KaliumException.InvalidRequestError(
            GenericAPIErrorResponse(
                code = 400,
                message = "Error in \$['qualified_ids'][1].domain: alphanumeric character: not enough input",
                label = "bad-request"
            )
        )
        everySuspend {
            arrangement.userDetailsApi.getMultipleUsers(any())
        } sequentiallyReturns listOf(
            NetworkResponse.Error(malformedQualifiedIdsError),
            NetworkResponse.Success(LIST_USERS_DTO, mapOf(), 200)
        )
        everySuspend {
            arrangement.userDAO.deleteUserByQualifiedID(any())
        }.returns(Unit)

        // When
        userRepository.fetchAllOtherUsers().shouldSucceed()

        // Then
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.deleteUserByQualifiedID(eq(UserIDEntity(value = "id2", domain = "")))
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDetailsApi.getMultipleUsers(eq(ListUserRequest.qualifiedIds(expectedRequestIds)))
        }
    }

    @Test
    fun givenGenericBadRequestContainingQualifiedIdsAndDomain_whenFetchingAllOtherUsers_thenShouldNotTriggerCleanupRetry() = runTest {
        // Given
        val usersInDb = listOf(
            UserIDEntity(value = "id1", domain = "domain1.com"),
            UserIDEntity(value = "id2", domain = ""),
        )
        val (arrangement, userRepository) = Arrangement()
            .arrange {
                withAllOtherUsersIdSuccess(usersInDb)
            }

        val genericBadRequestError = KaliumException.InvalidRequestError(
            GenericAPIErrorResponse(
                code = 400,
                message = "Request invalid: qualified_ids has invalid domain",
                label = "bad-request"
            )
        )
        everySuspend {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }.returns(NetworkResponse.Error(genericBadRequestError))

        // When
        userRepository.fetchAllOtherUsers().shouldFail()

        // Then
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.userDAO.deleteUserByQualifiedID(any())
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.upsertUsers(
                matches { it.firstOrNull()?.name != null },
            )
        }
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
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.userDetailsApi.getMultipleUsers(any())
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.userDAO.upsertUsers(any())
        }
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
        verifySuspend(VerifyMode.atLeast(1)) {
            arrangement.userDAO.removeUserAsset(any())
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.observeAllUsersDetailsByConnectionStatus(any())
        }
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

        verify(VerifyMode.exactly(1)) {
            arrangement.userDAO.observeUsersDetailsNotInConversation(any())
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.markUserAsDefederated(eq(TestUser.OTHER_FEDERATED_USER_ID.toDao()))
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.getUsersDetailsByQualifiedIDList(any())
        }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.selfApi.updateSupportedProtocols(any())
            }
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.userDAO.updateUserSupportedProtocols(any(), any())
            }
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
            verifySuspend(VerifyMode.exactly(1)) {
                arrangement.selfApi.updateSupportedProtocols(any())
            }
            verifySuspend(VerifyMode.exactly(0)) {
                arrangement.userDAO.updateUserSupportedProtocols(any(), any())
            }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.updateActiveOneOnOneConversation(eq(userId.toDao()), eq(conversationId.toDao()))
        }
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

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.getUserMinimizedByQualifiedID(any())
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDetailsApi.getUserInfo(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.upsertUsers(any())
        }
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.upsertConnectionStatuses(any())
        }
        verifySuspend(VerifyMode.exactly(0)) {
            arrangement.userDAO.upsertUsers(any())
        }
    }

    @Test
    fun givenUserId_whenFetchingUserInfoFailed_thenItShouldInsertIncompleteUserData() = runTest {
        val (arrangement, userRepository) = Arrangement()
            .withFailingGetUserInfo()
            .arrange()

        userRepository.fetchUserInfo(TestUser.USER_ID)

        verifySuspend(VerifyMode.atLeast(1)) {
            arrangement.userDAO.insertOrIgnoreIncompleteUsers(any())
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDetailsApi.getMultipleUsers(eq(QualifiedUserIdListRequest(requestedUserIds.map { it.toApi() }.toList())))
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDetailsApi.getMultipleUsers(eq(QualifiedUserIdListRequest(requestedUserIds.map { it.toApi() }.toList())))
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.upgradePersonalToTeamApi.migrateToTeam(any())
        }
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
        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.upgradePersonalToTeamApi.migrateToTeam(any())
        }
    }

    @Test
    fun givenSelfUserEmail_whenCallingInsertOrIgnoreIncompleteUserWithOnlyEmail_thenShouldCallDAOWithCorrectArguments() = runTest {
        val email = "user@email.com"
        val (arrangement, userRepository) = Arrangement()
            .withInsertOrIgnoreIncompleteUserWithOnlyEmailSuccess()
            .arrange()

        userRepository.insertSelfIncompleteUserWithOnlyEmail(email).shouldSucceed()

        verifySuspend(VerifyMode.exactly(1)) {
            arrangement.userDAO.insertOrIgnoreIncompleteUserWithOnlyEmail(eq(arrangement.selfUserId.toDao()), eq(email))
        }
    }

    @Test
    fun givenDAOFails_whenCallingInsertOrIgnoreIncompleteUserWithOnlyEmail_thenShouldPropagateException() = runTest {
        val email = "user@email.com"
        val exception = IllegalStateException("Oopsie Doopsie!")
        val (_, connectionRepository) = Arrangement()
            .withInsertOrIgnoreIncompleteUserWithOnlyEmailFailing(exception)
            .arrange()

        connectionRepository.insertSelfIncompleteUserWithOnlyEmail(email).shouldFail {
            assertIs<StorageFailure.Generic>(it)
            assertEquals(exception, it.rootCause)
        }
    }

    private class Arrangement {

        val userDAO = mock<UserDAO>(mode = MockMode.autoUnit)
        val clientDAO = mock<ClientDAO>(mode = MockMode.autoUnit)
        val memberDAO = mock<MemberDAO>(mode = MockMode.autoUnit)
        val appDAO = mock<AppDAO>(mode = MockMode.autoUnit)
        val selfApi = mock<SelfApi>(mode = MockMode.autoUnit)
        val userDetailsApi = mock<UserDetailsApi>(mode = MockMode.autoUnit)
        val teamsApi = mock<TeamsApi>(mode = MockMode.autoUnit)
        val sessionRepository = mock<SessionRepository>(mode = MockMode.autoUnit)
        val selfTeamIdProvider: SelfTeamIdProvider = mock<SelfTeamIdProvider>(mode = MockMode.autoUnit)
        val legalHoldHandler: LegalHoldHandler = mock<LegalHoldHandler>(mode = MockMode.autoUnit)
        val upgradePersonalToTeamApi: UpgradePersonalToTeamApi = mock<UpgradePersonalToTeamApi>(mode = MockMode.autoUnit)

        val selfUserId = TestUser.SELF.id

        val userRepository: UserRepository by lazy {
            UserDataSource(
                userDAO = userDAO,
                clientDAO = clientDAO,
                memberDAO = memberDAO,
                appDAO = appDAO,
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
            everySuspend {
                userDAO.getUserMinimizedByQualifiedID(any())
            }.returns(value)
        }

        suspend fun withDaoObservingByConnectionStatusReturning(userEntities: List<UserDetailsEntity>) = apply {
            everySuspend {
                userDAO.observeAllUsersDetailsByConnectionStatus(any())
            }.returns(flowOf(userEntities))
        }

        fun withDaoObservingNotInConversationReturning(userEntities: List<UserDetailsEntity>) = apply {
            every {
                userDAO.observeUsersDetailsNotInConversation(any())
            }.returns(flowOf(userEntities))
        }

        suspend fun withUpdateUserReturning() = apply {
            everySuspend {
                userDAO.updateUser(any<PartialUserEntity>())
            }.returns(Unit)
        }

        suspend fun withSuccessfulGetUsersInfo(result: UserProfileDTO = TestUser.USER_PROFILE_DTO) = apply {
            everySuspend {
                userDetailsApi.getUserInfo(any())
            }.returns(NetworkResponse.Success(result, mapOf(), 200))
        }

        suspend fun withFailingGetUserInfo() = apply {
            everySuspend {
                userDetailsApi.getUserInfo(any())
            }.returns(NetworkResponse.Error(TestNetworkException.generic))
        }

        suspend fun withSuccessfulFetchTeamMembersByIds(result: List<TeamMemberDTO>) = apply {
            everySuspend {
                teamsApi.getTeamMembersByIds(any(), any())
            }.returns(NetworkResponse.Success(TeamMemberListNonPaginated(false, result), mapOf(), 200))
        }

        suspend fun withSuccessfulGetUsersByQualifiedIdList(knownUserEntities: List<UserDetailsEntity>) = apply {
            everySuspend {
                userDAO.getUsersDetailsByQualifiedIDList(any())
            }.returns(knownUserEntities)
        }

        suspend fun withUserDaoReturning(userEntity: UserDetailsEntity? = TestUser.DETAILS_ENTITY) = apply {
            everySuspend {
                userDAO.observeUserDetailsByQualifiedID(any())
            }.returns(flowOf(userEntity))
        }

        suspend fun withDaoReturningNoMetadataUsers(userEntity: List<UserDetailsEntity> = emptyList()) = apply {
            everySuspend {
                userDAO.getUsersDetailsWithoutMetadata()
            }.returns(userEntity)
        }

        suspend fun withRemoteGetSelfReturningDeletedUser(): Arrangement = apply {
            everySuspend {
                selfApi.getSelfInfo()
            }.returns(NetworkResponse.Success(TestUser.SELF_USER_DTO.copy(deleted = true), mapOf(), 200))
        }

        suspend fun withSuccessfulGetMultipleUsersApiRequest(result: ListUsersDTO) = apply {
            everySuspend {
                userDetailsApi.getMultipleUsers(any())
            }.returns(NetworkResponse.Success(result, mapOf(), HttpStatusCode.OK.value))
        }

        suspend fun withGetMultipleUsersApiRequestFederationNotEnabledError() = apply {
            everySuspend {
                userDetailsApi.getMultipleUsers(any())
            }.returns(NetworkResponse.Error(federationNotEnabled))
        }

        suspend fun withGetMultipleUsersApiRequestGenericError() = apply {
            everySuspend {
                userDetailsApi.getMultipleUsers(any())
            }.returns(NetworkResponse.Error(generic))
        }

        suspend fun withUpdateDisplayNameApiRequestResponse(response: NetworkResponse<Unit>) = apply {
            everySuspend {
                selfApi.updateSelf(any())
            }.returns(response)
        }

        suspend fun withUpdateSupportedProtocolsApiRequestResponse(response: NetworkResponse<Unit>) = apply {
            everySuspend {
                selfApi.updateSupportedProtocols(any())
            }.returns(response)
        }

        suspend fun withRemoteUpdateEmail(result: NetworkResponse<Boolean>) = apply {
            everySuspend {
                selfApi.updateEmailAddress(any())
            }.returns(result)
        }

        suspend fun withSuccessfulRemoveUserAsset() = apply {
            everySuspend {
                userDAO.removeUserAsset(any())
            }.returns(Unit)
        }

        suspend fun withSuccessfulGetAllUsers(userEntities: List<UserDetailsEntity>) = apply {
            everySuspend {
                userDAO.getAllUsersDetails()
            }.returns(flowOf(userEntities))
        }

        suspend fun withSuccessfulGetMultipleUsers() = apply {
            everySuspend {
                userDetailsApi.getMultipleUsers(any())
            }.returns(NetworkResponse.Success(value = LIST_USERS_DTO, headers = mapOf(), httpCode = 200))
        }

        suspend fun withAllOtherUsersIdSuccess(
            result: List<UserIDEntity>,
        ) {
            everySuspend {
                userDAO.allOtherUsersId()
            }.returns(result)
        }

        suspend fun withMarkUserAsDefederated() = apply {
            everySuspend {
                userDAO.markUserAsDefederated(any())
            }.returns(Unit)
        }

        suspend fun withUpdateOneOnOneConversationSuccess() = apply {
            everySuspend {
                userDAO.updateActiveOneOnOneConversation(any(), any())
            }.returns(Unit)
        }

        suspend fun withUpdateOneOnOneConversationFailing(exception: Throwable) = apply {
            everySuspend {
                userDAO.updateActiveOneOnOneConversation(any(), any())
            }.throws(exception)
        }

        suspend fun withInsertOrIgnoreUsers() {
            everySuspend {
                userDAO.insertOrIgnoreIncompleteUsers(any())
            }.returns(Unit)
        }

        suspend fun withGetTeamMemberSuccess(result: TeamMemberDTO) = apply {
            everySuspend {
                teamsApi.getTeamMember(any(), any())
            }.returns(NetworkResponse.Success(result, mapOf(), 200))
        }

        suspend fun withMigrateUserToTeamSuccess() = apply {
            everySuspend {
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
            everySuspend {
                upgradePersonalToTeamApi.migrateToTeam(any())
            }.returns(NetworkResponse.Error(generic))
        }

        suspend fun withInsertOrIgnoreIncompleteUserWithOnlyEmailSuccess() = apply {
            everySuspend {
                userDAO.insertOrIgnoreIncompleteUserWithOnlyEmail(any(), any())
            }.returns(Unit)
        }

        suspend fun withInsertOrIgnoreIncompleteUserWithOnlyEmailFailing(exception: Throwable) = apply {
            everySuspend {
                userDAO.insertOrIgnoreIncompleteUserWithOnlyEmail(any(), any())
            }.throws(exception)
        }

        suspend inline fun arrange(block: (Arrangement.() -> Unit) = { }): Pair<Arrangement, UserRepository> {
            everySuspend {
                userDAO.observeUserDetailsByQualifiedID(any())
            }.returns(flowOf(TestUser.DETAILS_ENTITY))

            everySuspend {
                selfTeamIdProvider()
            }.returns(Either.Right(TestTeam.TEAM_ID))
            everySuspend {
                sessionRepository.updateSsoIdAndScimInfo(any(), any(), any())
            }.returns(Either.Right(Unit))
            withGetTeamMemberSuccess(TestTeam.memberDTO(selfUserId.value))
            everySuspend {
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
